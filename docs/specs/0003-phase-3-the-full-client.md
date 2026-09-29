# Phase 3 — The full client: the PvP feature set, set up in game, faster, and a launcher that looks like ash

## Problem Statement

Phase 2 proved that ash can run its own code inside the game on both version targets. It did that with two features, which is enough to prove a chain and not enough to be a client anyone would choose.

A competitive player comparing ash with Lunar, Badlion or Feather sees four gaps today:
- **Features.** ash has an FPS readout and toggle sprint. There is no custom crosshair, no hit indicator, no ping readout, no hit colour and no freelook, and every one of those is in the brief.
- **Setup.** A player changes those two features by finding a properties file in their game directory and editing it in a text editor. That is fine for a flag or two. It does not work for a crosshair's shape, size and colour, and it gives nowhere to move a readout.
- **Speed.** ash's client is meant to be performance-focused, and it is not yet.
  - On 1.21.11 ash bundles no optimisation mod.
  - A player cannot add Sodium themselves, even though ADR-0013 says that is how they get it, because ash has no way to load a mod the player supplies.
  - On 1.8.9, where no Sodium equivalent exists, ash does nothing to the frame rate at all.
- **Look.** The launcher is judged on function and looks it. Sora, Inter and JetBrains Mono are not bundled, so a system font stack stands in for the typefaces the brand is built on.

## Solution

Phase 3 covers four areas.

**The rest of the feature set, on both version targets:**
- a **custom crosshair**;
- a **hit indicator**;
- a **ping readout**;
- **hit colour**;
- **freelook**, if it passes the research below.

Each one works identically on 1.8.9 and 1.21.11, can be switched off, and degrades on its own without costing the player their session (ADR-0017).

**A settings screen in the game.** A key opens it, Right Shift by default and rebindable. Every feature is switched and set up there. Changes show at once and are saved as they are made. Readouts can be dragged where the player wants them, and put back where they started.

**A faster game:**
- Lithium ships as a bundled mod on 1.21.11.
- A player can opt an instance in to loading **third-party mods** they supply themselves, so that Sodium is one file away.
- On 1.8.9, ash ships optimisations of its own. Each is chosen by measuring where the frame time goes on real hardware, and each ships only with a before and after from that measurement.

**A launcher that looks like ash.** The three typefaces are bundled, and a design pass brings the launcher to the brief's grayscale identity.

## User Stories

### Crosshair

1. As a player, I want to replace the game's crosshair with one I design, so that I can see exactly where I am aiming against any background.
2. As a player, I want to choose the crosshair's shape from a short list, so that I get a good crosshair without drawing one pixel by pixel.
3. As a player, I want to set the crosshair's size, thickness, gap and colour, including how transparent it is, so that it suits my eyes and my screen.
4. As a player, I want an outline option, so that a light crosshair stays visible against snow and sky.
5. As a player, I want the crosshair to hide wherever the game's own crosshair hides, such as in third person or with the HUD hidden, so that it never floats where it should not.
6. As a player on 1.21.11, I want the attack cooldown indicator to keep working with ash's crosshair, so that I do not trade timing information for a nicer shape.
7. As a player, I want the crosshair to look identical on 1.8.9 and 1.21.11 with the same settings, so that my aim does not change when I switch version targets.
8. As a player, I want to see only one crosshair, never the game's and ash's on top of each other, so that I always know which one to aim with.
9. As a player, I want the game's own crosshair back if ash's cannot load, so that I am never left with none.

### Hit indicator

10. As a player, I want a clear mark at my crosshair when my attack lands, so that I know my hit counted without watching the target.
11. As a player, I want the mark to mean the server registered the hit, not that I clicked, so that it never tells me I hit when I missed.
12. As a player, I want to choose the mark's colour and how long it shows, so that it is noticeable without being distracting.
13. As a player, I want the hit indicator never to show damage numbers or anyone's health, so that no server has cause to call it an advantage.

