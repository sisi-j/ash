# Beta phase 4: Visuals and social

*Designed 2026-10-28 in a grilling session with the product owner (see 0006, the beta). Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

What players see, and how they talk, is the game's and nobody else's:
- **Seeing.** Caves are dark unless gamma is pushed past what the options allow. The block outline is thin and black, and lost on dark blocks.
- **Clutter.** Fire covers half the screen while burning. The shield blocks the view. Hurt-cam shake throws off aim. Pumpkin and powder-snow overlays hide the world.
- **The world's look.** Night, rain and particles are the server's to decide, even when a player only wants to see clearly or record a clip.
- **Chat.** It has no times, repeats the same message over and over, can't be copied from, and forgets after 100 lines.
- **Nametags.** A player can't see their own in third person or tune how others' look, and nothing shows who else uses ash.
- **Discord.** It shows nothing, while Lunar players show what they're playing and where.

## Solution

Visual tweaks, all on the player's screen only:
- **Fullbright:** see in the dark.
- **Block outline:** its colour and opacity, its thickness, and an optional fill.
- **Fewer overlays:**
  - lower fire;
  - lower shield;
  - less or no hurt-cam shake;
  - no pumpkin or powder-snow overlay;
  - less portal and nausea warping.
  Blindness and darkness are never removed.
- **Time and weather changer:** a fixed time of day, and clear, rain or snow, drawn for you alone.
- **Hit particles:** more particles on hits, and crit particles on every hit.

Social:
- **Chat tweaks:**
  - timestamps;
  - repeated messages stacked as "(x3)";
  - copy a message with a click;
  - a chat history of up to 1,000 lines.
- **Nametags:**
  - your own in third person;
  - background colour and opacity;
  - text shadow;
  - an ash mark beside other ash players' names.
- **Discord status:** "Playing Minecraft 26.3 on hypixel.net with ash" on your Discord profile while you play. It's on by default, server included, and each part can be switched off.

## User Stories

### Seeing

1. As a player, I want fullbright, so that I can see in caves and at night without torches.
2. As a player, I want a key to switch fullbright, so that I can turn it off to see real lighting.
3. As a player, I want the block outline's colour and opacity, so that it stands out on any block.
4. As a player, I want the outline's thickness, so that it's visible from across a build.
5. As a player, I want an optional translucent fill on the block I'm looking at, so that it's obvious which one I'll break.

### Fewer overlays

6. As a player, I want the fire overlay lowered, so that I can see while burning.
7. As a player, I want the shield lowered while blocking, so that it doesn't cover the screen.
8. As a player, I want hurt-cam shake reduced or removed, so that taking damage doesn't throw off my aim.
9. As a player, I want the pumpkin overlay removed, so that wearing a carved pumpkin doesn't blind me.
10. As a player, I want the powder-snow overlay removed, so that I can see while stuck in it.
11. As a player, I want the portal and nausea warping reduced, so that they don't make me feel sick.
12. As a player, I want blindness and darkness left as they are, so that ash never gives me an unfair advantage.

### The world on my screen

13. As a player, I want to fix the time of day on my screen, so that I can build or record in daylight while the server has night.
14. As a player, I want to choose clear, rain or snow on my screen, so that I can see through a storm.
15. As a player, I want "follow the server" for time and weather, so that I can put it back without remembering what it was.
16. As a player, I want more particles on my hits, so that hits feel and look solid.
17. As a player, I want crit particles on every hit, so that I see every hit land.
18. As a player, I want all of this to change only my screen, so that nobody else is affected and no server rule is broken.

### Chat

19. As a player, I want a time beside each chat message, so that I know when it was said.
20. As a player, I want 12- or 24-hour timestamps, with or without seconds, so that they read the way I read time.
21. As a player, I want repeated messages stacked into one line with a count, so that spam doesn't push real messages away.
22. As a player, I want to copy a chat message by clicking it with a modifier key, so that I can paste a coordinate, a name or a link.
23. As a player, I want to be told a message was copied, so that I know it worked.
24. As a player, I want chat to remember up to 1,000 lines, so that I can scroll back further.
25. As a player, I want each chat tweak switchable on its own, so that I take only what I like.

