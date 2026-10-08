---
description: Verify this clone can actually run before starting work
allowed-tools: Bash(java -version), Bash(git status:*), Bash(git branch:*), Bash(ls:*), Bash(cat:*), Bash(./gradlew --version)
---

Every one of the projects this template came from broke on a fresh clone in a different way, and in
every case the explanation existed only in a `.bat` file comment or in someone's head. Check, in
order, and report what is missing rather than guessing:

1. `java -version` reports 17 or newer, and `./gradlew --version` runs Gradle 8.9 on it. AGP 8.7
   refuses older JDKs.
2. The Android SDK is found: `ANDROID_HOME` is set, or `local.properties` has `sdk.dir`, and that
   directory has `platforms/android-35`. Do not guess a path; say which is missing.
3. Nothing else is needed: the app reads no environment variables and has no runtime dependencies.
4. For installing: `adb devices` lists the tablet. Name the command that would provide anything missing.
5. `git status` is clean and I am on the branch I expect. Name the branch.

Then say, in one line, whether `./gradlew installDebug` will work. If it won't, say exactly what to run first.
Do not run `./gradlew assembleDebug` on your own initiative if the tree is dirty — tell me first.