### Ping readout

14. As a player, I want my ping to the server on screen, so that I can tell lag from my own mistakes.
15. As a player, I want the ping readout to show the number the server reports, the same one the tab list shows, so that the two never disagree.
16. As a player in singleplayer, I want the ping readout to stay out of the way, so that I am not shown a number that means nothing.
17. As a player, I want the ping and FPS readouts to sit together without overlapping, so that both stay readable.

### Hit colour

18. As a player, I want to choose the colour an entity flashes when it is hurt, so that hits stand out against the world I play in.
19. As a player, I want to choose how strong that flash is, so that it is visible without hiding the entity.
20. As a player, I want hit colour to change only what I see, so that other players and the server see nothing different.

### Freelook

21. As a player, I want to hold a key and look around without turning my player, so that I can look behind me while running forward.
22. As a player, I want the camera to return where it was when I let go, so that I never lose my heading.
23. As a player, I want freelook to be offered only if the servers I play on allow it, so that I am never banned for a feature ash chose to ship.

### Setting features up in game

24. As a player, I want a key that opens ash's settings in game, so that I never edit a file to change a feature.
25. As a player, I want to choose that key in the game's own Controls screen, so that it does not collide with binds I already use.
26. As a player, I want every ash feature listed in one place with an on/off switch, so that I can see at a glance what is running.
27. As a player, I want each feature's options next to its switch, so that I set a feature up where I turn it on.
28. As a player, I want a change to show at once, while the screen is still open, so that I can tune a crosshair by looking at it.
29. As a player, I want changes saved as I make them, so that a crash or a closed window never loses them.
30. As a player, I want to put any feature back to its defaults, so that I can recover from settings I no longer like.
31. As a player, I want the settings screen to lay out and behave the same on both version targets, so that I learn it once.
32. As a player who likes editing files, I want my comments and ordering in the settings file kept when I also change things in game, so that the two ways of editing do not fight.
33. As a player, I want a feature that did not load shown as unavailable on the screen, not as a switch that does nothing, so that the screen never lies about what is running.
34. As a new player, I want the launcher to tell me which key opens ash's settings, so that I find the screen without being told by someone else.

### Moving readouts

35. As a player, I want to drag the FPS and ping readouts anywhere on screen, so that they sit where I look.
36. As a player, I want a readout I have placed to stay in the same place relative to its corner when I change resolution, window size or GUI scale, so that I set it once.
37. As a player, I want a readout kept on screen whatever I do, so that I can never lose it off an edge.
38. As a player, I want to put the readouts back where they started, so that I can undo a layout I do not like.

### Performance

39. As a player on 1.21.11, I want Lithium included, so that the game runs faster without my doing anything.
40. As a player, I want Lithium fetched, verified and shared through the depot like every other file, so that it is as trustworthy as the game itself.
41. As a player on 1.8.9, I want ash's own optimisations, so that the version most PvP is played on runs faster too.
42. As a player, I want each optimisation to change only how fast the game draws, never what I or the server can see, so that no optimisation gives an advantage.
43. As a player, I want to be able to switch an optimisation off, so that if one ever misbehaves on my machine I can still play.
44. As a player, I want an optimisation that could not load reported like any other feature, so that I know I am running without it.
45. As a developer, I want each optimisation justified by a measured before and after on real hardware, so that ash ships speed it can show rather than speed it hopes for.
46. As a player, I want to see and read the licences of the mods ash ships, so that ash meets the terms those mods are given under.

### Third-party mods

