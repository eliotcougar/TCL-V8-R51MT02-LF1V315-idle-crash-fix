# Changelog

## 0.2.0

First public release of the focused, service-only workaround.

- Finish Android TV Home's temporary idle helper with its existing broadcast.
- Use bounded retries on screensaver entry and screen-off, with wake cancellation
  and generation tokens to reject callbacks from previous idle sessions.
- Recover service startup through boot, package replacement, and START_STICKY.
- Remove the earlier activity, launcher icon, toggle, counters, and visible
  diagnostics. Retain Android's minimal required foreground notification.
- Restrict explicit service invocation to privileged DUMP callers.
- Include original source, Java transition tests, a standalone SDK build script,
  investigation notes, installation/rollback instructions, and validation limits.

## 0.1.0 (local development)

Initial standalone service with an activity, enable/disable control, and
diagnostics. Used for the original real-TV idle/wake and reboot tests. Not
published as a separate binary release here.
