# The beta: 0.2.0-beta.1

*Designed 2026-10-28 in a grilling session with the product owner. Every decision below was put to them and answered. The few written down as assumptions are marked. This is the overview: each beta phase has its own spec, 0007 to 0011.*

## Naming

Everything before the beta is an **alpha phase**: alpha phases 1 to 4 are specs 0001 to 0004, and the alpha release is spec 0005. The beta is built in **beta phases 1 to 5**, specs 0007 to 0011.

## Problem Statement

The alpha proved the launcher. Testers sign in with one click, launch three versions, install mods and wear a cape. But next to Lunar or Prism, ash is thin where players spend their time:
- **In game, it does too little.** There are nine features. Lunar's keystrokes, armour status, coordinates, zoom, fullbright and chat tweaks are missing, and these are what players switch launchers for.
- **What it does can't be shaped enough.** Only two readouts can be moved, and none of them can be styled. There's no way to save a setup, switch it per server or give it to a friend.
- **It's behind the game.** Minecraft is on 26.3, and ash's newest version is 1.21.11, three releases back.
- **Modpacks don't come in.** A player with a pack in Prism, the Modrinth app or a `.mrpack` file has to rebuild it by hand. Most popular packs are on versions ash's client doesn't cover, so ash can't run them at all.
- **Cosmetics stop at capes.** Worn models and emotes were designed in alpha phase 4 but not built.

## Solution

The beta is **0.2.0-beta.1**: an **open beta**, which anyone can download from ashlauncher.com and the Discord. It's for **Windows only** and **unsigned**, as the alpha was. It's built in five beta phases and released **once**, when all five are done. Minimalist by default, deeply customizable: Lunar's features at Prism's speed.

| Beta phase | Spec | What it delivers |
|---|---|---|
| **1. Foundations** | 0007 | 26.3 as a fourth version target, with the newest target moving forward as Minecraft releases. HUD items generalised: every one is placed, styled, snapped and hidden the same way. The settings screen gets option search, favourites and the new categories. One per-server fair-play list for every feature. |
| **2. Modpacks** | 0008 | Fabric on any version, with or without ash's client. Modrinth modpacks, from a `.mrpack` file or browsed in ash. Instances imported from Prism, MultiMC and the Modrinth app, plus worlds and packs from the official launcher. Export as `.mrpack`. Updates for mods and modpacks, on request. |
| **3. HUD and PvP** | 0009 | Keystrokes and clicks per second. Armour and potion status. Coordinates, direction, biome, server and clock. Zoom, hitboxes and toggle sneak. |
| **4. Visuals and social** | 0010 | Fullbright and the block outline. Fewer overlays. Time, weather and particles on your screen only. Chat tweaks, nametags and Discord status. |
| **5. Make it yours** | 0011 | Settings profiles: synced, switching per server, shared as codes, with starter profiles. An accent colour for the panel. Worn models and emotes. Crash reports, sent only when the player asks. |

**While the beta is built:**
- **Testers stay on alpha.1.** Real bugs they report are fixed and shipped as alpha patch releases (`0.1.0-alpha.2` and on) through the updater. No new features reach them early.
- **The product owner tries each phase.** At the end of every beta phase they get a test installer and a what-to-test list, as in the alpha walk. Problems are fixed while that phase is fresh.

**When the beta ends:** every beta phase has shipped in 0.2.0-beta.1. Then a release candidate goes **two weeks with no new crash or blocker report**. Then code signing, and 1.0.

## User Stories

These are the beta's whole-product stories. Each phase spec has its own long list.

1. As a player, I want one download that has everything, so that I try the beta as a whole rather than in pieces.
2. As an alpha tester, I want the bugs I report fixed in alpha updates while the beta is built, so that I'm not stuck with them for months.
3. As an alpha tester, I want the beta to arrive through ash's updater, so that moving to it is one click.
4. As a player on the newest Minecraft, I want ash's client there, so that I don't have to stay on an old version to use ash.
5. As a player, I want ash to follow Minecraft's new releases within weeks, so that updating the game doesn't mean losing ash.
6. As a player, I want any Fabric modpack to import into ash, even on a version ash's client doesn't cover, so that ash can be my only launcher.
7. As a player, I want Lunar's everyday features in ash, so that I don't miss anything by switching.
8. As a player, I want every HUD item styled and placed exactly how I like, so that my screen looks like mine.
9. As a player, I want ash to keep features off where a server forbids them, so that using ash never gets me banned.
10. As a new player, I want to start from a small, clean setup and add what I want, so that ash isn't cluttered the first time I open it.
11. As the product owner, I want to try each phase on my own machine before the next starts, so that problems are found while they're cheap.
12. As the product owner, I want the beta to end on a rule, so that 1.0 isn't a guess.
13. As the product owner, I want ideas I have while building to go into the next phase not yet started, so that new ideas don't derail the current one.

