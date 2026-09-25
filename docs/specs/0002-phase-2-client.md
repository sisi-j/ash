# Phase 2 — The client: a loader in an instance, and ash's own code running in the game

## Problem Statement

ash launches Minecraft. That is all it does, and vanilla Minecraft gives a competitive player none of the presentation they expect: the frame rate is behind a debug screen that covers a third of the display, and sprinting means holding a key down for an entire session.

The players ash is for do not play vanilla. They play Lunar, Badlion or Feather, each of which solves this by owning the whole stack — their launcher, their client, their account handling. A player who wants ash's launcher and someone else's client has to run two launchers, and nobody does that.

Phase 1 proved ash can put the right files on disk and start the game correctly. It proved nothing about ash being able to run its own code inside that game, which is the entire premise of the product. Every feature in the brief — crosshair, hit indicators, readouts, toggle sprint, freelook, cosmetics — sits behind that one unproven capability.

## Solution

A player creates an instance and chooses a loader alongside the version target. ash downloads and verifies Fabric Loader, the matching API, and its own client, assembles a modded launch, and starts the game. The client is there when the game opens.

Two features ship, both working identically on **1.8.9** and **1.21.11**: an **FPS readout** on screen, and **toggle sprint** on a key the player chooses.

If one of ash's features cannot load, the game still launches without it, the client writes a **load report**, and the launcher shows the player which feature is missing before they play again.

The point of the phase is not two features. It is that the chain from a Gradle build to pixels on a real screen works on both version targets, through a seam thin enough that the third feature and the tenth version target are additive rather than another negotiation with the game.

## User Stories

### Choosing a loader

1. As a player, I want to choose a loader when I create an instance, so that I decide whether it runs vanilla or with the ash client.
2. As a player, I want the loader offered in plain terms rather than as a version number, so that I never have to learn what Fabric Loader is.
3. As a player, I want to see which of my instances run the ash client, so that I can tell them apart in the list at a glance.
4. As a player, I want to be told before I create an instance that its loader cannot be changed afterwards, so that the constraint is not a surprise later.
5. As a player with existing vanilla instances, I want creating a modded instance to leave them completely untouched, so that I can try the client without risking anything I already have.
6. As a player, I want an instance's loader shown next to its version target, so that "1.21.11" and "1.21.11 with the ash client" are distinguishable.
7. As a player, I want a loader offered only where ash actually supports it, so that I cannot create an instance that can never work.

### Preparing a modded instance

8. As a player, I want ash to fetch everything the loader needs without me listing anything, so that I never have to learn what an intermediary is.
9. As a player, I want every loader file checked against its published hash, exactly like game files, so that corruption fails now rather than as a baffling crash later.
10. As a player, I want loader files shared between instances through the depot, so that a second modded instance on the same version target is nearly instant.
11. As a player, I want preparing a modded instance to show the same progress detail as a vanilla one, so that I know whether to wait or make coffee.
12. As a player, I want a fully prepared modded instance to plan and launch with no network at all, so that a dropped connection does not stop me playing.
13. As a player, I want ash to install a loader version it has actually tested, so that an upstream release never changes my game without warning.
14. As a returning player, I want an ash update that changes the pinned loader to fetch the new one on next prepare, so that the two stay in step without my involvement.
15. As a player, I want ash to put its client where the loader will find it, so that I never open the game directory to make the client work.
16. As a player, I want ash to leave everything else in my game directory alone, so that preparing an instance never touches my worlds, screenshots or resource packs.
17. As a player, I want deleting a modded instance to leave the depot alone, so that my other instances keep working.

### Launching

18. As a player, I want one Play button whether the instance is vanilla or modded, so that nothing about launching changes.
19. As a player, I want a modded instance to launch as the account I selected, exactly as a vanilla one does.
20. As a player, I want my machine-local overrides — memory, window size, Java path — to apply to modded instances too, so that tuning does not stop working when I add a loader.
21. As a player, I want to run a vanilla instance and a modded instance at the same time, so that I can compare them.
22. As a player, I want the game's log available after a crash in a modded instance, so that I can read the error or send it to someone who can.
23. As a player, I want ash's client to appear in the game's own mod list, so that I can confirm with my own eyes that it loaded.
24. As a player, I want ash to refuse to launch a modded instance whose client is missing, and say so, rather than starting a game with no client in it.
25. As a player, I want to know which version of the ash client an instance is running, so that a bug report can name it.

