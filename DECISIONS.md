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

### 2026-10-14 – Show on the update button whether a newer version is out
**Context.** "Download latest AppGate" looked the same whether or not there was anything new, so the parent couldn't tell whether an update was waiting.
**Decision.** Each time settings opens, `UpdateCheck` sends a HEAD request to `github.com/.../releases/latest` and reads the version from the `releases/tag/vX.Y.Z` redirect, with no GitHub API or token. The button reads "Update available: X (you have Y)" in bold, "AppGate is up to date (Y)", or the plain label if the check fails. It always stays tappable. The download link moved into `UpdateCheck`, so it is defined once.
**Rejected.** The GitHub releases API: rate-limited without a token, and JSON parsing for one string. Checking in the background and notifying: AppGate has no notifications, and the parent only acts on it from settings anyway.
**Consequence.** Opening settings makes one small request to github.com. If GitHub changes that redirect, the button quietly falls back to the plain label. Supersedes: none.

### 2026-10-14 – Add the 5 pick-a-show minutes to the 1-hour grant only
**Context.** The extra 5 minutes went on every grant, so 15 and 30 minutes unlocked for 20 and 35. The parent wants it only on the 1-hour grant.
**Decision.** `GrantPolicy` adds 5 minutes to grants of 60 minutes or more. 15 and 30 are exact.
**Rejected.** Keeping it on every grant: not what the parent wants.
**Consequence.** Grants last 15, 30 and 65 minutes. This changes the earlier rationale that shorter grants need picking time too. Supersedes: 2026-10-14 (Add 5 minutes to every grant for picking a show).

### 2026-10-14 – Add 5 minutes to every grant for picking a show
**Context.** A grant usually comes before the kids have picked a show, so "1 hour" ended five minutes into the last episode they chose.
**Decision.** `GrantPolicy` adds 5 minutes to every grant: phone commands, notification buttons, and the tablet's own **Allow videos** button. The buttons keep their round numbers (15, 30, 60), and a grant still never shortens one already running longer.
**Rejected.** Extra time on the 1-hour grant only: shorter grants need picking time too. Relabelling the buttons 20, 35 and 65: odd numbers for the parent to think in.
**Consequence.** Grants last 20, 35 and 65 minutes. The phone shows the real end time. Supersedes: none. Superseded by 2026-10-14 (Add the 5 pick-a-show minutes to the 1-hour grant only).

### 2026-10-14 – Hide the PIN behind three taps on the logo on paired tablets
**Context.** The visible **Use PIN instead** button told kids a PIN existed and gave them a box to guess at. The PIN entry also appeared on its own whenever the phone request failed, so turning the Wi-Fi off was a way to reach it.
**Decision.** On a paired tablet the unlock screen shows only the logo, the title and **Ask my phone to unlock**. Three taps on the logo within 2 seconds reveal the PIN entry. A failed request says to check the Wi-Fi and never reveals the PIN. Unpaired tablets show the PIN as before.
**Rejected.** A long-press or a hidden corner: no more secret than three taps, and harder to remember. Removing the PIN: no Wi-Fi or no phone would lock the parent out.
**Consequence.** Anyone who knows the gesture, or reads this repo, can reach the PIN box. It still needs the PIN, and the wrong-PIN lockouts still apply. Supersedes: 2026-10-12 (Unlock AppGate's settings from the parent's phone, with the PIN as backup), only its visible **Use PIN instead** button.

### 2026-10-12 – Recheck at the exact moment a rule can change, instead of every 30 s
**Context.** Opening an app is already caught by Android's accessibility events. The 30 s recheck existed only for time running out while an app is open, yet it ran all day, allowed hours included, and still blocked up to 30 s late.
**Decision.** `RecheckPolicy` gives the next moment the answer for the app in front could change on its own: the window closing or a grant ending for a video app, the pass ending for Settings, and never for other apps. `GateService` sets one timer for that moment, re-set on every app switch. Rule changes (`RuleStore.listenForRuleChanges`, so phone commands too) and clock or time-zone changes recheck at once. The screen-off pause stays.
**Rejected.** A shorter or longer fixed interval: either more wasted work or later blocks. AlarmManager: the screen is on whenever this matters, so a main-thread timer is enough.
**Consequence.** Blocking lands at the exact minute. With a non-video app in front, or a video app already blocked, AppGate does no timed work. Supersedes: 2026-10-08 (Bounce blocked apps home and show a full-screen activity), only its "30 s recheck" consequence.

### 2026-10-12 – Unlock AppGate's settings from the parent's phone, with the PIN as backup
**Context.** The parent wants getting into AppGate's settings on a tablet to need their phone's approval, with the PIN only as a fallback.
**Decision.** On a paired tablet, the PIN screen leads with **Ask my phone to unlock**. It publishes an ntfy notification with one **Unlock settings** button, which posts `unlock|<nonce>` back. The PIN screen waits up to 3 minutes on the command topic for that nonce, then opens settings. **Use PIN instead** reveals the PIN entry. The background listener never acts on unlock messages, so an approval opens settings only on the screen that asked.
**Rejected.** Removing the PIN: no Wi-Fi or no phone would lock the parent out. A signed approval from the phone app: it would need AppGate on the phone to receive notifications in the background.
**Consequence.** Like video approvals, an unlock approval is unsigned and trusts the request nonce, so anyone reading the request topic could approve that one open request. Topic names stay secret. PIN lockouts don't apply to phone unlocks. Supersedes: none.