47. As a player, I want to opt an instance in to loading mods I supply myself, so that I can add Sodium or anything else I trust.
48. As a player, I want third-party mods off unless I turn them on, per instance, so that trying one never touches my other instances.
49. As a player, I want a plain place to put my mods, reachable from the launcher, so that I do not have to find it myself.
50. As a player, I want ash never to delete, move or rename a mod I put there, so that preparing an instance never costs me a file.
51. As a player, I want turning third-party mods off to stop them loading without deleting them, so that I can switch back.
52. As a player, I want to see at a glance which instances have third-party mods on, so that I know where to look when something breaks.
53. As a player whose game crashed with third-party mods on, I want the launcher to say so and offer to try without them, so that I have a next step rather than a dead end.
54. As a player, I want a feature notice after a session with third-party mods on to say that one of my mods may be the cause, so that ash does not claim a fault it cannot know is its own.
55. As a player, I want ash's own features to keep working alongside Sodium, so that I do not have to choose between them.

### The launcher's look

56. As a player, I want the launcher in its own typefaces, so that it looks like one product rather than a system dialog.
57. As a player, I want numbers such as versions, memory and progress in a monospaced face, so that they read as instruments and line up.
58. As a player, I want the launcher to work with no network, and still look right, so that its look never depends on a download.
59. As a player, I want a layout I can read at a glance: my instances down the side, the selected one's details and Play in the middle, my account at the top, so that I am one click from playing.
60. As a player, I want the launcher to stay grayscale throughout, with weight and contrast doing the work colour usually does, so that it looks like nothing else.

### Building and testing

61. As a developer, I want each new feature's decisions tested in the shared module without the game, so that the test loop stays in seconds.
62. As a developer, I want every feature's options declared once and the settings file and settings screen both built from that, so that a setting cannot exist in one and not the other.
63. As a developer, I want every new mixin covered by the build's check that each injector landed, so that a broken mixin fails `./gradlew build` by name.
64. As a developer, I want each feature seen working in a real game on both version targets in CI, with a screenshot, so that "it draws" is shown and not inferred.
65. As a developer, I want the 1.21.11 real-game test run with Sodium present as a player would add it, so that a clash with the mod players are most likely to add is caught before they find it.
66. As a developer, I want the launcher's screens rendered in a browser against a fake of the launcher's API, so that its look can be checked without building the app.
67. As a developer, I want that check to fail if a bundled typeface does not load, so that a font silently falling back is caught.
68. As a developer, I want a repeatable frame-time measurement I can run on a real machine, on both version targets, so that optimisations are compared like for like.

## Implementation Decisions

### Shape

- **The seams are Phase 2's, plus three.** What stays:
  - Features stay the client's inbound seam, with their decisions in the shared module and tested against fakes.
  - Game events reach them through thin per-target adapters, in the pattern of the toggle-sprint key.
  - `Ash` stays the launcher's single inbound seam, with the existing fakes.

  The three new seams are:
  - **the settings model**;
  - **the launcher UI rendered against a fake API**;
  - **a frame-time measurement run on real hardware**.
- **`HudSurface` grows only what a feature needs.** The crosshair needs filled rectangles and the screen's width. Nothing is added ahead of a feature that calls it.
- **Every new feature and every optimisation is a feature in the load report's sense.** Each has an id, a setting that switches it off, and its own degraded state. Each fails alone.

### The features

- **Crosshair.** ash draws its own and suppresses the game's, so the player never sees two.
  - On 1.21.11 the element registry can replace the game's crosshair element. Whether the attack-cooldown indicator is drawn inside that element, and would go with it, is unverified.
  - On 1.8.9 the HUD callback is additive only, so suppressing the game's crosshair needs a mixin.
  - A degraded crosshair leaves the game's crosshair in place: never none, never two. Suppression and drawing are therefore one feature that degrades as a unit.
  - Visibility follows the game's own rules for its crosshair on each target.
