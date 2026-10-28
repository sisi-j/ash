# ash

ash is a Minecraft: Java Edition launcher paired with a matching game-side client build, aimed at competitive PvP. This glossary covers both halves of this monorepo; the backend lives in its own repo but shares this vocabulary.

## Language

### Components

**Launcher**:
The desktop application that manages accounts and instances, downloads game files, and starts the game.
_Avoid_: app, client, desktop client

**Client**:
The game-side mod layer ash injects into Minecraft. The other senses always get qualified — "the vanilla client", "an API client". It ships inside the installer rather than being downloaded, so the launcher and the client can never be version-skewed — which is what tells it apart from a bundled mod.
_Avoid_: mod, ash mod, Minecraft client

In front of players an instance is "ash client" when it runs the client - every modded instance ash makes does - and "vanilla" when it does not.

**Backend**:
The separately deployed service holding ash accounts, entitlements, synced settings, news posts and the emote relay. TypeScript on Cloudflare Workers, in its own public repository, `sisi-j/ash-backend` (ADR-0021). Nothing about playing depends on it: unreachable, it costs cosmetics, sync and news, never a launch.
_Avoid_: server (that means a Minecraft server here), API

### Identity

**Microsoft account**:
The account a player signs into. Establishes the right to play and carries no ash-specific data.
_Avoid_: MS profile, Xbox account, login

**Minecraft profile**:
Mojang's player identity — UUID, username and skin. Its UUID is what other players see in game.
_Avoid_: account, profile (unqualified), game account

**ash account**:
The app's record for one player, keyed by Minecraft profile UUID. Owns entitlements, equipped cosmetics and synced settings. Made at the player's first sign-in through the **join handshake**, and deleted entirely when they ask or after 12 months without a sign-in.
_Avoid_: user, profile, app profile

**Join handshake**:
How a player proves to the backend which Minecraft profile they are. The backend issues a one-time challenge, and the player's machine signs it with the key Mojang certifies for signed chat. The backend checks Mojang's certificate and the signature, offline, against Mojang's published keys. No Minecraft or Microsoft token reaches the backend. Until 23 October 2026 this was a join at Mojang's session server, which Mojang's firewall refuses from Cloudflare (ADR-0020, amended; research 0013). The launcher and the game each do their own.
_Avoid_: login (that is the Microsoft sign-in), auth (unqualified), token exchange

### Launcher

**Instance**:
A named, isolated game directory paired with exactly one version target and exactly one loader. Both are chosen when it is created, and neither changes afterwards.
_Avoid_: profile, installation, version, pack

**Version target**:
A specific Minecraft version ash supports as a first-class build, with ash's client in it: 1.8.9, 1.20.4, 1.21.11 and the **newest target**, which is the newest Minecraft release (26.3 at the start of the beta). Not a range — each target is its own build and its own maintenance burden. When Minecraft releases a new version, ash's port to it becomes the newest target and the one before is **retired**: its last client build keeps shipping unchanged, so its instances keep working, but it gets no new features (ADR-0028).
_Avoid_: version range, supported version, MC version

**Fabric-only instance**:
An instance with the Fabric loader on a version that isn't a version target. ash's client and bundled mods aren't in it; the player's mods always load in it. Any stable version Fabric supports can be one (beta phase 2).
_Avoid_: plain instance, vanilla Fabric, unsupported instance

**Modpack**:
A Modrinth project that bundles a Minecraft version, a loader, mods and override files, imported from a `.mrpack` file or from Modrinth. An instance made from one remembers it in `ash-modpack.json`, so the pack can be updated; the instance is still an instance, not a pack.
_Avoid_: pack (unqualified, for an instance), profile

**Loader**:
The mod-loading layer an instance runs under: vanilla, Fabric, or Legacy Fabric. Vanilla is a loader rather than the absence of one, so every instance has exactly one and nothing downstream needs a "no loader" case. Fabric and Legacy Fabric run the same Fabric Loader artifact; what differs is the intermediary mappings and the API around it.
_Avoid_: Fabric (ambiguous — Loader, API and Loom are three different things), modloader, Legacy Fabric Loader (there is no such artifact), no loader (vanilla is one)

