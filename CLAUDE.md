# AppGate

Sideloaded Android app for a parent's kids' tablets (Kindle Fire, Android Go): video apps open only
during set hours, and a parent PIN guards AppGate's own settings.

## Facts

- JDK 17 is the only JVM target here. It appears in `app/build.gradle.kts` (`compileOptions` and
  `kotlinOptions.jvmTarget`), in CI (`setup-java`), and in none other. Those change in one commit or not at all.
- Build tool: the committed Gradle wrapper (Gradle 8.9). Always `./gradlew` (`.\gradlew.bat` on Windows),
  never a system `gradle`. Plugin and dependency versions are pinned in the build files; CI validates the
  wrapper jar's checksum.
- Layout: one Gradle module, `app/`. Kotlin in `app/src/main/java/com/alteon/appgate/`, resources in
  `app/src/main/res/`, JVM unit tests in `app/src/test/java/com/alteon/appgate/`.
- Shared types and schemas: single module, so none across modules. `BlockReason` lives in `RuleStore.kt`,
  `TimeWindow` and `Schedule` in `Schedule.kt`. Import them; never redeclare a shape locally.
- Zero runtime dependencies: framework classes only. No AndroidX, no Play Services. It must run on Fire OS
  (no Google services) and on low-RAM Android Go. JUnit 4 is the only (test) dependency.
- `./gradlew assembleDebug` on a fresh clone · `./gradlew installDebug` to work · `./gradlew test assembleDebug lintDebug` before saying anything is done.

## Working agreement

- Before running anything that changes state (migration, deploy, bulk script, `git` history), say in
  one plain sentence what it will do. Name a concept the first time it comes up; don't assume it.
- Never report a task complete without running `./gradlew test assembleDebug lintDebug` and stating the result. "Should pass"
  is not a result.
- A change that departs from a recorded decision gets a new dated entry in `DECISIONS.md` that
  explicitly supersedes the old one. Never silently contradict a recorded decision.
- Update the affected doc in the **same commit** as the code — never "later". If a doc and reality
  disagree, fix the doc in that commit.
- Any doc describing something not yet true (unapplied IaC, a planned integration) says so in its
  first line.

## Code

- Every non-trivial module opens with a block comment: why it exists and what breaks without it.
  If it exists because something failed once, name the failure.
- Put the reason for a number next to the number — limits, timeouts, page sizes, retry counts.
- Android components (`*Activity`, `GateService`, `AdminReceiver`) are entry points only, and they stay thin. Logic goes in
  framework-free Kotlin (`Schedule.kt`, `LockoutPolicy.kt`) with no `android.*` import, so JVM unit tests
  call it directly without a device or emulator.
- A new entry point also needs an entry in `app/src/main/AndroidManifest.xml`. Skipping it fails silently — nothing errors,
  the route just doesn't exist.
- Never create a second copy of logic. Where a copy is genuinely unavoidable (no build step, another
  runtime), prefix every copied symbol with `_` and name the source file in a comment above the block.
- When two similar things are deliberately *not* unified, say so in a comment at the site, or someone
  will "fix" it.
- All data access goes through `RuleStore` (rules) and `PinManager` (PIN). Their `SharedPreferences` handles are
  private and never read or written directly anywhere else, including in tests.
- Validate at every boundary and reject on failure: intent extras, values read back from preferences,
  anything a picker or dialog returns. Input from our own screens is not trusted.
- Errors carry a code and remediation text. Raw error to the logs, user-safe message to the UI.
  Never hand the client an error payload it will render as if it were data.
- A secondary side effect — email, notification, webhook, avatar fetch, analytics write — may never
  fail the primary action. Catch it, log it, continue.
- Never present a partial result as if it were complete. Return an explicit `truncated`/`partial`
  flag and surface it in the UI.
- No `TODO`/`FIXME`/`HACK` in committed code. Fix it, or record it as a dated entry in `DECISIONS.md`.
- Naming: `PascalCase.kt` for Kotlin (one top-level class per file) and `snake_case.xml` for resources, as Android requires;
  camelCase functions, PascalCase types, snake_case for preference keys and JSON fields.
- UI copy uses sentence case for headings and buttons. Match the existing screens exactly.

## Environment and secrets

- No secret, signed URL, token, keystore, or connection string in source. Ever — not "temporarily", not in a
  `.bat` file, not in a test fixture.
- The app reads no environment variables. The only per-machine setting is the Android SDK location:
  `ANDROID_HOME`, or `sdk.dir` in `local.properties` (gitignored, never committed). The build reads the
  optional `APPGATE_KEYSTORE_FILE`, `APPGATE_KEYSTORE_PASSWORD`, `APPGATE_KEY_ALIAS` and `APPGATE_KEY_PASSWORD`
  for release signing; they and the repository secrets behind them are documented in `SETUP.md`. A new
  variable gets documented there in the same commit.
- Build configuration is read only in `app/build.gradle.kts`. No scattered `BuildConfig` or `System.getenv` reads.
- A fresh clone must reach a working build with `./gradlew assembleDebug` and nothing else beyond JDK 17 and the
  Android SDK. If the SDK is missing, AGP fails fast and names `ANDROID_HOME` and `local.properties`.

## Tests and gates

- `./gradlew test assembleDebug lintDebug` compiles every source set (the Kotlin typecheck), runs Android Lint, and runs JUnit 4.
  There is no formatter yet (see `DECISIONS.md`).
- Tests exercise the framework-free logic from source; Gradle compiles before it tests, so there is no stale build.
  A unit test that touches `android.*` fails with "Method ... not mocked": move the logic out of the framework class instead.
- Test files mirror the source tree under `app/src/test/`. Fixtures are sanitized and committed; real
  customer data is not, in any format.
- The failures worth testing are the quiet ones: a wrong number that still renders, a dropped tab
  that still builds. Test the pure function, not the framework around it.
- CI runs the gate on every push. Every deploy job declares `needs: test`.

## Git and delivery

- Commit subject: imperative, sentence case, no conventional-commit prefix, no ticket id. An
  `Area: ` scope prefix is fine. Body is prose: the problem, the decision, the alternative rejected,
  the consequence. The body is not optional on a non-trivial change.
- Trailers on **every** commit, not most: `Co-Authored-By: Claude <model> <noreply@anthropic.com>` (the model
  that wrote the commit) and `Claude-Session: <url>`.
- Branches are `<kind>/<slug>` off `main`; agent sessions use `claude/<slug>`.
- Never `git reset --hard`, force-push, or `git clean` a tracked tree. Stash, and say that you did.

## Docs

- `README.md` — quick start, layout, architecture, tablet setup · `SETUP.md` — fresh-clone checklist ·
  `DECISIONS.md` — dated, newest first, supersede-don't-contradict · `STATUS.md` — where things
  stand, written to be read cold · `PROJECT_BRIEF.md` — the original brief · `docs/devices.md` — per-device install and test notes.
- The editable source of every generated or shipped asset (PDF, deck, image) lives in `docs/`.
  Generated output alone is a file we will lose. The launcher icon is a vector in `app/src/main/res/drawable/`, which is its own source.

## Pinned preferences