## Implementation Decisions

### Releases
- **One beta release.** The beta phases are merged to `main` as they finish, and the version stays `0.1.0-alpha.N` the whole time. The merge that sets `0.2.0-beta.1` and dates its `CHANGELOG.md` section is the release (ADR-0025).
  - Until then, beta work on `main` must not reach testers through an alpha patch release. A beta phase's player-facing work stays switched off in released builds until the beta release, behind one build-time switch.
  - Alpha patch releases are cut from `main` with that switch off.
- **Test builds per phase.** Each phase's last ticket builds the installer with the switch on, from `main`. It isn't published: the product owner gets the file and a what-to-test list in `Downloads`.
- **Alpha patch releases** are bug fixes only, numbered `0.1.0-alpha.2` and on, published as alpha.1 was.

### Platforms and trust
- Windows only. The installer stays unsigned; code signing comes before 1.0.
- The site's download page keeps explaining Windows' SmartScreen warning.

### Fair play
- One **fair-play list**, shipped in the client: which features are off on which servers, and why. Freelook's block list is the first entry (beta phase 1). Every new feature gets an entry where a big server forbids it.
- ash adds **no minimap and no radar** of entities or players, and never removes the blindness or darkness effects. These are kept out because servers ban them and they'd get players banned.

### The newest version target moves forward
- The version targets are **1.8.9, 1.20.4, 1.21.11 and the newest release**, which is 26.3 at the start (ADR-0028).
- When Minecraft releases a new version, ash ports its client to it within a few weeks, and that becomes the newest target.
- The previous newest target is **retired**: its last client build keeps shipping unchanged, so its instances keep working with the features they had. It gets no new features, and the launcher suggests a new instance on the newest version.

### Where new ideas go
- An idea that comes up while building goes into the spec of the next beta phase that hasn't started, by amending that spec. A started phase takes only fixes.

## Testing Decisions

Each phase spec names its tests. Across the beta:
- **Test through the existing seams**, at the highest level that shows the behaviour:
  - the client's shared module, with JUnit against fakes of the game;
  - each version target's client game test, which proves the wiring in a real game;
  - the `Ash` facade, with fake HTTP and fixture folders;
  - `ui-check` for the launcher's screens;
  - vitest in workerd for the backend.
- **One new client seam, the game view.** It's an interface the shared module reads the game through: keys held, the player's position, armour, effects and chat. It's added in beta phase 1 and grown by the later phases. Every in-game feature is tested against a fake game view, with no game running.
- **A real game on every version target**, for every feature. A new feature's client game test runs on 1.21.11, 1.20.4 and 26.3. On 1.8.9 the smoke test checks that it loaded.
- **FPS marks** are measured for every new feature on every version target, as in the README's method, in a benchmark sitting with the product owner.

## Out of Scope

Kept for after the beta:
- **Features:** combat stats (combo, reach, the opponent's health), 1.7 animations, item-in-hand tweaks, scoreboard and tab-list tweaks, and a friends list.
- **Launchers and loaders:** CurseForge modpacks and CurseForge app instances. Forge, NeoForge and Quilt instances or packs. Legacy Fabric on versions other than 1.8.9.
- **Platforms:** macOS and Linux.
- **Trust:** code signing, until before 1.0.
- **Money:** selling anything. Cosmetics stay free (ADR-0010, amended).
- **Server-side control:** servers switching ash's features off through a protocol.
- **Fair-play exceptions:** a minimap, radar, or anything that removes blindness or darkness.
- **History:** syncing worlds, mods or screenshots between computers.

## Further Notes

1. **Needs the product owner:**
   - a test walk at the end of each phase;
   - benchmark sittings for FPS marks;
   - a Discord application for Discord status (beta phase 4);
   - real art for worn models and emotes (beta phase 5).
2. **Assumed, not asked:**
   - the beta's version number, `0.2.0-beta.1`;
   - the build-time switch that holds beta work back from alpha patch releases;
   - that a retired newest target keeps its last client build.
3. **Spending:** none planned. Code signing, which would cost about $10 a month, waits for 1.0.

## Tracker

Filed on GitHub: the beta is a tracker issue, and each beta phase is a sub-issue of it with its own tickets, per `docs/agents/issue-tracker.md`. This file is the source of truth.