- **Hit indicator.** Fires on the server's confirmation that an entity the player attacked was hurt, never on the click. It reads only what the client already receives, and never shows an amount of damage or anyone's health. Showing health is information the game does not give, and it fails ADR-0006.
- **Ping readout.** The latency the server reports for the player in the tab list, on both targets. It is hidden in singleplayer. It shares the readouts' layout with the FPS readout.
- **Hit colour.** Changes the tint and strength of the hurt flash on entities. It is rendering only. Both targets need a mixin, and the hook is unverified on both.
- **Freelook.** Ships only if two things hold:
  - research shows it passes ADR-0006's test, "would a fair player be disadvantaged by not having it?";
  - no major PvP server prohibits it.

  At least one major server is reported to forbid freelook. If research confirms that, freelook is cut from the phase and ADR-0006 is amended to say so. It is not shipped behind a switch that a player could leave on where it is banned: a single banned feature gets the whole client blocked (ADR-0006).

### The settings model and the settings screen

- **One declaration per option.** Each feature declares its options once, in the shared module. The option kinds are:
  - on/off;
  - one of a fixed set;
  - a whole number in a range;
  - a colour with opacity;
  - a position on screen.

  The settings file is read and written by walking that declaration, and the settings screen is built by walking it too. This extends the rule `Settings` already has, that a setting left out of the list has no value at all.
- **The settings file gains a second editor, not a second writer.** The client remains the only program that writes it, and the launcher still never does.
  - The screen changes a value in place, rewriting that value and nothing else, so a player's comments, ordering and unknown keys survive.
  - "Appended to, never rewritten" becomes "appended to, and changed only in place".
  - Writes stay atomic, and reads still never throw.
- **Changes apply at once and are saved as they are made.** Nothing waits for the screen to close.
- **Each target draws the screen with its own vanilla widgets, thinly.** Layout and behaviour are the same on both targets, and the widgets look like each target's own. The decisions sit in the shared module: which options exist, their order, their bounds, what "reset" restores, and when an option is unavailable.
- **The screen opens on a key binding, Right Shift by default**, registered like toggle sprint's and rebindable in Controls. A button on the title or pause screen is out of scope. The launcher names the key once, where a new player will see it.
- **A feature that degraded is shown as unavailable** on the screen, with the same wording the launcher's notice uses. It is never a live switch.
- **Readout positions are an anchor plus an offset.** The anchor is the nearest corner or edge, and the offset is in GUI units. That keeps a placed readout in place across resolution, window size and GUI-scale changes, and makes clamping on screen a pure calculation. Dragging is the only per-target part.

### Performance

- **Lithium is a bundled mod on 1.21.11.** It is pinned, verified, stored in the depot and shipped the way Fabric API is.
  - It is LGPL-3.0-only (ADR-0013), so its licence text and a written offer for its source ship with ash (ADR-0004).
  - No ash mixin targets a Lithium class (ADR-0004).
  - Whether ash mirrors it is decided the way Legacy Fabric's mirroring was, in the mirror document.
- **Optimisations on 1.8.9 are ash's own, and measured first.** Before any optimisation is written, the frame-time measurement profiles a fixed scene on real hardware to find where the time goes.
  - An optimisation ships only with a before and after from that measurement, recorded in its pull request.
  - It must not change what the player or the server can see. An optimisation that hid something the game shows, or revealed something it hides, fails ADR-0006.
  - Each one is a feature that can be switched off and degrades on its own.
  - Code from other clients' optimisation mods is not copied. Licences vary, and ash's client is proprietary.
- **The frame-time measurement** runs a fixed, repeatable scene on each version target and records frame times to a file. It is run by hand on a real machine: CI has no GPU, and its software renderer's numbers mean nothing for players. It is a developer tool and never runs in a player's game.

### Third-party mods

- **Opt-in per instance, off by default.** It is an instance setting, shown in the instance list and on the instance's own view, and it covers mods for the instance's own loader only. Forge mods cannot load on Fabric and are not supported.
- **ash's own jars and a player's mods never share a directory that ash has to sort out.**
  - The instance's `mods` folder is where players expect to put mods, so it becomes the player's.
  - ash hands its client and its bundled mods to the loader from outside that folder.
  - With the setting off, the loader loads nothing from it.
  - How to do both on both loaders is unverified: Fabric Loader has a documented way to add mod locations, and whether the pinned loader versions honour it, and how to stop the mods folder being read, must be confirmed first.
  - Instances prepared in Phase 2 have ash's jars in that folder. Preparing them removes only the files ash itself put there, by name.