### Nametags

26. As a player, I want to see my own nametag in third person, so that I see what others see.
27. As a player, I want nametags' background colour and opacity, so that names read clearly against the sky.
28. As a player, I want nametag text shadow switchable, so that names look crisp or soft.
29. As a player, I want a small ash mark beside other ash players' names, so that I know who else uses ash.
30. As a player, I want to choose not to show others that I use ash, so that I can stay unmarked.

### Discord status

31. As a player, I want my Discord profile to show that I'm playing Minecraft with ash, the version and for how long, so that my friends see what I'm doing.
32. As a player, I want it to show the server I'm on, so that friends can join me.
33. As a player, I want to switch Discord status off, so that nothing about my game shows on Discord.
34. As a player, I want to hide only the server, so that I share that I'm playing but not where.
35. As a player, I want the status cleared as soon as the game closes, so that Discord never shows me playing when I'm not.
36. As a player without Discord running, I want nothing to happen and no error, so that Discord status never gets in the way.
37. As a player in singleplayer, I want the status to say "in singleplayer", so that it's never wrong.

### Everything

38. As a player, I want the visual tweaks on the Visual tab and the chat tweaks on the Chat tab, so that I find them where I look.
39. As a player, I want FPS marks on each new feature's tile, so that I know what it costs.

## Implementation Decisions

### Visual features
All of these change only what this player sees. Nothing is sent to the server, and nothing another player sees changes.

| Feature | Tab | Options | How |
|---|---|---|---|
| **Fullbright** | Visual | a key binding to switch it, unbound by default | the game's brightness is overridden while it's on. The player's own gamma setting isn't changed or saved. |
| **Block outline** | Visual | colour with opacity, thickness 1–5, fill on or off, fill colour with opacity | the game's outline drawing, restyled |
| **Fewer overlays** | Visual | each of these, separately: fire height (0–100%), lower shield, hurt-cam shake (0–100%), pumpkin overlay, powder-snow overlay, portal and nausea warping (0–100%) | each overlay's drawing, scaled or skipped |
| **Time and weather** | Visual | time (follow the server, or a fixed time); weather (follow the server, clear, rain or snow) | the time and weather the world is drawn with, not the world's |
| **Hit particles** | Visual | particle amount (1–5×), crit particles on every hit | more of the particles the game already spawns on a hit, on this client only |

- **Fair play:**
  - blindness and darkness are never touched by fullbright or any other feature;
  - **fullbright is still dark under those effects:** while either is active, it gives way;
  - research 0019 checks the big servers' rules for each feature and adds fair-play list entries where needed.
- **Version targets:**
  - **1.8.9:** the powder-snow and shield-lowering options don't apply, since 1.8.9 has no powder snow and no shield, and aren't shown there;
  - **modern targets:** portal and nausea warping reuse the game's own distortion-effects option where it has one.