### 2026-10-12 – Pause the recheck and the remote connection while the screen is off
**Context.** The 30 s recheck and the held ntfy connection ran whether or not anyone was using the tablet. The connection's keepalives can wake a sleeping tablet's Wi-Fi.
**Decision.** `GateService` stops both on `ACTION_SCREEN_OFF`. On `ACTION_SCREEN_ON` it rechecks at once (hours or a grant may have ended meanwhile) and reconnects. `RemoteListener` uses a generation counter, so a stopped connection still blocked in a read can't run beside the new one.
**Rejected.** Keeping the connection open to receive grants sent while the tablet sleeps: those would mostly arrive stale anyway, past the 10-minute window.
**Consequence.** A command sent while the tablet's screen is off is missed, and the phone shows that the tablet didn't answer. A sleeping tablet does no AppGate work. Supersedes: none.

### 2026-10-12 – Count only activity windows as the app in front
**Context.** `GateService` remembered the package of the last window-state event as the app in front. A volume panel, dialog or overlay from another package replaced it, so the 30 s recheck stopped checking the video still playing underneath.
**Decision.** Only events whose class name is an activity of that package (looked up once with `PackageManager.getActivityInfo`, then cached) update the app in front. Every event is still checked for blocking.
**Rejected.** Reading the active window with `canRetrieveWindowContent`: it makes Android warn that AppGate can read the screen, for no other gain.
**Consequence.** An app's own popups no longer count as a switch. If a launcher shows its home screen without an activity event, the old app stays remembered until the next real switch. Supersedes: none.

### 2026-10-11 – Control tablets from the parent's phone through ntfy, signed with a pairing code
**Context.** The parent wants to grant video time (15, 30 or 60 minutes) and change settings from their Android phone, without walking to the tablet and typing the PIN, and to get a notification when a kid asks.
**Decision.** The same APK runs on the phone in parent mode, chosen on first launch. Each tablet shows a 26-character pairing code (128 bits) that the parent types into the phone once. Topics on ntfy.sh and an HMAC-SHA256 key are derived from it. Phone commands (allow, end, set hours, lock Settings, send status) are signed, timestamped and nonce'd. The tablet rejects bad signatures, anything older than 10 minutes, and repeated nonces, because ntfy caches and replays messages for 12 hours. "Ask a parent" on the blocked screen publishes an ntfy notification whose three buttons post an approval for that request's random nonce straight back to the tablet. The ntfy app delivers it, so AppGate on the phone needs no background service. The tablet's half runs inside `GateService`, the one part always running, over plain `java.net` HTTPS, so there is still no dependency. The PIN stays the fallback.
**Rejected.** Firebase push: needs Play Services, which Fire tablets lack. A separate parent app: a second codebase. Running our own server: something to host and keep alive. Unsigned commands on secret topic names: anyone who learned a name could unlock. Polling instead of a held connection: up to 30 s of delay on every tap.
**Consequence.** AppGate gains the INTERNET permission. The tablet holds one long-lived HTTPS connection, which costs a little battery on Go hardware. Remote control depends on ntfy.sh being up; when it isn't, the PIN still works. Notification approvals are unsigned and trust the request nonce, so anyone reading the request topic could approve that one request; topic names are derived from the secret and never committed. Choosing video apps stays on the tablet, because the list of installed apps is too big for ntfy's 4 KB messages. A device set up as the parent's phone never blocks anything. Supersedes: none.

### 2026-10-08 – Show the blocked screen first and go home when it is dismissed
**Context.** On a Kindle Fire, opening a blocked app only flickered: no "Videos are closed" screen. `GateService` sent `GLOBAL_ACTION_HOME` and then launched `BlockedActivity`. The home screen arrived after the blocked screen had opened, which stopped it, and `BlockedActivity` finishes itself on stop.
**Decision.** `GateService` launches `BlockedActivity` over the blocked app and does not go home itself. `BlockedActivity`'s OK and Back both go home, and it still finishes on stop. If the blocked screen fails to launch, the service falls back to going home.
**Rejected.** Keeping home-first and delaying the blocked screen: a timing guess that slower Fire hardware would beat. Not finishing on stop: the screen would then sit hidden behind the home screen, and still be invisible.
**Consequence.** While the message shows, the blocked app is stopped underneath rather than sent home. If a kid reaches it again through recent apps, it is bounced again. Supersedes: 2026-10-08 (Bounce blocked apps home and show a full-screen activity), only its order of home then screen; no overlay is still the rule.

