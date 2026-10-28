# Beta phase 1: Foundations

*Designed 2026-10-28 in a grilling session with the product owner (see 0006, the beta). Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

Everything the beta adds stands on four things ash doesn't have yet:
- **The newest Minecraft.** Minecraft is on 26.3. ash's client covers 1.8.9, 1.20.4 and 1.21.11, so a player on the current game gets none of ash's features.
- **HUD items that work alike.** Only the FPS and ping readouts are on the HUD, and they're wired in one by one. A player can drag them, but can't size them, colour them, give them a background, line them up or hide them in F3. Beta phase 3 adds a dozen more items, and each would need the same wiring again.
- **A settings screen that scales.** It has tabs and a search box over nine tiles, and that's enough for nine. With twenty-five tiles and dozens of options, a player needs to find an option by name and keep their favourites at hand. The categories also have to fit what's coming: visual tweaks and chat.
- **One answer to fair play.** Freelook has a list of servers it's off on. Zoom, fullbright, coordinates and the rest will each need the same, and a second or third mechanism would drift from the first.

## Solution

- **26.3 is a fourth version target,** with ash's whole client: every feature, capes, the settings screen, a client game test and FPS marks. The newest target moves forward as Minecraft releases (ADR-0028).
- **Every HUD item is the same kind of thing.** It is placed, sized, styled, snapped, hidden and reset the same way, from one registry. The FPS and ping readouts become the first two HUD items.
- **Per-item style:**
  - size;
  - text colour, including chroma (a slow rainbow);
  - background box with colour and opacity;
  - text shadow;
  - brackets around values;
  - its own label.
- **A better HUD editor:**
  - snap guides to the screen's edges and centre, and to other items;
  - hide an item while F3 is open, while chat is open, or both;
  - "only when relevant", for items that have nothing to show sometimes;
  - reset one item, or all.
- **The settings screen:**
  - search finds options as well as features;
  - a star pins a tile to a favourites row;
  - the categories become HUD, PvP, Visual, Chat, Movement, Performance and Cosmetics.
- **One fair-play list** for every feature, shipped in the client, with freelook's block list moved into it. A feature that's off on a server says so on its tile and in game.
- **The game view:** one interface through which the client's shared code reads the game, so that every in-game feature from here on is tested without a game.

## User Stories

### 26.3

1. As a player on 26.3, I want ash's client in my game, so that I don't have to stay on an older version to use ash.
2. As a player, I want a 26.3 instance ready after I sign in, alongside 1.21.11, 1.20.4 and 1.8.9, so that the newest version is one click away.
3. As a player, I want every feature I use on 1.21.11 to work the same on 26.3, so that moving up costs me nothing.
4. As a player, I want my cape and other players' capes on 26.3, so that cosmetics follow me.
5. As a player, I want ash to fetch the Java 26.3 needs by itself, so that I never install Java.
6. As a player, I want FPS marks on 26.3's tiles, measured rather than guessed, so that I can trust them there too.
7. As a player, I want my synced settings to apply to my 26.3 instance like any other, so that my setup is already there.
8. As a player, I want ash to support the next Minecraft release within a few weeks of it coming out, so that I'm never long without ash on the newest game.
9. As a player with a 26.3 instance, I want it to keep working with ash's features after ash moves on to a newer release, so that updating ash never breaks an instance.
10. As a player on a retired version, I want the launcher to tell me a newer version has ash's latest features, and offer a new instance on it, so that I know why features stopped arriving.

### HUD items

11. As a player, I want every HUD item to be placed, sized and styled the same way, so that learning one teaches me all of them.
12. As a player, I want to resize a HUD item from 50% to 200%, so that it's readable without taking over my screen.
13. As a player, I want to pick a HUD item's text colour with the colour picker, so that it matches my setup.
14. As a player, I want a chroma option that cycles a HUD item's text through the rainbow, so that I can have Lunar's look if I want it.
15. As a player, I want a background box behind a HUD item, with its own colour and opacity, so that it reads on bright and dark scenes alike.
16. As a player, I want to switch a HUD item's text shadow on or off, so that it looks crisp or soft as I like.
17. As a player, I want brackets around a value, like `[120 FPS]`, as an option, so that I can match the style I'm used to.
18. As a player, I want to change an item's label, or hide it, so that it says `FPS: 120`, `120 fps` or just `120`.
19. As a player, I want a HUD item to keep its place relative to its corner when I resize the window or change GUI scale, so that I set it up once.
20. As a player, I want new HUD items to arrive in sensible spots that don't overlap my existing ones, so that switching one on doesn't make a mess.

### The HUD editor

