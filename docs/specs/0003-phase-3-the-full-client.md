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
- **freelook**, ~~if it passes the research below~~ switched off by ash on servers whose published rules ban it;
- **snaplook**, a key that jumps straight to the game's own front-facing view while it is held.

(*Amended 2026-09-30, after `docs/research/0006`, by the product owner's decision*: freelook ships with a per-server block rather than being cut, and snaplook is added.)

Each one works identically on 1.8.9 and 1.21.11, can be switched off, and degrades on its own without costing the player their session (ADR-0017).

**A settings screen in the game.** A key opens it, Right Shift by default and rebindable. Every feature is switched and set up there. Changes show at once and are saved as they are made. Readouts can be dragged where the player wants them, and put back where they started.

**A faster game:**
- Lithium ~~ships as a bundled mod on 1.21.11~~ is measured on 1.21.11, and ships as a bundled mod only if it makes the game measurably faster. (*Amended 2026-09-30, after `docs/research/0006`*: Lithium's own documentation credits its frame-rate gains to singleplayer's built-in server, not to play on a server.)
- A player can opt an instance in to loading **third-party mods** they supply themselves, so that Sodium is one file away.
- On 1.8.9, ash ships optimisations of its own. Each is chosen by measuring where the frame time goes on real hardware, and each ships only with a before and after from that measurement.

**A launcher that looks like ash.** ~~The three typefaces are bundled, and a design pass brings the launcher to the brief's grayscale identity.~~ The launcher and the in-game panel share one final design, approved on 2026-10-03: Inter throughout, translucent dark surfaces, and a launcher laid out like Dawn's home screen (see *The launcher's look*).

## User Stories

### Crosshair

1. As a player, I want to replace the game's crosshair with one I design, so that I can see exactly where I am aiming against any background.
2. As a player, I want to choose the crosshair's shape from a short list, so that I get a good crosshair without drawing one pixel by pixel.
3. As a player, I want to set the crosshair's size, thickness, gap and colour, including how transparent it is, so that it suits my eyes and my screen.
4. As a player, I want an outline option, so that a light crosshair stays visible against snow and sky.
5. As a player, I want the crosshair to hide wherever the game's own crosshair hides, ~~such as in third person or with the HUD hidden~~ by the rules of the version I am playing, so that it never floats where it should not. (*Corrected 2026-09-29 by `docs/research/0004`*: 1.8.9's own crosshair shows in third person, and only 1.21.11's hides there.)
6. As a player on 1.21.11, I want the attack cooldown indicator to keep working with ash's crosshair, so that I do not trade timing information for a nicer shape.
7. As a player, I want the crosshair to look identical on 1.8.9 and 1.21.11 with the same settings, so that my aim does not change when I switch version targets.
8. As a player, I want to see only one crosshair, never the game's and ash's on top of each other, so that I always know which one to aim with.
9. As a player, I want the game's own crosshair back if ash's cannot load, so that I am never left with none.

### Hit indicator

10. As a player, I want a clear mark at my crosshair when my attack lands, so that I know my hit counted without watching the target.
11. As a player, I want the mark to mean the server registered the hit, not that I clicked, so that it never tells me I hit when I missed. (*Qualified 2026-09-29 by `docs/research/0004`*: on 1.8.9 the server's "this entity was hurt" message names no attacker. So the mark means "an entity you just attacked was hurt", and someone else's hit on the same entity in the same instant can light it.)
12. As a player, I want to choose the mark's colour and how long it shows, so that it is noticeable without being distracting.
13. As a player, I want the hit indicator never to show damage numbers or anyone's health, so that no server has cause to call it an advantage.

### Ping readout

14. As a player, I want my ping to the server on screen, so that I can tell lag from my own mistakes.
15. As a player, I want the ping readout to show the number the server reports, ~~the same one the tab list shows~~ the number behind the tab list's signal bars, so that the two never disagree. (*Corrected 2026-09-29 by `docs/research/0004`*: the tab list shows bars and never a number. A vanilla server refreshes the value about every 30 seconds, as a smoothed average, so the readout moves about twice a minute and not with each lag spike.)
16. As a player in singleplayer, I want the ping readout to stay out of the way, so that I am not shown a number that means nothing.
17. As a player, I want the ping and FPS readouts to sit together without overlapping, so that both stay readable.

### Hit colour

18. As a player, I want to choose the colour an entity flashes when it is hurt, so that hits stand out against the world I play in.
19. As a player, I want to choose how strong that flash is, so that it is visible without hiding the entity.
20. As a player, I want hit colour to change only what I see, so that other players and the server see nothing different.

### Freelook

21. As a player, I want to hold a key and look around without turning my player, so that I can look behind me while running forward.
22. As a player, I want the camera to return where it was when I let go, so that I never lose my heading.
23. As a player, I want freelook ~~to be offered only if the servers I play on allow it~~ switched off by ash on servers whose rules ban it, so that I am never banned for a feature ash chose to ship.
69. As a player on a server where freelook is switched off, I want to be told so when I press its key, so that I do not think the feature is broken.

### Snaplook

*Stories 69 to 71 were added on 2026-09-30, after the list was written. They are numbered after it so that references to 1–68 elsewhere stay true.*

70. As a player, I want to hold a key and see myself from the front, the way the game's own third-person front view shows me, so that I can check behind me in one press instead of cycling F5 twice.
71. As a player, I want the view to return to first person when I let go, so that I never end up stuck in the wrong view mid-fight.

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

39. As a player on 1.21.11, I want Lithium included ~~, so that the game runs faster without my doing anything~~ if, and only if, it makes the game measurably faster, so that ash ships nothing it cannot show earns its place. (*Amended 2026-09-30, after `docs/research/0006`.*)
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

56. As a player, I want the launcher in its own ~~typefaces~~ typeface, the same one as in game, so that it looks like one product rather than a system dialog.
57. ~~As a player, I want numbers such as versions, memory and progress in a monospaced face, so that they read as instruments and line up.~~ As a player, I want numbers that change, such as progress and memory, to line up and not jitter, so that they read cleanly. (*Amended 2026-10-03: one face, with tabular figures.*)
58. As a player, I want the launcher to work with no network, and still look right, so that its look never depends on a download.
59. As a player, I want a layout I can read at a glance: ~~my instances down the side, the selected one's details and Play in the middle,~~ a big LAUNCH GAME at the top, my instances clearly marked as the thing to choose, the selected one's servers and details beside them, and my account at the top, so that I am one click from playing. (*Amended 2026-10-03.*)
60. ~~As a player, I want the launcher to stay grayscale throughout, with weight and contrast doing the work colour usually does, so that it looks like nothing else.~~ As a player, I want colour to mean only one thing each - green on or faster, red off or slower, grey no change - so that I never have to learn what a colour means twice. (*Amended 2026-10-03.*)

### Building and testing

61. As a developer, I want each new feature's decisions tested in the shared module without the game, so that the test loop stays in seconds.
62. As a developer, I want every feature's options declared once and the settings file and settings screen both built from that, so that a setting cannot exist in one and not the other.
63. As a developer, I want every new mixin covered by the build's check that each injector landed, so that a broken mixin fails `./gradlew build` by name.
64. As a developer, I want each feature seen working in a real game on both version targets in CI, with a screenshot, so that "it draws" is shown and not inferred.
65. As a developer, I want the 1.21.11 real-game test run with Sodium present as a player would add it, so that a clash with the mod players are most likely to add is caught before they find it.
66. As a developer, I want the launcher's screens rendered in a browser against a fake of the launcher's API, so that its look can be checked without building the app.
67. As a developer, I want that check to fail if a bundled typeface does not load, so that a font silently falling back is caught.
68. As a developer, I want a repeatable frame-time measurement I can run on a real machine, on both version targets, so that optimisations are compared like for like.

### The final look

*Added 2026-10-03 with the approved design.*

72. As a player, I want ash's in-game settings to look like a modern client's, with smooth text, rounded shapes and the game blurred behind, so that ash looks as good as the clients I compare it with.
73. As a player, I want every feature shown as a tile I can switch from where I see it, so that turning things on and off is one click.
74. As a player, I want each tile to say whether the feature raises, lowers or does not change my frame rate, measured rather than guessed, so that I can trust it when I tune for speed.
75. As a player, I want the panel to open and close smoothly but quickly, so that it feels polished without slowing me down.
76. As a player, I want a full colour picker, with a hex box and my recent colours, so that I can match any colour I want.
77. As a player, I want to make ash's interface bigger or smaller, so that it suits my screen.
78. As a player, I want LAUNCH GAME to show clearly that it was clicked and what it is doing, and to make a sound I can turn off, so that I never click twice wondering whether it worked.
79. As a player, I want to rejoin a server I play on in one click from the launcher, so that I go from opening ash to playing in as few steps as possible.
80. As a player, I want my selected instance's ash features, play time and mods at a glance, so that I know what I am about to launch.

## Implementation Decisions

### Shape

- **The seams are Phase 2's, plus three.** What stays:
  - Features stay the client's inbound seam, with their decisions in the shared module and tested against fakes.
  - Game events reach them through thin per-target adapters, in the pattern of the toggle-sprint key.
  - `Ash` stays the launcher's single inbound seam, with the existing fakes.

  The three new seams are:
  - **the settings model**, and since 2026-10-03 **ash's own interface over it**: drawn in the shared module through a screen surface, with each target's input passed through to it (see *The settings model and the settings screen*);
  - **the launcher UI rendered against a fake API**;
  - **a frame-time measurement run on real hardware**.
- **`HudSurface` grows only what a feature needs.** The crosshair needs filled rectangles and the screen's width. Nothing is added ahead of a feature that calls it.
- **Every new feature and every optimisation is a feature in the load report's sense.** Each has an id, a setting that switches it off, and its own degraded state. Each fails alone.

### The features

- **Crosshair.** ash draws its own and suppresses the game's, so the player never sees two.
  - ~~On 1.21.11 the element registry can replace the game's crosshair element. Whether the attack-cooldown indicator is drawn inside that element, and would go with it, is unverified.~~ **On 1.21.11 it is a mixin inside the game's crosshair drawing, not a replacement of the element.** It wraps the one draw call that places the crosshair sprite and draws ash's shape there instead.
    - The cooldown indicator is drawn in the same method straight after the crosshair, and replacing the element through Fabric's registry would lose it.
    - A replacement would also keep only the HUD-hidden rule, because third person, spectator and the debug screen's 3D crosshair are all checked inside that method.
    - Wrapping the one call keeps every rule and the indicator.
    - *Corrected 2026-09-29 by `docs/research/0004`.*
  - On 1.8.9 the HUD callback is additive only, so suppressing the game's crosshair needs a mixin: the same wrap, around the one call in the HUD's render that draws the crosshair. 1.8.9 has no cooldown indicator.
  - So the crosshair is a mixin on both targets. That is what makes its degradation clean: a wrap that does not land leaves the game's own crosshair drawing, never none and never two. Suppression and drawing are one feature that degrades as a unit.
  - Visibility follows each target's own rules for its crosshair. ash inherits them by wrapping the draw, not by restating them. So in third person the crosshair shows on 1.8.9 and hides on 1.21.11, as each game's own does. That is a deliberate exception to Phase 2's "consistent across targets" rule: a replacement belongs where the thing it replaces would have been.
- **Hit indicator.** Fires on the server's confirmation that an entity the player attacked was hurt, never on the click. It reads only what the client already receives, and never shows an amount of damage or anyone's health. Showing health is information the game does not give, and it fails ADR-0006. *Settled 2026-09-29 by `docs/research/0004`:*
  - **On 1.21.11 it is exact.** The damage-event packet names the attacker's entity id, so the indicator fires when that id is the local player's. The hook is inside the packet handler at its hand-off to the entity, because the handler runs twice per packet.
  - **On 1.8.9 it is a match.** The hurt status names no attacker, so it is matched by entity id and a short time window to the player's own recent attack. Legacy Fabric API has no attack event, so recording the attack is a mixin too.
    - *Settled 2026-10-03 by #35:* the window is **1000 ms** from each attack, long enough for a ping of most of a second. Each attack lights at most one mark, and every attack is kept, so two hits in flight on one entity can both be marked.
    - Its limit: if the player's own swing did not land (the entity was still invulnerable from an earlier hit) and someone else's lands on the same entity inside the window, the mark lights for a hit that was not theirs.
    - On a connection slower than the window, the player's own hits show no mark.
  - *Settled 2026-10-03 by #35, for the stand-in look (design A):*
    - The mark is four short diagonals around the crosshair's centre, clear of its arms. It is solid for the first half of its duration and then fades.
    - The player sets its colour and opacity, and its duration from 100 to 1000 ms in steps of 50.
    - It shows whenever the HUD does, in any camera view, on both targets. It confirms a hit; it is not a crosshair, so it does not follow 1.21.11's rule of hiding the crosshair in third person.
  - Traps the ticket must avoid, all from the bytecode:
    - on 1.21.11, entity event 2 is not "hurt";
    - the hurt-animation packet goes only to the player who was hurt, about themselves;
    - on both targets the client's own attack path reports success on the click against a player;
    - Fabric's attack callback also fires on the integrated server.
- **Ping readout.** The latency the server reports for the local player's tab-list entry, on both targets. A vanilla server refreshes it about every 30 seconds. ash shows that value and never measures latency itself: measuring would mean sending the server something vanilla does not. It shares the readouts' layout with the FPS readout.
  - It is hidden in singleplayer.
  - On 1.21.11 the test for that is whether the client is hosting the world. The game's own "is singleplayer" turns false once a world is opened to LAN (`docs/research/0004`).
- **Hit colour.** Changes the tint and strength of the hurt flash on entities. It is rendering only, and armour never flashes on either target. *Settled 2026-09-29 by `docs/research/0004`:*
  - On 1.21.11 the colour is baked into the game's small overlay texture, which every entity's flash samples. Changing it means rewriting those pixels and re-uploading, through an accessor mixin. The change is global and can be made live.
  - On 1.8.9 it is four constants in the living-entity renderer, rewritten per draw by a mixin. That can also be made live.
- **Freelook.** ~~Ships only if two things hold: research shows it passes ADR-0006's test, and no major PvP server prohibits it. If research confirms a ban, freelook is cut.~~ **Ships, switched off by ash on servers whose published rules ban it.** (*Amended 2026-09-30.*)
  - Research 0006 confirmed the bans. Hypixel bans freelook in writing as *"a significant unfair advantage"*, and says the player is responsible for what their client does. MCC Island and Hoplite ban it by name. CubeCraft, PikaNetwork and PvPHQ list it, or an equivalent, as allowed.
  - The product owner chose to ship it with a per-server block rather than cut it. ADR-0006 is amended to record that, and how it answers ADR-0006's test.
  - **The block list ships inside the client:** Hypixel, MCC Island and Hoplite, each with the rules page that bans it.
    - A server is matched by the address the player connected with, including its subdomains, and by the server's own name for itself where it gives one.
    - On a listed server, freelook's key does nothing but say that freelook is off on this server and why. The settings screen shows freelook as unavailable there.
    - Everywhere else it works as normal.
  - **The residual risk is accepted, not solved.**
    - A server that bans freelook but is not on the list, or a listed server reached by an address the match misses, is a ban for the player.
    - The list changes only with an ash release until Phase 4's backend can serve it.
    - The list is kept short and sourced so that it can be kept true.
  - Freelook never changes what the server receives. The player's own rotation, which movement packets carry, is left untouched. A leak of the camera's rotation into movement would be a bug that fails ADR-0006.

  1.8.9 costs more than 1.21.11 (`docs/research/0004`).
  - On 1.21.11 the camera takes its rotation in one place, and the mouse reaches the player in one place.
  - On 1.8.9 the camera reads the player's rotation directly, and so do terrain visibility and particle facing. A camera-only freelook there would turn the view without re-checking which chunks are visible.
- **Snaplook.** Holding its key switches the game to its own front-facing third-person view, and letting go restores the view the player had. (*Added 2026-09-30.*)
  - It uses the game's own camera modes and shows nothing F5 cannot, which is where the servers that draw a line draw it. MCC Island allows mods that skip between F5's modes by name (`docs/research/0006`).
  - It needs no server block.
  - Where each target keeps its camera mode is unverified (Further Notes, item 8).

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
- ~~**Each target draws the screen with its own vanilla widgets, thinly.** Layout and behaviour are the same on both targets, and the widgets look like each target's own.~~ **ash draws its own interface, the same on both targets.** (*Amended 2026-10-03 by the product owner's decision, after the prototype on branch `prototype/ash-ui`.*)
  - The product owner wants a menu like Lunar's in kind, but simpler and in ash's grayscale. Vanilla widgets would look like each game's own grey buttons, so they are dropped.
  - ~~**The layout, for now, is the prototype's design A, "Panel"**, a stand-in drawn from rectangles and text in the game's own font, with stepped rounded corners.~~ Superseded by the final design below. Design A shipped with #55 and #34 and stays until the final design replaces it.
  - **The final design** (*settled 2026-10-03 by the product owner's brief and three rounds on an interactive mockup: branch `prototype/final-design`, https://claude.ai/artifact/J3nd5iXkeJtMsU1Nnt2ZJe, version 3*):
    - **Frame.**
      - The panel covers 85% of the screen, centred with equal margins, and has rounded corners and a soft shadow.
      - Everything behind it, the margins included, is the game, blurred.
      - The panel is 55% black over that blur.
    - **Left strip.** A strip joined to the panel by a thin divider, darker than the panel (about 79% black).
      - At the top: SETTINGS in large, upright capitals stacked closely one under another.
      - At the bottom: two square buttons, each as wide as the strip allows with equal margins either side. Edit HUD sits above a gear.
      - **Edit HUD** is dimmed until the HUD editor (#41) exists; pressing it gives a short shake and "coming soon".
      - **The gear** opens ash's own settings: the open key, interface size (80–130%), animations on or off, and background blur on or off.
    - **The tiles (the panel opens here).**
      - A search box and category tabs (All, then a tab per category) run across the top.
      - Below them, one tile per feature, five to a row at 1920×1080, scrolling when there are more.
    - **A tile**, top to bottom: the feature's name; its icon; its FPS mark; and a gear beside an ENABLED or DISABLED button.
      - Clicking the tile's body or its gear opens its options. Only the button switches the feature.
      - **The FPS mark**, with "FPS" written under it:
        - a green rounded triangle pointing up if the feature raises the frame rate;
        - a red one pointing down if it lowers it;
        - a grey horizontal line if the difference is within ±3%.
      - It is measured, never guessed: each feature is run on and off through the frame-time measurement, and the result is recorded with the feature. Every feature so far measures grey.
      - **A feature that did not load** is a dimmed tile whose grey button reads UNAVAILABLE. Clicking it shakes the tile and gives a one-line reason, worded as the launcher's notice is.
      - **A feature with no options yet** keeps its gear, which opens a page saying so.
    - **Options pages** replace the tiles with a fade; the strip stays.
      - At the top: back, the feature's icon and name, and its ENABLED button.
      - The options are rounded rows. A live preview sits on the right where one helps: the crosshair over sky, snow and night, or the hit indicator with a "Test a hit" button.
      - "Reset to defaults" puts the switch and every option back.
    - **The colour picker** folds open from the colour's chip. It has:
      - a saturation and brightness square;
      - a hue bar and an opacity bar;
      - a hex box;
      - preset swatches and recent colours.
    - **Colours.**
      - Text is white, icons are white at 80%, and every highlight is white: slider fills, the selected tab, focus, the picker's markers.
      - Green means enabled or faster, red means disabled or slower, and grey (#A7ADA6) means no change or unavailable. They are bright, as other clients' are.
      - Tiles are a faint white fill (about 6%) that brightens on hover.
    - **Type and icons.**
      - Inter everywhere, in game and in the launcher. Exceptions are rare.
      - Icons are one outline set with rounded strokes.
    - **Motion.**
      - **Opening:** the blur and the panel fade in together while the panel rises from slightly below and slows to a stop, in about 350 ms. The tiles then rise and fade in, one just after another.
      - **Closing:** the reverse, in about half the time.
      - **Pages** cross-fade.
      - **Switches** move fast and then ease out.
      - **A short shake** marks only a refused action: an unavailable feature, freelook on a server that bans it, or an invalid colour code. It shakes the thing refused, never the whole screen.
      - Animations can be switched off in ash's settings.
    - **Size** follows the screen, so the panel keeps its proportions at 1080p and 1440p. Interface size in ash's settings scales it further.
  - **How it is drawn.** Rectangles of solid colour in GUI units, stepped corners and the game's bitmap font cannot look like this, so the drawing changes. Exactly how, on each target, is unverified item 9 in *Further Notes*.
    - ash draws the panel at the screen's real resolution, not in the game's scaled GUI units.
    - Text is ash's own: Inter, at any size.
    - Rounded shapes are anti-aliased.
    - The game behind the panel is blurred on both targets. On 1.8.9 the game blurs before it draws the HUD, so ash hides the HUD while the panel is open there, and both targets look the same (*settled 2026-10-03, after `docs/research/0007`*).
  - **The shared module draws it and decides everything.** It draws through a screen surface. That surface grows from "fill a rectangle, draw text, measure text" to also cover rounded rectangles, text in a given size and weight, icons, clipping, and drawing a group at an opacity, all at real resolution. It takes input passed through from the target: mouse press, drag, release and scroll, keys mapped to ash's own names, and typed characters. Every widget, from the switch and the slider to the colour picker and the HUD editor, is ash's own and tested in the shared module against a fake surface with simulated input.
  - **Each target supplies only a thin screen.** That screen forwards the game's input, implements the surface, and closes on its key.
- **The screen opens on a key binding, Right Shift by default**, registered like toggle sprint's and rebindable in Controls. A button on the title or pause screen is out of scope. The launcher names the key once, where a new player will see it.
  - Right Shift is unbound by default on both targets.
  - A key binding receives no presses while a screen is open, so the screen recognises its own key to close.
  - *Settled 2026-09-29 by `docs/research/0005`.*
- **The building blocks, per target** (*settled 2026-09-29 by `docs/research/0005`*; *since 2026-10-03, ash draws its own widgets, so what follows about each target's widgets is background, not the plan. The key polling and the input each target gives a screen still apply*):
  - **1.21.11** has every widget the option kinds need: a cycling button, a checkbox, a slider, a text box and a scrolling list of rows. The key is polled on Fabric API's client tick event, which is already inside the Fabric API ash ships, so there are no new modules.
    - The game's own options list is not used, because it rewrites the game's `options.txt` every time the screen closes.
  - **1.8.9** has a slider, which works in fractions and is made whole-number by the shared module. It has a text box and a scrolling list like its Controls screen. It has no cycling button and no checkbox; the game's own screens use a plain button whose label changes, and so does ash.
    - The key is polled from **ash's own small hook on the client tick**, not from Legacy Fabric's lifecycle-events module. That module would mean two more jars pinned and mirrored, and its code is required, so if it failed to apply the whole game would stop. ash's own hook degrades only the settings key.
- **Colour options never go below the smallest opacity both targets draw the same way.** At near-zero opacity, 1.8.9 draws text fully opaque and 1.21.11 draws nothing (`HudSurface` records the same fault). The settings model clamps the value, so the same setting never looks opposite on the two targets.
- **A feature that degraded is shown as unavailable** on the screen, with the same wording the launcher's notice uses. It is never a live switch.
- **Readout positions are an anchor plus an offset.** The anchor is the nearest corner or edge, and the offset is in GUI units. That keeps a placed readout in place across resolution, window size and GUI-scale changes, and makes clamping on screen a pure calculation. ~~Dragging is the only per-target part.~~ Dragging is ash's own too, from the panel's "Edit HUD" mode; the target only passes the mouse through.

### Performance

- **Lithium is ~~a bundled mod~~ measured on 1.21.11 first, and bundled only on a measured gain** (*amended 2026-09-30, after `docs/research/0006`*).
  - Its own documentation credits its frame-rate gains to singleplayer's built-in server. It makes no claim for play on a server, and it sends players to Sodium for rendering.
  - With a default config, its client-only mixins are four pieces of chunk and entity bookkeeping.
  - So its ticket measures frame time with and without it, both in singleplayer and on a server, using the frame-time measurement. It bundles Lithium only if that shows a gain, the same rule the 1.8.9 optimisations follow. If it shows none, ADR-0013's "Lithium stays" is amended.
  - If it ships, it is pinned, verified, stored in the depot and shipped the way Fabric API is.
    - The candidate pin is `0.21.4+mc1.21.11`. It needs no Fabric API, accepts both loader pins, and was verified against Modrinth on 2026-09-30. Its branch has unreleased fixes, so the ticket re-queries Modrinth first.
  - It is LGPL-3.0-only (ADR-0013, confirmed from the shipped jar). ~~so its licence text and a written offer for its source ship with ash (ADR-0004)~~ If ash ships it:
    - the LGPL text **and the GPL text** ship with it, because the jar carries only the LGPL;
    - ash gives clear directions to its source next to where it is offered (GPL-3.0 §6(d)). The "written offer" is for physical products.

    *Corrected 2026-09-30 by `docs/research/0006`.*
  - No ash mixin targets a Lithium class (ADR-0004). Its mixins are required, so an ash mixin that disturbed one of its methods would crash the game, not degrade. None of Phase 3's hooks is in its path. `Entity` and `LivingEntity` are the adjacent classes a later ticket must check.
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
  - ~~How to do both on both loaders is unverified: Fabric Loader has a documented way to add mod locations, and whether the pinned loader versions honour it, and how to stop the mods folder being read, must be confirmed first.~~ *Settled 2026-09-29 by `docs/research/0005`, run against the real launch on both targets.*
    - ash's client and bundled mods always go to the loader by path, with `fabric.addMods`.
    - The mods folder cannot be switched off, only moved. So "off" sets `fabric.modsFolder` to an empty directory ash owns, and "on" leaves it unset.
    - Both properties are added where the launch sets memory, not in the loader pin's arguments. On 1.8.9, any JVM argument in the pin switches off the fallback that supplies the classpath, and the game does not start.
    - A missing `addMods` path is only a warning to the loader. ash therefore checks that every one of its own jars exists before each launch, as it already does for the client.
  - Instances prepared in Phase 2 have ash's jars in that folder. Preparing them removes only the files ash itself put there, ~~by name~~ by exact file name and pinned hash.
  - **Phase 2's preparation already breaks this rule, and the fix belongs in this area's first ticket.** It removes any jar whose name starts with a bundled mod's artifact name. That includes a player's own download of the same mod, such as Modrinth's `legacy-fabric-api-1.20.1.jar`. Its comments also say the loader refuses to start when two files claim one mod id, and the research shows it does not (below).
- **Two copies of one mod are neither a crash nor a warning.** The loader silently keeps the newest version that works, from wherever it came.
  - So with third-party mods on, a player's newer Fabric API, or a newer Legacy Fabric API module, quietly replaces the one ash pinned and tested.
  - The reverse happens too: a player's copy whose own dependencies are missing is quietly dropped for ash's.
  - The load report therefore records which copy of each bundled mod actually loaded, ash's or the player's, which the client can read from the loader. A notice after a session where the player's copy replaced ash's says so.
- **The launcher never deletes, moves, renames, vets or updates a third-party mod.** It opens the folder for the player, and that is all.
- **A crash with third-party mods on names them as the likely cause** and offers to launch without them.
- **The load report records whether third-party mods were loaded**, so the degradation notice can say that one of the player's mods may be the cause instead of claiming the fault is ash's. This amends ADR-0017, and the amendment is recorded as an ADR in the ticket that makes it.
- **Sodium is the case that matters most**, and the one ADR-0013 names. Sodium's mixin configs are required, so an ash mixin that disturbs a method Sodium overwrites is a crash on launch. The 1.21.11 real-game test therefore also runs with Sodium present. Downloading it in CI to test against is not bundling it.

### The launcher's look

- ~~**Sora 500, Inter 400 and 500, and JetBrains Mono 500 are bundled**~~ **Inter alone is bundled** (*amended 2026-10-03: the product owner wants one face everywhere, in game and in the launcher*). It is bundled as local font files in the weights the design uses, allowed by the app's content security policy, with its licence shipped alongside. No font is ever fetched at runtime.
  - *Settled 2026-09-30 by `docs/research/0006`:* Inter is under the SIL Open Font Licence 1.1 and declares no Reserved Font Name.
  - The official woff2 files ship unmodified. Subsetting is allowed but would make a modified version.
  - ~~Sora's variable font and JetBrains Mono's v2.304 release~~ are no longer needed.
- ~~**The brief's layout:** the instance switcher down the left; the selected instance's details and Play in the centre; the account switcher and settings in a top bar.~~
- **The final layout** (*settled 2026-10-03, with the in-game design: the same mockup, version 3*), drawn from Dawn's home screen:
  - **Its own title bar**, replacing the Windows one:
    - the "ash" wordmark on the left, where a logo joins it later;
    - the account's name and face on the right, opening a menu to switch account, add one or sign out;
    - then minimise, maximise and close.
    - The bar drags the window.
  - **An icon-only sidebar**: Play, Mods and News at the top, Settings at the bottom. Each name shows on hover, and the current page is highlighted.
  - **Play**:
    - **Greeting:** "Welcome back," with the account's face and name.
    - **The LAUNCH area** runs the full width, over a pixel scene ash draws itself.
      - The scene follows the local time of day (day, sunset, night) and drifts slowly.
      - A green **LAUNCH GAME** button sits in the middle, with the selected instance under it.
      - **When clicked:**
        - the button presses in and a ripple spreads from the click;
        - the scene brightens and slowly zooms, with a light sweeping across;
        - the button fills through named steps (checking files, downloading with a percentage, starting) and ends on a green tick and PLAYING;
        - a short sound plays on the click, and a quieter one when the game starts, unless launch sounds are off in Settings.
    - **Below it, left to right:**
      - **Recent servers** for the selected instance, each with its status and player count, the most recent first. **Join** launches the instance straight into that server. A server that is offline cannot be joined.
        - *Settled 2026-10-03, after `docs/research/0008`:* the game's server list records no recency, so **ash's client records every server the player joins, with the time**, however they joined. A vanilla instance, or an ash instance that has joined nothing yet, shows its own server list in the player's order.
      - **This instance**: the ash features that are on, play time, the last session, the mods, and a shortcut to the instance's page.
      - **Instances**:
        - Each instance is a row with its version, name, "ash client" or "vanilla", and when it was last played.
        - The selected row is highlighted translucent white, and its cog opens the instance's own page.
        - A small New button sits at the top.
        - The card has a soft white glow and a brighter surface than its neighbours, because choosing what to play is the page's main job.
  - **The instance's own page**: name, memory, window size, Java, open folder, and delete, which asks for confirmation on the page itself.
  - **Mods**: the selected instance's third-party mods (see *Third-party mods*).
  - **News**: "coming soon" until the news panel in Phase 5.
  - **Settings**: default memory, what the launcher does when the game starts, launch sounds, and language.
- ~~**Grayscale only**, from the brief's four values.~~ **The in-game palette** (*amended 2026-10-03*): near-black with a faint soft glow, translucent white surfaces, white text, and green, red and grey for their meanings. Numbers use Inter's tabular figures.
- **Tone** is the brief's: short, blunt, sentence case.

## Testing Decisions

A good test states a player-visible fact and fails if that fact stops being true. It drives the product through a seam rather than reaching into a module, and it names what it proves. Phase 2 kept finding tests that passed for the wrong reason: a "switched off" test that passed because "off" did not parse, and a leak test whose second forgery hid a missing guard. Phase 3's tests are held to the same bar, and the guards that make a vacuous pass impossible are kept.

**Client, shared module.** Plain JUnit on every `./gradlew build`, with fakes for the per-target seams. Prior art is the FPS readout, toggle sprint and load report tests. What needs covering:
- the crosshair's geometry for every shape and setting, including odd sizes and the outline;
- the hit indicator's timing, and its refusal to fire on a click with no confirmation;
- the ping readout's formatting and its absence in singleplayer;
- freelook's camera state across hold, release and a screen opening mid-hold;
- the freelook block list: a listed server's addresses and subdomains match, a look-alike domain does not, and a blocked press says why;
- snaplook's hold-and-restore of the view the player had;
- the settings model: every option round-trips through the file; an in-game change preserves comments, ordering and unknown keys; out-of-range and malformed values fall back without throwing;
- readout anchoring across resolution and GUI-scale changes, and clamping on screen.

**Client, per target.** The real-game tests on both targets: Fabric's client game test on 1.21.11 and ash's smoke test on 1.8.9.
- Each feature is seen working, with a screenshot.
- The settings screen is opened, a value is changed, and both the file and the feature are checked.
- `AshMixinsLandTest` covers every new mixin by construction, because it reads the mixin configs.
- The 1.21.11 test runs a second time with Sodium present.
- 1.8.9's tier stays weaker than 1.21.11's (Phase 2, #22). A ticket that assumes the two targets have the same tier will be wrong about the weaker one.

**Launcher.** Every new test drives `Ash` with the existing fakes. Prior art is the loader tests for pins, depot and preparation, and the instance tests for instance settings. What needs covering:
- if Lithium ships, it is planned, verified, resumed and shared through the depot, and a corrupt copy fails as a game file does;
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
- freelook is refused on Hypixel with its reason shown, and works on a server that allows it;
- a 1.8.9 optimisation shows its measured gain;
- the launcher is seen in its own typefaces.

No fixture substitutes for this.

## Out of Scope

- The backend, cosmetics, entitlements, synced settings and news. These are Phase 4.
- macOS, ~~server entries and quick-connect~~ hand-made server entries, and the news panel. These are Phase 5. (*Amended 2026-10-03: joining a recent server from the launcher, read from the instance's own server list, is in this phase, with the final design.*)
- Bundling Sodium (ADR-0013). Players add it as a third-party mod.
- Forge mods, and mods for any loader other than the instance's own.
- Finding, downloading, vetting or updating third-party mods for the player.
- Version targets other than 1.8.9 and 1.21.11 (ADR-0005).
- Features outside the brief's list, such as keystrokes, clicks per second, armour and potion status, or coordinates.
- A protocol for servers to switch ash's features off. Freelook's block list is ash's own, shipped in the client.
- Updating freelook's block list between ash releases. Phase 4's backend could serve it.
- A button for ash's settings on the title or pause screen.
- Shaders.
- Self-update of the launcher or the client.
- Changing an existing instance's loader.

## Further Notes

- **~~Seven~~ ~~Eight~~ Ten things are unverified and must not be assumed while ticketing.** (*Item 8 added 2026-09-30; items 9 and 10 added 2026-10-03 with the final design.*) Phase 2 had three, and research settled each before its ticket needed it. Settling these is the next step after this spec.
  1. ~~**The game-side hooks for each feature on each target.** These are the crosshair and whether the 1.21.11 cooldown indicator goes with it, the packet or event that confirms a hit, the ping in the tab list, the hurt tint, and the camera for freelook. They need a decompiled-source pass like toggle sprint's.~~ *Answered 2026-09-29 in `docs/research/0004-vanilla-hooks-for-phase-3-features.md`, read from this build's mapped jars.* Three findings changed the decisions above:
     - the cooldown indicator is inside the crosshair method, so the crosshair is a mixin on both targets;
     - the hit indicator is exact on 1.21.11 and a match on 1.8.9;
     - the tab list shows bars, refreshed about every 30 seconds.

     Still unchecked: whether Sodium touches the same targets. The Sodium run of the 1.21.11 real-game test is what will answer that.
  2. ~~**Freelook's standing with major PvP servers.** This decides whether it ships.~~ *Answered 2026-09-30 in `docs/research/0006-freelook-lithium-and-launcher-typefaces.md`:* Hypixel, MCC Island and Hoplite ban it in their published rules. CubeCraft, PikaNetwork and PvPHQ allow it or an equivalent. It ships with a per-server block (see *The features*).

     The same research found that Hypixel's page lists "auto-sprint" as disallowed automation without defining it. ADR-0006 now records why toggle sprint is not that.
  3. ~~**Lithium for 1.21.11:** the version to pin, where it is published and with what hash, its licence as shipped, and whether any of its mixins touch what ash's do.~~ *Answered 2026-09-30 in `docs/research/0006`:* the pin, licence and mixin overlap are recorded above. Its frame-rate benefit is in singleplayer only, which is why it is now measured before it is bundled.
  4. ~~**How to load a player's mods and ash's own separately** on the pinned Fabric Loader, for both version targets.~~ *Answered 2026-09-29 in `docs/research/0005-loading-third-party-mods-and-settings-screen-widgets.md`, by running the real launch on both targets.* The mechanism is `fabric.addMods` plus `fabric.modsFolder`.

     The pins are not one loader: 1.21.11 pins Fabric Loader 0.19.5 and 1.8.9 pins 0.19.3. Their mod-discovery code is identical.

     The research also overturned the belief that duplicate mods stop the game, and found Phase 2's preparation deleting by prefix. Both are in the decisions above.
  5. **Where 1.8.9's frame time goes.** This is measured, not guessed, and it decides which optimisations exist.
  6. ~~**Which widgets each target offers for the settings screen**, such as sliders and text fields, and whether 1.8.9 needs further Legacy Fabric API modules. Each module would be pinned, mirrored and shipped as toggle sprint's was.~~ *Answered 2026-09-29 in `docs/research/0005`, from the mapped jars.* Both targets have what the option kinds need. 1.8.9 lacks a cycling button and a checkbox, and uses a button whose label changes instead. No new Legacy Fabric module is needed, because ash hooks the client tick itself.
  7. ~~**The typefaces' licences.** All three are expected to be under the SIL Open Font Licence. That needs confirming from each project's own repository.~~ *Answered 2026-09-30 in `docs/research/0006`:* all three are OFL 1.1, with no Reserved Font Name. Sora supplies 500 only as a variable font.
  8. **Where freelook's block list and snaplook meet the game.** This means:
     - the address the player connected with, and the server's own name for itself, on each target;
     - whether the name each listed server gives identifies it reliably;
     - where each target keeps its camera mode.

     It needs a pass through the mapped jars like research 0004's, and a check against the listed servers themselves.
  9. ~~**How each target can draw the final in-game design**~~ *Answered 2026-10-03 in `docs/research/0007-drawing-the-final-in-game-design.md`, with a spike in both real games:* text, shapes and icons are rasterised by Java 2D in the shared module and drawn 1:1 at real resolution over each game's own blur, from cached pieces the GPU moves. (*Item added 2026-10-03.*) What it covered: This means:
     - drawing at the screen's real resolution instead of in GUI units;
     - text in Inter: 1.21.11 may load a TTF through its own font system; 1.8.9 has no TTF support, so ash would render its own glyphs;
     - anti-aliased rounded shapes;
     - blurring the game behind the panel: 1.21.11 has a menu blur of its own, while 1.8.9 would need a post-processing shader;
     - an icon set and its licence;
     - what each costs in frame time while the panel is open.

     It decides how the screen surface grows. It needs a pass through both mapped jars and a working spike on each target before the drawing layer is ticketed.
  10. ~~**What the launcher's new Play page needs from the game and the system**~~ *Answered 2026-10-03 in `docs/research/0008-what-the-launchers-play-page-needs.md`.* All of it is feasible; one finding needs a product decision: the server list records no recency, so "Recent servers" needs a source. (*Item added 2026-10-03.*) What it covered: This means:
      - reading an instance's server list (`servers.dat`, NBT) on both targets;
      - asking a server for its status and player count the way the game's own server list does, so the launcher sends nothing the game would not;
      - launching straight into a server on each target: 1.21.11's quick-play arguments and 1.8.9's server arguments;
      - recording play time and the last session from the launcher's own launch and exit;
      - a frameless window with its own title bar in Tauri on Windows, including dragging and snapping;
      - the launch sound, and its licence or origin.
- **Order.**
  - The settings model comes first, because every feature after it declares its options through it.
  - Lithium ~~is early, because it is small and changes the environment every later test runs in~~ follows the frame-time measurement, since it ships only on a measured gain.
  - The features can then proceed in parallel.
  - The 1.8.9 optimisations start with the measurement and nothing else.
  - The launcher's look is independent of everything else and can go at any time.
  - The final design is built in game and in the launcher together, as the product owner chose. In game, item 9's research and the drawing layer come before the panel; the features already built move onto it unchanged, because the panel is built from the same settings declaration.
- **This phase is bigger than Phase 2.** It has four areas, where Phase 2 had one. If it needs splitting, the launcher's look and the 1.8.9 optimisations split off cleanly, since neither blocks nor is blocked by the features.
- ~~**Freelook is the likeliest feature to be cut, and cutting it is a success.**~~ **Freelook is the feature most likely to cost a player a ban**, which is why its block list is sourced and tested, and why missing a server is the accepted risk named in ADR-0006. ADR-0006 exists so that ash never ships the feature that gets it banned; for freelook it now does that server by server.
- **Windows-only runtime failures still have no CI signal** (ADR-0016). Manual acceptance remains the only gate on them, and the settings screen and the third-party mod path are new places for them to hide.
- Per the project brief, this phase does not begin until Phase 2 works end to end. It does: manual acceptance passed on 2026-09-29 and #15 is closed.

## Tracker

Filed as [#30](https://github.com/sisi-j/ash/issues/30), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. This file is the source of truth; the issue is the tracker entry. Keep them in step if either changes.