**Version document**:
The JSON that says what a build is made of - its libraries, arguments, main class and client jar. Mojang publishes one per version target. A loader publishes a **loader profile**: a version document carrying only what the loader adds, which names the version target it inherits from and is merged onto it before anything is prepared or launched.
_Avoid_: profile (unqualified - it means an instance or an ash account), manifest (that is Mojang's index of every version), version JSON

**Mirror**:
ash's own copy of third-party artifacts whose upstream has a single point of failure, fetched only when that upstream cannot be reached. A second copy, never a second authority: a mirrored file is verified against the same recorded hash as the original, so it can be wrong but it cannot be trusted instead. See `docs/mirror.md`.
_Avoid_: cache (that is the depot), fallback repository, CDN

**Depot**:
The single content-addressed pool of downloaded jars, libraries and assets that every instance draws from.
_Avoid_: store (reserved for the cosmetics storefront), cache, library folder

**Game directory**:
The per-instance folder holding saves, config, resource packs, screenshots and mods. Never shared between instances.
_Avoid_: .minecraft, instance folder

**Session**:
One run of the game, from the launcher starting it to its exit, kept in the instance's own metadata. Play time is every session added together. A session the launcher did not see end, because it was closed while the game ran, is taken to have ended when the game last wrote its log.
_Avoid_: playthrough, run (ambiguous with a CI run)

### Client

**Feature**:
One ash capability that loads, or degrades, on its own, such as toggle sprint or the custom crosshair. Most have a switch the player can turn off; ash's settings screen has none, since nothing switches off the way to switch things. Presentation layer only.
_Avoid_: mod, module, tweak, hack

**Load report**:
The record the client writes saying which features loaded, which degraded, and which the player switched off, whether any of the player's own mods loaded, and whose copy of each bundled mod ran (ADR-0018) - `ash/load-report.json` in the game directory, replaced each time the client starts and again whenever the player changes a setting in game, so it says what the session ended with. Written by the client, read by the launcher before the next play and into ash's own log, never the other way.
_Avoid_: health check, status file, diagnostics

**Bundled mod**:
A third-party mod ash ships unmodified inside an instance — currently Fabric API on modern targets and Legacy Fabric API on 1.8.9. The two are not the same shape: Fabric API is one jar, while Legacy Fabric API is a metadata-only aggregator in front of 44 separately versioned module jars, none of which declares a dependency on any other. ash ships the aggregator plus exactly the modules its client calls into — four of the 44 today — so a feature that reaches for a fifth means pinning and mirroring it, as toggle sprint's key binding did.
_Avoid_: dependency, vendored mod

**Client game test**:
The tier that launches a real vanilla client with ash in it and asserts what loaded — the only one that can catch a mixin which has stopped matching its target. Fabric provides the framework on modern targets; on 1.8.9 there is none, and ash's hand-rolled stand-in is a **smoke test**, named differently because it proves less: no ticking, no world, no screenshot. Both are Linux-only in CI.
_Avoid_: integration test, e2e test, game test (that is Minecraft's server-side framework, a different tier)

**Client settings**:
The client's own configuration file, `config/ash.properties` in an instance's game directory. The client writes it on first run, appends settings an older file lacks, and changes a value in place - that value and not one other byte - when the player changes it in game; the launcher is its only other writer, for synced settings, and only while that instance's game is closed, changing the synced values and not one other byte (ADR-0022).
_Avoid_: config (unqualified), options (that is the game's own `options.txt`), synced settings (Phase 4, and a different thing)

**Settings screen**:
ash's in-game screen for its own features, opened with Right Shift by default and rebindable in Controls. Drawn by ash itself, the same on both version targets - a panel over the blurred game, with a SETTINGS strip down its left and a **tile** per feature under search and category tabs - never with the game's own widgets. One switch per feature, built by walking the declared client settings, so a feature declared there appears on it with no screen code of its own; a feature that did not load is shown as such and cannot be switched. A change takes effect at once and is saved as it is made, into the client settings - the screen is a second editor of that file, never a second writer.
_Avoid_: menu, mod menu, options (that is the game's own screen), config screen

**Tile**:
One feature on the settings screen: its name, icon, FPS mark, a gear for its options and an ENABLED/DISABLED button.
_Avoid_: card, module (Lunar's word), mod

**FPS mark**:
The sign on a tile of what a feature does to the frame rate, measured on and off: green triangle up, red triangle down, or a grey line within ±3%.
_Avoid_: performance badge, FPS impact (in UI text)

**HUD item**:
One thing a feature draws on the HUD - the FPS readout, keystrokes, armour status - declared once in a registry and placed, sized, styled, snapped and hidden the same way as every other (beta phase 1). A feature can have more than one.
_Avoid_: HUD mod, widget, element, readout (only the FPS and ping items are readouts)

**Game view**:
The read-only interface through which the client's shared code reads the game - keys held, position, armour, effects, chat open, the current server. Each version target implements it from its own game; tests fill in a fake one, so every in-game feature is tested without a game.
_Avoid_: game state, adapter, bridge

**Fair-play list**:
The one list, shipped in the client, of which features are off on which servers and why. Freelook's block list was its first entry. A feature off on a server does nothing there, says so, and comes back on leaving; the player's own switch is never changed.
_Avoid_: block list (that was freelook's alone), blacklist, server rules

**Feature option**:
One setting that shapes how a feature looks or behaves rather than whether it is on - the crosshair's shape, size, gap, thickness, colour and outline. Declared in the client settings beside the feature's switch, and edited on the feature's own page of the settings screen, reached from its card; a feature that did not load offers no page, and an option that does nothing for what the others are set to (a dot's gap) is not shown. "Reset to defaults" there puts the switch and every option back.
_Avoid_: option (unqualified, in docs - it is the game's own word), sub-setting, config value

**Hit indicator**:
The feature that draws a **mark** at the crosshair when the server confirms one of the player's hits landed - never on the click alone, and never with an amount of damage or anyone's health. Exact on 1.21.11, where the server names who caused the damage; a match on 1.8.9, where it does not, so a hurt counts if it is of an entity the player attacked in the last second.
_Avoid_: hit marker (in docs), hitmarker, damage indicator

**Third-party mod**:
A mod the player supplies themselves, loaded only when they opt in. It lives in the instance's `mods` folder, which is the player's alone: ash hands its own client and bundled mods to the loader by path, and puts something there only when the player installs it from the Mods page (ADR-0026). While third-party mods are off, the loader is pointed at an empty folder of ash's instead. The opt-in is per instance, off by default, and machine-local: it decides which files in this machine's folder load. A crash with them on names them and offers to play without them, once, without changing the setting.
_Avoid_: mod (unqualified), external mod, custom mod

### Cosmetics

**Cosmetic**:
A wearable item definition: a cape, a worn model or an emote. Rendered client-side and seen by other ash players; the Minecraft server never sees it. Free to every ash account in Phase 4, and a cape is never sold (ADR-0010).
_Avoid_: skin (that means Mojang's player texture), item, unlockable

**Entitlement**:
The grant of one cosmetic to one ash account. The existence of the grant, not the wearing of it.
_Avoid_: ownership, unlock, purchase

**Equipped cosmetic**:
The cosmetic an account currently wears, one per **slot**: a cape, one on the head and one on the back, plus up to 8 emotes on the **emote wheel**. Requires a matching entitlement. An equipped cape replaces the player's Mojang cape for ash players who see it. Public by UUID, and looked up without a record of who asked (ADR-0023).
_Avoid_: active cosmetic, selected skin, worn item

**Emote**:
A short animation of the player's model, played from the emote wheel (hold B by default) and relayed by the backend to the ash players who can see them. The player's own view goes to third person while it plays; moving, attacking or being hurt stops it. Visual only.
_Avoid_: dance, animation (unqualified), gesture

**Wardrobe**:
The launcher page where a player equips cosmetics, on a 3D preview of their own skin. The client's settings panel equips them too.
_Avoid_: store (reserved for a paid storefront, which does not exist), shop, locker, inventory

### Settings

**Settings profile**:
A named, complete set of the client settings' feature values - every switch, option, HUD item's place and style, and favourites - that the player switches between, automatically per server if they choose, synced and shareable as a **share code** (`ash1:` text that no server stores). Never holds machine-local overrides. **Starter profiles** - Minimal, PvP and Everything - are the ones offered the first time a new player opens the panel.
_Avoid_: profile (unqualified - it means an instance or an ash account), preset, loadout, config

**Synced settings**:
What follows an ash account between machines: feature settings (one set per account, not per instance), launcher preferences, instance definitions and each instance's server list. Each setting carries its own time and the latest change wins; a server list is one setting. Only the launcher syncs, and only while the game is closed (ADR-0022). On whenever the player is signed in, with a switch to turn it off.
_Avoid_: config, preferences, sync blob

**Launcher preferences**:
The launcher's own settings, as opposed to any one instance's: launch sounds, and what the launcher does when the game starts (keep open, minimise or close). Language joins them when there is a second one to choose. Kept by ash-core under the data root. Unlike a machine-local override, a preference means the same on any machine, so synced settings will carry it. The Settings page's default memory is therefore not one.
_Avoid_: launcher settings, app settings, config

**Machine-local override**:
A field deliberately excluded from sync because it only makes sense on one machine: memory allocation, Java path, window resolution, and whether an instance loads third-party mods. Also this machine's default memory for every instance, which an instance's own memory overrides.
_Avoid_: local config, machine settings, device settings

**Server entry**:
A saved Minecraft server address available for quick-connect. On the launcher's Play page, an instance's server entries are the servers it joined most recently first, from the client's own record in `ash/recent-servers.json` (addresses and times only, written by the client and only read by the launcher), then the rest of the game's own list from `servers.dat`, in the player's order. ash asks only these servers for their status, and Join only goes to one of them: never a server the player has neither listed nor joined.
_Avoid_: favourite, bookmark, saved server

**News post**:
One entry in the launcher's news and changelog feed, written and published on the backend's `/admin` page, which Cloudflare Access guards.
_Avoid_: announcement, article, update

### Releasing

**Alpha phase / beta phase**:
How the work is counted. Alpha phases 1-4 are specs 0001-0004, and the alpha release is 0005; beta phases 1-5 are specs 0007-0011, under the beta's overview, 0006. The beta is released once, as `0.2.0-beta.1`, when every beta phase is done; alpha patch releases carry fixes meanwhile.
_Avoid_: phase (unqualified, once the beta starts), milestone, sprint

**Test build**:
A build of ash with the beta switch on (ADR-0029): the beta phases' work is in it, and Settings says "test build" beside the version. Made with `npm run build:test-installer` for each beta phase's test walk, and never published. A **release build** has the switch off, and is the only kind a release publishes.
_Avoid_: beta build, dev build, nightly

**Crash report**:
The redacted report Copy report makes after a crash, which the player can also send to ash with "Send report" (beta phase 5). Sent only when they click; kept 90 days, with no account or IP address.
_Avoid_: telemetry, crash log, diagnostics

**Release**:
One version of ash, published to GitHub Releases on its own when a merge to `main` changes the version and its installer passes the installed-app test (ADR-0025). Alpha releases are numbered `0.1.0-alpha.N`, and are ordinary releases rather than GitHub pre-releases.
_Avoid_: build (that's any compile), deploy (that's the backend)

**Update**:
A newer release, offered by the launcher when it starts, with its section of `CHANGELOG.md` as what's new. Signed with ash's update key, and installed only when the player chooses, never while a game ash started is running.
_Avoid_: patch, upgrade, news post (that's the News page)
