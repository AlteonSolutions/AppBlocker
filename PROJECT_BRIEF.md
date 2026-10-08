# AppGate: project brief

Paste this into the INIT.md interview when creating the repo from `alteon-project-template`.

## Purpose

Personal-use Android app for Duke's kids' tablets at home. Video apps open only during an afternoon window; outside it they are bounced to a friendly "videos are closed" screen. A parent PIN protects AppGate's own settings.

## Target devices

- Kindle Fire tablets: Fire OS 6 (API 25), 7 (API 28), 8 (API 30). No Google Play Services.
- Android Go tablets: low-RAM devices; `SYSTEM_ALERT_WINDOW` is not grantable.
- Distribution: sideloaded APK only.

## Stack

- Kotlin, Gradle Kotlin DSL, AGP 8.7.3, Gradle 8.9, Kotlin 2.0.21
- minSdk 25, targetSdk 34, compileSdk 35
- Zero runtime dependencies: framework classes only, no AndroidX, no Play Services. JUnit 4 for tests.
- Package `com.alteon.appgate`

## Architecture

| File | Role |
|---|---|
| `GateService` | AccessibilityService. On `TYPE_WINDOW_STATE_CHANGED`, asks `RuleStore.blockReason(pkg)`; if blocked, `GLOBAL_ACTION_HOME` then launches `BlockedActivity`. 30 s recheck catches an app left open past closing time. Ignores systemui and the current keyboard. |
| `RuleStore` | SharedPreferences: video package set, weekday and weekend windows, parent override expiry, Settings session expiry. Owns the single allow/block decision. |
| `Schedule` / `TimeWindow` | Pure logic: is-open and next-opening. Unit tested. |
| `PinManager` / `LockoutPolicy` | Salted PBKDF2WithHmacSHA1 (20k iterations) PIN hash; lockout 1, 5, then 15 min after 5, 6, 7+ wrong tries. Policy unit tested. |
| `PinActivity` | Launcher. Create PIN (enter twice) or verify, then opens settings. |
| `SettingsActivity` | Not exported; finishes in `onStop` so leaving it re-locks. Setup buttons, app picker dialog, time windows, 30-min override, 5-min Settings pass, change PIN, lock. |
| `BlockedActivity` | Shows when videos reopen ("today at 3:00 PM", "tomorrow", weekday name), or that Settings is locked. |
| `AdminReceiver` | Device admin with no policies; exists only to block uninstall. |

## Decisions

- **AccessibilityService over UsageStats polling:** event-driven, near-zero battery on Go hardware, works on Fire OS.
- **Bounce plus full-screen activity, no overlay:** overlays aren't allowed on Android Go; a system-bound accessibility service is exempt from background-activity-start limits.
- **No Device Owner mode:** provisioning requires removing all accounts, impractical with Fire's Amazon account.
- **Block all of `com.android.settings` outside a PIN session:** one rule covers disabling the service, changing the clock, force-stop, revoking admin, and uninstall.
- **Browsers and app stores are not handled here:** already locked on the kids' tablets by other means.
- **Nothing blocks until a PIN exists:** setup can't lock the parent out.
- **Overnight windows unsupported:** start must be before end; simplifies next-opening logic.
- **No AndroidX:** keeps the APK around 1 MB or less.

## Status

- Pure logic (Schedule, LockoutPolicy) compiled and all 14 tests pass.
- All app Kotlin type-checks against the API 30 framework.
- **Not yet built with Gradle/AGP or run on a device.** First session task: `./gradlew test assembleDebug`, fix anything that comes up, then install on one Fire and one Go tablet and walk the setup in README.md.

## Ideas for later

- Separate windows per weekday
- Daily total-minutes budget using UsageStatsManager
- Optional Device Owner "hard mode" with `setPackagesSuspended`
