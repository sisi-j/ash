# Beta phase 5: Make it yours

*Designed 2026-10-28 in a grilling session with the product owner (see 0006, the beta). Every decision below was put to them and answered. The few written down as assumptions are marked.*

## Problem Statement

By beta phase 4, ash has twenty-five features and every one of them is customizable. But a player has exactly one setup:
- **No second setup.** A player who wants a busy HUD for one server and a bare one for another changes a dozen settings each time.
- **No sharing.** A friend who likes a setup can only copy it by looking at it.
- **The first open is cluttered or bare.** A new player opening ash's panel faces twenty-five tiles and has to decide everything at once, or gets a bare screen and has to discover everything.
- **The panel can't take a colour.** It's grayscale, which is a good default, but nobody can make it theirs.
- **Cosmetics stop at capes.** Worn models and emotes were designed in alpha phase 4 and never built.
- **Crashes depend on Discord.** When the game or launcher crashes, the product owner only hears about it if the player copies the report and pastes it in Discord.

## Solution

- **Settings profiles.** A settings profile is a named, complete setup: every feature's switch and options, and every HUD item's place and style.
  - **Switching:** in the panel, or automatically on chosen servers, going back on leaving.
  - **Syncing:** they follow the player's ash account, like their settings.
  - **Sharing:** a profile can be copied as a text code that a friend pastes in. No server stores it.
- **Starter profiles.** The first time a new player opens ash's panel, they pick Minimal, PvP or Everything. It's an ordinary profile, so anything can change later.
- **An accent colour** for the panel, picked with the colour picker. Grayscale stays the default.
- **Worn models and emotes,** as alpha phase 4 designed them, on all four version targets: free models in the Wardrobe, and an emote wheel on B.
- **Crash reports, only when asked.** After a crash, "Send report" sits beside "Copy report" and sends the same redacted report to ash. Nothing is sent without that click.

## User Stories

### Settings profiles

1. As a player, I want to save my whole setup as a named profile, so that I can come back to it.
2. As a player, I want to switch profiles from the panel in one click, so that changing setups is instant.
3. As a player, I want a profile to hold every feature's switch and options and every HUD item's place and style, so that switching changes everything that makes a setup.
4. As a player, I want changes I make while a profile is active to save into that profile, so that I never press "save".
5. As a player, I want to make a new profile as a copy of the current one, so that I start from what I have.
6. As a player, I want to rename and delete profiles, so that my list stays tidy.
7. As a player, I want a profile to switch on by itself when I join chosen servers, so that each server gets its own setup.
8. As a player, I want the profile I had before to come back when I leave that server, so that automatic switching never strands me.
9. As a player, I want a short note in game when a profile switches by itself, so that I know why my screen changed.
10. As a player, I want server rules for profiles to match subdomains, as the fair-play list does, so that `mc.hypixel.net` counts as `hypixel.net`.
11. As a player, I want my profiles to follow my ash account to another computer, so that my setups are everywhere I play.
12. As a player, I want profiles to leave my memory, Java and window settings alone, so that a profile never breaks this computer's setup.

### Sharing

13. As a player, I want to copy a profile as a code, so that I can paste it to a friend in Discord.
14. As a player, I want to paste a code to add the profile as a new one, so that my own profiles aren't overwritten.
15. As a player, I want a code with settings ash doesn't know or values out of range to still import, with the unknown parts dropped and the rest fixed up, so that a code from an older or newer ash still works.
16. As a player, I want a broken code refused with a plain reason, so that I know it was the code and not me.
17. As a player, I want nothing about me in a code, only the settings, so that sharing is safe.
18. As a player, I want sharing to work without any ash server, so that codes keep working forever.

### Starter profiles

19. As a new player, I want to pick Minimal, PvP or Everything the first time I open ash's panel, so that I start with a setup that suits me.
20. As a new player, I want each starter described in a line, with what it switches on, so that I choose knowingly.
21. As a player, I want the starter I picked to become an ordinary profile, so that I can change anything in it.
22. As a player, I want to make a new profile from any starter later, so that I can try another without losing mine.
23. As an alpha tester with my own settings already, I want them kept as a profile and no picker forced on me, so that the beta doesn't reset my setup.

### The panel's colour

24. As a player, I want to pick an accent colour for ash's panel, so that it feels like mine.
25. As a player, I want the accent on the panel's selected tab, switches, sliders and buttons, so that the colour shows where it matters.
26. As a player, I want new HUD items to be able to take the accent as their text colour, so that my HUD matches my panel.
27. As a player, I want to put the panel back to grayscale in one click, so that I can undo it.
28. As a player, I want my accent to follow my account, as my settings do, so that it's the same on every computer.

