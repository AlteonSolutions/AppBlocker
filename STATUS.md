# Status

_Last updated 2026-10-08._

## Where things stand

- All app code from the brief exists: blocker service, rule store, schedule, PIN with lockout,
  PIN, settings and blocked screens, device admin. See the architecture table in `README.md`.
- Pure logic (`Schedule`, `LockoutPolicy`) has 14 JUnit tests. Before import they had been compiled
  and passed outside Gradle, and all app Kotlin type-checked against the API 30 framework.
- **Not yet built with Gradle/AGP, and not yet run on any device.**

## Next

1. `./gradlew test assembleDebug lintDebug` green locally and in CI.
2. Install on one Fire tablet and one Android Go tablet and walk the setup in `README.md`. Record
   the results in `docs/devices.md`.

## Ideas for later (not committed to)

- Separate windows per weekday
- Daily total-minutes budget using `UsageStatsManager`
- Optional Device Owner "hard mode" with `setPackagesSuspended` (see the Device Owner entry in `DECISIONS.md`)
