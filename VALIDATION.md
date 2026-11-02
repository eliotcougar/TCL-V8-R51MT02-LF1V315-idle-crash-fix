# Validation record

Last summarized: November 3, 2026. One physical TV running Android 11 and TCL
V8-R51MT02-LF1V315 was used. This is a record of completed checks, not a claim
that every hardware or firmware variant has been tested.

## Version 0.2.0: service-only release

- Package `com.eliotcougar.tvidlefix`, versionCode 2; signed APK 16,861 bytes.
- APK SHA-256:
  `071ebba709968b8e602e75a7c5e04d45b1756164cde4cc7d0a5670b4230a3d3f`.
  The APK installed on the TV matched the release artifact.
- APK Signature Scheme v2 and v3 verified. The signing certificate is the same
  as the earlier service build.
- The activity, both launcher entries, UI resources, toggle, persistent counters,
  success logs, and custom diagnostic dump were removed. The foreground-service
  notification remains minimal and has no control or activity intent.
- The APK requests only boot-completed and foreground-service permissions.
  The exported service is protected by the privileged DUMP caller permission.
- In-place update restarted the service. An idempotent privileged ADB start
  succeeded, and the service was foreground afterward.
- TV and regular launcher queries both returned no activities.
- The existing TGuard/AppBootPolicy protection remained semantically unchanged,
  and the running TGuard whitelist retained the fixer. TGuard and AppRecord
  stayed enabled.
- The update did not change kernel boot identity or `system_server` PID and was
  performed without waking the sleeping TV.
- A later authorized wake test left the existing five-minute idle timeout and
  Backdrop selection unchanged. No further input or manual dream-start command
  was sent. Backdrop started naturally; events showed the helper finishing by
  app request. The final UI queue was empty and the service remained foreground.

At approximately 22:55 MSK on November 2, a read-only inspection found:

- The same kernel and Android system process, with about 19½ hours of uptime
  since an earlier planned reboot.
- No new persistent DropBox watchdog or ANR entries compared with the 18:22
  baseline, and an empty retained crash buffer.
- The current Backdrop session running for almost four hours.
- No active helper, zero pending UI messages, and the fixer still foreground.
- Three brief samples showing 0% CPU for the fixer and `system_server`.

One background launcher SIGKILL was recorded at 19:01. Its sender was not
identified, and no accompanying crash/ANR or Android restart was recorded.
Backdrop continued. Stock graphics warnings, attempts to start a separately
disabled TCL telemetry service, and one Wi-Fi scan timeout remained in retained
logs. Log rings cover limited intervals; persistent reports were checked
separately. These results do not exclude every transient fault.

**An additional reboot/persistence test was explicitly deferred for version
0.2.0.** Its source includes boot startup, but the completed reboot test below
belongs to the earlier version.

## Earlier 0.1.0 service tests

- Real app-origin broadcasts finished the helper while Colors continued.
  An early broadcast arrived before receiver registration; a retry succeeded.
- Quick screensaver entry/exit cancelled pending retries. Java transition tests
  covered duplicate events, wake cancellation, stale callbacks, and dream exit
  while the display remains off.
- A real standby transition reached the service. ADB disconnected in standby;
  the user woke the TV with the physical remote and confirmed normal picture
  and remote response. The service process survived that cycle.
- A planned full reboot started the service automatically. At that time the app
  still had a UI and private preferences; its enabled state survived the reboot.
- Package replacement restarted the service, and another screensaver/wake smoke
  test passed after the final event-handling correction.
- The user confirmed normal picture, sound, Home, and Back after the reboot.
- TCL Companion's normal protected-app selection was used. Only the fixer was
  added to the relevant existing memory/boot protection lists; other selections
  were preserved. No management loop was added to the fixer.
- Google Backdrop was subsequently restored and confirmed active with the helper
  absent. The later 0.2.0 natural-entry and sustained-idle checks used Backdrop.

## Automated checks and limits

Before publication, the release source was built again with the supplied build
script. Java transition tests and APK v2/v3 signature verification passed. Every
ZIP-entry payload in the rebuilt APK matched the published APK, including DEX,
manifest, resources, and signature entries. Archive metadata/signing-block bytes
can differ between builds, so this is not a byte-for-byte reproducible-build
claim. The published asset is the original, hash-verified APK installed on the TV.
Only build-tool path/output selection was made portable for publication; runtime
Java, resources, and the manifest were unchanged from that installed build.

`build.py` runs `IdleSessionTest`, covering duplicate idle events, cancellation
on wake, stale generation tokens, and transitions from dreaming to standby.
The release contains the Java source, manifest, resources, and build script.
There is no network permission, accessibility service, root requirement,
periodic polling, alarm, or wake lock in the app.

The successful helper intervention establishes recovery from the observed idle
loop. Limited real-TV tests and uptime observations do not establish a permanent
fix for all watchdogs. Repeat sleep/wake, remote, Home/Back, previous-app
restoration, boot, and long-idle checks on any other configuration.
