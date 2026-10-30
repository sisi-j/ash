# Research 0017: modpacks, Fabric on any version, imports and updates

30 October 2026, for #187 (spec 0008). It settles the facts beta phase 2 depends on: the `.mrpack` format, how ash verifies Fabric on a version it doesn't pin, what Fabric Loader does with two copies of Fabric API, where the Modrinth app, Prism and MultiMC keep an instance's version and loader, Modrinth's hash-based update endpoints, and what 1.8.9 packs declare.

Everything was read from first-party documentation and source at a named commit or tag, or asked of the live service. Twenty-two real packs were downloaded from Modrinth, checked against Modrinth's SHA-512, and their `modrinth.index.json` read. Nothing was launched, and no Microsoft or Minecraft account was used.

Sources are tagged in brackets and listed at the end.

## Summary

- **The `.mrpack` format is as the spec says, with three corrections.**
  - Modrinth's docs list four download domains, but Modrinth's own code allows three: `gitlab.com` isn't in it [mrpack-doc] [labrinth-env].
  - Modrinth's own path rule is stricter than its docs. It also refuses backslashes and Windows device names such as `CON` and `NUL` [path-util].
  - Modrinth's API can mislabel a pack version. One that a Fabric search returns is Quilt, and one listed for 1.8.9 is for 1.21.8 (section 1.8). **Only the pack's own index decides.**
- **Fabric's Maven publishes `.md5`, `.sha1`, `.sha256` and `.sha512` for every file checked, and a signature for the loader jar but not the intermediary** [maven]. Fabric Meta's profile has hashes only for ASM and Mixin. The intermediary and the loader come bare [meta] [meta-src].
- **Loaders before 0.15.0 have no hashes in their own document,** and ash's parser can't read one [maven] [ash]. Five of fifteen popular Fabric packs sampled pin one (section 2.3).
- **The way to verify any version:** read the sidecars once, when the instance is made, and record the hashes in the instance. After that the instance is verified against its own record, as a target is against `loader.rs`, and ADR-0024 applies. That changes ADR-0014, so it wants an ADR (section 2.5).
- **Two copies of Fabric API is not a crash.** Fabric Loader 0.20.1 keeps the newest copy that satisfies every mod and drops the other without a word. The code is byte-for-byte research 0005's [loader-src]. So leaving a pack's Fabric API out is safe only when it isn't newer than ash's. On 1.8.9 a pack's Legacy Fabric API must stay, because it carries about 90 modules ash doesn't ship (section 3).
- **The Modrinth app** keeps everything under `%APPDATA%\ModrinthApp`. Version and loader live in its SQLite database, `app.db`. Since app 0.15.0 (26 June 2026) they're in `instance_content_sets`, before that in `profiles`, and before 0.8 in each folder's `profile.json` [app-dirs] [app-schema] [app-legacy]. Since 0.21.0 an instance's mods can be hard links into the app's store (section 4).
- **Prism** keeps instances in `%APPDATA%\PrismLauncher\instances` unless it's portable or moved. **MultiMC** keeps them beside its own `.exe`. Both use `instance.cfg` for the name and `mmc-pack.json` components for the version and loader [prism] [multimc].
- **Updates:** `POST /v2/version_files/update` takes hashes, an algorithm, loaders, game versions and optional version types. It answers with a map from hash to the newest matching version. "Newest" is by publish date, betas included unless asked otherwise [labrinth-routes] [api]. For packs, the hash route needs `loaders: ["mrpack"]`, not `["fabric"]` (section 6.5).
- **1.8.9 packs declare `fabric-loader`.** All six 1.8.9 Fabric packs on Modrinth do, with `minecraft: "1.8.9"`. Modrinth can't accept anything else, and upstream Fabric has no 1.8.9 at all [packs] [labrinth-pack] [meta]. So the 1.8.9 export refusal in the spec has a way round it (open question 4).

## 1. The `.mrpack` format

### 1.1 The file

- A ZIP with the extension `.mrpack`, MIME type `application/x-modrinth-modpack+zip` [mrpack-doc].
- `modrinth.index.json` at the root, in UTF-8 [mrpack-doc].
- Optional `overrides/`, `client-overrides/` and `server-overrides/` folders [mrpack-doc].
- It's "the only format of modpack that can be uploaded to Modrinth" [mrpack-doc].

### 1.2 `modrinth.index.json`

| Field | Required | What Modrinth says |
| --- | --- | --- |
| `formatVersion` | yes | A number. "The current value at the time of writing is 1." |
| `game` | yes | "The only available type is `minecraft`." Labrinth refuses anything else on upload [labrinth-validate]. |
| `versionId` | yes | "A unique identifier for this specific version of the modpack." 1 to 512 characters on upload [labrinth-pack]. |
| `name` | yes | Human-readable. 1 to 512 characters [labrinth-pack]. |
| `summary` | no | A short description. At most 2,048 characters [labrinth-pack]. |
| `files` | yes | The files to download. See below. Labrinth refuses a pack with no files and no `overrides/` entries [labrinth-validate]. |
| `dependencies` | yes | The game and loader, section 1.3. |

All from [mrpack-doc] unless tagged otherwise.

Each entry of `files`:

| Field | Required | What Modrinth says |
| --- | --- | --- |
| `path` | yes | Where it goes, "relative to the Minecraft instance directory", e.g. `mods/MyMod.jar`. Section 1.6. |
| `hashes` | yes | "This MUST contain the SHA1 hash and the SHA512 hash. Other hashes are optional, but will usually be ignored." Labrinth refuses a file without either [labrinth-validate]. |
| `env` | no | `{"client": …, "server": …}`, each `required`, `optional` or `unsupported`. "server refers to the dedicated server." Labrinth's type also accepts `unknown` [labrinth-pack] [labrinth-sides]. |
| `downloads` | yes | "An array containing HTTPS URLs where this file may be downloaded." No unencoded spaces or other characters RFC 3986 forbids. |
| `fileSize` | yes | Bytes, "mostly provided as a utility for launchers to allow use of progress bars". A `u32` in labrinth, so at most 4 GiB [labrinth-pack]. |

- **Optional files:** Modrinth recommends "showing a dialog to the user that allows them to select which optional files they would like to install" [mrpack-doc]. That's what spec 0008 does.
- **Unsupported on the client:** don't install it. "If something is marked server only, it should not be installed on the client" [mrpack-doc].

### 1.3 `dependencies`

A map from an id to a version string. The ids Modrinth documents are `minecraft`, `forge`, `neoforge`, `fabric-loader` and `quilt-loader`. "We may add other supported dependency IDs at any time. Please ensure your application handles this" [mrpack-doc].

- **Labrinth's set is closed.** Its `PackDependency` is an enum of exactly those five, so a pack with any other key can't be uploaded today [labrinth-pack]. The Modrinth app reads the same five and also accepts `neo-forge` for NeoForge [app-mrpack].
- **What ash does with each:**
  - `minecraft` gives the instance's version.
  - `fabric-loader` gives the loader version.
  - `forge`, `neoforge` (or `neo-forge`) and `quilt-loader` are refused, naming the loader.
  - **An unknown key** should be refused the same way, naming it.
  - **No loader key at all** is a vanilla pack. Spec 0008 doesn't say what happens to one (open question 6).
- **On a version target, the pack's loader version doesn't apply.** The instance runs ash's pinned loader, as every target instance does. Real 1.8.9 packs name Fabric Loader 0.16.12 to 0.18.4, and ash's 1.8.9 pin is 0.19.3 [packs] [ash].

### 1.4 Overrides