- **The launcher never deletes, moves, renames, vets or updates a third-party mod.** It opens the folder for the player, and that is all.
- **A crash with third-party mods on names them as the likely cause** and offers to launch without them.
- **The load report records whether third-party mods were loaded**, so the degradation notice can say that one of the player's mods may be the cause instead of claiming the fault is ash's. This amends ADR-0017, and the amendment is recorded as an ADR in the ticket that makes it.
- **Sodium is the case that matters most**, and the one ADR-0013 names. Sodium's mixin configs are required, so an ash mixin that disturbs a method Sodium overwrites is a crash on launch. The 1.21.11 real-game test therefore also runs with Sodium present. Downloading it in CI to test against is not bundling it.

### The launcher's look

- **Sora 500, Inter 400 and 500, and JetBrains Mono 500 are bundled** as local font files, allowed by the app's content security policy, with their licences shipped alongside. No font is ever fetched at runtime.
- **The brief's layout:**
  - the instance switcher down the left;
  - the selected instance's details (version target, loader, last played) and Play in the centre;
  - the account switcher and settings in a top bar.

  The news panel is Phase 5.
- **Grayscale only**, from the brief's four values. Contrast and weight carry the hierarchy. Numeric readouts are JetBrains Mono.
- **Tone** is the brief's: short, blunt, sentence case.

## Testing Decisions

A good test states a player-visible fact and fails if that fact stops being true. It drives the product through a seam rather than reaching into a module, and it names what it proves. Phase 2 kept finding tests that passed for the wrong reason: a "switched off" test that passed because "off" did not parse, and a leak test whose second forgery hid a missing guard. Phase 3's tests are held to the same bar, and the guards that make a vacuous pass impossible are kept.

**Client, shared module.** Plain JUnit on every `./gradlew build`, with fakes for the per-target seams. Prior art is the FPS readout, toggle sprint and load report tests. What needs covering:
- the crosshair's geometry for every shape and setting, including odd sizes and the outline;
- the hit indicator's timing, and its refusal to fire on a click with no confirmation;
- the ping readout's formatting and its absence in singleplayer;
- freelook's camera state across hold, release and a screen opening mid-hold;
- the settings model: every option round-trips through the file; an in-game change preserves comments, ordering and unknown keys; out-of-range and malformed values fall back without throwing;
- readout anchoring across resolution and GUI-scale changes, and clamping on screen.

