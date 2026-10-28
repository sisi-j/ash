# Beta phase 3: HUD and PvP

*Designed 2026-10-28 in a grilling session with the product owner (see 0006, the beta). Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

The features players switch clients for are the ones ash still lacks:
- **What's happening with my hands.** There's no keystrokes display and no clicks per second, which PvP players use to watch and show their inputs.
- **What I'm wearing and under.** Armour durability and potion timers are buried in the inventory screen, or shown only as small icons. On 1.8.9 the game shows effects nowhere on the HUD.
- **Where I am.** Coordinates and facing mean opening F3's wall of text. There's no simple readout of where, which way, which biome, which server or what time.
- **Seeing and moving.** There's no zoom, no way to colour or tune hitboxes, and no toggle sneak beside toggle sprint.

Beta phase 1 made HUD items cheap to add and the game testable without a game. This phase is the payoff.

## Solution

New HUD items, each placed, styled, snapped and hidden like every other (beta phase 1):
- **Keystrokes:** W, A, S, D, space and both mouse buttons light up as they're pressed, and can show clicks per second on the mouse buttons.
- **CPS:** clicks per second for each mouse button, on its own.
- **Armour status:** worn armour and the held item, with durability, which turns red when nearly broken.
- **Potion effects:** each active effect with its level and time left. It flashes when about to run out.
- **Coordinates:** X, Y and Z, with facing direction and biome as options. Hidden on servers that reduce debug info.
- **Server:** the address of the server you're on.
- **Clock:** real time, or the in-game time of day.
- **Movement status:** shows when sprint or sneak is toggled on.

And three features:
- **Zoom:** hold C, scroll to change how far. Smooth, and slower to aim while zoomed.
- **Hitboxes:** the game's own hitbox view, with a colour per kind of entity and its own thickness, switched with a key.
- **Toggle sneak:** like toggle sprint.

## User Stories

### Keystrokes and CPS

1. As a PvP player, I want W, A, S and D shown on screen, lighting up as I press them, so that I can watch my movement.
2. As a PvP player, I want space and both mouse buttons in the keystrokes, each switchable, so that it shows what I care about.
3. As a player who has rebound movement keys, I want keystrokes to show the keys I actually use, so that it matches my hands.
4. As a PvP player, I want clicks per second shown on the mouse buttons in keystrokes, so that I see my clicking without a second item.
5. As a player, I want CPS as its own HUD item too, so that I can show it without keystrokes.
6. As a player, I want CPS to count clicks in the last second, rolling, so that the number moves with how I'm clicking now.
7. As a player, I want the colours of a pressed and an unpressed key in keystrokes, and how quickly a key fades back, so that it looks how I like.
8. As a player, I want keystrokes to take the same size, colours, background and shadow options as any HUD item, so that it fits with the rest.

### Armour and potions

9. As a player, I want my worn armour shown with durability, so that I know when a piece is about to break.
10. As a player, I want durability as a number, a percentage, a bar or not at all, so that it's as detailed as I want.
11. As a player, I want a nearly broken piece shown in red, so that I notice in the middle of a fight.
12. As a player, I want the item in my hand shown too, with its durability or count, so that I know how many blocks or arrows I have left.
13. As a player on a modern version, I want my off-hand item shown as an option, so that a shield or totem is in view.
14. As a player, I want armour status laid out across or down, so that it fits where I put it.
15. As a player, I want armour status to show only when I'm wearing armour, if I choose, so that it's gone when there's nothing to show.
16. As a player, I want each active potion effect shown with its icon, name, level and time left, so that I know what I'm under and for how long.
17. As a player, I want an effect to flash in its last ten seconds, so that I know to drink again.
18. As a player, I want effects listed with the soonest to run out first, so that what matters is on top.
19. As a player, I want the option to hide the game's own effect icons when ash's are on, so that I don't see them twice.
20. As a player on 1.8.9, I want potion effects on my HUD, which the game itself never shows, so that I have what modern players have.
21. As a player, I want potion effects to show only while one is active, if I choose, so that the item is gone otherwise.

### Where you are

