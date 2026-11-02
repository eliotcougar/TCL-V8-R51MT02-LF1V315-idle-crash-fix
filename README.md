# TV Idle Fix for TCL V8-R51MT02-LF1V315

A tiny Android TV service that works around an idle-time launcher helper loop
observed on TCL firmware **V8-R51MT02-LF1V315**. The loop caused sustained
`system_server` load and queued window-management work, and was present before
watchdog-triggered Android framework restarts.

The service lets the Google launcher create its temporary helper, then asks that
helper to finish using the launcher's own existing broadcast. It needs **no root**
and leaves the launcher and screensaver installed and enabled.

**[Download the APK](https://github.com/eliotcougar/TCL-V8-R51MT02-LF1V315-idle-crash-fix/releases/latest)** ·
[Investigation](docs/INVESTIGATION.md) · [Validation](VALIDATION.md)

This is a targeted workaround, validated on one TV. It is not an official TCL or
Google update, a firmware repair, or evidence that every idle hang has this cause.

## The problem

The TV would become unresponsive while showing a screensaver and sometimes
appear to reboot without any remote input. In the investigated incidents, the
kernel kept running while Android's `system_server` was restarted by its
watchdog. A black screen or a frozen slideshow could precede the restart.

The investigation found an invisible Google Android TV Home activity,
`com.google.android.tvlauncher/.BootHelperEmptyActivity`, repeatedly resuming and
pausing behind the screensaver at about **2.8 cycles per second**. One captured
interval contained **555 pauses in 197 seconds**. Watchdog traces repeatedly
sampled message-queue operations in window-management paths while holding the
global window-manager lock, with `system_server` consuming approximately one
CPU core.

Changing the photo screensaver to Android's built-in **Colors** did not prevent
the failures. The helper loop also occurred behind Colors, so changing slideshow
images was not a sufficient explanation.

In a controlled live test, sending the helper's existing finish command stopped
the activity churn while Colors continued running. The UI queue drained from
174 messages to zero, and `system_server` CPU fell to approximately 0–2% after
the backlog cleared. Neither Android nor the kernel needed to restart for this
recovery. That demonstrated the helper loop's contribution to the idle load;
the precise framework compatibility defect and its role in every historical
watchdog remain unproven. See the [investigation notes](docs/INVESTIGATION.md).

## How the workaround works

Google's recommendations app launches the helper during screensaver/sleep
transitions. Code inspection suggests it is a temporary placeholder for boot,
sleep, and wake handoffs, including returning to Home or a previous app. The
helper registers a receiver for this existing action:

```text
com.google.android.tvlauncher.intent.action.FINISH_EMPTY_ACTIVITY
```

TV Idle Fix sends that action only to `com.google.android.tvlauncher`, with
`FLAG_RECEIVER_REGISTERED_ONLY`. The helper's own receiver calls `finish()`.
The fixer does not force-stop the launcher, disable its helper component, or
modify Google's APKs or Android framework files.

The foreground service listens for Android's `DREAMING_STARTED`,
`DREAMING_STOPPED`, `SCREEN_OFF`, and `SCREEN_ON` broadcasts:

1. On screensaver entry or screen-off, it schedules finish broadcasts at
   **0.5, 1.5, 3.5, 7, and 15 seconds** after the event. The early retries cover
   the interval before the helper has registered its receiver.
2. Wake or interactive screensaver exit cancels pending work immediately.
   A generation token also rejects stale callbacks from an earlier idle session.
3. Screensaver exit while the display remains off continues standby protection.
   Duplicate events cannot produce an unbounded retry burst.
4. At service startup, an already-off display starts an idle burst. An
   interactive display gets one catch-up broadcast after one second, covering
   a helper that was created before the service registered its receivers.

After the bounded burst there is no periodic polling. There are no alarms,
wake locks, network requests, accessibility service, or ADB client in the APK.
Boot and package-update broadcasts start the service; `START_STICKY` requests
normal Android service recovery if its process is killed.

Version **0.2.0** has no activity, launcher icon, toggle, counters, or diagnostics
screen. Android's required foreground-service notification remains quiet and
minimal. The package is still visible in Settings and installed-app managers.

## Compatibility

| Component | Investigated configuration |
| --- | --- |
| TCL firmware | **V8-R51MT02-LF1V315** (R51MT02 family) |
| Android | Android 11 / API 30 |
| Platform | Realtek RTD2851M / `rtd288o`, TCL BeyondTV4/BeyondTV6 identifiers |
| Android TV Home | `com.google.android.tvlauncher` **7.7.15-968255986-f**, versionCode `1010943512` |
| Android TV recommendations | `com.google.android.tvrecommendations` **7.4.0-848496343-f**, versionCode `1010926449` |
| Screensavers tested | Google Backdrop and Android Colors |
| Fixer package | `com.eliotcougar.tvidlefix`, versionCode **2** |
| APK minimum / target API | **26 / 30** |

Compatibility is scoped by the firmware and app versions above. Model names
such as C725, P725, and P815 do not establish compatibility for every regional
hardware or firmware variant. Only the configuration above was investigated directly.
R51MT02 and R851T02 are different identifiers. The broadcast is an internal
launcher implementation detail and may change with a Google app update.

## Install and start

Download `tv-idle-fix-0.2.0.apk` from
[Releases](https://github.com/eliotcougar/TCL-V8-R51MT02-LF1V315-idle-crash-fix/releases).
Enable debugging on the TV and authorize your computer. Use Android SDK
[Platform Tools](https://developer.android.com/tools/releases/platform-tools).

If several ADB devices are connected, select the TV with `adb -s TV_SERIAL ...`
for each command. For a TV offering network ADB, first use `adb connect TV_IP:5555`.
Use the endpoint shown by your TV; this app does not enable debugging or network ADB.

```sh
adb install -r tv-idle-fix-0.2.0.apk
adb shell am start-foreground-service -n com.eliotcougar.tvidlefix/.FixService
```

**Run the second command once after a fresh install.** There is no launcher
activity to open. Once started, boot and in-place package updates are handled by
the app's receivers. Existing users should update with the same signing key.

The explicit service entry point requires `android.permission.DUMP`, which
privileged ADB shell/system callers hold. This restricts who can invoke the
service; it does not give the fixer that permission. The app itself requests
only `RECEIVE_BOOT_COMPLETED` and `FOREGROUND_SERVICE`.

### Keep the service running

On the tested firmware, TCL's TGuard may unload background apps. Add **TV Idle
Fix** to the normal protected-app selection in **TCL Companion** and apply the
selection, if you use that management tool. Its installed-package list can find
the fixer despite the absence of a launcher icon. This repository does not bundle
TCL Companion or change TGuard policies itself.

TGuard and AppRecord remained enabled during the successful tests. Disabling
either is not an installation step or a requirement of this workaround.

### Check operation

```sh
adb shell dumpsys activity services com.eliotcougar.tvidlefix
adb shell dumpsys dreams
adb shell dumpsys activity activities
adb shell logcat -d -s TVIdleFix:E AndroidRuntime:E
```

The service dump should contain `.FixService` and `isForeground=true`. While
the screensaver is active, the helper should finish shortly after entry. A
historical helper name in an empty task is not evidence of a running activity.
The app logs unexpected failures only; an empty `TVIdleFix` error log is normal.

Check real sleep/wake, Home/Back, remote response, sound, and previous-app
restoration on your TV. Closing the helper participates in Google's wake/app
handoff, so those checks matter on other app or firmware versions.

## Disable or remove

To stop the workaround and its automatic startup reversibly:

```sh
adb shell pm disable-user --user 0 com.eliotcougar.tvidlefix
```

To enable and start it again:

```sh
adb shell pm enable --user 0 com.eliotcougar.tvidlefix
adb shell am start-foreground-service -n com.eliotcougar.tvidlefix/.FixService
```

To remove it, first remove its protected-app entry in TCL Companion if you added
one, then uninstall through Settings or:

```sh
adb uninstall com.eliotcougar.tvidlefix
```

No launcher, screensaver, firmware, or system-component rollback is needed.

## Build from source

The app uses Java and Android platform APIs, with no Gradle, AndroidX, Kotlin,
or third-party runtime dependency. The release toolchain was **Windows, JDK 22,
Python 3, Android platform 30, and Android build-tools 35.0.0**.

Install the Android command-line SDK and the `platforms;android-30` and
`build-tools;35.0.0` packages. Set `JAVA_HOME` to a modern JDK (JDK 22 was tested),
then run from the repository root:

```sh
python build.py --sdk-root PATH_TO_ANDROID_SDK
```

Alternatively set `ANDROID_HOME` and run `python build.py`. The script also
accepts `--java-home`, `--platform`, `--build-tools`, `--signing-dir`, and
`--output-dir`; `python build.py --help` lists the options. Windows builds were
verified; other host operating systems have not been exercised.

The legacy archive layout used during development is also supported:

```text
SDK_ARCHIVES/
  build-tools/android-15/       # contents of build-tools 35.0.0
  platform/android-11/          # contents of platform 30
```

```sh
python build.py --toolchain PATH_TO_SDK_ARCHIVES
```

The script runs the Java idle-session tests, compiles resources and sources,
generates DEX, aligns the APK, signs it, and verifies its signature. Outputs go
to `dist/`, including the APK, build metadata, and signature-verification text.

On the first build it creates a local signing key and a random password under
`signing/`. Keep that directory private and backed up. A locally generated key
cannot update the published APK in place; use the same key for your own future
builds, or uninstall the differently signed build before switching. The release
private key and password are not included in this repository.

## Release integrity and validation limits

The published **0.2.0 APK is 16,861 bytes**. SHA-256:

```text
071ebba709968b8e602e75a7c5e04d45b1756164cde4cc7d0a5670b4230a3d3f
```

Signing-certificate SHA-256:

```text
22ff5a7e68c38c5b2e6864f639274082ef074677d7399442f2682db8e46b201c
```

APK Signature Scheme v2 and v3 verification passed. A checksum file and the
signature-verification report accompany the release.

The original service passed real-TV screensaver, wake, remote, picture/sound,
and boot checks. Version 0.2.0 passed in-place update, service startup, no-launcher,
and natural screensaver-entry checks. A later inspection found no new watchdog
or ANR report, with the Android system process unchanged for about 19½ hours and
Backdrop running for almost four hours. The 0.2.0 simplification has **not** had
an additional reboot test. See [VALIDATION.md](VALIDATION.md) for the distinction.

This workaround does not address unrelated hardware, network, memory-management,
or firmware faults. Stock graphics warnings and attempts to start a separately
disabled TCL telemetry service remained in logs during the healthy observation
period. A short clean interval is not proof of a permanent cure.
