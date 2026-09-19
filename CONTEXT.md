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

**Backend**:
The separately deployed service holding ash accounts, entitlements, stats, synced settings and news.
_Avoid_: server (that means a Minecraft server here), API

### Identity

**Microsoft account**:
The account a player signs into. Establishes the right to play and carries no ash-specific data.
_Avoid_: MS profile, Xbox account, login

**Minecraft profile**:
Mojang's player identity — UUID, username and skin. Its UUID is what other players see in game.
_Avoid_: account, profile (unqualified), game account

**ash account**:
The app's record for one player, keyed by Minecraft profile UUID. Owns entitlements, stats and synced settings.
_Avoid_: user, profile, app profile

### Launcher

**Instance**:
A named, isolated game directory paired with exactly one version target and exactly one loader. Both are chosen when it is created, and neither changes afterwards.
_Avoid_: profile, installation, version, pack

**Version target**:
A specific Minecraft version ash supports as a first-class build. Not a range — each target is its own build and its own maintenance burden.
_Avoid_: version range, supported version, MC version

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

### Client

**Feature**:
One toggleable ash capability, such as toggle sprint or the custom crosshair. Presentation layer only.
_Avoid_: mod, module, tweak, hack

**Load report**:
The record the client writes saying which features loaded and which degraded. Written by the client, read by the launcher, never the other way.
_Avoid_: health check, status file, diagnostics

**Bundled mod**:
A third-party mod ash ships unmodified inside an instance — currently Fabric API on modern targets and Legacy Fabric API on 1.8.9. The two are not the same shape: Fabric API is one jar, while Legacy Fabric API is a metadata-only aggregator in front of 44 separately versioned module jars, none of which declares a dependency on any other. ash ships the aggregator plus exactly the modules its client calls into — three of the 44 today — so a feature that reaches for a fourth means pinning and mirroring it.
_Avoid_: dependency, vendored mod

**Client game test**:
The tier that launches a real vanilla client with ash in it and asserts what loaded — the only one that can catch a mixin which has stopped matching its target. Fabric provides the framework on modern targets; on 1.8.9 there is none, and ash's hand-rolled stand-in is a **smoke test**, named differently because it proves less: no ticking, no world, no screenshot. Both are Linux-only in CI.
_Avoid_: integration test, e2e test, game test (that is Minecraft's server-side framework, a different tier)

**Third-party mod**:
A mod the player supplies themselves, loaded only when they opt in.
_Avoid_: mod (unqualified), external mod, custom mod

### Cosmetics

**Cosmetic**:
A wearable item definition, such as a cape or an emote. Rendered client-side; the Minecraft server never sees it.
_Avoid_: skin (that means Mojang's player texture), item, unlockable

**Entitlement**:
The grant of one cosmetic to one ash account. The existence of the grant, not the wearing of it.
_Avoid_: ownership, unlock, purchase

**Equipped cosmetic**:
The cosmetic an account currently wears. Requires a matching entitlement.
_Avoid_: active cosmetic, selected skin, worn item

### Settings

**Synced settings**:
The per-account blob that follows a player between machines — feature settings, launcher preferences, instance definitions and server entries.
_Avoid_: config, preferences, sync blob

**Machine-local override**:
A field deliberately excluded from sync because it only makes sense on one machine: memory allocation, Java path, window resolution.
_Avoid_: local config, machine settings, device settings

**Server entry**:
A saved Minecraft server address available for quick-connect.
_Avoid_: favourite, bookmark, saved server

**News post**:
One entry in the launcher's news and changelog feed.
_Avoid_: announcement, article, update