22. As a player, I want my X, Y and Z on screen, so that I don't open F3 to find my way.
23. As a player, I want to choose whether coordinates show decimals, so that it's as precise as I want.
24. As a player, I want my facing shown as north, south, east or west and the axis I'm looking along, so that I can follow directions.
25. As a player, I want my biome shown as an option, so that I know where I am when exploring.
26. As a player, I want coordinates hidden on servers that reduce debug info, as F3's are, so that ash never shows what the server hides.
27. As a player, I want the server I'm on shown as an option, so that it's in my screenshots and recordings.
28. As a player, I want the server item to show nothing, or "Singleplayer", in singleplayer, as I choose, so that it's never wrong.
29. As a player, I want a clock showing real time in 12 or 24 hours, so that I know when to stop playing.
30. As a player, I want the clock to be able to show the in-game time of day instead, so that I know when night is coming.

### Movement

31. As a player, I want toggle sneak, so that I don't hold Shift while building.
32. As a player, I want toggle sneak to work like toggle sprint, with the same key behaviour, so that I learn it once.
33. As a player, I want toggle sneak to switch itself off when I fly, so that I don't sink while flying.
34. As a player, I want a small status item showing when sprint or sneak is toggled on, so that I know which state I'm in.

### Zoom

35. As a player, I want to hold C to zoom, so that I can see far away in a moment.
36. As a player, I want to rebind the zoom key in Controls, so that it fits my keys.
37. As a player, I want zoom to be able to toggle with a press instead of holding, so that I can zoom for a while hands-free.
38. As a player, I want to scroll while zoomed to zoom further in or out, so that I choose how far.
39. As a player, I want zooming in and out to be smooth, as an option, so that it isn't jarring.
40. As a player, I want my mouse slower while zoomed, so that I can aim at what I'm looking at.
41. As a player, I want the cinematic camera while zoomed as an option, so that zoomed views look smooth on recordings.
42. As a player, I want my hand hidden while zoomed, so that it doesn't block the view.
43. As a player, I want scrolling while zoomed not to change my hotbar slot, so that zooming doesn't change my item.
44. As a player, I want the zoom to start from a level I choose, so that my first zoom is the one I use most.

### Hitboxes

45. As a player, I want hitboxes switched on with ash's key, and kept on across restarts if I like, so that I don't press F3+B every time.
46. As a player, I want a different colour for players, mobs, items and projectiles, so that I can tell them apart at a glance.
47. As a player, I want to choose each kind of hitbox shown or hidden, so that only what I care about is drawn.
48. As a player, I want the hitbox line's thickness, so that it's visible but not heavy.
49. As a player, I want the eye-height line and the look direction each switchable, so that the box is as plain as I want.

### Everything

50. As a player, I want each new feature filed under the right tab, so that I find it where I look.
51. As a player, I want each new feature off by default unless a starter profile switches it on, so that ash stays clean the first time.
52. As a player, I want FPS marks on each new feature's tile, so that I know what it costs.

## Implementation Decisions

### The game view grows
- **This phase adds to the game view (beta phase 1):**
  - keys and mouse buttons held, by binding;
  - mouse-button presses as events, for CPS;
  - position, facing and biome;
  - whether the server reduces debug info;
  - worn armour, held and off-hand items with durability and count;
  - active effects with level and time left;
  - real and in-game time.
- **Each version target fills it from its own game.** 1.8.9 has no off-hand, so that option isn't offered there.

### HUD items
All are declared in the registry (beta phase 1) and take every HUD style option.

| Item | Tab | Options | Only when relevant |
|---|---|---|---|
| **Keystrokes** | HUD | space, mouse buttons and CPS on mouse buttons, each on or off. Pressed and unpressed colours, and fade time, 0–300 ms in steps of 50. | no |
| **CPS** | HUD | which buttons, and a left/right separator | no |
| **Armour status** | HUD | across or down; durability as number, percent, bar or none; held item; off-hand (modern only) | yes, when any armour is worn |
| **Potion effects** | HUD | hide the game's own effect icons (modern only) | yes, while any effect is active |
| **Coordinates** | HUD | decimals, facing, biome | no |
| **Server** | HUD | in singleplayer, show nothing or "Singleplayer" | yes, outside multiplayer, when set to show nothing |
| **Clock** | HUD | real time in 12 or 24 hours, or in-game time | no |
| **Movement status** | Movement | — | yes, while neither is toggled |

- **Keystrokes and the bindings.** Keystrokes shows the bound key's name, not a fixed letter, so a rebound key shows its own label.
- **CPS** counts presses in a rolling one-second window, from the game view's press events. Keystrokes reuses the same counter.
- **Red durability** means under 10% left.
- **Effects:**
  - they flash in their last ten seconds;
  - they're sorted by time left, with infinite ones last;
  - names and levels come from the game's own translations.