### Chat tweaks
- **Tab:** Chat. Each tweak is switched on its own.
- **Timestamps:** a time is prefixed to each line as it's received. The format is 12 or 24 hours, with seconds on or off. The time is local.
- **Stacking:** a message identical to the one just before it, including formatting, replaces it with a count, "(x3)". Any other message in between ends the stack.
- **Copying:** Ctrl+clicking a line copies its plain text and shows "Copied" for a moment. The game's own click events still work on a plain click.
- **History length:** 100 (the game's) up to 1,000 lines, in steps of 100.

### Nametags
- **Tab:** Visual.
- **Own nametag:** shown in third person.
- **Look:** background colour and opacity, and text shadow, for every nametag.
- **The ash mark:**
  - a small ash mark beside the name of players who use ash;
  - **Whose:** players with a published ash profile file (ADR-0023). Today a profile file is published only when a player equips something, so ash will publish one for every signed-in ash account, empty if nothing is equipped;
  - **Opting out:** a launcher setting, "Show others that I use ash", on by default. Switched off, the file is removed, and the player's cosmetics aren't shown to others either;
  - **What ash keeps:** no record of who looked whom up, as now (ADR-0023);
  - **The site:** the privacy statement is updated to say an account's use of ash is visible this way.

### Discord status
- **The launcher runs it,** not the game, through Discord's local IPC. It's tested through a Discord port faked in tests.
  - **Nothing in the game:** no native library is added to the game, and no Discord code runs on 1.8.9's old Java.
  - **Discord not running:** that's a quiet no-op.
- **Where the server comes from:** the client writes the current server to a new `ash/current-server.json` in the game directory when the player joins or leaves one. It holds the address only, or "singleplayer". The launcher reads it while that game runs. Like the load report and recent servers, it's written by the client and only read by the launcher.
- **What it shows:**
  - the details line is "Minecraft <version>";
  - the state line is "on <server>", "in singleplayer" or "in the menus";
  - an elapsed timer from the game's start;
  - ash's logo as the large image.
  It's cleared when the game exits.
- **Settings:**
  - two launcher preferences: "Discord status", on, and "Show server on Discord", on;
  - they sync like other launcher preferences, and are switched in the launcher's Settings.
- **Discord's application:** the status needs a Discord application and its ID, made by the product owner in Discord's Developer Portal with ash's logo uploaded. The ID ships in the launcher.

## Testing Decisions

- **Visual features:**
  - the option-to-effect mappings are unit-tested in the shared module against the fake game view: fire and shield offsets, shake scale, time and weather choice, particle multiplier;
  - the blindness and darkness rule is tested on its own;
  - each modern target's game test switches each feature and takes a screenshot;
  - 1.8.9's smoke test checks they load.
- **Chat:** timestamps, stacking (identical, differently formatted, interleaved), copying and history length are unit-tested on a chat model. A game test on each modern target sends repeated messages and checks the stack.
- **Nametags:** the mark decision, profile file present or absent, is unit-tested. A two-client local-server test, as in alpha phase 4's cosmetics, sees the mark.
- **Backend:**
  - publishing an empty profile file for every ash account, and removing it on opt-out, is tested in vitest;
  - the existing no-record tests stay green.
- **Discord status:**
  - ash-core tests drive a fake Discord port through start, join, leave and exit, and through each preference;
  - the current-server file is read as the client writes it;
  - Discord not running is a quiet no-op.
  - The launcher's preferences are `ui-check` states.
- **FPS marks** for every new feature on every target.
- **Prior art:** `HitColourTest`, the cosmetics two-client test, the load-report tests, and the launcher-preferences tests.

## Out of Scope

- Scoreboard and tab-list tweaks.
- Removing or weakening blindness or darkness.
- Changing anything other players see.
- A friends list, and joining a friend from Discord.
- Discord status for the launcher alone, while no game runs.

## Further Notes

1. **Research before this phase: research 0019.** It covers:
   - each target's hook for brightness, the outline, each overlay, world time and weather, hit particles, chat lines and nametags;
   - Discord's IPC protocol as the launcher will use it;
   - Hypixel's and other big servers' rules on each feature.
2. **Needs the product owner:**
   - the Discord application and its ID;
   - a benchmark sitting for FPS marks;
   - the end-of-phase test walk.
3. **Assumed, not asked:**
   - Ctrl+click to copy;
   - 1,000 lines as the most chat history;
   - publishing an empty profile file for every ash account, so the mark can show;
   - "Show others that I use ash" on by default;
   - Discord status showing nothing while no game runs.

## Tracker

Filed as [#172](https://github.com/sisi-j/ash/issues/172), a sub-issue of the beta's tracker, [#168](https://github.com/sisi-j/ash/issues/168), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its tickets, #208 to #216, are its sub-issues, with blocking links set. This file is the source of truth, and the issue is the tracker entry.