**Client, per target.** The real-game tests on both targets: Fabric's client game test on 1.21.11 and ash's smoke test on 1.8.9.
- Each feature is seen working, with a screenshot.
- The settings screen is opened, a value is changed, and both the file and the feature are checked.
- `AshMixinsLandTest` covers every new mixin by construction, because it reads the mixin configs.
- The 1.21.11 test runs a second time with Sodium present.
- 1.8.9's tier stays weaker than 1.21.11's (Phase 2, #22). A ticket that assumes the two targets have the same tier will be wrong about the weaker one.

**Launcher.** Every new test drives `Ash` with the existing fakes. Prior art is the loader tests for pins, depot and preparation, and the instance tests for instance settings. What needs covering:
- Lithium is planned, verified, resumed and shared through the depot, and a corrupt copy fails as a game file does;
- third-party mods are off by default;
- a player's file in the mods folder survives preparing, updating and deleting other instances;
- the loader is told to load a player's mods only when the setting is on;
- a Phase 2 instance's own jars are moved out and nothing else is touched;
- the crash and degradation wording changes when third-party mods were on.

**Launcher UI.** New. The launcher's screens are rendered in a browser engine against a fake of the launcher's API, in each state that matters: signed out, no instances, preparing, playing, a failed launch and a degradation notice.
- The check fails if a screen throws or a bundled typeface fails to load.
- Screenshots are kept for review and not pixel-diffed, because font rendering differs across machines.
- The Windows app's web view is Chromium-based, so a Chromium browser is a fair stand-in.

**Performance.** Measured by hand on real hardware with the frame-time measurement, before and after, and recorded in the pull request. CI does not measure it.

**Manual acceptance, not automated.** The phase is not done until all of this has been done on a real Windows machine with a real account:
- every shipped feature is seen working on both version targets;
- each feature is changed through the settings screen and the change survives a restart;
- a readout is moved and survives a resolution change;
- Sodium is added as a third-party mod on 1.21.11 and the game is playable with ash's features working;
- a 1.8.9 optimisation shows its measured gain;
- the launcher is seen in its own typefaces.

No fixture substitutes for this.

## Out of Scope

- The backend, cosmetics, entitlements, synced settings and news. These are Phase 4.
- macOS, server entries and quick-connect, and the news panel. These are Phase 5.
- Bundling Sodium (ADR-0013). Players add it as a third-party mod.
- Forge mods, and mods for any loader other than the instance's own.
- Finding, downloading, vetting or updating third-party mods for the player.
- Version targets other than 1.8.9 and 1.21.11 (ADR-0005).
- Features outside the brief's list, such as keystrokes, clicks per second, armour and potion status, or coordinates.
- A protocol for servers to switch ash's features off.
- A button for ash's settings on the title or pause screen.
- Shaders.
- Self-update of the launcher or the client.
- Changing an existing instance's loader.

## Further Notes

- **Seven things are unverified and must not be assumed while ticketing.** Phase 2 had three, and research settled each before its ticket needed it. Settling these is the next step after this spec.
  1. **The game-side hooks for each feature on each target.** These are the crosshair and whether the 1.21.11 cooldown indicator goes with it, the packet or event that confirms a hit, the ping in the tab list, the hurt tint, and the camera for freelook. They need a decompiled-source pass like toggle sprint's.
  2. **Freelook's standing with major PvP servers.** This decides whether it ships.
  3. **Lithium for 1.21.11:** the version to pin, where it is published and with what hash, its licence as shipped, and whether any of its mixins touch what ash's do.
  4. **How to load a player's mods and ash's own separately** on the pinned Fabric Loader, for both version targets.
  5. **Where 1.8.9's frame time goes.** This is measured, not guessed, and it decides which optimisations exist.
  6. **Which widgets each target offers for the settings screen**, such as sliders and text fields, and whether 1.8.9 needs further Legacy Fabric API modules. Each module would be pinned, mirrored and shipped as toggle sprint's was.
  7. **The typefaces' licences.** All three are expected to be under the SIL Open Font Licence. That needs confirming from each project's own repository.
- **Order.**
  - The settings model comes first, because every feature after it declares its options through it.
  - Lithium is early, because it is small and changes the environment every later test runs in.
  - The features can then proceed in parallel.
  - The 1.8.9 optimisations start with the measurement and nothing else.
  - The launcher's look is independent of everything else and can go at any time.
- **This phase is bigger than Phase 2.** It has four areas, where Phase 2 had one. If it needs splitting, the launcher's look and the 1.8.9 optimisations split off cleanly, since neither blocks nor is blocked by the features.
- **Freelook is the likeliest feature to be cut, and cutting it is a success.** ADR-0006 exists so that ash never ships the feature that gets it banned.
- **Windows-only runtime failures still have no CI signal** (ADR-0016). Manual acceptance remains the only gate on them, and the settings screen and the third-party mod path are new places for them to hide.
- Per the project brief, this phase does not begin until Phase 2 works end to end. It does: manual acceptance passed on 2026-09-29 and #15 is closed.

## Tracker

Filed as [#30](https://github.com/sisi-j/ash/issues/30), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. This file is the source of truth; the issue is the tracker entry. Keep them in step if either changes.
