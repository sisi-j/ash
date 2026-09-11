# ash

ash is a Minecraft: Java Edition launcher paired with a matching game-side client build, aimed at competitive PvP. This glossary covers both halves of this monorepo; the backend lives in its own repo but shares this vocabulary.

## Language

### Components

**Launcher**:
The desktop application that manages accounts and instances, downloads game files, and starts the game.
_Avoid_: app, client, desktop client

**Client**:
The game-side mod layer ash injects into Minecraft. The other senses always get qualified — "the vanilla client", "an API client".
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
A named, isolated game directory paired with exactly one version target and one loader.
_Avoid_: profile, installation, version, pack

**Version target**:
A specific Minecraft version ash supports as a first-class build. Not a range — each target is its own build and its own maintenance burden.
_Avoid_: version range, supported version, MC version

**Loader**:
The mod-loading layer that bootstraps the client into a version target: Fabric Loader on modern targets, Legacy Fabric Loader on 1.8.9.
_Avoid_: Fabric (ambiguous — Loader, API and Loom are three different things), modloader

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

**Bundled mod**:
A third-party mod ash ships unmodified inside an instance — currently Sodium and Lithium.
_Avoid_: dependency, vendored mod

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
