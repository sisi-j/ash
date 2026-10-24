# The alpha: 0.1.0-alpha.1

*Designed 2026-10-24 in an interview with the product owner. Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

ash works, but only on its product owner's computer:
- **There's no way to get it.** Installers are built by hand on one machine. The website says ash "is not yet available to download".
- **There's no way to keep it current.** A fix means sending a new installer to everyone, so testers would drift onto old versions.
- **There's no way to hear back.** A tester has nowhere to report a problem, and nothing to send with the report.

Testing also needs two things ash doesn't have:
- **Mods.** Players can drop jars into an instance's `mods` folder by hand, but they can't find or install a mod from inside ash. The Mods page says "coming soon".
- **1.20.4.** It's a version the testers play, and ash's client exists only for 1.21.11 and 1.8.9.

The first few minutes and the game's defaults matter more to someone new than to the person who built them:
- The first run leaves a new player with an empty instance list.
- Every instance gets a fixed 2 GB of memory, whatever the computer has and however many mods it runs.

## Solution

The alpha is **0.1.0-alpha.1**, for a few people the product owner knows and a wider community where some may try it. It ships when everything below works. Phase 4's remaining steps (worn models, emotes) come after it.

- **Getting it:**
  - a download page on ashlauncher.com, pointing at the latest GitHub release;
  - unsigned, with the page explaining Windows' SmartScreen warning.
- **Keeping it current:**
  - every merge to `main` that changes the version is built, checked and published to testers on its own (ADR-0025);
  - ash offers each update with what's new, and never interrupts a running game.
- **Hearing back:**
  - a "Send feedback" link opens the product owner's Discord;
  - a "Copy report" button on errors and crashes puts what's needed on the clipboard. Nothing is sent automatically.
- **Mods:** the Mods page searches Modrinth for mods that fit an instance, including Legacy Fabric mods for 1.8.9. It installs them with their required dependencies, and turns each one on or off or removes it (ADR-0026).
- **1.20.4:** a third version target, with ash's whole client, tested in a real game like the others (ADR-0027).
- **A first run that ends at LAUNCH GAME:**
  - three instances (1.21.11, 1.20.4 and 1.8.9) are ready after sign-in;
  - a one-line note says it's an alpha and where to send feedback.
- **Game defaults:**
  - memory sized from the computer's RAM;
  - Java flags measured, and changed only where they help.

Everything Phase 4 has built ships in it: ash accounts, news, synced settings and capes.

## User Stories

### Getting and updating ash

1. As a tester, I want to download ash from ashlauncher.com, so that I get the current version from a place I trust.
2. As a tester, I want the download page to tell me what Windows' warning means and how to get past it, so that an unsigned installer doesn't stop me.
3. As a tester, I want ash to tell me when a new version is ready, with what's new in it, so that I get fixes without looking for them.
4. As a tester, I want to choose when an update installs, and never have it interrupt a game, so that updating fits around playing.
5. As the product owner, I want a version bump merged to `main` to reach testers without anything done by hand, so that a fix costs one merge.
6. As the product owner, I want a release to go out only if the real installer passed the installed-app test, so that automatic publishing can't ship an ash that doesn't start.

### Feedback

7. As a tester, I want a "Send feedback" link in ash, so that reporting a problem takes one click.
8. As a tester, I want a "Copy report" button wherever ash shows an error or a crash, so that I can paste what's needed without finding log files.
9. As a tester, I want to see what a report contains before I share it, so that I know what I'm sending.
10. As the product owner, I want the feedback link to come from ash's servers, so that I can change the invite without a release.

### Being an alpha

11. As a tester, I want ash to show it's an alpha, with its version in Settings, so that I know what to expect and can say which version I'm on.
12. As a new tester, I want one line on the first run saying things may break and where to say so, so that I know my feedback is wanted.

### Mods

