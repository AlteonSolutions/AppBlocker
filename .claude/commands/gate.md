---
description: Run the full quality gate and report the real result
allowed-tools: Bash(./gradlew compileDebugKotlin:*), Bash(./gradlew lintDebug:*), Bash(./gradlew test:*), Bash(./gradlew assembleDebug:*)
---

Run the gate, in this order, and do not stop at the first failure — collect all of it:

1. `./gradlew compileDebugKotlin compileDebugUnitTestKotlin` — the Kotlin typecheck for app and test sources.
2. `./gradlew lintDebug` — Android Lint.
3. Format check: none yet (see `DECISIONS.md`). Say so in the report rather than skipping silently.
4. `./gradlew test assembleDebug` — JUnit 4 for both build types, then the debug APK.

Then report, in this shape and nothing longer:

- One line per step: pass/fail plus the failure count.
- For each failure: the file, the line, and what it means in plain words.
- If everything passes, say so plainly. Never write "should pass" or "expected to pass" — if you
  did not run it, say you did not run it.

Do not fix anything as part of this command unless I ask. Report first.
