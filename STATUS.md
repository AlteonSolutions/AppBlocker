# Status

_Last updated 2026-10-08._

## Where things stand

- All app code from the brief exists: blocker service, rule store, schedule, PIN with lockout,
  PIN, settings and blocked screens, device admin. See the architecture table in `README.md`.
- Pure logic (`Schedule`, `LockoutPolicy`) has 14 JUnit tests.
- First Gradle/AGP build is green in CI (2026-10-08): `./gradlew test assembleDebug` and
  `./gradlew lintDebug` pass on JDK 17 with no changes to the imported app code. The debug APK is
  the `app-debug` artifact on each CI run.
- **Not yet built on the parent's Windows machine, and not yet run on any device.**

- Releases publish to GitHub (`release.yml`). Latest is 0.2.1. From 0.2.0, tablets update through
  **Download latest AppGate** under Parent tools, so Obtainium is no longer needed.
- On a Kindle Fire, 0.1.0 blocked apps with only a flicker and never showed the blocked screen.
  0.2.1 shows the blocked screen first and goes home when it is dismissed.
- 0.3.0 adds remote control from the parent's Android phone through ntfy: pairing, "Ask a parent"
  with 15/30/60-minute buttons, and phone-side controls for video time, hours and the Settings lock.
  The protocol is unit tested; ntfy itself could not be reached from the build environment.
- 0.4.0: unlocking AppGate on a paired tablet asks the parent's phone first, with the PIN as backup;
  the recheck and remote connection pause while the screen is off; popups no longer hide a video
  playing underneath from the recheck.
- 0.4.3: every grant includes 5 extra minutes to pick a show (60 min unlocks for 65).
- 0.4.2: on paired tablets the PIN is hidden; three quick taps on the logo reveal it.
- 0.4.1: the 30 s recheck is replaced by one timer at the exact moment the app in front could become
  blocked, plus an immediate recheck on any rule change (including from the phone).
- **Not yet verified on a device:** the blocked screen, the download-and-update flow, and the whole
  remote-control round trip (pairing, requests, unlock, commands, status).

## Next

1. `.\gradlew.bat test assembleDebug` locally, after pointing Gradle at the SDK (`SETUP.md`).
2. Install on one Fire tablet and one Android Go tablet and walk the setup in `README.md`. Record
   the results in `docs/devices.md`.

## Ideas for later (not committed to)

- Separate windows per weekday
- Daily total-minutes budget using `UsageStatsManager`
- Optional Device Owner "hard mode" with `setPackagesSuspended` (see the Device Owner entry in `DECISIONS.md`)