21. As a player, I want items to snap to the screen's edges and centre while I drag them, with a guide line showing, so that lining them up is easy.
22. As a player, I want items to snap to each other's edges, so that a column of items lines up.
23. As a player, I want to hold a key while dragging to switch snapping off, so that I can place an item exactly where I want.
24. As a player, I want to see every item's outline in the editor, including ones with nothing to show right now, so that I can place them all.
25. As a player, I want to hide a HUD item while F3's debug screen is open, so that it doesn't cover the debug text.
26. As a player, I want to hide a HUD item while chat is open, so that it doesn't cover what I'm typing.
27. As a player, I want an "only when relevant" option on items that have nothing to show sometimes, so that my screen stays clean. Armour shows only when I wear armour, and potion effects only when one is active.
28. As a player, I want F1 to hide all of ash's HUD, as it hides the game's, so that screenshots are clean.
29. As a player, I want to reset one item to its default place and style, so that I can undo one experiment without losing the rest.
30. As a player, I want to reset the whole HUD, so that I can start over.
31. As a player, I want to open an item's style options from the editor by clicking it, so that styling happens where I'm looking.

### The settings screen

32. As a player, I want search to find options as well as features, so that typing "colour" shows me every colour setting.
33. As a player, I want a search result for an option to open that option's page with the option highlighted, so that I don't hunt for it again.
34. As a player, I want to star a tile, so that features I change often sit in a favourites row at the top.
35. As a player, I want my favourites to follow me between computers with my settings, so that my panel looks the same everywhere.
36. As a player, I want the tabs to be HUD, PvP, Visual, Chat, Movement, Performance and Cosmetics, so that I can find a feature by what it's for.
37. As a player, I want a tab with nothing in it yet not to show, so that the panel never shows me an empty page.
38. As a player, I want search to work in any tab and to clear with Escape before Escape closes the panel, so that the panel behaves as it does today.

### Fair play

39. As a player, I want a feature a server forbids to be off on that server, so that using ash never gets me banned.
40. As a player, I want to be told in game when a feature is off because of the server, so that I don't think it's broken.
41. As a player, I want a tile to say which server it's off on and why, so that I understand the rule.
42. As a player, I want a feature to come back on by itself when I leave that server, so that I never have to remember to switch it back.
43. As a player, I want the rule to apply to a server's other addresses and subdomains, so that joining through `mc.hypixel.net` counts the same as `hypixel.net`.
44. As the product owner, I want one list for every feature's server rules, so that adding a rule for a new feature is one entry, not new code.

### Building on it

45. As a contributor, I want to test an in-game feature against a fake game, so that its logic is proved without starting Minecraft.
46. As a contributor, I want to add a HUD item by declaring it, with no editor, style or settings-screen code of its own, so that each new item is small.
47. As a contributor, I want a feature to declare which version targets it runs on, so that a feature that can't exist on one target is shown as unavailable there rather than missing.

## Implementation Decisions

### 26.3 as a version target (ADR-0028)
- **A new target module** beside the three, ported from 1.21.11. Its facts:
  - Mojang's own names: 26.x ships unobfuscated, with no mappings to download;
  - Java 25, Mojang's `java-runtime-epsilon`;
  - Fabric Loader 0.20.1;
  - Fabric API 0.165.0+26.3, bundled as on the other modern targets.
- **The pins are research, then code.** The ticket's first step is research 0016: where each of ash's hooks is in 26.3, what changed in rendering, skins and capes since 1.21.11, and the exact Fabric versions. Everything is pinned in the same way as the other targets (loader pins, mirror, hashes).
- **First run** makes four instances: 26.3, 1.21.11, 1.20.4 and 1.8.9.
- **Moving the newest target forward.** ADR-0028 records this:
  - **What stays:** the version targets are 1.8.9, 1.20.4, 1.21.11 and the newest release;
  - **A new release:** when one comes out, a port to it becomes the newest target;
  - **The retired target:** the release before keeps its last client build unchanged, and its instances keep launching with the features they had. The launcher marks the instance "ash's newest features need 26.4", with a button that makes a new instance on it.
  - **Glossary:** the glossary's version-target entry is updated to match.

### HUD items
- **A HUD item** is declared once. Its declaration says:
  - its feature;
  - its default anchor and offset;
  - what it shows, as a function of the game view;
  - whether it can be "only when relevant";
  - which style options it takes, since a crosshair-like item may not take a background, for example.
- **The registry replaces the hard-wired readouts.** The HUD layout today knows exactly two readouts, by name. It becomes a registry of HUD items, and the FPS and ping readouts move into it without changing what players see or where.
- **Positions don't move.** A position already saved in client settings keeps its key and its meaning.
- **Every HUD item gets the same style options** in client settings, declared once as a group. They are size (50–200%, in steps of 10), text colour (with chroma), background on/off, background colour (with opacity), shadow, brackets, label. Its options page shows them under the item's own options, so that no HUD item has screen code of its own.
- **Chroma** is a hue cycle over time, the same speed for every item. An item set to chroma keeps the colour the player picked for when chroma is switched off.
- **Default placement:** a newly switched-on HUD item takes its declared default spot. If that spot overlaps an item already shown, it moves down its column to the first free spot.

### The HUD editor
- **Snapping.** While an item is dragged, it snaps within 4 GUI pixels to:
  - the screen's edges and its horizontal and vertical centre lines;
  - the edges and centres of other shown items.
  A guide line shows what it snapped to. Holding Shift while dragging switches snapping off.
