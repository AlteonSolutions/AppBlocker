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

- GitHub Releases pipeline exists (`release.yml`), but **no release has been published yet**:
  the signing secrets are not set up (`SETUP.md`, "Release signing key").

## Next

1. `.\gradlew.bat test assembleDebug` locally, after pointing Gradle at the SDK (`SETUP.md`).
2. Install on one Fire tablet and one Android Go tablet and walk the setup in `README.md`. Record
   the results in `docs/devices.md`.

## Ideas for later (not committed to)

- Separate windows per weekday
- Daily total-minutes budget using `UsageStatsManager`
- Optional Device Owner "hard mode" with `setPackagesSuspended` (see the Device Owner entry in `DECISIONS.md`)