### 2026-10-08 – Update tablets from an in-app download link instead of Obtainium
**Context.** Obtainium stayed installed on the kids' tablets to deliver updates. It can install any app, and the kids would poke at it. Updates are expected only a handful of times, while AppGate is being made to work properly.
**Decision.** A **Download latest AppGate** button under Parent tools (behind the PIN) opens the browser on `releases/latest/download/appgate.apk`. Every release uploads the APK under that fixed name as well as the versioned one, so the link never changes. The button also starts the 5-minute Settings pass, because the first browser install sends the parent to Android Settings, which AppGate otherwise blocks. The button shows the installed version.
**Rejected.** Keeping Obtainium: an installer the kids can reach. AppGate downloading and installing itself: no browser needed, but it needs the install-packages permission, a package-installer session and a status receiver. That is more code and more device risk than a link, for a handful of updates.
**Consequence.** The parent must unlock the browser around each update, and lock it again after. If the browser is one of AppGate's blocked video apps, the button is bounced outside the allowed hours. The Settings pass stays open for its 5 minutes after the browser opens, because Settings closes as soon as it is left. Supersedes: 2026-10-08 (Make the repo public), only its recommendation of Obtainium.

### 2026-10-08 – Make the repo public, with the shared signing key still committed
**Context.** Installing from GitHub on a tablet needs a login or an Obtainium token while the repo is private. The kids are young, and the app only ever runs on the family's own tablets.
**Decision.** Make the repo public and keep the committed signing key. Releases then download with no account, and Obtainium needs only the repo URL. A stranger still can't reach the tablets: an update needs someone to install it on the unlocked tablet, and only accounts with write access can publish this repo's releases.
**Rejected.** Staying private with a one-time Obtainium token: workable, but it protects against a risk that doesn't exist yet. Going public with the key moved back into secrets: brings back the setup the previous entry removed.
**Consequence.** Anyone can read how AppGate works and how to get around it, and anyone can sign an APK that installs as an AppGate update on a tablet they can use. The realistic risk is a kid who is old enough to go looking. When that day comes, make the repo private again; if it matters, also replace the key, which means an uninstall and fresh setup on every tablet. The owner's email in commit history and the first name in `PROJECT_BRIEF.md` are public. Supersedes: 2026-10-08 (Sign every build with one shared key committed to the private repo), only its condition that the repo stay private.

### 2026-10-08 – Sign every build with one shared key committed to the private repo
**Context.** The release pipeline kept its signing key in GitHub secrets. That needed a one-time `keytool` and four-secret setup, plus an off-GitHub backup of the key, and local and CI builds still used other keys that couldn't update a tablet running a release. AppGate is personal: sideloaded onto the family's own tablets, never published.
**Decision.** Commit one keystore, `app/signing/appgate.keystore` (alias and passwords `appgate`), and sign both debug and release builds with it. `release.yml` needs no secrets: a `vX.Y.Z` tag still runs the CI gate, builds, and publishes `appgate-X.Y.Z.apk`. CI checks on every push that the release APK's certificate matches the committed keystore. This is a deliberate exception to the no-keystore-in-source rule in `CLAUDE.md`, approved by the owner on the condition that the repo stays private.
**Rejected.** Keeping the key in repository secrets: setup and backup cost for a key that guards nothing worth it here. Building and installing only from one PC over USB: simplest of all, but it gives up installing from GitHub.
**Consequence.** Anyone with read access to the repo could sign an APK that installs as an AppGate update, though installing it still needs the unlocked tablet. Before the repo is ever made public, the key must be replaced, which means an uninstall and fresh setup on every tablet. Supersedes: 2026-10-08 (Publish releases to GitHub Releases, signed with one stable key from repository secrets). Its private-repo condition superseded by 2026-10-08 (Make the repo public).

### 2026-10-08 – Publish releases to GitHub Releases, signed with one stable key from repository secrets
**Context.** Tablets should install and update AppGate straight from GitHub. Each CI runner generates its own debug key, and Android refuses an update signed by a different key. The only way past that is an uninstall, which wipes the PIN and every rule.
**Decision.** Pushing a `vX.Y.Z` tag runs `release.yml`: the CI gate first (`needs: test`), then `assembleRelease` signed with a key held only in repository secrets, published as `appgate-X.Y.Z.apk` on a GitHub release. The tag sets `versionName`, and `versionCode = X*10000 + Y*100 + Z`, so every release is a valid update. The workflow fails rather than publish when a secret is missing. CI exercises the same signing path on every push with a throwaway key. Obtainium (with a read-only token, since the repo is private) is the recommended way to install and update on a tablet.
**Rejected.** Installing CI artifacts: they are zipped, need a GitHub login, and are signed by a different debug key each run. Committing a keystore: a secret in source. Making the repo public to allow anonymous downloads: not needed for one family, and it can be done later without changing the pipeline.
**Consequence.** The keystore and its passwords must be backed up outside GitHub: secrets can't be read back, and losing the key means an uninstall on every tablet. Debug and locally built APKs must not be installed on a tablet that runs releases. Minor and patch versions must stay below 100. Supersedes: 2026-10-08 (Ship as a sideloaded APK, with release builds signed by the debug key for now). Superseded by 2026-10-08 (Sign every build with one shared key).

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
