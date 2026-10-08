# AppGate

A tiny Android app that only lets video apps open during set hours, with a parent PIN guarding its
settings. Built for Kindle Fire (Fire OS 6 to 8) and Android Go tablets, and sideloaded.

## Quick start

Requires JDK 17+ and the Android SDK (platform 35). See `SETUP.md` for pointing Gradle at the SDK.

```
./gradlew test assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

On Windows use `.\gradlew.bat`. The full gate, before calling anything done, is
`./gradlew test assembleDebug lintDebug`. CI runs it on every push and attaches the debug APK to
the workflow run as the `app-debug` artifact.

Every build, local or CI, is signed with the shared key in `app/signing/`, so any of them installs
over any other on a tablet, keeping the PIN and rules. A local `./gradlew assembleRelease` gives the
shrunk APK.

## Releases: install from GitHub

Push a version tag and the Release workflow tests, builds and signs the APK, and publishes it as
`appgate-<version>.apk` on the repo's Releases page:

```
git tag v0.2.0
git push origin v0.2.0
```

No git handy? On GitHub (website or app), go to Actions > Release > Run workflow, enter the version
(`0.2.0`), and run it on `main`. It creates the tag for you.

Tags must be `vMAJOR.MINOR.PATCH`, each higher than the last. The tag becomes `versionName` and
`versionCode` (v1.2.3 is 10203). Android won't install a lower `versionCode` over a higher one, and
local and CI builds are `versionCode` 1. So once a tablet runs a release, update it only with a
newer release.

Each release carries the APK twice: `appgate-<version>.apk`, and `appgate.apk` so that
`https://github.com/AlteonSolutions/AppBlocker/releases/latest/download/appgate.apk` always downloads
the newest one. No GitHub account is needed (the repo is public).

**Updating a tablet:** unlock the browser by whatever means it is normally locked, open AppGate,
enter the PIN, tap **Download latest AppGate** under Parent tools, open the download and tap
**Install** (or **Update**). The PIN and rules are kept. The button also starts the 5-minute Settings
pass, in case Android asks to allow the browser to install apps. Then lock the browser again.

**First install** (before AppGate is on the tablet): open that same link in the tablet's browser.

## Layout

| Path | What it is |
|---|---|
| `app/src/main/java/com/alteon/appgate/` | All Kotlin. Android components plus framework-free logic. |
| `app/src/main/res/` | Layouts, strings, themes, the vector launcher icon, accessibility and device-admin config. |
| `app/src/main/AndroidManifest.xml` | Every activity, service and receiver must be registered here. |
| `app/src/test/java/com/alteon/appgate/` | JUnit 4 tests for the pure logic, run on the JVM. |
| `gradle/wrapper/`, `gradlew`, `gradlew.bat` | The pinned Gradle 8.9 wrapper. Always build through it. |
| `PROJECT_BRIEF.md` | The original brief: purpose, target devices, stack, decisions. |
| `STATUS.md` | Where things stand. |
| `docs/devices.md` | Per-device install and test notes. |

## Architecture

Zero runtime dependencies: framework classes only, no AndroidX, no Play Services.

| File | Role |
|---|---|
| `GateService` | AccessibilityService. On `TYPE_WINDOW_STATE_CHANGED`, asks `RuleStore.blockReason(pkg)`; if blocked, `GLOBAL_ACTION_HOME` then launches `BlockedActivity`. A 30 s recheck catches an app left open past closing time. Ignores systemui and the current keyboard. |
| `RuleStore` | SharedPreferences: video package set, weekday and weekend windows, parent override expiry, Settings session expiry. Owns the single allow/block decision. |
| `Schedule` / `TimeWindow` | Pure logic: is-open and next-opening. Unit tested. |
| `PinManager` / `LockoutPolicy` | Salted PBKDF2WithHmacSHA1 (20k iterations) PIN hash; lockout 1, 5, then 15 min after 5, 6, 7+ wrong tries. Policy unit tested. |
| `PinActivity` | Launcher. Create PIN (enter twice) or verify, then opens settings. |
| `SettingsActivity` | Not exported; finishes in `onStop` so leaving it re-locks. Setup buttons, app picker dialog, time windows, 30-min override, 5-min Settings pass, change PIN, lock. |
| `BlockedActivity` | Shows when videos reopen ("today at 3:00 PM", "tomorrow", weekday name), or that Settings is locked. |
| `AdminReceiver` | Device admin with no policies; exists only to block uninstall. |

Why it is built this way (accessibility service over polling, no overlay, no Device Owner, all of
Settings locked) is in `DECISIONS.md`.

## Set up a tablet

1. **Allow sideloading.** Fire: Settings > Security & Privacy > Apps from Unknown Sources. Android: allow installs from the source you use (Files, adb needs nothing).
2. **Install the APK** and open AppGate.
3. **Create the parent PIN.** Nothing is blocked until a PIN exists.
4. **Turn on blocker.** This opens Accessibility settings; enable "AppGate video schedule".
   - Android 13+ only: if the switch is greyed out ("restricted setting"), go to Settings > Apps > AppGate > ⋮ > Allow restricted settings, then try again.
5. **Turn on uninstall protection** (device admin).
6. **Choose video apps** and set the allowed hours. Don't add the browser if it is locked some other
   way: **Download latest AppGate** needs it to open.
7. **Lock and close.**

On Fire, if the kids use a separate profile, install and set up AppGate inside that profile. Accessibility services are per user.

## Forgot the PIN

```
adb shell pm clear com.alteon.appgate
```

This wipes the PIN and all rules. Then redo setup.
