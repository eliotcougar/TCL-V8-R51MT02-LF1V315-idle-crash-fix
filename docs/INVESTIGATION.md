# Investigation: idle activity loop and Android framework watchdogs

These notes summarize observations made on one TCL TV on November 1–2, 2026.
They distinguish the demonstrated trigger from the parts that remain hypotheses.
Only original fixer source is distributed here; copied Google/TCL APKs,
decompiled proprietary code, and private device logs are not included.

## Failure signature

The user observed long idle/wake delays, an unresponsive screensaver, and apparent
reboots. Persistent watchdog reports and kernel/process identity checks showed
that the investigated apparent reboots were Android framework restarts:
`system_server` was replaced, while kernel boot identity and uptime were retained.

Thirteen saved watchdog reports showed a broadly recurring queue/window-lock
signature. A runnable Binder thread was sampled in `MessageQueue.enqueueMessage`
or `MessageQueue.removeMessages`, called through window placement or
activity-pause paths. It held a queue lock and `WindowManagerGlobalLock`, while
other system services waited. The sampled `system_server` CPU was about 100% of
one core. Some reports predated the TV's package-cleanup experiments.

These samples identify where time was spent and locks were held. They do not
prove an infinite loop or corrupted linked list inside MessageQueue. An early
circular-queue hypothesis was weakened when queued work later drained without
restarting the process.

## A trigger shared by two screensavers

Switching Google Backdrop to Android's built-in Colors did not prevent new
watchdog restarts. A further failure occurred at 02:25 on November 2 with Colors
running. Before that incident, the event buffer contained 555 pauses for the same
`BootHelperEmptyActivity` between 02:22:18 and 02:25:35: about 2.8 per second,
with matching resume/stopping events.

A later live capture showed the loop continuing in the replacement system
process. Colors was the visible, focused window, while the hidden launcher
helper was being repeatedly resumed and paused in the activity-management state.

## What the helper does

Inspection of the installed Google app code found that:

- The recommendations app handles screensaver/sleep events and launches the
  launcher's `BootHelperEmptyActivity`.
- The helper installs an empty layout and registers an existing receiver for
  `com.google.android.tvlauncher.intent.action.FINISH_EMPTY_ACTIVITY`.
- The receiver calls the helper activity's `finish()` method. Google's own
  launcher/recommendations code also sends that finish action in normal paths.
- The helper's own `onResume()` did not contain a self-scheduled 3 Hz loop.

The surrounding code suggests a temporary placeholder task used during boot,
sleep, and wake handoffs, including restoring Home or the previous app. This
purpose is inferred from code, not an official description from Google.

Disabling the component would leave a caller that still tries to launch it.
The inspected launch path had no handler for a missing target, making an
uncaught `ActivityNotFoundException` a plausible consequence. That failure was
not deliberately provoked on the TV. Finishing a launchable helper uses the
existing completion path and avoids introducing a missing component.

## Controlled finish-command test

The following one-time diagnostic command was sent during the live loop:

```sh
adb shell am broadcast --user 0 -a com.google.android.tvlauncher.intent.action.FINISH_EMPTY_ACTIVITY -p com.google.android.tvlauncher
```

| Observation | Before | After |
| --- | --- | --- |
| Helper | Repeated resume/pause transitions | Finished; churn stopped |
| Screensaver | Colors running | Colors continued running |
| UI looper | 174 queued messages; oldest about 29 seconds late | Zero pending messages; polling normally |
| `system_server` CPU | Samples included 109% | Initially high while work drained; about 2% after one minute, later 0% samples |
| Kernel and system process | Running | Same boot identity and process; no restart |

This intervention demonstrated that closing the helper stopped the observed
activity churn and allowed the loaded system to recover. It does not prove that
every historical watchdog had this trigger, or fully explain the firmware's
queue/lock behavior.

## From diagnostic command to service

The next screensaver/sleep event can create another helper, so a one-time command
does not persist as protection. TV Idle Fix registers for those system events
and sends a short, bounded burst of the same finish action. The service uses
package-restricted, registered-receiver-only broadcasts.

The first real-TV service test showed why retries matter: a first finish
broadcast preceded receiver registration; a later broadcast finished the helper.
Wake events cancel remaining work, and generation tokens prevent old callbacks
from affecting a later session. No continuous polling or wake lock is required.

The exact source and Google app versions are listed in the main
[README](../README.md). The broadcast is an undocumented internal interface;
future app updates may change or remove it. The bounded retry timing was tuned
to this configuration, not established as universal.

## Remaining questions

- What precise Google-app/TCL-framework interaction causes repeated transitions?
- Do all historical watchdogs share this trigger, or are there additional causes?
- Do other launcher, recommendations, and regional firmware versions behave the same?
- Does closing the placeholder change previous-app restoration in other setups?
- Does a longer observation period remain free of the original failures?

TGuard/AppRecord management and unrelated stock firmware warnings are separate
issues. Their removal is not required by this fix. The original photo screensaver
was restored after the helper workaround was installed.