- `overrides/` is "copied to the root of the Minecraft Instance directory upon installation" [mrpack-doc].
- `client-overrides/` and `server-overrides/` are layers applied after `overrides/`, "overwriting its contents". The client applies `client-overrides/` and skips `server-overrides/` [mrpack-doc].
- **The docs don't say whether index files or overrides win** when both name one path. The Modrinth app downloads the index's files first and copies the overrides after them [app-mrpack]. So overrides win there, and ash should do the same.
- **Other top-level entries mean nothing.** Two real 1.8.9 packs carry a Prism `mmc-pack.json` and `patches/` at the root. The app copies only `overrides/` and `client-overrides/` [packs] [app-mrpack]. ash should ignore the rest, but see section 5.5.

### 1.5 Allowed download domains

| Source | Domains |
| --- | --- |
| Modrinth's docs [mrpack-doc] | `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com`, `gitlab.com` |
| Labrinth's default, in code [labrinth-env] | `cdn.modrinth.com`, `github.com`, `raw.githubusercontent.com` |
| Labrinth's `.env.local` [labrinth-env] | the same three |

- **How labrinth checks:** each URL must parse, must equal its own normalised form, and its host must be exactly one of the list [labrinth-pack]. A subdomain doesn't count.
- **Production's list can't be confirmed.** It's an environment variable, `WHITELISTED_MODPACK_DOMAINS`. Every pack sampled used only `cdn.modrinth.com` [packs].
- **Docs: "Other platforms implementing the specification SHOULD have a defined set of whitelisted domains"** [mrpack-doc]. Spec 0008's four is the documented set. Keeping `gitlab.com` is harmless.
- **Check the URL in the index, not where it redirects.** The docs say redirects "MUST be followed", at least three [mrpack-doc]. A `github.com` release download answers 302 to `release-assets.githubusercontent.com` [api]. A check on the final host would refuse every GitHub file. ash's HTTP client is `reqwest`, which follows redirects by default [ash].
- **The Modrinth app doesn't check domains when it installs** a pack file [app-mrpack]. ash checking them is stricter than the app.

### 1.6 Path safety

Modrinth's docs: "make sure this field doesn't exit the Minecraft instance directory … make sure it doesn't contain `..` or start with a drive name (i.e., `[A-Z]:/`, `[A-Z]:\`, `/`, and `\`)." Uploads are checked for this [mrpack-doc].

Modrinth's own code goes further. Labrinth and the app parse every `path` as `SafeRelativeUtf8UnixPathBuf` [labrinth-pack] [app-mrpack]. It refuses [path-util]:
- anything that isn't a Unix-style relative path;
- an empty path;
- any component that's `/`, `..` or a prefix;
- any backslash, anywhere;
- Windows device names, in any case and with any extension: `CON`, `PRN`, `AUX`, `NUL`, `COM1` to `COM9`, `LPT1` to `LPT9`, the superscript forms, `CONIN$`, `CONOUT$`.

The app checks every override entry the same way, after removing the `overrides/` or `client-overrides/` prefix [app-mrpack]. It also refuses a pack where two entries land on one mod, such as `mods/a.jar` and `mods/a.jar.disabled` [app-mrpack].