### The features

26. As a player, I want my frame rate on screen without opening the debug screen, so that I can watch performance while I am actually playing.
27. As a player, I want the FPS readout to look and read the same on 1.8.9 and 1.21.11, so that switching version targets does not change what I am looking at.
28. As a player, I want the FPS readout placed clear of the hotbar, the crosshair and the chat, so that it never covers something I need.
29. As a player, I want toggle sprint, so that I am not holding a key down for an entire session.
30. As a player, I want to choose the key toggle sprint is bound to, so that it does not collide with binds I already use.
31. As a player, I want toggle sprint to behave identically on both version targets, so that my muscle memory survives switching.
32. As a player, I want toggle sprint's state to be unambiguous, so that I am never sprinting when I think I am not, or the reverse.
33. As a player, I want my feature settings to survive closing the game, so that I set them once.
34. As a player, I want every ash feature to change only what I see, never what the server sees, so that no server has cause to ban me for using ash.
35. As a player on a server with anti-cheat, I want ash to be indistinguishable from vanilla in anything it sends, so that I am not punished for my client.

### When a feature cannot load

36. As a player, I want the game to launch even when one of ash's features cannot load, so that one broken feature does not cost me a session.
37. As a player, I want the launcher to tell me which feature did not load, so that I find out before I join a server rather than during a fight.
38. As a player, I want that notice to persist until it is actually fixed, so that closing the launcher does not hide it.
39. As a player, I want a degraded feature described as ash's problem rather than mine, so that I am not sent hunting through my own setup.
40. As a developer, I want the load report in ash's own log file, so that I can diagnose from an artefact a player sends me.

### Building and testing the client

41. As a developer, I want a feature's logic unit-tested without launching the game, so that the test loop is seconds rather than minutes.
42. As a developer, I want each feature written once and adapted per version target, so that a third target is a small addition rather than a third copy.
43. As a developer, I want the shared module unable to import Minecraft or Fabric API types at all, so that the seam cannot quietly rot toward one target.
44. As a developer, I want mappings chosen per target without the shared module ever knowing, so that the two mapping families never have to be reconciled.
45. As a developer, I want CI to build both version targets on every push, so that a break is attributed to the change that caused it.
46. As a developer, I want CI to launch a real client on both targets, so that a mixin which has stopped matching is caught before a player finds it.
47. As a developer, I want Legacy Fabric's artifacts mirrored into ash's own build inputs, so that the project going dormant does not stop the build.
48. As a developer, I want the EULA acceptance that client testing requires recorded where a reader will find it, rather than buried in a build script.
49. As a developer, I want the launcher's loader behaviour tested through the same seam and the same fakes as everything else, so that Phase 2 does not introduce a second way to test the launcher.
50. As a developer, I want adding a version target later to be a new thin module against an unchanged seam, so that the plan to add versions in bulk is real rather than aspirational.

## Implementation Decisions

### Shape

- **The launcher gains no new seam.** Loader support extends existing operations on `Ash`: instance creation takes a loader, planning and preparation resolve and verify loader artifacts, launching uses the merged profile, and one new read exposes the load report. Loader metadata arrives over HTTP, which `HttpPort` already covers, so no fourth outbound port is introduced.
- **The client mirrors the launcher's architecture.** A feature is the inbound seam — tests drive one directly. What a feature draws, and what it listens to, go out through interfaces the shared module defines and each version module implements. Fakes stand in for those interfaces in unit tests, exactly as `FakeHttp` does for the launcher.
- Three client modules: one shared, one per version target. See ADR-0015.

### The loader

