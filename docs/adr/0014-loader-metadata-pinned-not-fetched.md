# Loader versions are pinned, and the profile is built locally

ash pins the Fabric Loader version it installs, builds the launch profile itself from immutable inputs, and verifies every artifact against a published hash. It does not fetch a profile from Fabric Meta at launch time.

## Context

The ordinary way to install Fabric without its installer is to ask `meta.fabricmc.net` for a profile JSON and hand that to the launcher. ash cannot do that and keep a promise it already made.

The depot's invariant is that every file is verified against a published hash before use, and that invariant is what makes the offline fallback safe — without it, "the network is down" becomes the way to get ash to run whatever is in its cache.

**Fabric Meta's profile JSON is generated per request.** Its `releaseTime` and `time` are the server's clock at the moment of asking — confirmed by two cache-busted fetches four seconds apart returning different bytes, and by Fabric Meta's own source (`profile.addProperty("releaseTime", currentTime)`). There is no published hash for it and there cannot be one.

Everything it points at, however, is hashed. `maven.fabricmc.net` and `maven.legacyfabric.net` publish `.md5`/`.sha1`/`.sha256`/`.sha512` sidecars for every jar including the loader and the intermediary, and Fabric additionally publishes a GPG signature on the loader jar. There is also an immutable document: `fabric-loader-<version>.json` is the loader's own launcher metadata, fixed per loader version, carrying hashes and sizes for ASM and Mixin inline.

Worth knowing what the field does: **Prism Launcher downloads Fabric Loader, intermediary, ASM and Mixin with no integrity check of any kind** — its `Library::getDownloads` attaches a validator only when the metadata carries a sha1, and Fabric's entries carry none. Fabric's own installer verifies exactly one file, and it is the Minecraft server jar.

## Decision

ash carries a pinned loader version per version target. It fetches the immutable per-version document, resolves the artifacts, verifies each against its published sidecar, and assembles the profile itself. Fabric Meta is not in the launch path.

## Consequences

- The depot invariant survives contact with a loader, which means a prepared modded instance still plans and launches with no network.
- ash does not automatically get new loader releases. Updating the pin is a deliberate act in a release. This is the point rather than the price: a launcher that silently changes the loader underneath a player turns every bug report into an archaeology exercise, and the loader is properly a property of a version target, which is already how ash thinks.
- ash reimplements the `inheritsFrom` merge, which is specified nowhere. It appears in no Mojang documentation, no Fabric documentation, and not in minecraft.wiki's `client.json` article. Every launcher reconstructs it from observation, and so does ash. It nests, and HMCL's implementation treats cycles as something to defend against rather than a theoretical concern.
- Legacy Fabric's artifacts are mirrored into ash's own build inputs, with hashes recorded. `repo.legacyfabric.net` is a single self-hosted server behind Cloudflare with no published mirror, and the project has a bus factor of one. What ash needs from it is small — a 147KB intermediary jar, about 5MB of LWJGL jars and natives, the API jar. Mirroring converts a dormancy risk into nothing for roughly half a day of work.
- ash ends up verifying more than any launcher we looked at. That is a nice place to be and it costs almost nothing, because the verification machinery already exists for the depot.