**For ash** (#189), any of these refuses the whole pack:
1. Modrinth's rule, above, for every `files[].path` and every override entry.
2. Two more, which are mine, not Modrinth's:
   - a `:` anywhere, which on NTFS names an alternate data stream;
   - a component ending in `.` or a space, which Windows strips, so two names reach one file.
3. After joining, the result must still be inside the game directory.
4. Two entries that reach one file, `.disabled` included, compared without case as Windows does.

Zip entries are written as plain files. A symlink entry is never made a link.

### 1.7 The Modrinth app, as prior art

From `install_mrpack.rs` [app-mrpack]:
- It skips files whose `env.client` is `unsupported`.
- It installs `optional` files without asking: "TODO: Future update: prompt user for optional files in a modpack".
- It downloads each file with its SHA-512 and `fileSize` as the expected hash and size.
- It reports progress per file and per byte.

### 1.8 What real packs look like

Twenty-two packs were downloaded and their indexes read [packs]:
- **Every one has `formatVersion` 1 and `game` `minecraft`.** Every download is on `cdn.modrinth.com`.
- **The API's labels can be wrong.** The index is what decides.
  - `sodiumplus` 1.4.3 came back from `GET /v2/project/sodiumplus/version?loaders=["fabric"]&game_versions=["1.19.2"]`. Its own `loaders` say `quilt`, and its index declares `quilt-loader` [api] [packs].
  - `recorders-modpack` 4.8 lists 95 game versions on Modrinth, from 1.0 to 1.21.8, so a 1.8.9 search finds it. Its index says `minecraft: "1.21.8"` [api] [packs].
- **Overrides can hold things that aren't mods.** One pack has packwiz `.pw.toml` files in `overrides/mods/` [packs]. They're harmless, because the loader reads only `.jar` files (research 0005).
- **Packs are big.** Four of the popular packs found were 66 to 239 MB as `.mrpack` files, before their mods are downloaded [api]. That's why spec 0008 wants resume.

## 2. Fabric on any version: which hashes, and how ash verifies them

### 2.1 What Fabric's Maven publishes

Checked on 30 October 2026 [maven]:

| File | `.md5` | `.sha1` | `.sha256` | `.sha512` | `.asc` |
| --- | --- | --- | --- | --- | --- |
| `fabric-loader-0.20.1.jar` | yes | yes | yes | yes | yes |
| `fabric-loader-0.20.1.json` (the loader's document) | yes | yes | yes | yes | yes |
| `intermediary-1.20.1.jar` | yes | yes | yes | yes | **no** |
| `intermediary-1.14.jar` | yes | yes | yes | yes | **no** |
| ASM 9.5, Mixin 0.12.5, tiny-mappings-parser (0.14.21's libraries) | | yes | | | |
| `fabric-api-0.141.6+1.21.11.jar`, `sponge-mixin-0.17.5+mixin.0.8.7.jar` | | | | | yes |

A blank cell wasn't checked.

- **Every sidecar checked agreed with a local hash** of the downloaded bytes. That covers all four for `intermediary-1.20.1.jar`, the document's `.sha1` (`d1fefa65…`), and the loader jar's (`7c7f9831…`, ash's pin) [maven].
- **A sidecar is a bare hex string** and comes from the same host as the file, `maven.fabricmc.net`, behind Cloudflare [maven].
- **The signatures are by "Fabric CI <ci@fabricmc.net>",** RSA 4096, fingerprint `9E2B 2198 D20D C6E4 B02C 7031 11B8 91CF E51C 003E`. The same key signs loaders 0.14.21, 0.16.14 and 0.20.1. 0.20.1's signature verifies [sig].
  - The key is on `keyserver.ubuntu.com`, not on `keys.openpgp.org`. I found no first-party page of Fabric's that publishes it.
- ADR-0014 already says the sidecars exist and that the loader jar is signed. This confirms it and adds that the intermediary isn't signed.

### 2.2 What Fabric Meta's profile carries

`GET https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.20.1/profile/json` [meta]:
- names ASM 9.10.1's five jars and `sponge-mixin` 0.17.5, each with `md5`, `sha1`, `sha256`, `sha512` and `size`;
- then `net.fabricmc:intermediary:1.20.1` and `net.fabricmc:fabric-loader:0.20.1` as `{name, url}` only, with **no hash and no size**;
- and `releaseTime` and `time` are the moment of asking, so the document itself can't have a hash (ADR-0014).

Fabric Meta's source shows why [meta-src]:
- `buildProfileJson` copies the loader document's own `libraries.common` and `libraries.client`, hashes and all.
- It adds the intermediary and the loader with `formatLibrary`, which writes only `name` and `url`.

The same holds for 1.14, 26.1 and 26.2 [meta]. On 26.x the profile has no intermediary at all (section 2.4).

So **Fabric Meta has no hash for either of the two jars that matter most.** What ash can verify them against is their Maven `.sha1` (or `.sha256`, `.sha512`) sidecar, and for the loader jar its signature.

### 2.3 The loader's own document

ash already takes this document directly for its targets, not Fabric Meta's profile (ADR-0014). It has two shapes [maven]:

| Loader | Document `version` | Libraries carry |
| --- | --- | --- |
| 0.15.0 and later (checked 0.15.0, 0.15.1, 0.15.11, 0.16.0, 0.16.14, 0.17.0, 0.18.0, 0.20.1) | 2, with `min_java_version: 8` | `md5`, `sha1`, `sha256`, `sha512`, `size` |
| 0.14.25 and earlier (checked 0.13.3, 0.14.21 to 0.14.25) | 1 | `name` and `url` only |

- **A version 1 document can't be parsed by ash today.** `loader.rs`'s `DocumentLibrary` requires `sha1` and `size` [ash].
- **Its libraries are different, too.** 0.14.21's are `tiny-mappings-parser` 0.3.0, `sponge-mixin` 0.12.5, `tiny-remapper` 0.8.2, `access-widener` 2.1.0 and ASM 9.5, all on Fabric's Maven, all with `.sha1` sidecars [maven].
- **Real packs pin these old loaders.** Sixteen popular packs were sampled on 1.16.5 to 1.21.1. Fifteen are Fabric, and five of those pin a loader before 0.15.0 [packs]:
  - Fabulously Optimized on 1.16.5 (0.13.3), 1.19.2 (0.14.19) and 1.20.1 (0.14.23);
  - `sodiumplus` on 1.18.2 (0.13.3);
  - `fps` on 1.19.2 (0.14.9).
- **The newest loader runs on Java 8.** 0.20.1's document says `min_java_version: 8` [maven]. So "the newest stable loader" works from 1.14, which is Java 8, to 26.3.
- **Fabric Meta marks one loader stable today, 0.20.1.** It lists all 255 loader versions for every game version, not just the ones that work with it [meta]. "Newest stable" is the first with `stable: true`.
- **The game versions:** Fabric Meta's list has 48 stable releases, and the oldest is 1.14 [meta]. That's spec 0008's range exactly.

### 2.4 Which versions have an intermediary

- **Fabric Meta adds an intermediary only when the game is obfuscated** (`env.game().version().obfuscated()`) [meta-src].
- **Obfuscated means** released before 1 October 2025, or with a `client_mappings` or `server_mappings` download in Mojang's version JSON (`McObfuscationChecker`) [meta-src]. 26.1, 26.2 and 26.3 have neither, so they have none. That's what research 0016 found for 26.3.
- **Fabric Meta reports intermediary `0.0.0` for 26.1** [meta]. The Maven redirects `intermediary/26.3/intermediary-26.3.jar` to a 572-byte `intermediary-0.0.0.jar` [maven].
- **Prism's meta names `net.fabricmc:intermediary:26.3` all the same** [prism-meta]. So an imported Prism 26.x instance carries an intermediary component that ash should ignore, deciding by the rule above.

### 2.5 How ash should verify a Fabric-only instance (#188)

**Pin at creation, in the instance.** When ash makes a Fabric-only instance, or imports one, it reads once:
- the loader document `fabric-loader-<L>.json` and its `.sha1`;
- the loader jar's `.sha1`;
- for an obfuscated version, `intermediary-<V>.jar`'s `.sha1`;
- for a version 1 document, each of its libraries' `.sha1`. A version 2 document already carries `sha1` and `size` for them.

It checks each download against its sidecar, and records the `sha1` and `size` of each in the instance, next to the version and loader version it already records.

From then on:
- **Preparing is verified against the instance's own record,** the way a target is verified against `loader.rs`. It needs no sidecar request and no Fabric Meta, so an instance made once still prepares offline. That keeps ADR-0014's point: Fabric Meta isn't in the launch path.
- **ADR-0024 applies unchanged.** A file verified once stands until its size or modification time changes.

Notes for building it:
- **Size.** A sidecar has no size, and the depot's `Artifact` and `fetch_metadata` check one [ash]. Take it from the bytes that matched the hash. The hash binds the content, so nothing is lost.
- **SHA-1** is what the depot keys on. `.sha512` is published too, if ash wants a stronger check on first download [maven].
- **Fabric Meta is only for choosing:** the stable game versions (`/v2/versions/game`) and the newest stable loader (`/v2/versions/loader`) [meta].
- **This needs an ADR.** ADR-0014 says ash "carries a pinned loader version per version target" and verifies against hashes "recorded here rather than fetched". A Fabric-only instance's pin is per instance and read from the Maven when the instance is made.

**What a sidecar proves.** It comes from the same host as the jar, so it proves the bytes arrived as the Maven holds them. It doesn't prove the Maven holds the right bytes. That's the same trust ash's targets already have, since their pins were read from these sidecars at release time (`loader.rs`). It's the same as for every Modrinth mod, whose SHA-512 comes from Modrinth's API (ADR-0026). It's more than Prism, which checks none of these files (ADR-0014). Two ways to go further, neither needed now:
- **Ship a table of intermediary hashes.** There are 43 obfuscated stable versions, and each intermediary is fixed once published (`intermediary-1.20.1.jar` is dated June 2023 [maven]). A release-time table takes the Maven out of the trust path for them. Loader versions newer than the table would still use sidecars.
- **Check the loader jar's signature** against Fabric CI's fingerprint. It covers only the loader, since the intermediary isn't signed, and it adds an OpenPGP dependency.

## 3. Two copies of Fabric API

### 3.1 What Fabric Loader does

Research 0005 read 0.19.3 and 0.19.5 and ran the cases. ash now also pins 0.20.1 for 26.3, and Fabric-only instances will run 0.20.1 or a pack's own loader.

- **0.20.1 is the same code.** `ModPrioSorter`, `ModSolver`, `ModResolver`, `ModDiscoverer` and `ResultAnalyzer` are byte-identical at 0.19.5 and 0.20.1 [loader-src]. So 0005's findings hold:
  - **Not a crash.** A jar in `mods` and a jar through `-Dfabric.addMods` are both root mods. For an id with several roots, the solver requires "at least one" and "at most one" (`ROOT_FORCELOAD`, `UNIQUE_ID`), and prefers the higher version. It logs nothing about the copy it drops [loader-src].
  - **Which wins:** the newest copy that satisfies every mod's constraints, wherever it came from (`testDuplicateRootModsOfDiffVers` loads 2.0.0 over 1.0.0). A mod that `breaks` the newer one makes the older one load instead (`testDuplicateRootModsOfDiffVersLowestCompat`) [loader-src].
  - **When it fails:** only when no copy satisfies everything ("Some of your mods are incompatible with the game or each other!"), or when a mod shares an id with `minecraft`, `java` or `fabricloader` [loader-src].
- **Build metadata doesn't count.** `SemanticVersionImpl.compareTo` compares the version and any pre-release, never the `+…` part. So `0.141.6+1.21.11` and `0.141.6+1.21.10` are the same version [loader-src].
- **Nested modules don't mix between copies.** A nested mod can load only if one of its parents is selected (`NESTED_REQ_PARENT`). Identical nested jars are shared between parents (`jijDedupMap`) [loader-src]. So the modules come from the copy that won.
- **Old loaders follow the same rule.** 0.14.21's comparator is the same order: root first, then id, then highest version [loader-src]. Fabric has done this since 0.12: "It will select the latest compatible version if more than one option is present" (research 0005).

### 3.2 A pack on a version target

ash's copy comes through `-Dfabric.addMods`. The pack's is in `mods`:

| The pack's Fabric API | What loads | If ash leaves the pack's out |
| --- | --- | --- |
| Older than ash's | ash's | The same |
| The same version | Either one. Modrinth's file is ash's pin, byte for byte (research 0005). | The same |
| Newer than ash's | **The pack's.** ash runs on a Fabric API it wasn't tested with. ADR-0018's notice says so. | ash's, if every mod accepts it. **Otherwise the launch fails** with "incompatible mods", for any mod that needs the newer one. |
| Older, and a mod `breaks` ash's | The pack's | The launch fails |

**Newer is a real case on the newest target.** Today ash's pins are Modrinth's newest Fabric API on all three modern targets. But 26.3 had three releases between 14 and 23 October, 0.163.0 to 0.165.0 [api]. A pack built after an ash release can carry a newer one.

**Recommendation for #189:**
- **Leave the pack's Fabric API out only when it's not newer than ash's pin.** Then nothing changes for the loader, and the instance stays on the copy ash tested.
- **When it's newer, install it like any of the pack's files.** The load report's `bundled` already tells the player which copy ran.
- **To know it's Fabric API,** use its project id, `P7dR8mSH`, which is in its `cdn.modrinth.com` URL, or read its `fabric.mod.json`. `mods.rs` already reads that for the mod list [ash].
- **In a Fabric-only instance** there's one copy, the pack's, and it's simply a mod.

### 3.3 Legacy Fabric API in a 1.8.9 pack

Four of the six 1.8.9 packs ship Modrinth's `legacy-fabric-api` 1.13.2 or 1.13.3 [packs]. The 1.13.3 jar:
- has the id `legacy-fabric-api` and depends only on `fabricloader >=0.15.11`, with no OSL;
- nests 94 module jars, for 1.3 to 1.12.2;
- has the same versions as ash's four bundled modules, apart from build metadata: base-common 1.2.2, keybinding-common 1.2.0, rendering 1.0.1 and rendering-common 1.0.1 [packs] [ash].

ash's aggregator is 1.13.5, so it's newer and is preferred. But the pack's other modules, about 90 that ash doesn't ship, load only if their parent, the pack's jar, is selected. A pack mod needing one of them forces that.

**So on 1.8.9 the pack's Legacy Fabric API has to stay.** Leaving it out would take away modules its mods need. That matches research 0014, where ash installs Modrinth's full jar when a mod needs one. This is read from the code and the jars. It hasn't been run.

## 4. The Modrinth app's profiles (#192)

All from `modrinth/code` at `e5a5663` (30 October 2026). The newest app release is 0.22.1, published 29 October 2026 [app-releases].

### 4.1 Where

| What | Where on Windows |
| --- | --- |
| The app's folder | `dirs::data_dir()` joined with the app's identifier, `ModrinthApp` [app-dirs]. `data_dir` is `{FOLDERID_RoamingAppData}` [dirs]. So `C:\Users\<user>\AppData\Roaming\ModrinthApp`. |
| Moved by | the `THESEUS_CONFIG_DIR` environment variable, which replaces the whole folder [app-dirs] |
| The database | `app.db` in that folder, SQLite in WAL journal mode [app-dirs] |
| Instances | `<config dir>\profiles\<path>\`. The config dir is the `custom_dir` setting if set, else the app's folder [app-dirs]. The folder is still named `profiles` (`INSTANCES_FOLDER_NAME`). |
| `custom_dir` | a column of the `settings` table in `app.db` [app-dirs] |

### 4.2 Where version and loader are recorded

It's moved twice:

| App versions | Where | Fields |
| --- | --- | --- |
| Before 0.8 | `%APPDATA%\com.modrinth.theseus\profiles\<name>\profile.json` | `metadata.game_version`, `metadata.loader`, `metadata.loader_version.id` [app-legacy] |
| 0.8 (by 0.8.7) to 0.14 | `app.db`, table `profiles` | `path`, `name`, `game_version`, `mod_loader`, `mod_loader_version`, `linked_project_id`, `linked_version_id`, `install_stage` [app-schema] |
| 0.15.0 (26 June 2026) on | `app.db`, table `instances`, joined to `instance_content_sets` by `instances.applied_content_set_id` | `instances`: `id`, `path`, `name`, `install_stage`. `instance_content_sets`: `game_version`, `loader`, `loader_version`. `instance_links`: `link_kind`, `modrinth_project_id`, `modrinth_version_id` [app-schema] |

- **The old tables are gone.** Migration `20260619120000_finish-instance-profile-migration` drops `profiles`, and it's in 0.15.0 but not in 0.14.0 [app-schema]. On first start, later versions move the pre-0.8 `profile.json` data into the database, unless `settings.migrated` is set [app-legacy].
- **What the values look like:**
  - `loader` is written by `ModLoader::as_str`: `vanilla`, `forge`, `fabric`, `quilt` or `neoforge` [app-types].
  - `loader_version` is the id of the loader version in Modrinth's own meta [app-types]. For Fabric that's the bare loader version, such as `0.20.1` [modrinth-meta].
- **The query the app itself uses** for an instance and its version is in `instance_rows.rs` [app-schema]. For ash it comes down to:

```sql
SELECT i.path, i.name, i.install_stage,
       cs.game_version, cs.loader, cs.loader_version,
       link.modrinth_project_id, link.modrinth_version_id
FROM instances i
LEFT JOIN instance_content_sets cs
       ON cs.id = i.applied_content_set_id AND cs.instance_id = i.id
LEFT JOIN instance_links link ON link.instance_id = i.id;
```

- **`install_stage`** is one of `installed`, `minecraft_installing`, `pack_installed`, `pack_installing` or `not_installed` [app-types]. Only `installed` is a finished instance to offer.
- **ash should tell which schema it has by the tables present,** `instances` or `profiles`, and refuse anything else with the reason. The app has 51 migrations, the first from July 2024, so its schema keeps moving [app-schema].

### 4.3 Reading it without changing anything

- **Opening a WAL database can write.**
  - SQLite reads a WAL database only if its `-wal` and `-shm` files exist, or can be created beside it [sqlite-wal].
  - The last connection to close "does one last checkpoint and then deletes the WAL and its associated shared-memory file" [sqlite-wal].
  - So opening `app.db` where it is can create, change or delete files in the app's folder. Spec 0008 says the source's bytes must be unchanged afterwards.
- **So copy first.** Copy `app.db`, and `app.db-wal` and `app.db-shm` if present, into a temporary folder of ash's. Open the copy read-only, and delete it after.
  - The `-wal` file matters: it holds whatever the app wrote since its last checkpoint.
  - If the app is running, a copy can catch it mid-write. Asking the player to close the app first is the safe reading.
- **ash has no SQLite dependency yet** [ash]. #192 adds one.

### 4.4 The files

- **The instance folder holds the game's files as usual:** `mods`, `config`, `saves` and so on, under `profiles\<path>\` [app-dirs].
- **Since 0.21.0 (14 September 2026), mods can be links into the app's store.** `store_instance_files.materialization_kind` is `reflink`, `hardlink` or `copy`, pointing at `store_blobs` keyed by SHA-512 [app-schema] [app-releases].
  - Copying reads the bytes, so ash's copy gets a real file.
  - **ash must never open the source files for writing.** A write through a hard link changes the app's store, and every instance that shares it.
- **Switched-off mods** carry a `.disabled` suffix, the same rename ash uses [app-store] [ash].

## 5. Prism Launcher and MultiMC (#191)

Prism at tag `11.1.1` (28 September 2026), MultiMC at `develop` (`f6bd711`, 30 October 2026).

### 5.1 Where the instances are

**Prism's data folder** is the first of these that applies (`Application.cpp`) [prism]:
1. the `--dir` command-line argument;
2. the `PRISMLAUNCHER_DATA_DIR` environment variable (`<LAUNCHER_NAME>_DATA_DIR`, upper-cased);
3. portable: a `UserData` folder beside the `.exe`, or a `portable.txt` beside it, which makes the `.exe`'s own folder the data folder;
4. otherwise `QStandardPaths::AppDataLocation`, one level up.
   - Prism sets both its organisation and application names to `PrismLauncher`.
   - On Windows that location is `C:/Users/<USER>/AppData/Roaming/<APPNAME>` [qt].
   - So the default is `%APPDATA%\PrismLauncher`.

**The instances folder** is the `InstanceDir` setting, default `instances`, read from `prismlauncher.cfg` in the data folder [prism]. A relative value is relative to the data folder, because Prism makes that its working directory. Prism also reads `polymc.cfg` and `multimc.cfg` there, for launchers it migrated from.

**MultiMC** uses its `.exe`'s own folder unless given `--dir`. Its `InstanceDir`, default `instances`, is in `multimc.cfg` there [multimc]. There's nowhere fixed to find it, so the player points ash at the folder (story 25).

**An instance** is any folder in the instances folder with an `instance.cfg` (`InstanceList.cpp`) [prism].

### 5.2 An instance folder

| Path | What it is |
| --- | --- |
| `instance.cfg` | The instance's settings, as INI |
| `mmc-pack.json` | Its components: the game, the loader, and their parts |
| `patches/<uid>.json` | Local overrides of a component's metadata [prism] |
| `minecraft/` or `.minecraft/` | The game directory. Prism uses `.minecraft` only if it exists and `minecraft` doesn't (`MinecraftInstance::gameRoot`) [prism]. `mods`, `config`, `resourcepacks`, `shaderpacks`, `saves`, `screenshots` and `servers.dat` are inside it. |
| `instgroups.json` | Groups, one level up in the instances folder [prism] |

### 5.3 `instance.cfg`

- **Prism reads and writes it with Qt's INI format** (`QSettings::IniFormat`), and adds `ConfigVersion=1.3` [prism].
  - Qt writes a key with no group "in the INI file's 'General' section" [qt].
  - Files with no `ConfigVersion` are the older plain `key=value` form, with MultiMC's own escaping, which Prism still parses (`parseOldFileFormat`).
  - Legacy Fabric's own instance template is the old form, with no section [lf-mmc].
  - So ash reads both. The Modrinth app's importer does the same, accepting the file with or without `[General]` [app-mmc].
- **The keys ash needs** (`BaseInstance.cpp`) [prism]:
  - `name`, default "Unnamed Instance";
  - `InstanceType`: `OneSix`, or empty, which Prism also treats as `OneSix`;
  - `iconKey`;
  - `ManagedPack`, `ManagedPackType`, `ManagedPackID`, `ManagedPackVersionID` and `ManagedPackName`. These say an instance came from a Modrinth pack (`ManagedPackType=modrinth`). The Modrinth app reads them to link the pack [app-mmc]. ash could use them to fill `ash-modpack.json`'s project and version.

### 5.4 `mmc-pack.json`

`{"formatVersion": 1, "components": [ … ]}`. Prism refuses any other `formatVersion` [prism]. Each component (`componentFromJsonV1`):

| Field | Meaning |
| --- | --- |
| `uid` | Which component (required) |
| `version` | The version chosen |
| `cachedVersion`, `cachedName`, `cachedRequires`, `cachedConflicts`, `volatile` | Prism's cache of the component's metadata |
| `important`, `dependencyOnly` | Prism's own flags |
| `disabled` | `true` when the player switched it off |

The uids [prism] [prism-meta]:

| uid | What | ash |
| --- | --- | --- |
| `net.minecraft` | The game. `version` is the Minecraft version. | The instance's version |
| `net.fabricmc.fabric-loader` | Fabric Loader. `version` is the loader version. | The loader version |
| `net.fabricmc.intermediary` | Intermediary mappings | Ignored. ash decides by section 2.4. |
| `org.lwjgl`, `org.lwjgl3` | LWJGL | Ignored. Mojang's version document names it. |
| `net.minecraftforge`, `net.neoforged`, `org.quiltmc.quilt-loader`, `com.mumfrey.liteloader` | The other loaders Prism knows (`Component::KNOWN_MODLOADERS`) | Refused, naming the loader |

- **A component with `disabled: true` doesn't count.** Prism skips it when it works out the loaders (`getModLoaders`) [prism].
- **One Fabric Loader and no other loader** is the only shape ash takes.

### 5.5 1.8.9 instances, and `patches/`

- **Legacy Fabric's own Prism and MultiMC template** names `net.minecraft`, `net.fabricmc.intermediary` and `net.fabricmc.fabric-loader`. It then overrides the intermediary in `patches/net.fabricmc.intermediary.json` with `net.legacyfabric:intermediary:<version>` from `maven.legacyfabric.net`, and LWJGL with a patch of its own [lf-mmc].
- **Two of the 1.8.9 `.mrpack` files carry the same patches** at the zip's root [packs].
- **So a 1.8.9 Prism instance looks like a Fabric one,** and ash should take `net.minecraft` 1.8.9 with a Fabric Loader component to mean Legacy Fabric, on ash's 1.8.9 pin.
- **Any other `patches/` file** changes what a component is in ways ash can't follow. Prism lets a patch replace any component's libraries. Refusing an instance with patches, on any version but 1.8.9, is the safe reading (open question 5).

## 6. Hash-based updates on Modrinth (#195, #196)

Read from labrinth's routes and Modrinth's published API docs, and called live with a research User-Agent [labrinth-routes] [api-docs] [api].

### 6.1 Many files: `POST /v2/version_files/update`

Request body (`ManyUpdateData`):

```json
{
  "hashes": ["<sha512 hex>", "…"],
  "algorithm": "sha512",
  "loaders": ["fabric"],
  "game_versions": ["1.21.11"],
  "version_types": ["release"]
}
```

- **`algorithm`** is `sha1` or `sha512`. Left out, labrinth guesses from the first hash's length, taking 128 characters or more as SHA-512 [labrinth-routes]. One algorithm covers the whole request.
- **`loaders` and `game_versions`** are required in the published docs, and optional in the code [api-docs] [labrinth-routes].
- **`version_types`** (`release`, `beta`, `alpha`) is optional. Left out, it means all three [labrinth-routes].

Response: a JSON object from each hash, as sent, to a full version object. That's the same shape as `GET /v2/version/{id}`: `id`, `project_id`, `version_number`, `version_type`, `date_published`, `files` (with `url`, `hashes`, `size`, `primary`), `dependencies`, `game_versions`, `loaders` and the rest [api]:
- A hash Modrinth doesn't know, or with no version matching the filters, is **left out**. A request where none match answers `200 {}` [api].
- A mod that's already current comes back as **its own version**. So "has an update" is "the returned `id` isn't the installed `version_id`" [api].
- The keys are lower-case hex from Modrinth's database [labrinth-routes]. `ash-mods.json` already stores them that way [ash].

Live, for Sodium 0.8.13 on 1.21.11 [api]:

| `version_types` | Answer |
| --- | --- |
| left out | `0.8.15-beta.1` (`RB7CDjTS`), a beta |
| `["release"]` | `0.8.14` (`rkdTcxoT`) |

**"Newest" is by date.** Labrinth orders matching versions by `date_published` descending and takes the first [labrinth-routes]. It isn't the highest version number. A maintainer who publishes a fix for an older line after a newer release would have that fix offered as the update. That's rare, and the player sees the version number before choosing.

### 6.2 One file: `POST /v2/version_file/{hash}/update?algorithm=sha1|sha512`

- **Body:** `{"loaders": […], "game_versions": […], "version_types": […]}` [api-docs] [labrinth-routes].
- **Answer:** one version object, `404` for an unknown hash. A current mod returns its own version, as above [api].
- An optional `version_id` query picks between versions when one hash belongs to several [labrinth-routes].

### 6.3 Two variants that aren't in the published docs

Labrinth also serves these, but `docs.modrinth.com`'s OpenAPI file doesn't list them [labrinth-routes] [api-docs]:
- **`POST /v2/version_files/update_individual`** takes `{"algorithm": …, "hashes": [{"hash", "loaders", "game_versions", "version_types"}, …]}`, with filters per hash, and answers the same map. Live, it returned the newest release for a release-installed Sodium and the newest beta for a beta-installed one, in one request [api].
- **`POST /v2/version_files/update_many`** answers every matching version per hash, not just the newest.

### 6.4 For ash's Mods page (#195)

- **Hashes:** send SHA-512, which `ash-mods.json` already records, and hash hand-added jars, `.disabled` ones too, with SHA-512 so one algorithm covers both.
- **Filters:**
  - `loaders`: the instance's Modrinth loader tag, the one `mods.rs` searches with (`fabric`, or `legacy-fabric` on 1.8.9) [ash].
  - `game_versions`: the instance's version.
- **Releases versus betas.** Research 0014's rule is "the newest release, or a pre-release only if there's no release". Spec 0008 asks for "one request":
  - **The documented endpoint takes one `version_types` for every hash.** So it's two requests: `["release"]` for mods installed as releases and hand-added jars, and no filter for mods the player installed as a beta or alpha.
  - **`update_individual` does it in one,** but it's undocumented (open question 7).
- **Rate limit:** 300 requests a minute, and each call reports what's left in `X-Ratelimit-Remaining` [api]. One or two requests per opening of the Mods page is nothing.

### 6.5 For pack updates (#196)

- **The hash route needs `mrpack` as the loader.** A modpack version's loader in Modrinth's database is `mrpack`, with the real loader in `mrpack_loaders` (v3 API) [api].
  - With `loaders: ["fabric"]`, `POST /v2/version_file/<pack's sha512>/update` answers `404`.
  - With `["mrpack"]`, or no `loaders` at all, it answers the next version, AxolotlClient 0.1.2 to 0.1.3 on 1.8.9 [api].
  - The v2 API still shows the version's `loaders` as `["fabric"]`.
- **The listing route takes `fabric`, but doesn't filter reliably.** `GET /v2/project/{id}/version?loaders=["fabric"]&game_versions=[…]` works for packs, but it returned Quilt versions of `sodiumplus` (section 1.8) [api].
- **So for either route, check the answer's `loaders`, then the new index's `dependencies`, before offering it.**
- **"A newer Minecraft version" comes from the index too.** A version's `game_versions` on Modrinth can be 95 versions wide (section 1.8). So ash downloads the new `.mrpack`, which is usually small, before deciding between "Update" and "Make a new instance from it".

### 6.6 For export (#194)

- **`POST /v2/version_files`** with `{"hashes": […], "algorithm": "sha1"}` answers a map from hash to version [api-docs] [api]. That's how a mod is found on Modrinth.
- **It has what an index entry needs:** the file's `url` on `cdn.modrinth.com`, `hashes.sha1` and `hashes.sha512`, and `size` [api].
  - Live: ash's pinned Fabric API, SHA-1 `c98467cb…`, is project `P7dR8mSH`, version `6qAuTtLR`, at `https://cdn.modrinth.com/data/P7dR8mSH/versions/6qAuTtLR/fabric-api-0.141.6%2B1.21.11.jar`.
- **The Modrinth app's exporter** writes `formatVersion: 1`, `game: "minecraft"`, the loader and `minecraft` dependencies, and an `env` per file taken from the version's environment [app-export].

## 7. Do Legacy Fabric packs declare `fabric-loader`?

**Yes. Every 1.8.9 Fabric pack on Modrinth declares `fabric-loader`,** with `minecraft: "1.8.9"`.
- Searching modpacks with `versions:1.8.9` and `categories:fabric` finds six [api]. Each one's newest 1.8.9 version was downloaded, checked against its SHA-512, and its index read [packs]:

| Pack | Version | `dependencies` |
| --- | --- | --- |
| `axolotlclient-modpack` | 0.1.3+1.8.9 | `fabric-loader` 0.16.12, `minecraft` 1.8.9 |
| `nicklyoptimized` | 1.8.9-2.0.0 | `fabric-loader` 0.18.4, `minecraft` 1.8.9 |
| `bedwars-1.21` | 1.0.0 | `minecraft` 1.8.9, `fabric-loader` 0.18.1 |
| `ezfps` | 1.8.9.1.1 | `fabric-loader` 0.18.4, `minecraft` 1.8.9 |
| `slimline` | 1.1.1 | `fabric-loader` 0.18.4, `minecraft` 1.8.9 |
| `recorders-modpack` | 4.8 | `fabric-loader` 0.17.2, `minecraft` **1.21.8**. Mislabelled, section 1.8. |

- **There's no other way to say it.** Labrinth's `PackDependency` has no Legacy Fabric id [labrinth-pack].
  - Modrinth's `legacy-fabric` loader tag is for mods only. Its `supported_project_types` are `mod` and `project`, where `fabric`'s include `modpack` [api].
  - A modpack search with `categories:legacy-fabric` finds nothing [api].
- **Upstream Fabric has no 1.8.9.** Fabric Meta answers `400`, with an empty list, for `/v2/versions/loader/1.8.9`, and Fabric's Maven has no `intermediary-1.8.9.jar` (`404`) [meta] [maven]. Modrinth's own meta for Fabric has no 1.8.9 entry, only a placeholder for any version [modrinth-meta]. So `fabric-loader` with `minecraft: "1.8.9"` can only mean Legacy Fabric. I didn't run the Modrinth app to see what it does with one.

**What follows:**
- **Import (#189).** `fabric-loader` and `minecraft: "1.8.9"` makes a 1.8.9 instance, which is a version target: ash's client, ash's Legacy Fabric pin and ash's loader (0.19.3). The pack's Legacy Fabric API stays (section 3.3).
- **Export (#194).** Spec 0008 refuses 1.8.9 because "a `.mrpack` can't name Legacy Fabric". It can, the way every 1.8.9 pack on Modrinth does. Whether another launcher runs such a pack is that launcher's business. Prism needs the patches these packs carry (open question 4).
- **Browsing (#190).** The Modpacks view's `categories:fabric` facet finds 1.8.9 packs. `legacy-fabric` would find none.

## 8. For the tickets

| Ticket | What this note changes |
| --- | --- |
| #188 Fabric-only instances | Pin at creation, recorded in the instance (2.5), and an ADR amending ADR-0014. Parse version 1 loader documents (2.3). No intermediary on 26.x (2.4). The size comes from the verified bytes. |
| #189 Import a `.mrpack` | Path rules beyond Modrinth's docs (1.6). Check the URL in the index, not where it redirects (1.5). Leave Fabric API out only when it's not newer than ash's (3.2), and keep Legacy Fabric API on 1.8.9 (3.3). Unknown dependency keys refused. Overrides after index files. Packs can be 240 MB. |
| #190 Browse packs | The API's loaders and versions for a pack can be wrong. Read the index before installing (1.8). Use `categories:fabric`, which covers 1.8.9. |
| #191 Prism and MultiMC | Data folder rules (5.1). Both INI forms (5.3). `.minecraft` or `minecraft` (5.2). Ignore the intermediary component. 1.8.9 means Legacy Fabric. Other patches (5.5). `ManagedPack*` keys can link the pack. |
| #192 The Modrinth app | Three schemas (4.2). Copy the database before reading it (4.3). Source mods may be hard links (4.4). A SQLite dependency. |
| #194 Export | Both hashes required. 1.8.9 can be exported as `fabric-loader` (7). |
| #195 Mod updates | Two documented requests, or one undocumented (6.4). "Has an update" means a different `id`. Betas are included unless filtered out. |
| #196 Pack updates | `mrpack` as the loader on the hash route (6.5). Decide "new Minecraft version" from the new index. |

## 9. Open questions

1. **ADR for per-instance pins.** Section 2.5's design changes ADR-0014's "pinned per target, recorded in ash's source". Whether to also ship a table of intermediary hashes, or check the loader's signature, is a separate choice. Neither is needed for #188.
2. **Old loaders.** Spec 0008 says an import keeps its loader version. That means version 1 documents and their libraries (2.3). The other choice is the newest stable loader for any pack before 0.15.0. Fabric Loader is meant to be backward compatible, but I didn't test old packs on a new loader.
3. **A newer Fabric API in a pack on a target** runs ash on a Fabric API it wasn't tested with (3.2). Section 3.2 accepts that, with the notice, over a failed launch. The product owner may prefer a refusal instead.
4. **Exporting 1.8.9.** It's possible in the form Modrinth's 1.8.9 packs use (7). Keeping the refusal is also defensible, since only some launchers can run the result. It's a product decision for #194.
5. **Prism instances with `patches/`** on versions other than 1.8.9. Refusing is the safe reading. How common they are wasn't measured.
6. **Vanilla packs.** A `.mrpack` with only a `minecraft` dependency, such as resource packs or data packs alone, isn't covered by spec 0008. It could become a vanilla instance, a Fabric-only one, or be refused.
7. **`update_individual`** would make #195 one request, but it isn't in Modrinth's published docs (6.3). Two documented requests are the safe reading.
8. **Production's allowed domains** can't be read from outside. The docs say four, and the code's default is three (1.5).
9. **Untested in a game:** the 1.8.9 Legacy Fabric API case (3.3), and every duplicate case on 0.20.1. 0.20.1 is the same code as 0.19.5, which research 0005 ran.
10. **The Modrinth app while it's running.** A copy of `app.db` taken mid-write may be inconsistent (4.3). Whether ash asks the player to close the app, or just retries, is #192's choice.

## 10. Method

- **Modrinth's format and API:**
  - the support article on the `.mrpack` format;
  - labrinth's pack model, validator, environment defaults and version-file routes;
  - the `path-util` crate;
  - the published `openapi.yaml`;
  - all at `modrinth/code` `e5a5663`.
- **Live calls** to `api.modrinth.com` on 30 October 2026, with the User-Agent `sisi-j/ash/research (+https://github.com/sisi-j/ash)`.
- **The Modrinth app:** `packages/app-lib` and `apps/app` at the same commit. Migrations were checked at release tags `v0.8.7`, `v0.14.0`, `v0.15.0` and `v0.21.0` to date each schema.
- **Fabric:**
  - Fabric Meta's endpoints, and `FabricMC/fabric-meta` at `14c83c0`;
  - Fabric's Maven sidecars, with local hashes of the files they describe;
  - the loader's signature, verified with GnuPG in a scratch keyring;
  - `FabricMC/fabric-loader`'s discovery code at tags `0.14.21`, `0.19.5` and `0.20.1`, compared file by file.
- **Prism** at tag `11.1.1`, and its meta index. **MultiMC** at `develop` `f6bd711`. Legacy Fabric's `multimc-instance-creator` at `aab8541`.
- **Packs:**
  - the six 1.8.9 Fabric packs, plus sixteen popular packs from Fabric searches on 1.16.5 to 1.21.1;
  - packs over 30 MB were skipped;
  - each was checked against Modrinth's SHA-512 and read with Python's `zipfile`, never extracted.
- **Not done:** no game, launcher or Modrinth app was run, and no account was used.

## Sources

- **[mrpack-doc]** Modrinth, [Modrinth Modpack format (.mrpack)](https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack), read 30 October 2026.
- **[labrinth-pack]** [`apps/labrinth/src/models/v3/pack.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/models/v3/pack.rs): `PackFormat`, `PackFile`, `validate_download_url`, `PackDependency`.
- **[labrinth-validate]** [`apps/labrinth/src/validate/modpack.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/validate/modpack.rs).
- **[labrinth-env]** [`apps/labrinth/src/env.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/env.rs) (`WHITELISTED_MODPACK_DOMAINS`) and [`apps/labrinth/.env.local`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/.env.local).
- **[labrinth-sides]** [`apps/labrinth/src/models/v2/projects.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/models/v2/projects.rs): `LegacySideType`.
- **[labrinth-routes]** [`apps/labrinth/src/routes/v2/version_file.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/routes/v2/version_file.rs) and [`v3/version_file.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/labrinth/src/routes/v3/version_file.rs): `update_files`, `update_files_internal`, `get_update_from_hash`, `update_individual_files`, `update_files_many`, `default_algorithm_from_hashes`.
- **[api-docs]** Modrinth's API docs: [`apps/docs/public/openapi.yaml`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/docs/public/openapi.yaml), served at [docs.modrinth.com/api](https://docs.modrinth.com/api/): `getLatestVersionFromHash`, `getLatestVersionsFromHashes`, `versionsFromHashes`.
- **[path-util]** [`packages/path-util/src/lib.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/path-util/src/lib.rs): `SafeRelativeUtf8UnixPathBuf`, `is_reserved_windows_device_name`.
- **[app-mrpack]** [`packages/app-lib/src/api/pack/install_mrpack.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/api/pack/install_mrpack.rs) and [`install_from.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/api/pack/install_from.rs).
- **[app-export]** [`packages/app-lib/src/api/instance/export_mrpack.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/api/instance/export_mrpack.rs).
- **[app-mmc]** [`packages/app-lib/src/api/pack/import/mmc.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/api/pack/import/mmc.rs).
- **[app-dirs]** At `e5a5663`:
  - [`packages/app-lib/src/state/dirs.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/dirs.rs);
  - [`db.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/db.rs);
  - [`settings.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/settings.rs);
  - [`state/mod.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/mod.rs);
  - [`apps/app/tauri.conf.json`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/apps/app/tauri.conf.json) (`"identifier": "ModrinthApp"`, the same at `v0.8.7`, `v0.14.0` and `v0.15.0`).
- **[app-schema]** `packages/app-lib/migrations/` at `e5a5663`:
  - `20240711194701_init.sql` (`profiles`);
  - `20260611120000_instances-content-foundation.sql`;
  - `20260619120000_finish-instance-profile-migration.sql`;
  - `20260910120000_shared-content-store.sql`.
  - Each was checked at the release tags named in Method.
  - Also [`state/instances/adapters/sqlite/instance_rows.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/instances/adapters/sqlite/instance_rows.rs).
- **[app-types]** [`state/instance_types.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/instance_types.rs) (`ModLoader`, `InstanceInstallStage`), [`state/instances/commands/create_instance.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/instances/commands/create_instance.rs) and `launcher/mod.rs` (`get_loader_version_from_profile`).
- **[app-legacy]** [`state/legacy_converter.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/legacy_converter.rs).
- **[app-store]** [`state/content_store/domain/paths.rs`](https://github.com/modrinth/code/blob/e5a5663ab4e2c75cd98e20231073d29a5b362f43/packages/app-lib/src/state/content_store/domain/paths.rs).
- **[app-releases]** [modrinth/code releases](https://github.com/modrinth/code/releases): v0.14.0 (27 May 2026), v0.15.0 (26 June 2026), v0.21.0 (14 September 2026), v0.22.1 (29 October 2026).
- **[modrinth-meta]** Modrinth's launcher meta: [Fabric manifest](https://launcher-meta.modrinth.com/fabric/v0/manifest.json) and [0.20.1 profile](https://launcher-meta.modrinth.com/fabric/v0/versions/0.20.1.json).
- **[dirs]** [`dirs::data_dir`](https://docs.rs/dirs/latest/dirs/fn.data_dir.html).
- **[sqlite-wal]** SQLite, [Write-Ahead Logging](https://www.sqlite.org/wal.html): "Read-Only Databases" and "Avoiding Excessively Large WAL Files".
- **[meta]** Fabric Meta:
  - [game versions](https://meta.fabricmc.net/v2/versions/game);
  - [loader versions](https://meta.fabricmc.net/v2/versions/loader);
  - loaders for [1.20.1](https://meta.fabricmc.net/v2/versions/loader/1.20.1), [1.14](https://meta.fabricmc.net/v2/versions/loader/1.14) and [26.1](https://meta.fabricmc.net/v2/versions/loader/26.1);
  - [intermediary for 26.1](https://meta.fabricmc.net/v2/versions/intermediary/26.1);
  - the profiles for [1.20.1 + 0.20.1](https://meta.fabricmc.net/v2/versions/loader/1.20.1/0.20.1/profile/json), 1.14, 26.1 and 26.2;
  - and `/v2/versions/loader/1.8.9` (400).
- **[meta-src]** [FabricMC/fabric-meta](https://github.com/FabricMC/fabric-meta/tree/14c83c077b6d9a81c494fb559833f93ce108a5b4) at `14c83c0`: `web/ProfileHandler.java` (`buildProfileJson`, `formatLibrary`) and `utils/McObfuscationChecker.java`.
- **[maven]** Fabric's Maven, `https://maven.fabricmc.net/`, read 30 October 2026:
  - `fabric-loader` 0.13.3, 0.14.21 to 0.14.25, 0.15.0, 0.15.1, 0.15.11, 0.16.0, 0.16.14, 0.17.0, 0.18.0 and 0.20.1: documents, jars and sidecars;
  - `intermediary` 1.14, 1.20.1, 1.21.11, 1.8.9 (absent), 26.1 and 26.3 (redirected);
  - 0.14.21's libraries;
  - the `fabric-api` and `sponge-mixin` signatures.
- **[sig]** The `.asc` files for `fabric-loader` 0.14.21, 0.16.14 and 0.20.1, and the key `0x11B891CFE51C003E` from [keyserver.ubuntu.com](https://keyserver.ubuntu.com/pks/lookup?op=get&search=0x11B891CFE51C003E). Verified with GnuPG.
- **[loader-src]** [FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader):
  - `src/main/java/net/fabricmc/loader/impl/discovery/` (`ModPrioSorter`, `ModSolver`, `ModResolver`, `ModDiscoverer`, `ResultAnalyzer`, `ModCandidateImpl`) at [`0.20.1`](https://github.com/FabricMC/fabric-loader/tree/0.20.1/src/main/java/net/fabricmc/loader/impl/discovery), `0.19.5` and `0.14.21`;
  - `util/version/SemanticVersionImpl.java` at 0.20.1;
  - `src/test/.../ModResolverTest.java` at 0.20.1.
- **[prism]** [PrismLauncher/PrismLauncher](https://github.com/PrismLauncher/PrismLauncher/tree/11.1.1) at `11.1.1`:
  - `launcher/Application.cpp`, `BaseInstance.cpp`, `InstanceList.cpp`;
  - `minecraft/MinecraftInstance.cpp`, `PackProfile.cpp`, `Component.cpp`;
  - `settings/INIFile.cpp`, `modplatform/ModIndex.h`;
  - `program_info/CMakeLists.txt`, `CMakeLists.txt`.
- **[prism-meta]** [Prism's meta index](https://meta.prismlauncher.org/v1/index.json), [`net.fabricmc.fabric-loader/0.20.1`](https://meta.prismlauncher.org/v1/net.fabricmc.fabric-loader/0.20.1.json), [`net.fabricmc.intermediary/26.3`](https://meta.prismlauncher.org/v1/net.fabricmc.intermediary/26.3.json).
- **[qt]** Qt 6, [QStandardPaths](https://doc.qt.io/qt-6/qstandardpaths.html): `AppDataLocation` on Windows, and what `<APPNAME>` is. [QSettings](https://doc.qt.io/qt-6/qsettings.html): the INI format's "General" section.
- **[multimc]** [MultiMC/Launcher](https://github.com/MultiMC/Launcher/blob/develop/launcher/Application.cpp) `develop` at `f6bd711`: `launcher/Application.cpp`.
- **[lf-mmc]** [Legacy-Fabric/multimc-instance-creator](https://github.com/Legacy-Fabric/multimc-instance-creator/tree/aab85410701e4c9eedae080788b26419711065e7) at `aab8541`: `skel/instance.cfg`, `skel/mmc-pack.json`, `skel/patches/net.fabricmc.intermediary.json`.
- **[api]** Modrinth's API, `https://api.modrinth.com`, 30 October 2026:
  - `GET /v2/search` for modpacks on 1.8.9 (`fabric`: 6 hits; `legacy-fabric`: 0) and on 1.16.5 to 1.21.1;
  - `GET /v2/project/{id}/version` for Sodium, Fabric API, `sodiumplus` and the 1.8.9 packs;
  - `GET /v3/version/7jZW2l33`, `GET /v3/project/sodiumplus/version`, `GET /v2/tag/loader`;
  - `POST /v2/version_files/update`, `/update_individual`, `/v2/version_files`, and `/v2/version_file/{hash}/update`, with the cases in section 6;
  - the redirect from a `github.com` release download.
- **[packs]** `.mrpack` files from `cdn.modrinth.com`, each checked against its SHA-512:
  - 1.8.9: `2fXi8IpU/7jZW2l33`, `YH7hhhOX/fgjEh3Kb`, `QZ6l1mUp/jfM63Q6P`, `EUf8lb0a/ouyRx9ky`, `18Hch8C1/5X8ymAus`, `oM4nvDiZ/2Rkc6f5t`;
  - the popular sample: Fabulously Optimized, OptiFabric Modpack, Remarkably, OptiFine for Fabric, Sodium Plus, FPS and Vanilla Perfected, at their newest version for each game version named in section 2.3;
  - and the `legacy-fabric-api-1.13.3.jar` from `ezfps`'s index.
- **[ash]** This repository:
  - `launcher/core/src/loader.rs` (`DocumentLibrary`, `LoaderPin`, the pins), `depot.rs` (`Artifact`, `fetch_metadata`), `mods.rs` (`InstalledMod`, `loader_tag`, `jar_metadata`, `.disabled`), `launcher/core/Cargo.toml`;
  - ADR-0014, 0018, 0024, 0026;
  - research 0005, 0014, 0016;
  - spec 0008;
  - `CONTEXT.md`.