- An instance carries a loader as well as a version target. Both are chosen at creation and neither changes afterwards; **vanilla is a loader**, so every instance has exactly one and there is no special case.
- ash pins the loader version per version target, fetches the immutable per-version loader document, and assembles the launch profile itself. Fabric Meta is not in the launch path. Every artifact is verified against a published hash. See ADR-0014.
- The `inheritsFrom` merge is reimplemented from observation, because it is specified nowhere. It nests, and cycles are treated as a real possibility rather than a theoretical one.
- Legacy Fabric's artifacts — intermediary, the LWJGL 2 fork and its natives, the API — are mirrored into ash's build inputs with recorded hashes.
- Legacy Fabric runs **upstream Fabric Loader unmodified**. What differs between the two targets is the intermediary, the mappings and the API, not the loader.
- **Fabric API and Legacy Fabric API are bundled mods**, both Apache-2.0. ash's client hard-depends on them: the FPS readout is an event on both targets, not a mixin.
- Neither Sodium nor Lithium is bundled. See ADR-0013.

### Distribution

- ash's own client jars ship inside the installer, so the launcher and client can never be version-skewed. Everything third-party downloads into the depot and is verified there.
- ash never distributes a modified game jar. It distributes a loader and mods, and assembly happens on the player's machine.

### The seam's shape

