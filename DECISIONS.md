# Decisions

Newest first. A change to a decision **supersedes** it — add a new entry, never edit or delete the
old one. Entry format:

```
### YYYY-MM-DD — <the decision in one imperative line>
**Context.** What forced the choice.
**Decision.** What we are doing.
**Rejected.** What we are not doing, and why.
**Consequence.** What this costs or constrains. Supersedes: <date, or "none">.
```

---

### 2026-10-08 – Publish releases to GitHub Releases, signed with one stable key from repository secrets
**Context.** Tablets should install and update AppGate straight from GitHub. Each CI runner generates its own debug key, and Android refuses an update signed by a different key. The only way past that is an uninstall, which wipes the PIN and every rule.
**Decision.** Pushing a `vX.Y.Z` tag runs `release.yml`: the CI gate first (`needs: test`), then `assembleRelease` signed with a key held only in repository secrets, published as `appgate-X.Y.Z.apk` on a GitHub release. The tag sets `versionName`, and `versionCode = X*10000 + Y*100 + Z`, so every release is a valid update. The workflow fails rather than publish when a secret is missing. CI exercises the same signing path on every push with a throwaway key. Obtainium (with a read-only token, since the repo is private) is the recommended way to install and update on a tablet.
**Rejected.** Installing CI artifacts: they are zipped, need a GitHub login, and are signed by a different debug key each run. Committing a keystore: a secret in source. Making the repo public to allow anonymous downloads: not needed for one family, and it can be done later without changing the pipeline.
**Consequence.** The keystore and its passwords must be backed up outside GitHub: secrets can't be read back, and losing the key means an uninstall on every tablet. Debug and locally built APKs must not be installed on a tablet that runs releases. Minor and patch versions must stay below 100. Supersedes: 2026-10-08 (Ship as a sideloaded APK, with release builds signed by the debug key for now).

### 2026-10-08 – Keep the APK free of runtime dependencies: no AndroidX, no Play Services
**Context.** Target tablets are Kindle Fire (no Google Play Services) and low-RAM Android Go devices, and the APK is sideloaded.
**Decision.** Framework classes only (`android.app.Activity`, `AlertDialog`, `TimePickerDialog`, `SharedPreferences`). `android.useAndroidX=false`. JUnit 4 is the only dependency, and it is test-only.
**Rejected.** AndroidX AppCompat/Material: most of a megabyte for widgets the framework already has on API 25+. Play Services: absent on Fire.
**Consequence.** The APK stays around 1 MB or less. Framework-theme UI only, and no `ViewModel`/`lifecycle` helpers, so activities stay small by design. Supersedes: none.

### 2026-10-08 – Support only same-day time windows
**Context.** An allowed window that crosses midnight complicates both the is-open check and the next-opening calculation shown on the blocked screen.
**Decision.** A window's start must be before its end. An inverted window counts as closed, and the settings screen rejects it with a message.
**Rejected.** Overnight windows: no current need, and they double the next-opening edge cases.
**Consequence.** "Videos 8 PM to 1 AM" is not expressible. Revisit with per-weekday windows (an idea in `PROJECT_BRIEF.md`). Supersedes: none.

### 2026-10-08 – Block nothing until a parent PIN exists
**Context.** If the blocker were active before a PIN existed, a half-finished setup could lock the parent out of Settings with no way back in.
**Decision.** `RuleStore.blockReason` returns allow for everything while no PIN is set.
**Rejected.** Shipping a default PIN: it would be guessable and never changed.
**Consequence.** An installed but unconfigured AppGate blocks nothing. The setup order in `README.md` puts PIN creation first. Supersedes: none.

### 2026-10-08 – Leave browsers and app stores to other controls
**Context.** A browser can reach video sites, and an app store can install a new video app the picker has never seen.
**Decision.** AppGate does not handle either. They are already locked on the kids' tablets by other means.
**Rejected.** URL filtering or store blocking in AppGate: duplicates existing controls and needs window-content access.
**Consequence.** AppGate alone is not a complete video block on a tablet where those other controls are off. Supersedes: none.