### Worn models and emotes

29. As a player, I want to wear free models, a hat or wings, chosen in the Wardrobe, so that I can look different from everyone else.
30. As a player, I want other ash players to see my models, on any of ash's versions, so that wearing one means something.
31. As a player, I want to put up to 8 emotes on a wheel and play one by holding B, so that emoting is quick.
32. As a player, I want an emote to show me in third person while it plays and to stop as soon as I move, attack or am hurt, so that it never gets in the way of a fight.
33. As a player, I want to see other ash players' emotes, so that emoting is social.
34. As a player, I want to hide others' models and emotes, as I can hide capes, so that I can keep my screen plain.
35. As a player, I want emotes switched off on servers where they're forbidden, by the fair-play list, so that they never get me in trouble.

### Crash reports

36. As a player whose game crashed, I want "Send report" beside "Copy report", so that the product owner hears about it without me going to Discord.
37. As a player, I want to see exactly what a report contains before I send it, so that I know what I'm sharing.
38. As a player, I want names, tokens and my file paths removed from a report, as Copy report does, so that sending it is safe.
39. As a player whose launcher crashed, I want to be offered the report the next time ash starts, so that a launcher crash can be reported too.
40. As a player, I want to be told a report was sent, or why it couldn't be, so that I know it arrived.
41. As a player, I want nothing sent unless I click, so that I'm in control of what leaves my computer.
42. As the product owner, I want sent reports listed on the admin page with their version, instance and error, so that I can see what crashes most.
43. As the product owner, I want reports deleted after 90 days, so that ash keeps no more than it needs.

## Implementation Decisions

### Settings profiles
- **A settings profile** is a new glossary term, qualified because "profile" alone already means an instance or an ash account. It's a named set of the client settings' feature values: switches, options, HUD item placement and style, and favourites.
  - **What it holds:** the client settings' feature values only. Machine-local overrides and the panel's own interface settings (size, animations, blur) are never in a profile.
  - **Where it lives:** profiles are stored beside the client settings in the game directory. The active profile's values are the client settings themselves, so nothing else changes how the game reads its settings.
- **Switching** writes the profile's values into the client settings in one go, through the same writer that changes one value in place. A change made while a profile is active is saved into it.
- **Automatic switching:**
  - **Matching:** a profile can list server patterns, matched as the fair-play list matches (beta phase 1).
  - **Joining:** on joining a matching server, ash switches to that profile and notes it in game.
  - **Leaving:** ash switches back to the profile that was active before.
  - **Clashes:** if two profiles match, the first in the player's order wins.
- **Syncing** (ADR-0022): each profile is one synced setting, latest change wins, as a server list is. The profile list and which profile is active are synced too. As with all synced settings, remote changes are applied only while the game is closed.
- **Share codes:**
  - **The format:** a code is `ash1:` followed by the profile's values and its name, compressed and base64url-encoded. A version prefix lets later formats coexist.
  - **What's not in it:** the player's name, account and anything machine-local.
  - **Importing:** a code is validated setting by setting. Unknown keys are dropped, out-of-range values are clamped, malformed colours fall back to the default, and the result is added as a new profile.
  - **Nothing online:** codes are made and read entirely in the client. No backend is involved.

### Starter profiles

| Starter | What's on | Description |
|---|---|---|
| **Minimal** | FPS readout, crosshair, toggle sprint | "Just the essentials." |
| **PvP** | Minimal, plus keystrokes with CPS, armour status, potion effects, hit indicator, hit colour, zoom and toggle sneak | "Everything for fighting." |
| **Everything** | every feature that loaded, each HUD item in its default spot | "Every feature on, to switch off what you don't want." |

- **The picker** shows the first time a player opens ash's panel when the client settings have never been changed from their defaults.
- **Alpha testers** already have changed settings. Those become a profile called "My settings", and no picker is shown.
- **Starters are declared in the client,** so a new feature joins whichever starters it belongs to by declaration, with no screen code.

### The panel's accent colour
- **One client setting:** an accent colour with the colour picker, default the current grayscale white, and a "Back to grayscale" button.
- **Where it shows:**
  - the selected tab;
  - ENABLED buttons;
  - switches and sliders;
  - the colour picker's selection;
  - focus outlines.