- The shared drawing surface is expressed as "draw these primitives at this position", never as "replace element X". Modern Fabric API offers a named, ordered element registry; Legacy Fabric API offers a single additive-only HUD callback that cannot replace or remove anything. The narrower target dictates the seam.
- The shared module names no Minecraft type and no Fabric API type. The key-binding helper alone ~~has a different class name on each target~~ is `KeyBindingHelper` in a different package on each target, over a different key type (*corrected 2026-09-24, while implementing #24* - the simple names match; the packages and key types do not).
- Feature state machines, timers, settings and formatting live in the shared module. Everything that touches the game lives in a version module.

### Failure

- Mixin configs allow a feature to be absent rather than fatal. Conditional application uses an `IMixinConfigPlugin`, not optional injectors.
- The client writes a **load report** naming what loaded and what degraded. The launcher reads it; the launcher never writes the client's configuration. See ADR-0017.
- The load report surfaces in the launcher before play, and in ash's own log.

### Settings

- The client owns its configuration file in the game directory. The launcher does not write it in this phase. Synced settings are Phase 4, and the conflict rules that come with a second writer belong to that design rather than this one.

## Testing Decisions

A good test here states a player-visible fact and would fail if that fact stopped being true. It drives the product through a seam rather than reaching into a module, and it names what it proves — Phase 1's credential-redaction test asserts `"the test proves nothing if the flag is absent"`, and that guard caught a test which was passing while proving nothing. Repeat that pattern wherever a test could pass vacuously.

**Launcher.** Every new test drives through `Ash` with the existing fakes, alongside the current 203. Prior art is `tests/launch.rs` for invocation assembly, `tests/depot.rs` for verification and resume, and `tests/instances.rs` for instance lifecycle. What needs covering: a loader chosen at creation and never changing; loader artifacts planned, verified, resumed and shared through the depot; a corrupt or vanished loader artifact failing the way a game file does; the merged profile producing the right main class, classpath order and arguments on both targets; a prepared modded instance planning with the offline fake; a load report naming a degraded feature being surfaced; an instance whose client jar is missing refusing to launch.

**Client, shared module.** ~~Fabric Loader JUnit~~ **plain JUnit** — running on every `./gradlew build` and in CI, with fakes for the version seam. This is where toggle sprint's latch is proved: held across ticks, unambiguous after a key repeat, correct when the key is rebound. No game launches.

*Corrected 2026-09-16, while implementing #20.* Fabric Loader JUnit cannot run here. It stands Fabric Loader up inside the test JVM and the loader's first act is to locate the game; `fabric.skipMcProvider` disables the Minecraft game provider and Knot then fails with `No game providers present on the class path!`, because it requires at least one. So the tier needs a game jar, and a game jar belongs to exactly one version target — the one thing the shared module may never have. It costs nothing: Fabric ships that tier because mod code relies on Mixin and on registries that only exist once the loader is up, and the shared module has neither by construction. The tier lives in the target modules instead, where the game already is — `AshModMetadataTest` stands the real loader up and asserts what it made of `fabric.mod.json`.

**Client, per target.** ~~Fabric's client game tests, launching a real client on both targets.~~ **A real vanilla client on both targets, by two different means.** This tier exists for one purpose — catching a mixin that has stopped matching — and ADR-0017's choice to degrade rather than crash depends on it.

*Corrected 2026-09-19, while implementing #22.* Fabric's client game tests exist on 1.21.11 only. Legacy Fabric API ships no gametest module — none of its 44 modules is one — so 1.8.9 gets a hand-rolled equivalent instead: a client mod in its own source set that waits for a screen, prints the loaded mod list, asserts ash is among it and stops the game. It proves less than the framework does, and a ticket that assumes the two targets have the same tier will be wrong about the weaker one.

**Manual acceptance, not automated.** The phase is not done until, on a real Windows machine with a real account, both version targets launch with the ash client, the FPS readout is on screen, and toggle sprint works — and the game is playable. No fixture substitutes for this. Phase 1 found four significant bugs this way, none of which was in an acceptance criterion.

## Out of Scope

- The in-game feature editor. The client reads its configuration file; it does not yet offer a screen to edit it.
- Every feature except the FPS readout and toggle sprint. Crosshair, hit indicators, hit colour, ping readout and freelook are later — and freelook and hit colour are mixin-only on both targets with no verified hook.
- Third-party mods. Anything the player supplies themselves is not supported, detected or accounted for.
- Sodium and Lithium as bundled mods.
- The backend, cosmetics, entitlements, synced settings and news — Phase 4.
- macOS — Phase 5.
- Changing an existing instance's loader, or converting a vanilla instance.
- Version targets other than 1.8.9 and 1.21.11.
- Self-update of the launcher or the client.

## Further Notes

- **Three things are unverified and must not be assumed while ticketing.** ~~Whether Fabric's client game tests run on a GPU-less Windows CI runner — ADR-0016 says outright that if they do not, ADR-0017 should be reopened rather than worked around.~~ *Answered 2026-09-19, while implementing #22: they run on a GPU-less **Linux** runner, on both targets. On **Windows** they do not — measured, not assumed: the client hangs acquiring an OpenGL context and a 30-minute timeout ends the job. ADR-0017 is not reopened, because the failure mode it names is a mixin that stopped matching, and that is decided by bytecode rather than by the operating system. Windows-only runtime failures have no CI signal at all, and manual acceptance is the only gate on them. See ADR-0016 and ADR-0017.* Legacy Fabric API's pinnable version, where three sources give three different answers and it must be resolved empirically. And the vanilla class and method names toggle sprint's mixin needs on each target, which need a decompiled-source pass before any ticket can specify them.
- **Legacy Fabric has a bus factor of one.** One person authored 69 of 69 commits to the API repo over twelve months, and comparable proportions elsewhere. The cadence is real and current; the exposure is `repo.legacyfabric.net`, a single self-hosted server with no published mirror. Mirroring is in scope for this phase precisely because it is cheap now and impossible later.
- **Mojang's mappings may not be redistributed complete and unmodified.** Loom fetches them at build time; nothing may be committed to the repository.
- **1.8.9 is again the harder target, for a different reason than in Phase 1.** There the difficulty was legacy arguments and natives extraction. Here it is that Legacy Fabric API gives one additive HUD callback where modern Fabric API gives a full element registry — and that single difference dictates the shape of the seam for every feature that will ever be written.
- **ADR-0004 was wrong on both its premises** and is superseded in part by ADR-0013. Anything written against it should be re-read.
- The paid-cosmetics question gained a complication during Phase 2 research: the Usage Guidelines now explicitly permit selling cosmetics *"except for capes or anything that attempts to visually act like the feature of a Minecraft player cape"*. That names ash's headline cosmetic. It does not block this phase, and it still needs legal advice before Phase 4 is designed.
- Per the project brief, this phase does not begin until Phase 1 works end to end. It does.

## Tracker

Filed as [#15](https://github.com/sisi-j/ash/issues/15), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. This file is the source of truth; the issue is the tracker entry. Keep them in step if either changes.