### 2026-10-08 – Lock all of `com.android.settings` outside a PIN session
**Context.** Settings lets a child disable the accessibility service, change the clock, force-stop AppGate, revoke device admin, or uninstall.
**Decision.** One rule: the whole Settings package is blocked unless a parent opened a 5-minute Settings pass from AppGate.
**Rejected.** Matching individual Settings screens: fragile across Fire OS and OEM builds, and each gap is a bypass.
**Consequence.** Kids can't reach harmless settings like brightness or Wi-Fi either, and the parent must open a pass from AppGate first. Supersedes: none.

### 2026-10-08 – Do not use Device Owner mode
**Context.** Device Owner would allow `setPackagesSuspended` and a far harder lock.
**Decision.** Use a plain device admin with no policies, only to block uninstall.
**Rejected.** Device Owner: provisioning requires removing every account, which is impractical with the Amazon account Fire tablets depend on.
**Consequence.** Enforcement depends on the accessibility service staying enabled, which is why Settings is locked. A "hard mode" stays on the ideas list. Supersedes: none.

### 2026-10-08 – Bounce blocked apps home and show a full-screen activity, with no overlay
**Context.** A blocked app has to be visibly stopped. Overlays need `SYSTEM_ALERT_WINDOW`, which Android Go does not grant.
**Decision.** On a blocked app, `GLOBAL_ACTION_HOME`, then launch `BlockedActivity`. A system-bound accessibility service is exempt from background-activity-start limits.
**Rejected.** A `TYPE_APPLICATION_OVERLAY` window over the app: unavailable on Go.
**Consequence.** The blocked app is backgrounded, not killed, and is re-bounced if reopened. A 30 s recheck catches an app left open past closing time. Supersedes: none.

### 2026-10-08 – Detect the foreground app with an AccessibilityService rather than UsageStats polling
**Context.** The blocker must notice an app opening, cheaply, on Go hardware and Fire OS.
**Decision.** `GateService` listens for `TYPE_WINDOW_STATE_CHANGED` events.
**Rejected.** Polling `UsageStatsManager`: costs battery on a timer, adds latency, and its permission flow is unreliable on Fire OS.
**Consequence.** The parent must enable the service by hand (on Android 13+, also "Allow restricted settings" for sideloaded apps). It is per user, so each Fire profile needs its own setup. Supersedes: none.

### 2026-10-08 – Ship as a sideloaded APK, with release builds signed by the debug key for now
**Context.** Personal use on a handful of family tablets; no store listing is planned. The template's deploy-target row assumes a server.
**Decision.** Deploy target is "none": `adb install` or copying the APK to the tablet. CI builds the debug APK and uploads it as a workflow artifact. `assembleRelease` is shrunk with R8 and signed with the debug key.
**Rejected.** Play Store and Amazon Appstore: review overhead for a private app, and an accessibility-service blocker draws policy scrutiny.
**Consequence.** No `needs: test` deploy job exists. The debug key differs per machine, so an APK built on another machine (or by CI) cannot install over one built locally without uninstalling first, which wipes the PIN and rules. A real keystore, kept out of the repo, would fix that. Supersedes: none. Superseded by 2026-10-08 (Publish releases to GitHub Releases).