- **Every item is shown while editing,** including ones that are "only when relevant" with nothing to show, drawn with sample values so they can be placed.
- **Hide while:** each item has "Hide while F3 is open" and "Hide while chat is open", both off by default. F1 hides all of ash's HUD, always.
- **"Only when relevant"** is on by default for items that declare it.
- **Opening and resetting:** clicking an item in the editor opens its style options. Its options page has "Reset this item". The editor has "Reset all", which asks first.

### The settings screen
- **Search covers every feature option,** by its name and its feature's name. A result for an option shows the feature's tile with the matched option named under it. Opening it goes to the option's page, with the option highlighted for a moment.
- **Favourites:**
  - a star on a tile pins it to a favourites row above the tiles on the All tab;
  - favourites are a client setting, so they sync with feature settings (ADR-0022);
  - stars are kept in the order they were added.
- **Categories:** HUD, PvP, Visual, Chat, Movement, Performance and Cosmetics.
  - **Changes now:** Hit colour moves to Visual.
  - **For later phases:** each new feature's spec names its category.
  - **Empty tabs:** a tab with no tiles isn't shown.

### Fair play
- **The fair-play list** is one resource shipped in the client. Each entry gives a feature, server address patterns and a reason. A pattern can be an exact host or a host with all its subdomains. Freelook's existing block list moves into it, with the same servers.
- **One check:** a feature asks the list whether it's allowed on the current server; there is no per-feature code. Off means the feature does nothing on that server. The player's own switch is unchanged, and the feature comes back on leaving.
- **Telling the player:**
  - when the player joins a server where a switched-on feature is off, a short note in game names the feature and the reason, once per join;
  - the feature's tile shows the reason while on that server.
- **When the list changes:** it's updated only with a client release, as freelook's list is today. Serving it from the backend stays out of scope.

### The game view
- **The game view** is an interface in the shared module. Each version target implements it from its own game. It gives the shared code read-only facts:
  - keys and mouse buttons held;
  - the player's position, facing and biome;
  - worn armour and the held item, with durability;
  - active effects with time left;
  - the current server address;
  - the debug screen open or not, and chat open or not;
  - whether the server reduces debug info.
- **It grows with the phases.** This phase adds what the HUD editor needs: F3 open, chat open, the current server. The other facts arrive with the features that need them in beta phases 3 and 4.
- **Faked in tests:** a fake game view is filled in by tests, the way `FakeHudSurface` is today.

### Features per version target
- **A feature declares which version targets it runs on.** One that can't exist on a target (an option the old game has no hook for) is a tile marked "Not on 1.8.9", like a feature that didn't load, rather than a missing tile. The load report names it the same way.

## Testing Decisions

- **26.3:**
  - its client game test runs in CI as on 1.21.11 and 1.20.4: everything loads, the cape draws, and the settings screen opens;
  - FPS marks are measured in a benchmark sitting, by the README's method;
  - the four-instance first run is unit-tested in ash-core.
- **HUD items and style:**
  - the shared module's tests drive items through `FakeHudSurface` and a fake game view: size, colour, chroma over time, background, shadow, brackets and labels;
  - the readouts' existing placement tests keep passing unchanged, which proves the move into the registry didn't move anything.
- **Editor:** snapping, guides, the Shift override, hide-while, "only when relevant" and resets are unit-tested on the layout, with no game. Each target's game test drags an item and finds it snapped.
- **Settings screen:** option search, favourites order, empty tabs and Escape are tested on the panel's model. A game test types a search on each modern target.
- **Fair play:**
  - the list's matching (exact host, subdomains, case, trailing dots, ports) is unit-tested;
  - freelook's existing block-list tests move over unchanged;
  - a game test on a local server whose address is in a test list sees the feature off and the note shown.
- **Prior art:** `CrosshairTest`, `PlacementTest`, `BlockListTest`, `AshLoadsGameTest`, and the first-run tests in ash-core.

## Out of Scope

- New HUD items beyond the two readouts. They're beta phase 3.
- Settings profiles and the panel's accent colour. They're beta phase 5.
- Serving the fair-play list from the backend, and servers switching features off themselves.
- Versions other than the four targets. Fabric on any version is beta phase 2, without ash's client.

## Further Notes

1. **Research before 26.3:** research 0016, on where each of ash's hooks sits in 26.3 and what changed since 1.21.11. It covers rendering, the HUD, skins and capes, entity rendering for hit colour, and clouds and culling, plus the exact Fabric Loader and Fabric API versions.
2. **Needs the product owner:**
   - a benchmark sitting for 26.3's FPS marks;
   - the end-of-phase test walk.
3. **Assumed, not asked:**
   - the size range, 50% to 200%;
   - the snap distance, 4 GUI pixels;
   - Shift to switch snapping off;
   - Hit colour moving to the Visual tab.

## Tracker

Filed as [#169](https://github.com/sisi-j/ash/issues/169), a sub-issue of the beta's tracker, [#168](https://github.com/sisi-j/ash/issues/168), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its tickets, #174 to #186, are its sub-issues, with blocking links set. This file is the source of truth, and the issue is the tracker entry.