13. As a player, I want to search Modrinth from the Mods page for mods that fit an instance's version and loader, so that I never install one that can't load.
14. As a player, I want one click to install a mod and everything it requires, so that I don't chase dependencies.
15. As a player, I want to see every mod in an instance, including jars I added by hand, with its name and version, so that I know what's in it.
16. As a player, I want to turn a mod off or remove it, so that I can find what's breaking something.
17. As a 1.8.9 player, I want to browse Legacy Fabric mods too, so that the Mods page works on every target.
18. As a player, I want to be told when a mod overlaps an ash feature, so that I can switch one of them off instead of running both.
19. As a player, I want a mod's file checked against Modrinth's hash before it's used, so that a broken or tampered download never loads.

### 1.20.4

20. As a 1.20.4 player, I want ash's full client there, the same features as on 1.21.11, so that the version I play isn't a lesser ash.
21. As a 1.20.4 player, I want capes, synced settings and the in-game settings to work there too, so that it's ash everywhere.

### First run and defaults

22. As a new tester, I want three instances, 1.21.11, 1.20.4 and 1.8.9, ready after I sign in, so that the first thing I see is LAUNCH GAME.
23. As a new tester on a second computer, I want my synced instances instead of three new ones, so that the first run doesn't duplicate what follows me.
24. As a player, I want the game given memory to suit my computer, so that a big machine isn't held to 2 GB and a small one isn't starved.
25. As a player, I want ash's Java settings to be ones that were measured to help, so that defaults aren't folklore.

## Implementation Decisions

### Releases and updates (ADR-0025)

- **The version is in one place.** `launcher/src-tauri/tauri.conf.json` holds it. A check in CI keeps the launcher's `package.json`, the Cargo packages and the client's `fabric.mod.json` files on the same number.
  - The client keeps a version of its own only if a reason appears.
  - *Assumption:* one version for the whole of ash keeps "which version are you on" a single answer.
- **What's new comes from `CHANGELOG.md`.** It has one section per version, and a release whose version has no section fails before building.
- **Publishing is automatic.** A push to `main` whose version has no GitHub release yet runs the release job:
  1. build the installer on Windows;
  2. sign the update files with the update key;
  3. run the installed-app test on that exact installer;
  4. publish a GitHub release with the installer, the update bundle, its signature and `latest.json`.

  Any failure publishes nothing.
- **Alpha releases are ordinary GitHub releases, not pre-releases.** GitHub's "latest release" link skips pre-releases, and both the updater and the download page read it.
  - The installer is uploaded a second time under a stable name, `ash-setup.exe`, so the download page's link never changes.
- **The updater is Tauri's** (`tauri-plugin-updater`). It reads `latest.json` from the latest release, and checks the update's signature against the public key built into ash.
  - The private key is a GitHub Actions secret. The product owner keeps an offline copy: if it's lost, no installed ash can ever be updated again.
- **Updates are offered, not forced.** When ash starts and a newer version exists, a quiet bar shows the version, its what's-new and an Update button.
  - Update downloads, installs and restarts ash, and is unavailable while a game ash started is running.
  - Later hides the bar until the next start.
  - A failed check is silent: it never costs a launch.
- **Unsigned.** No code-signing certificate for the alpha. Revisit before anything public (Out of Scope).

### Feedback

- **"Send feedback"** sits in the sidebar, under the pages, and opens the Discord invite in the browser.
  - The invite comes from ash's backend, `GET /v1/links`, and is edited on the admin page.
  - When the backend can't be reached, the last invite ash saw is used. Before it has seen one, the link isn't shown.
  - The invite itself is the product owner's to make. Until it exists, the link isn't shown.
- **"Copy report"** appears on the failed-launch panel, the crash panel and any page's error. It copies plain text: ash's version, Windows' version, the instance's version and loader, its mods on or off, the error's kind and message, and the last 200 lines of ash's log. For a crash it also copies the last 200 lines of the game's output.
  - The report is shown, scrollable, before it's copied. Tokens are already redacted in ash's log.
  - The game's output can include server addresses and player names, and the panel says so.