- **For HUD items:** "Use the accent for new HUD items" makes newly switched-on HUD items take it as their text colour.
- **Syncing:** it syncs as a feature setting. It's also part of a settings profile, so a profile can carry a colour.

### Worn models and emotes
- **As specified in alpha phase 4 (spec 0004)**, unchanged except that they're built on all four version targets. That covers:
  - the catalogue and entitlements, all free;
  - the Wardrobe;
  - profile files read through the edge (ADR-0023);
  - the emote relay subscribing by player, keeping no record;
  - the wheel on B with up to 8 emotes;
  - stopping on movement, attack or hurt;
  - the ash art format from Blockbench;
  - hiding others' cosmetics.
- **Research 0012** covered drawing on 1.21.11 and 1.8.9. Research 0020 extends it to 1.20.4 and 26.3.
- **Fair play:** emotes get a fair-play list entry wherever a big server forbids them, from research 0020.
- **Art:** placeholders are built first, for building and testing; the product owner supplies the real art.

### Crash reports
- **Where Send report appears:**
  - the launcher's crash screen, beside Copy report;
  - a launcher error's report panel.
- **What's sent:** the exact report Copy report makes, redacted the same way: no names, tokens or user paths. The player sees it before sending.
- **A launcher crash:** the launcher records a panic to a file in its data root. On the next start it offers the report, with Send and Dismiss.
- **The backend's endpoint:**
  - a new public endpoint takes a report;
  - it's rate-limited per IP and capped in size;
  - it stores the report with its ash version, instance version, loader and error kind;
  - it keeps no account and no IP address.
- **Viewing and expiry:** reports are listed on `/admin`, behind Cloudflare Access. They're deleted after 90 days by the existing daily cron.
- **The site:** the privacy statement gains a section on crash reports. That's an ash-site change, part of this phase.

## Testing Decisions

- **Profiles:**
  - switching, saving changes into the active profile, copying, renaming and deleting are unit-tested in the shared module on in-memory client settings;
  - the tests check that switching changes the client settings' feature values and nothing else, against golden files as ADR-0019 did;
  - automatic switching is tested with the fake game view's current server: join, leave, two matching profiles.
- **Share codes:**
  - a round trip of every setting;
  - an unknown key dropped, an out-of-range value clamped, a malformed colour reset;
  - a broken code refused;
  - a code proven to carry no machine-local or personal field.
- **Sync:** ash-core's synced-settings tests are extended to profiles, the active profile and the accent. Applying them only while the game is closed is already covered.
- **Starters:**
  - the picker's condition (untouched settings, or "My settings" for changed ones) is unit-tested;
  - each starter's set of features is asserted against the declarations;
  - a game test opens the panel on a fresh game directory and sees the picker.
- **Accent:** the panel's colour use is checked through the panel's model, and in a game-test screenshot.
- **Worn models and emotes:** as spec 0004's testing decisions, on all four targets. That means drawing with a screenshot, an emote playing and stopping, and a second ash client on a local server seeing both.
- **Crash reports:**
  - the backend's endpoint is tested in vitest: rate limit, size cap, no IP or account stored, and 90-day deletion;
  - the launcher's send, the next-start offer after a panic, and the failure message are ash-core tests and `ui-check` states.
- **Prior art:** the client-settings golden-file tests, the synced-settings tests, the cosmetics two-client test, the Copy report redaction tests, and the backend's news and wardrobe tests.

## Out of Scope

- Selling cosmetics, or anything else.
- Short share links stored on a server.
- Sending crash reports automatically.
- Themes beyond one accent colour: light mode, layouts and fonts.
- A friends list.

## Further Notes

1. **Research before worn models and emotes on the new targets: research 0020.** It covers how 1.20.4 and 26.3 draw worn models and pose the player for an emote, and the big servers' rules on emotes.
2. **Needs the product owner:**
   - real art for worn models and emotes;
   - the end-of-phase test walk.
3. **Assumed, not asked:**
   - the code format, `ash1:` and base64url;
   - what each starter switches on;
   - "My settings" for alpha testers' existing settings;
   - which parts of the panel take the accent;
   - keeping crash reports for 90 days;
   - keeping no IP address with a report.

## Tracker

Filed as [#173](https://github.com/sisi-j/ash/issues/173), a sub-issue of the beta's tracker, [#168](https://github.com/sisi-j/ash/issues/168), labelled `ready-for-agent`, per `docs/agents/issue-tracker.md`. Its tickets, #217 to #227, are its sub-issues, with blocking links set. This file is the source of truth, and the issue is the tracker entry.