### 2026-10-08 – Gate on compile, Android Lint and JUnit, and defer a Kotlin formatter
**Context.** The template's gate is typecheck + lint + format + tests, wired for npm. This project builds with Gradle.
**Decision.** Gate is `./gradlew test assembleDebug lintDebug`: compiling every source set is the typecheck, Android Lint (built into AGP, no new dependency) is the linter, and JUnit 4 runs on the JVM against the framework-free logic. The pre-commit hook runs only the Kotlin compile, because Lint is slow. Version scheme: integer `versionCode`, semver `versionName`.
**Rejected.** ktlint or Spotless now: either adds a build plugin, which the brief's no-dependency rule did not settle for build tooling. Instrumented (on-device) tests: need an emulator in CI for code that is mostly framework glue.
**Consequence.** Formatting is unchecked, and the "Linter + formatter" variant below stays open for the formatter half. Activities, the service and preferences are verified only by hand on a device. Supersedes: none.

### 2026-10-08 – Adapt the Node-oriented template to a single-module Android project
**Context.** The template assumes Node: `package.json` engines, a lockfile, workspaces, ESM, zod, `.env` with a config module. AppGate is Kotlin on Gradle.
**Decision.** Map each concept to its Gradle equivalent instead of leaving it unfilled. The runtime pin is JDK 17 (`app/build.gradle.kts` and CI). The "package manager" is the committed Gradle wrapper (8.9) with versions pinned in the build files (AGP 8.7.3, Kotlin 2.0.21, minSdk 25, targetSdk 34, compileSdk 35). One module, `app/`. Entry points are Android components registered in the manifest, and logic lives in framework-free Kotlin. Data access goes only through `RuleStore` and `PinManager`. There are no environment variables, so no `.env.example` or config module; validation means checking intent extras and stored values. Module system, shared-code build, migrations, `noUncheckedIndexedAccess` and LLM evaluation do not apply.
**Rejected.** Scaffolding `package.json`, `.env.example` and a setup script anyway: they would describe tooling the project does not use. A `setup` script: AGP already fails fast and names `ANDROID_HOME`/`local.properties` when the SDK is missing.
**Consequence.** `CLAUDE.md`'s Facts, Code and Environment sections use Android wording, and `.claude/` permissions use `./gradlew` patterns. A second Gradle module would need its own typecheck in the gate. Supersedes: none.

---

## Open variants

Every row below is something all four audited projects needed and answered differently, or answered
by accident. Settle the **decide first** rows before writing code; record each as a dated entry
above. A row left unanswered becomes a convention by default, which is how most of these got their
current answers.

### Decide first

| Item | Options seen across the four projects | Recommended default | Why |
|---|---|---|---|
| Linter + formatter | ESLint 9 flat + Prettier; none; none; none | ESLint flat + Prettier, config committed, `format:check` in the gate | Three of four listed "any linter at all" under what's missing before day one |

### Repo and code

| Item | Options seen | Recommended default | Why |
|---|---|---|---|
| Error-handling depth | Per-module error classes with HTTP status; typed codes with remediation text; gate objects + degrade-don't-throw; sparse local `try/catch` | Typed error codes + remediation text; secondary side effects never fail the primary action | Two projects converged on codes; the degrade rule was restated across six modules in the third |
| Logging | pino structured with event names; `console.*`; `console.*`; framework `context.log` | Structured logger with event-name strings | Only one project does it, but it is the only one that can answer "what failed last Tuesday" |
| Secret storage | `.env` + AES-256-GCM at rest for one webhook; Azure App Service config + GH Actions secrets; Function App settings + Key Vault; a live signed URL hardcoded in source | Platform secret store + GH Actions secrets; `.env` local only | The hardcoded-secret project is the cautionary case; never carry that pattern forward |
| Second code population | ES5 browser globals alongside modern Node tooling | Avoid; if unavoidable, state the constraint and mark duplicated symbols | Duplicated compute logic in three parallel copies meant the same bug was fixed by hand three times |

### Quality gates

| Item | Options seen | Recommended default | Why |
|---|---|---|---|
| Real deps vs mocks | Real Postgres with an RLS guard refusing privileged roles; Azurite emulator, self-skipping locally; Azurite booted by the test itself; none | Emulator/real dependency, self-skipping when unconfigured | Both projects that mocked nothing caught the bugs that mattered |
| Test fixtures | Sanitized workbooks committed with gitignore exceptions; none | Sanitized fixtures committed, format blocked globally | Otherwise the suite depends on files that exist on one machine |

