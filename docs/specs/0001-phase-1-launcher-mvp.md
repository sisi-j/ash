# Phase 1 — Launcher MVP: sign in, prepare, launch vanilla on Windows

## Problem Statement

A player who wants to play competitive Minecraft today juggles the vanilla launcher for authentication, a separate tool for managing versions, and manual file shuffling to stop a 1.8.9 setup and a modern setup from corrupting each other. The vanilla launcher is slow to start, heavy while idle, and has no concept of keeping one version's resource packs away from another's.

Before ash can offer anything of its own, it has to do the boring part correctly: sign a player in, put the right files on disk, and start the game. If that part is not solid, nothing built on top of it matters.

## Solution

A launcher a player installs on Windows, signs into with their Microsoft account, and uses to launch unmodified Minecraft. They pick a version, ash creates an instance for it, downloads and verifies everything that version needs into a shared depot, and starts the game with the correct arguments as the selected account.

Each instance keeps its own game directory, so a 1.8.9 setup and a 1.21.x setup never touch each other's saves, config or resource packs. Downloaded jars, libraries and assets live once in the depot and are shared.

No client, no loader, no bundled mods, no backend, no cosmetics. Success is the game reaching its main menu, signed in as the right player, on both first-class version targets.

## User Stories

### Installing and first run

1. As a new player, I want to install ash from a single Windows installer, so that I can start without hunting for dependencies.
2. As a new player, I want ash to open in a couple of seconds, so that launching the game doesn't begin with waiting.
3. As a new player, I want ash to work without me installing Java myself, so that I don't have to learn which Java version each Minecraft version needs.
4. As a new player, I want it to be obvious that I must sign in before anything else, so that I'm not confused about why the play button does nothing.
5. As a returning player, I want ash to remember where it put things, so that reinstalling doesn't re-download the whole game.

### Signing in

6. As a player, I want to sign in with my Microsoft account, so that I'm using the account I already own Minecraft on.
7. As a player, I want a short code and a URL to enter it at, so that I can finish sign-in in my normal browser with my password manager available.
8. As a player, I want the code copyable in one click, so that I don't mistype it.
9. As a player, I want ash to show that it's waiting while I complete sign-in, so that I know it hasn't frozen.
10. As a player, I want ash to notice the moment I approve the sign-in, so that I don't have to come back and click something.
11. As a player, I want to be told plainly when a code expires and be given a fresh one, so that I can retry without restarting ash.
12. As a player, I want a clear message if my Microsoft account doesn't own Minecraft: Java Edition, so that I understand the problem is the account, not ash.
13. As a Game Pass subscriber, I want my entitlement recognised, so that I can play on a subscription rather than a purchase.
14. As a returning player, I want to stay signed in between sessions, so that I don't repeat the device-code flow every launch.
15. As a returning player, I want my session refreshed silently in the background, so that an expired token never interrupts a launch.
16. As a player, I want to be prompted to sign in again only when my refresh token is genuinely dead, so that I understand why and can fix it in one step.
17. As a security-conscious player, I want my tokens kept in the Windows credential store rather than a plain file, so that other software on my machine can't trivially read them.
18. As a player with more than one account, I want to add several Microsoft accounts, so that I can switch between my main and my alt.
19. As a player with several accounts, I want the active account visible at a glance, so that I don't join a server as the wrong player.
20. As a player, I want to choose which account a launch uses, so that I control which Minecraft profile appears in game.
21. As a player, I want to sign an account out and have its stored tokens removed, so that I can safely hand my machine to someone else.
22. As a player, I want each account shown with its Minecraft profile username and skin face, so that I can tell accounts apart without reading UUIDs.

### Choosing a version

23. As a player, I want to see the versions Mojang publishes, so that I don't have to know version ids by heart.
24. As a player, I want releases shown by default and snapshots behind a toggle, so that the list isn't dominated by builds I'll never play.
25. As a competitive player, I want 1.8.9 and the current 1.21.x release easy to find, so that the two versions I actually play aren't buried.
26. As a player, I want the version list to work from cache when I'm offline, so that I can still see what I already have.
27. As a player, I want to know when the cached list was last refreshed, so that I can tell whether a new release is missing or merely unfetched.

### Instances

