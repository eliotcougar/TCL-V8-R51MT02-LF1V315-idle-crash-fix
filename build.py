"""Build and sign TV Idle Fix using an installed Android SDK or SDK archives."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import shutil
import subprocess
import tempfile
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
manifest = ET.parse(ROOT / 'AndroidManifest.xml').getroot()
version = manifest.get('{http://schemas.android.com/apk/res/android}versionName')
version_code = int(manifest.get('{http://schemas.android.com/apk/res/android}versionCode'))
parser = argparse.ArgumentParser()
sdk_group = parser.add_mutually_exclusive_group()
sdk_group.add_argument('--sdk-root', type=Path,
                       help='Android SDK root; defaults to ANDROID_HOME or ANDROID_SDK_ROOT')
sdk_group.add_argument('--toolchain', type=Path,
                       help='Legacy archive layout: build-tools/android-15 and platform/android-11')
parser.add_argument('--build-tools', default='35.0.0')
parser.add_argument('--platform', default='android-30')
parser.add_argument('--java-home', type=Path, help='JDK root; otherwise use JAVA_HOME or javac on PATH')
parser.add_argument('--signing-dir', type=Path, default=ROOT / 'signing')
parser.add_argument('--output-dir', type=Path, default=ROOT / 'dist')
args = parser.parse_args()
if args.toolchain:
    tools = args.toolchain.resolve() / 'build-tools/android-15'
    android = args.toolchain.resolve() / 'platform/android-11/android.jar'
else:
    sdk_root = args.sdk_root or os.environ.get('ANDROID_HOME') or os.environ.get('ANDROID_SDK_ROOT')
    if not sdk_root:
        parser.error('Set ANDROID_HOME or pass --sdk-root (or --toolchain for extracted archives).')
    tools = Path(sdk_root).resolve() / 'build-tools' / args.build_tools
    android = Path(sdk_root).resolve() / 'platforms' / args.platform / 'android.jar'
java_home = args.java_home or os.environ.get('JAVA_HOME')
if not java_home:
    compiler = shutil.which('javac')
    if not compiler:
        parser.error('Set JAVA_HOME, pass --java-home, or put a JDK on PATH.')
    java_home = Path(compiler).resolve().parent.parent
java_bin = Path(java_home) / 'bin'
exe = '.exe' if os.name == 'nt' else ''
java = java_bin / ('java' + exe)
javac = java_bin / ('javac' + exe)
keytool = java_bin / ('keytool' + exe)
aapt2 = tools / ('aapt2' + exe)
zipalign = tools / ('zipalign' + exe)
for path in [aapt2, zipalign, tools / 'lib/d8.jar', tools / 'lib/apksigner.jar', android, java, javac, keytool]:
    if not path.is_file():
        raise SystemExit(f'Required build tool missing: {path}')

build_root = ROOT / 'build'
build_root.mkdir(exist_ok=True)
build = Path(tempfile.mkdtemp(prefix='apk-', dir=build_root))
dist = args.output_dir.resolve()
dist.mkdir(parents=True, exist_ok=True)

def run(command, env=None):
    result = subprocess.run([str(v) for v in command], cwd=ROOT, capture_output=True,
                            text=True, encoding='utf-8', errors='replace', env=env)
    if result.stdout.strip():
        print(result.stdout.strip())
    if result.stderr.strip():
        print(result.stderr.strip())
    if result.returncode:
        raise SystemExit(f'Build step failed: {Path(command[0]).name} ({result.returncode})')
    return result.stdout

test_classes = build / 'test-classes'
test_classes.mkdir()
run([javac, '--release', '8', '-d', test_classes,
     ROOT / 'src/com/eliotcougar/tvidlefix/IdleSession.java', ROOT / 'tests/IdleSessionTest.java'])
run([java, '-cp', test_classes, 'com.eliotcougar.tvidlefix.IdleSessionTest'])

compiled = build / 'resources.zip'
generated = build / 'generated'
generated.mkdir()
resource_apk = build / 'resources.apk'
run([aapt2, 'compile', '--dir', ROOT / 'res', '-o', compiled])
run([aapt2, 'link', '-o', resource_apk, '--manifest', ROOT / 'AndroidManifest.xml',
     '-I', android, '--java', generated, '--min-sdk-version', '26', '--target-sdk-version', '30', compiled])
classes = build / 'classes'
classes.mkdir()
sources = sorted((ROOT / 'src').rglob('*.java')) + sorted(generated.rglob('*.java'))
run([javac, '--release', '8', '-encoding', 'UTF-8', '-classpath', android, '-d', classes, *sources])
class_jar = build / 'classes.jar'
with zipfile.ZipFile(class_jar, 'w', zipfile.ZIP_DEFLATED) as archive:
    for path in sorted(classes.rglob('*.class')):
        archive.write(path, path.relative_to(classes).as_posix())
dex = build / 'dex'
dex.mkdir()
run([java, '-cp', tools / 'lib/d8.jar', 'com.android.tools.r8.D8', '--release', '--min-api', '26',
     '--lib', android, '--output', dex, class_jar])
unsigned = build / 'unsigned.apk'
with zipfile.ZipFile(resource_apk) as original, zipfile.ZipFile(unsigned, 'w') as archive:
    for item in original.infolist():
        archive.writestr(item, original.read(item.filename))
    archive.write(dex / 'classes.dex', 'classes.dex', compress_type=zipfile.ZIP_DEFLATED)
aligned = build / 'aligned.apk'
run([zipalign, '-f', '4', unsigned, aligned])

signing = args.signing_dir.resolve()
signing.mkdir(parents=True, exist_ok=True)
key = signing / 'tv-idle-fix.p12'
password_file = signing / 'password.txt'
if key.exists() != password_file.exists():
    raise SystemExit('Signing key/password pair is incomplete; restore the original pair before rebuilding.')
if not password_file.exists():
    password_file.write_text(secrets.token_urlsafe(32), encoding='utf-8')
environment = os.environ.copy()
environment['TV_IDLE_SIGN_PASSWORD'] = password_file.read_text(encoding='utf-8')
if not key.exists():
    run([keytool, '-genkeypair', '-keystore', key, '-storetype', 'PKCS12', '-alias', 'tv-idle-fix',
         '-storepass:env', 'TV_IDLE_SIGN_PASSWORD', '-keypass:env', 'TV_IDLE_SIGN_PASSWORD',
         '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
         '-dname', 'CN=TV Idle Fix local build', '-noprompt'], env=environment)
apk = dist / ('tv-idle-fix-' + version + '.apk')
run([java, '-jar', tools / 'lib/apksigner.jar', 'sign', '--ks', key, '--ks-key-alias', 'tv-idle-fix',
     '--ks-pass', 'env:TV_IDLE_SIGN_PASSWORD', '--key-pass', 'env:TV_IDLE_SIGN_PASSWORD',
     '--out', apk, aligned], env=environment)
verification = run([java, '-jar', tools / 'lib/apksigner.jar', 'verify', '--verbose', '--print-certs', apk])
(dist / ('signature-verification-' + version + '.txt')).write_text(verification, encoding='utf-8')
metadata = {'package': 'com.eliotcougar.tvidlefix', 'version': version, 'version_code': version_code, 'bytes': apk.stat().st_size,
            'sha256': hashlib.sha256(apk.read_bytes()).hexdigest(),
            'permissions': ['android.permission.RECEIVE_BOOT_COMPLETED', 'android.permission.FOREGROUND_SERVICE'],
            'build_directory': str(build)}
(dist / 'build.json').write_text(json.dumps(metadata, indent=2), encoding='utf-8')
(dist / ('build-' + version + '.json')).write_text(json.dumps(metadata, indent=2), encoding='utf-8')
print(json.dumps(metadata, indent=2))