### Being an alpha

- An **"alpha"** tag beside the ash logo, and the full version (`0.1.0-alpha.1`) in Settings.
- **The first-run note** shows once, after the first sign-in: "This is an alpha: things may break. Tell us on Discord." It includes the feedback link, and dismissing it is remembered per machine.

### Mods (ADR-0026)

- **Modrinth only, through its public API** (`api.modrinth.com/v2`), with the User-Agent Modrinth asks for, naming ash, its version and a contact.
  - Search is filtered to mods for the instance's version and loader: `fabric` on 1.21.11 and 1.20.4, `legacy-fabric` on 1.8.9.
  - Vanilla instances say mods need a Fabric instance, and offer to make one.
- **Installing:**
  1. Pick the newest version of the mod for the instance's game version and loader.
  2. Download its primary file and check it against Modrinth's SHA-512.
  3. Put it in the instance's `mods` folder.
  4. Do the same for each required dependency, recursively.
  - Optional dependencies are listed, not installed.
  - An incompatibility Modrinth declares stops the install and says which mod it clashes with.
- **ash's bundled mods stand in for themselves.** A dependency on Fabric API (or Legacy Fabric API's modules) is already met by ash's bundled copy, so it's never installed twice.
  - *Assumption:* Legacy Fabric API's aggregator is what Modrinth's `legacy-fabric-api` project is. To be confirmed by the research ticket, which also covers mods that need modules ash doesn't ship.
- **Installing a mod turns the instance's third-party mods on.**
  - The `mods` folder stays the player's alone: ash puts a file there only when the player asks for that mod. Turning third-party mods on is the same thing the instance page's switch does, and the page says so when it happens.
  - *Assumption:* asked for the mod, a player wants it loaded. Leaving them off would make the install look broken.
- **The Mods page lists every jar in the folder.** Mods installed from Modrinth show Modrinth's name, version and icon. Jars added by hand show the name and version read from their own `fabric.mod.json`, and are marked as added by hand.
  - Each has an on/off switch, which renames the file to and from `.jar.disabled`, as other launchers do, and a remove.
  - Installed mods are recorded in `ash-mods.json` in the instance: Modrinth ids, version ids and hashes, for listing and, later, updating.
- **Overlap notes.** A mod whose Modrinth project is on ash's overlap list gets a note naming the ash feature it overlaps, such as a freelook mod and ash's freelook. The list lives in the launcher's repository.
  - Nothing is hidden or blocked: ash's features already step aside when a mod clashes with their hooks (ADR-0017).
- **Mods don't sync.** Synced settings carry instance definitions, not their mods. *Assumption:* an instance's mods are machine-local for the alpha, like its worlds.

### 1.20.4 (ADR-0027)

- **A third client project, `target-1.20.4`,** over the shared module, like the other two (ADR-0015).
  - Fabric Loader and Fabric API are pinned for 1.20.4 (ADR-0014).
  - It runs on the Java Mojang names for 1.20.4 (17), so the client builds for Java 17.
- **Every feature 1.21.11 has**, at the same hooks where 1.20.4 has them, found by a research ticket before any code: the readouts, crosshair, hit indicator, hit colour, freelook, snaplook, toggle sprint, faster clouds, faster view scan, the settings screen, the load report and capes.
  - A hook that 1.20.4 lacks is a degraded feature with its reason (ADR-0017), not a gap papered over.
- **Tested like the others:** the client game test runs on 1.20.4 in CI, and its FPS marks are measured in a benchmark sitting on the product owner's machine.

### First run

- **Three instances after the first sign-in:** 1.21.11, 1.20.4 and 1.8.9, each with ash's client, named after their version. They're made only if, after the first sync, the account still has no instances, so a second computer gets the account's instances instead.
- **Nothing downloads until LAUNCH GAME.** An instance nobody plays costs a line in the list.
- **The first-run path is walked and timed** on a clean Windows user before alpha.1: install, sign in, launch each target. Anything slow or unclear is fixed or ticketed.