28. As a player, I want to create an instance from a version, so that I have a named setup I can return to.
29. As a player, I want to name my instances, so that "ranked 1.8.9" and "smp 1.21" are distinguishable at a glance.
30. As a player, I want each instance to own its saves, config, resource packs and screenshots, so that a 1.8.9 pack never lands in a 1.21 instance.
31. As a player, I want game files shared between instances rather than duplicated, so that several instances don't cost several copies of the game.
32. As a player, I want each instance to show its version and when I last played it, so that I can find the right one quickly.
33. As a player, I want to rename an instance, so that a naming decision isn't permanent.
34. As a player, I want deletion to tell me exactly what will be lost, so that I don't destroy a world by accident.
35. As a player, I want deleting an instance to leave the depot alone, so that my other instances keep working.
36. As a player, I want to open an instance's game directory in Explorer, so that I can drop in a resource pack by hand.
37. As a player, I want to set how much memory an instance gets, so that I can tune it to my machine.
38. As a player, I want to set the game window size per instance, so that I get the resolution I play at.
39. As a player, I want machine-specific settings to stay on this machine, so that a memory figure from a 32GB desktop never follows me to an 8GB laptop.

### Preparing an instance

40. As a player, I want ash to fetch everything a version needs without me listing anything, so that I never have to know what a library or an asset index is.
41. As a player, I want meaningful progress while it downloads, so that I know whether to wait or make coffee.
42. As a player, I want downloads to run in parallel, so that thousands of small asset files don't take forever one at a time.
43. As a player, I want an interrupted download to resume rather than restart, so that a dropped connection doesn't cost me everything.
44. As a player, I want every file checked against its published hash, so that corruption fails now rather than as a baffling crash later.
45. As a player, I want a file that fails verification re-fetched automatically, so that I'm not left to diagnose it.
46. As a player, I want files already in the depot skipped, so that a second instance on the same version is nearly instant.
47. As a player, I want to cancel a preparation in progress, so that I'm not stuck waiting for something I started by mistake.
48. As a player, I want a failed preparation to name what failed and offer a retry, so that a transient network problem isn't a dead end.
49. As a player, I want the right Java runtime fetched for the version I'm launching, so that 1.8.9 and 1.21.x both work without me managing JDKs.

### Launching

50. As a player, I want one obvious button that starts the game, so that launching isn't a multi-step ritual.
51. As a player, I want the game to launch as the account I selected, so that I appear in game as the right player.
52. As a player, I want anything missing prepared automatically before launch, so that pressing play just works on a fresh instance.
53. As a player, I want visible feedback that the game is starting, so that I don't press play twice.
54. As a player, I want ash to detect that the game window has actually opened, so that the UI stops saying "launching" indefinitely.
55. As a player, I want ash to stay light while the game runs, so that the launcher isn't competing with Minecraft for resources.
56. As a player, I want to be told when the game exits, and told differently when it crashed, so that I know whether something went wrong.
57. As a player, I want the game's log output available after a crash, so that I can read the error or send it to someone who can.
58. As a player, I want to be stopped from launching the same instance twice at once, so that two processes don't corrupt the same world.
59. As a player, I want to run two different instances simultaneously, so that I can have 1.8.9 and 1.21.x open side by side.
60. As a player, I want ash to keep running after the game starts, so that I can launch another instance or check something.

### Failures, edge cases, diagnostics

61. As a player behind a corporate proxy, I want ash to honour my system proxy settings, so that downloads work on my network.
62. As a player whose disk fills mid-download, I want a clear out-of-space message, so that I know to free space rather than suspect ash.
63. As a player whose antivirus quarantines a file, I want a specific message naming the file, so that I can whitelist it.
64. As a player whose Windows username contains spaces or non-ASCII characters, I want launching to work anyway, so that my account name doesn't break the game.
65. As a player, I want ash to refuse to launch and say why when my account has no valid session, rather than starting a game that instantly fails.
66. As a developer, I want ash to write its own log file, so that I can diagnose a player's problem from an artefact they can send me.
67. As a developer, I want the exact JVM invocation recorded in that log, so that I can reproduce a launch problem by hand.
68. As a developer, I want access tokens and credentials redacted from logs, so that a player sharing a log doesn't leak their account.

## Implementation Decisions

### Shape