### Git and delivery

| Item | Options seen | Recommended default | Why |
|---|---|---|---|
| Commit subject | `Area: imperative`; `Area: imperative`; imperative sentence case with occasional `Area:`; mixed, ~12% `type:` prefixes | Imperative sentence case, optional `Area:` scope, no conventional-commits | Three of four already do it; the fourth is the one with no convention at all |
| Commit body | Multi-paragraph why; multi-paragraph why; six-paragraph why; short | Prose body: problem, decision, rejected alternative, consequence | Called "the single strongest convention in the repo and documented nowhere" |
| Trailers | Both on 100% of commits; both on recent commits; both, inconsistently; none | Both, on every commit | Consistency is the variant worth copying, not the presence |
| Branch naming | `claude/<slug>`; `claude/<adjective-hash>` + a private data branch; `feature/<slug>`; `<kind>/<slug>` incl. `claude/`, `docs/`, `review/` | `<kind>/<slug>`, `claude/` for agent sessions | Superset of the others; agree the kinds up front |
| Delivery flow | Long-lived branch, no PRs; direct pushes to main; manual paste; PR merge commits, one commit per PR, no squash | PRs into trunk, squash, one branch per change | The long-lived-branch project needed 15 PRs to merge one branch |
| PR template / CODEOWNERS | None in any of the four | A short PR template: what changed, why, what you ran | Three listed it under missing-before-day-one |
| Where production data lives | A private `data-deploy` git branch merged at build; gitignored runtime files; n/a; cloud storage | Never a git branch | Works, but couples a data update to a deploy |
| Windows launcher | `start.bat` with `git fetch` + `reset --hard`; `start-backend.bat`; two `.bat` launchers; `start.bat` with `reset --hard` | Keep the launcher, drop the hard reset — refuse to start on a dirty tree | Present in three of four, and the reset silently destroys uncommitted work |

### Ops and product shape

| Item | Options seen | Recommended default | Why |
|---|---|---|---|
| Session/build-time tracking | Automated `SessionStart`/`SessionEnd` hook to an orphan branch; manual `TIMELOG.md` row per session; none; none | The automated hook | Two projects wanted the data; the manual one started months late and its earlier hours are unrecoverable estimates |
| Claude Code commands/agents | None in any of the four | Ship `/gate`, `/ship`, `/decision`, `/preflight`; no subagents until a repeated review actually exists | Every repeated operation was "a remembered incantation" in all four |
| `.mcp.json` | Absent in all four; session MCP tools came from the environment | None, and record that as the decision | Absent-by-accident and absent-by-decision look identical six months later |
| Local emulator | Azurite as a devDependency with a connection-string fallback; Azurite in CI only; Docker Compose Postgres; none | Emulator as a devDependency with a fallback so a clone runs unconfigured | The only project where a fresh clone just worked |
| Auth model | Session + role CHECK + row-level security; JWT + magic links; none; Entra + a per-page gate object | Project-specific | Genuinely determined by the product |
| Multi-tenancy | RLS with `SET LOCAL app.tenant_id`; tenant JSON file; none; partition-key-per-tenant | Enforce at the data layer, not the query site, when the database supports it | The RLS project could not leak across tenants even with a bug in a handler |
| Dev/seed endpoint gate | Three-state `DEV_TOOLS` (`on`/`off`/environment-detected), endpoints 404 when off | Copy the three-state pattern | "Do not let a synthetic record touch a real tenant" needed three enforcement points before it stuck |
| Side-effect integrations | SMTP + Teams webhooks behind a `*_LIVE` capture-vs-send flag; Resend; ACS email | Capture-vs-send flag, defaulting to capture | Both projects that send mail invented the same flag independently |