- **Facing** is a compass point plus the axis being looked along, for example "North (−Z)". The biome uses the game's own name.
- **Fair play for coordinates:** when the game view says the server reduces debug info, coordinates and facing are hidden, as F3 hides them. This is enforced by the item itself rather than the fair-play list, since the server tells the game directly.
- **The clock** uses the computer's local time.

### Zoom
- **The key:** a key binding, C by default, rebindable in Controls. Hold is the default, and toggle is an option.
- **How far:**
  - it starts at the player's starting level, 4× by default;
  - scrolling while zoomed changes it between 1.5× and 50×, and doesn't change the hotbar slot;
  - the level returns to the starting level on the next zoom.
- **How it moves:**
  - smooth zoom eases the field of view over 150 ms, and is on by default;
  - mouse sensitivity is divided by the zoom level;
  - the cinematic camera while zoomed is an option, off by default;
  - the hand is hidden while zoomed.
- **On each target:** it's done by changing the field of view the game renders with. Each target has its own hook (research 0018).
- **Tab:** PvP.

### Hitboxes
- **What it changes:** the game's own hitbox drawing (F3+B), restyled while the feature is on:
  - a colour with opacity for each of players, other mobs, items and projectiles;
  - each kind shown or hidden;
  - line thickness;
  - the eye-height line and look vector, each switchable.
- **Switching it:** a key binding, unbound by default, switches hitboxes. A "Keep on across restarts" option is off by default. F3+B still works and does the same thing.
- **Tab:** PvP.

### Toggle sneak
- **It works like toggle sprint** (alpha phase 3): pressing sneak toggles it. A long hold sneaks only while held, as toggle sprint's long press does.
- **When it switches off:** while flying, and when the player opens a screen that sneaking would affect.
- **Tab:** Movement.

### Fair play
- None of these is known to be forbidden on a big server. Research 0018 checks Hypixel's and other big servers' published rules for each, and anything forbidden gets a fair-play list entry.

## Testing Decisions

- **Every HUD item** is unit-tested in the shared module against a fake game view and `FakeHudSurface`. The tests cover:
  - what it shows for given facts: armour durability colours, effect order and the flash, facing words, decimals;
  - the reduced-debug-info hiding;
  - the 12/24-hour clock;
  - CPS's rolling window;
  - "only when relevant".
- **Zoom:** levels, the scroll range and its clamp, sensitivity division and the easing are unit-tested on a zoom model. Each modern target's game test zooms and checks the field of view. 1.8.9's smoke test checks it loads.
- **Hitboxes:** the per-kind colour choice is unit-tested. A game test on each modern target switches it and takes a screenshot.
- **Toggle sneak:** its key behaviour reuses toggle sprint's tests, and flying switches it off.
- **Real games:** each modern target's game test places every new HUD item with a scripted world state (armour on, an effect applied, a set position) and checks it draws, with a screenshot.
- **FPS marks** for every new feature on every target, in a benchmark sitting.
- **Prior art:** `CrosshairTest`, `FpsReadoutTest`, `PingReadoutTest`, the toggle sprint tests, and `AshLoadsGameTest`.

## Out of Scope

- Combat stats: combo counter, reach display, and the opponent's health.
- 1.7 animations and item-in-hand tweaks.
- Scoreboard and tab-list tweaks.
- A minimap, waypoints, or anything showing entities through walls.
- Showing coordinates on servers that reduce debug info.

## Further Notes

1. **Research before zoom and hitboxes: research 0018.** It covers:
   - each target's hook for the field of view and the hitbox renderer;
   - what each target gives for effects, durability and biome names;
   - Hypixel's and other big servers' published rules for each new feature.
2. **Needs the product owner:**
   - a benchmark sitting for the new features' FPS marks;
   - the end-of-phase test walk.
3. **Assumed, not asked:**
   - zoom's 1.5× to 50× range;
   - 4× as the default starting level;
   - smooth zoom on by default;
   - red at under 10% durability;
   - the ten-second flash;
   - the movement status item.

## Tracker

Filed as [#171](https://github.com/sisi-j/ash/issues/171), a sub-issue of the beta's tracker, [#168](https://github.com/sisi-j/ash/issues/168), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its tickets, #198 to #207, are its sub-issues, with blocking links set. This file is the source of truth, and the issue is the tracker entry.