- All launcher behaviour lives in **ash-core**, a plain Rust library crate with no Tauri dependency. The Tauri layer is a thin command adapter: each command delegates to one ash-core operation and does nothing else. This keeps the whole product testable without a UI or a webview.
- Two outbound ports are injected into ash-core rather than called directly:
  - an **HTTP port** covering all network access (Microsoft identity, Xbox Live, Minecraft Services, the Mojang version manifest, the Mojang CDN, the Java runtime manifest);
  - a **process port** covering spawning and observing the game process.
- Depot root and instances root are ordinary configuration values, not seams — tests point them at a temporary directory.

### Modules and their operations

- **Accounts** — begin sign-in, poll sign-in, list accounts, select the active account, remove an account, refresh a session. Owns the full token chain and credential storage.
- **Catalogue** — refresh the version catalogue from Mojang's manifest, list versions with release/snapshot filtering, report cache age.
- **Instances** — create, list, get, rename, delete, update settings, reveal the game directory.
- **Depot** — resolve what a version needs, download with bounded concurrency, verify, resume, deduplicate, report typed progress, cancel.
- **Runtime** — select and provision the Java runtime a version requires.
- **Launch** — resolve version metadata, evaluate rules, assemble the classpath, extract natives, template the arguments, spawn, and track lifecycle.

### Authentication

- The device-code flow runs the full chain: Microsoft device code → Microsoft access token → Xbox Live → XSTS → Minecraft Services → entitlement check → Minecraft profile.
- The entitlement check is mandatory and its failure is a distinct, user-facing outcome, not a generic error. It must distinguish "signed in but owns no copy" from "sign-in failed".
- Refresh tokens are stored in the Windows credential store, never in a plaintext file alongside config.
- Accounts are keyed by **Minecraft profile UUID**, per ADR-0009. The Microsoft account identifier is never used as a key.
- There is no offline or cracked path, per ADR-0007. It must not exist even as a stub or a feature flag.

### Storage

- Isolated game directory per instance, single shared content-addressed depot, per ADR-0008.
- The depot stores version metadata and jars, libraries, asset indexes, asset objects and Java runtimes. Instances reference depot content; they never own copies of it.
- Deleting an instance never touches the depot. Depot reclamation is a separate concern and is out of scope here.

### Version metadata and launch construction

- Both argument formats must be supported: the legacy single argument string used by 1.8.9, and the modern structured game/JVM argument lists with conditional rules used by 1.21.x. This is the main reason both targets belong in Phase 1 — one format alone produces an abstraction that will not survive the other.
- Library rules are evaluated for operating system and architecture before a library reaches the classpath. Natives are extracted where the version requires it.
- Java runtimes are provisioned by ash from Mojang's runtime manifest. System Java is ignored entirely, including whatever happens to be on PATH.
- The assembled classpath, main class, JVM arguments and game arguments are produced as a single value that can be inspected before anything is spawned. This is what makes launch assertable without running Minecraft.

### Cross-cutting

- Every downloaded artifact is verified by published hash and size. Mismatch triggers one automatic re-fetch, then a typed failure.
- Progress is reported as a stream of typed events, not an opaque percentage — the UI derives its own display from them.
- Errors are a typed enum mapped to user-facing messages at the adapter boundary. No raw error strings reach the UI.
- A lock in the game directory prevents a second concurrent launch of the same instance while permitting concurrent launches of different instances.
- Logs redact tokens and credentials at the point of writing, not by post-processing.

## Testing Decisions

### What makes a good test here

A test drives ash-core's public API and asserts only on what a player could observe: which accounts exist and how they are identified, which files ended up in the depot, the progress events emitted, the recorded JVM invocation, and the typed error returned. It never asserts on internal call sequences, private structure, or module layout. Any refactor that does not change player-visible behaviour must leave every test passing.

### The seam

One inbound seam: **ash-core's public API**. Two outbound ports are faked:

- the **HTTP port** serves recorded fixtures for the Microsoft chain, the version manifest, the CDN and the runtime manifest — so no test touches the network;
- the **process port** records the invocation and simulates lifecycle transitions — so no test spawns a JVM.

Depot and instance roots are a fresh temporary directory per test.

### Prior art

None. This repo is greenfield, so this spec establishes the pattern rather than following one. Tests are plain `cargo test` integration tests against ash-core. The Tauri adapter is deliberately thin enough to need no tests of its own; if it ever grows logic worth testing, that logic belongs in ash-core instead.