### Game defaults

- **Memory is sized from the computer's RAM.** The numbers are set by measurement: how much heap modded 1.21.11 uses with a representative mod set, on a machine of each size. The starting point is 2 GB under 8 GB of RAM, 3 GB up to 16 GB, and 4 GB above that.
  - An instance's own setting overrides it, as now.
- **Java flags are measured, like boot time was:** each candidate flag set is benchmarked against the current flags, and ships only if it helps, with the evidence in `client/benchmark-results`.

### Legal surface

- **The privacy statement** adds two services ash now contacts:
  - **Modrinth**, for mod searches and downloads;
  - **GitHub**, for update checks and downloads.

  Both see the player's IP address, as Mojang's servers do. It ships with alpha.1.
- **The download page** says ash is an alpha, isn't signed yet, and isn't affiliated with Mojang or Microsoft.

## Testing Decisions

- **Release pipeline:** it can't be dry-run safely, since publishing is the effect. So:
  - the release job's steps run on every pull request, with publishing left out;
  - the first real release is alpha.1 itself;
  - the updater is proven end to end when alpha.2 reaches an installed alpha.1, on the product owner's machine.
- **Updater, in the launcher:**
  - the bar's states are in `ui-check`: available, downloading, failed, and blocked by a running game;
  - the decision to offer an update is unit-tested against `latest.json` fixtures.
- **Copy report:** the report's contents are unit-tested, including redaction. Its panel is in `ui-check`.
- **Mods:**
  - ash-core tests run against a fake Modrinth serving recorded API answers: search, version selection, dependency resolution, the hash check, the bundled stand-ins, incompatibility and the `.jar.disabled` switch;
  - reading `fabric.mod.json` from hand-added jars is tested against real jars in fixtures;
  - the Mods page's states are in `ui-check`.
- **1.20.4:**
  - the client game test runs on it in CI, as on 1.21.11;
  - its FPS marks follow the README's method;
  - capes get the same CI step as the other targets.
- **First run:** the three-instance rule is unit-tested, including the second-computer case where synced instances arrive.
- **Memory and flags:** measured with the evidence committed, and the RAM-to-memory rule is unit-tested.

## Out of Scope

- **Phase 4's remaining steps,** worn models and emotes, which come after the alpha.
- **Code signing,** until before a public release.
- **Mod updates, modpacks and syncing mods.** `ash-mods.json` records what updates will need.
- **Mods from anywhere but Modrinth,** such as CurseForge.
- **Versions beyond the three targets.**
- **macOS and Linux builds.**
- **Automatic crash reporting.**

## Further Notes

1. **Research before mods:** Modrinth's API as ash will use it. That means search facets for `fabric` and `legacy-fabric`, choosing versions, dependency types, hashes, rate limits and the User-Agent rule, and what Legacy Fabric API is on Modrinth.
2. **Research before 1.20.4:** where each of ash's hooks is in 1.20.4. That covers rendering (GuiGraphics, the cloud and culling code) and the skin and cape API, which changed in 1.20.2, plus whether 1.20.4's Fabric API has what ash's client calls.
3. **Needs the product owner:**
   - the Discord invite;
   - an offline copy of the update key, handed over when it's made;
   - a benchmark sitting for 1.20.4's FPS marks;
   - the clean-Windows-user first-run walk.
4. **Publishing is automatic by the product owner's choice.** The installed-app test on the real installer is the only gate, so a merge that bumps the version is a release.
5. **Spending:** none. GitHub Releases, Actions on the public repo and Modrinth's API are free.

## Tracker

Filed as [#139](https://github.com/sisi-j/ash/issues/139), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its 15 tickets are sub-issues, across this repository, [`sisi-j/ash-backend`](https://github.com/sisi-j/ash-backend) (#19) and `sisi-j/ash-site` (#4), with blocking links set. This file is the source of truth, and the issue is the tracker entry.