### Cases that must be covered

- A complete sign-in produces an account keyed by Minecraft profile UUID.
- Device-code expiry, user denial, and "signed in but owns no copy" each produce distinct typed outcomes.
- An expired access token is refreshed silently; a dead refresh token surfaces as a re-authentication prompt, not a launch failure.
- Two instances on the same version cause exactly one download of the shared content.
- A corrupted depot file is detected by hash and re-fetched.
- An interrupted download resumes rather than restarting.
- A 1.8.9 launch produces legacy-format arguments and extracted natives on the recorded invocation.
- A 1.21.x launch produces rule-evaluated modern arguments.
- The classpath contains exactly the rule-permitted libraries plus the version jar, in the right order.
- Launching with no valid session fails before anything is spawned.
- A second concurrent launch of the same instance is refused; a concurrent launch of a different instance succeeds.
- Paths containing spaces and non-ASCII characters survive into the invocation correctly quoted.
- No token or credential appears in any log output.

### Manual acceptance, not automated

Phase 1 is not done until, with a real Microsoft account on a real Windows machine, both 1.8.9 and the current 1.21.x reach the main menu signed in as the correct player, and the game is playable. No fixture-driven test substitutes for this.

**This clause is blocked on the Mojang allow-list and nothing else in the spec is.** Hops 1–4 of the authentication chain — Microsoft device code, Microsoft token, Xbox Live, XSTS — work today with an unapproved client id, so the auth module can be built and its fixtures captured against the real services. Hops 5–7 — login with Xbox, entitlement check, profile fetch — return `403 Invalid app registration` until the request is granted, so user stories 6 through 22 cannot be exercised end to end against a live account. Every other part of Phase 1 proceeds unaffected. Do not discover this dependency at the end of the phase.

## Out of Scope

- The client, any loader, any bundled or third-party mod — Phases 2 and 3.
- The backend and everything depending on it: server-side ash accounts, entitlements, cosmetics, stats, synced settings, news posts — Phase 4.
- macOS support and packaging — Phase 5.
- Server entries and quick-connect — Phase 5.
- Launching a fully prepared instance with no network at all. Stale-token behaviour needs care and it is not worth blocking Phase 1 on.
- Self-update of the launcher.
- Importing instances from other launchers, and instance export or backup.
- Depot garbage collection and reclaiming space from unused versions.
- The final branding pass. Phase 1 UI uses the grayscale palette and the three typefaces but is judged on function.
- Telemetry and analytics of any kind.

## Further Notes

- **API access is gated — now confirmed, and it blocks this phase's definition of done.** Mojang runs a manual allow-list for third-party apps calling the Java Edition game service APIs (announced 2023-05-30; earlier apps grandfathered). No criteria, turnaround or SLA is published anywhere, so the wait is unbounded. Submit the request before writing auth code. See `docs/research/0001-minecraft-launcher-api-access.md`.
- **Register the Azure app as "ash", not as anything containing "Minecraft".** Microsoft's identity platform terms let them reject a display name that could cause confusion, and the allow-list exists specifically to screen out launchers that look like phishing. A name that trips either check sinks the application on sight.
- **The paid-cosmetics model has a plain-text conflict with the Usage Guidelines and the EULA.** It does not block Phase 1 engineering, but it bears on positioning and branding, which Phase 1 does touch. It needs a decision before Phase 4 is designed. See §8 of the research document.
- Downloads must come from Mojang's manifest and CDN. The depot is a local cache and never a redistribution point — the EULA requires game downloads to come from an authorised source.
- The development machine has Java 26 installed, which cannot run either target. Because ash provisions its own runtimes and ignores system Java, this is harmless — but do not let a system Java on PATH create a false positive while testing.
- 1.8.9 is the harder target: legacy argument format, natives extraction, and an old Java requirement. Build it second, but do not push it past Phase 1 — it is precisely what proves the launch abstraction is real rather than a 1.21-shaped function with parameters.
- Affected versions ship a Log4j configuration that needs the known mitigation applied at launch.
- Per the project brief, Phase 2 does not begin until this phase works end to end.

## Tracker

Filed as [#1](https://github.com/sisi-j/ash/issues/1), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. This file is the source of truth; the issue is the tracker entry. Keep them in step if either changes.
