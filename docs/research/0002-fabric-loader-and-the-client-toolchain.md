# Installing Fabric and Legacy Fabric without their installers, and building a client against two version targets

Research date: 2026-09-14. All sources retrieved on that date unless stated otherwise.

Every claim below is labelled:

- **[DOC]** — the first-party documentation says this.
- **[PRACTICE]** — observed behaviour of the live API, or shipped code in an established project.
- **[COMMUNITY]** — a reverse-engineered or community source is the only available evidence.

---

## Summary

**Four of ash's existing decisions are in trouble, and two of them are load-bearing.**

**1. ADR-0005's modern target no longer exists as written, and the version it names is the end of a line.** Minecraft: Java Edition changed its version scheme in 2026. The current release is **26.2** (2026-06-16); the latest 1.21.x is **1.21.11** (2025-12-09) **[PRACTICE — Mojang's own `version_manifest_v2.json`]**. More important than the numbering: **from 26.1 onward the game ships unobfuscated.** 26.1 and 26.2 have no `client_mappings`/`server_mappings` downloads, and Fabric Meta reports intermediary `0.0.0` for them, versus a real `net.fabricmc:intermediary:1.21.11` for 1.21.11 **[PRACTICE]**. Fabric states it plainly: *"Minecraft: Java Edition was obfuscated from its release until 1.21.11"* and *"Intermediary will no longer exist; the game will use Mojang's names at runtime"* **[DOC]**. Yarn is deprecated; Fabric *"can't see a way to justify maintaining Yarn in its current state"* **[DOC]**. Java requirement also moves: 1.21.11 wants Java 21, 26.x wants Java 25 **[PRACTICE]**.

So "the latest 1.21.x" is a choice to target the **last obfuscated version**, with intermediary, refmaps, Yarn-or-Mojmap, and a toolchain Fabric has announced it is retiring. That may still be the right call — 1.21.11 is where players are today and it is frozen, which is a maintenance argument in its favour — but it must be an explicit choice, not a stale "latest".

**2. ADR-0004 states Sodium's licence incorrectly, and the correct one is hostile to ash.** Sodium is not LGPL-3.0. It is **PolyForm Shield License 1.0.0** — verified in `LICENSE.md` on `dev`, on `mc1.21.11-0.8.14`, and on `mc26.2-0.9.2`, and in the shipped jar's own `fabric.mod.json` (`"license": "Polyform-Shield-1.0.0"`) **[PRACTICE]**. PolyForm Shield is source-available with a **Noncompete** clause: *"Any purpose is a permitted purpose, except for providing any product that competes with the software or any product the licensor or any of its affiliates provides using the software"*, and *"Goods and services compete even when they provide functionality through different kinds of interfaces or for different technical platforms… Goods and services compete even when provided free of charge"* **[DOC]**. ADR-0004's entire premise — "they're LGPL, so keep them at arm's length and our code stays proprietary" — is the wrong analysis for Sodium. The LGPL analysis still holds for **Lithium**, which is genuinely LGPL-3.0-only **[PRACTICE]**. See §7.

**3. ADR-0004's technical claim about territory is contradicted by Sodium's own mixin manifest.** ADR-0004 says ash's work "takes entity rendering, HUD and text rendering, particles, and client-side tick — the hotspots neither covers." Sodium 0.8.14's `sodium-common.mixins.json` demonstrably covers glyph rendering (`features.render.gui.font.BakedGlyphMixin`), GUI graphics (`features.textures.animations.tracking.GuiGraphicsMixin`), entity culling and model parts (`features.render.entity.cull.EntityRendererMixin`, `ModelPartMixin`, `CubeMixin`), entity shadows, particles, and the immediate-mode buffer path every HUD and entity draw goes through (`BufferBuilderMixin`, `MultiBufferSourceMixin`, `VertexConsumerMixin`). Lithium's client list includes `experimental.client_tick.*` **[PRACTICE]**. The territory is contested, not disjoint. Worse, Sodium's configs are `"required": true` with `injectors.defaultRequire: 1` and `overwrites.requireAnnotations: true` **[PRACTICE]** — an ash mixin that disturbs a method Sodium overwrites produces a hard crash, not a degraded frame.

**4. The depot invariant survives, but not through Fabric Meta.** The profile JSON at `meta.fabricmc.net` is **generated per request** — its `releaseTime`/`time` are the server's clock at the moment you ask, confirmed by two cache-busted fetches four seconds apart returning different bytes, and by Fabric Meta's own source (`profile.addProperty("releaseTime", currentTime)`) **[PRACTICE + DOC]**. There is no published hash for it and there cannot be. But **every artifact it points at is hashed**: `maven.fabricmc.net` and `maven.legacyfabric.net` publish `.md5`/`.sha1`/`.sha256`/`.sha512` sidecars for every jar including the loader and intermediary, verified byte-for-byte against the downloads, and Fabric additionally publishes a `.asc` GPG signature on the loader jar **[PRACTICE]**. And there *is* an immutable, pinnable document: `https://maven.fabricmc.net/net/fabricmc/fabric-loader/<version>/fabric-loader-<version>.json` is the loader's own launcher metadata, immutable per loader version, and it carries `md5`/`sha1`/`sha256`/`sha512`/`size` for the ASM and Mixin libraries inline **[PRACTICE]**. ash can keep its invariant by building the profile itself from pinned inputs rather than fetching a generated one. §2 sets out how.

**What established launchers do is the opposite, and it is worth knowing.** Prism Launcher downloads Fabric Loader, intermediary, ASM and Mixin **with no integrity check of any kind** — `Library::getDownloads` attaches a `ChecksumValidator` only when the metadata carries a sha1, and Fabric library entries carry none, so the call is `add_download(raw_storage, raw_dl, QString())` **[PRACTICE]**. Fabric's own installer verifies a sha1 for exactly one file, the Minecraft *server* jar **[PRACTICE]**. ash would be doing better than the field here, not worse.

**One more thing worth reading before Phase 4 is designed.** The Usage Guidelines' "Servers and hosting" section — as served today — explicitly permits *"Selling cosmetics, **except for capes or anything that attempts to visually act like the feature of a Minecraft player cape**"* **[DOC]**. That is Mojang naming capes, specifically, as the one cosmetic you may not sell, in the only context where selling cosmetics is blessed at all. Research 0001 §8 enumerated this same section and did not report these bullets. See §6 and gap 4.

---

## Confidence and gaps

Read this section before acting on anything below.

### Could not verify from any source

1. **Whether the `inheritsFrom` merge is specified anywhere.** It is not. `inheritsFrom` does not appear in Mojang documentation, in Fabric's documentation, or in minecraft.wiki's `client.json` article — the de-facto community reference for the version-JSON format, whose full field tree I read and which never mentions it **[PRACTICE]**. The official launcher evidently honours it (Fabric's installer writes a profile into `.minecraft/versions/` and registers it in `launcher_profiles.json`, and that is the entire shipping install path) **[PRACTICE]**, but no published contract describes the merge. **Every launcher reimplements it from observation.** §1 reconstructs it from two independent implementations, which agree on the shape and disagree on the details that matter most to 1.8.9.

2. **Whether the Usage Guidelines' server-monetisation bullets are new since 2026-09-09.** I could not establish it. The Wayback Machine holds 148 captures of the page between 2026-06-01 and 2026-09-14, but every one of them is the JavaScript shell — none contains the body text at all (`"Extended functionality"`, `"Servers and hosting"` and the required disclaimer are all absent from the 2026-06-01 and 2026-09-09 captures). The page carries no revision date. So §6 reports the current text as authoritative and cannot date it.

3. **Which Java versions 1.8.9 + Legacy Fabric is actually supported on, beyond 8.** Legacy Fabric's launcher metadata states `min_java_version: 8` and Mojang's 1.8.9 metadata asks for `jre-legacy` / major 8 **[PRACTICE]**. Legacy Fabric's LWJGL 2 fork README lists *"Fix crash on Java 11+ due to missing symbols"* **[DOC]**, which implies newer Java is at least intended to work, but no first-party source states a supported range or an upper bound. **Provision Java 8 for 1.8.9 and treat anything else as unproven.**

4. **Exact vanilla class and method names to mixin for ash's features on either target.** §5 names every hook I verified in Fabric API and Legacy Fabric API source. For the features neither API exposes — crosshair replacement on 1.8.9, freelook, toggle sprint, ping and FPS readouts on either target — I deliberately do **not** name vanilla classes, because I did not read the game's own source and a plausible-looking wrong name is worse than a gap. Those need a decompiled-source pass against each target before they can be specified.

5. **Whether ash's bundling of Sodium triggers PolyForm Shield's Noncompete clause.** §7 sets out the text and the reading. It is a reading, not a ruling, and the licensor is an individual (JellySquid) with no published interpretation, FAQ or exception process that I could find.

6. ~~**Legacy Fabric API's real current version for 1.8.9.**~~ **Resolved 2026-09-15, while implementing #18.** The answer is **`1.13.5+1.8.9`**, which is what `maven.legacyfabric.net` actually serves - the repository ash downloads from, and so the only one of the three sources that can be right. GitHub's releases running to 1.16.1 are for other Minecraft versions; the example mod's `1.13.2+1.8.9` is simply older.

    **And the artifact is not what this section assumed.** `legacy-fabric-api-1.13.5+1.8.9.jar` is **5,217 bytes: four entries, no classes, no nested `jars`** - a metadata-only aggregator. Its POM names **43 separate module dependencies**, each independently versioned (some `+1.8.9`, some `+1.12.2`, plus `-common` variants). Legacy Fabric API is modular where Fabric API is one 2.4MB fat jar, so "the same thing on 1.8.9" is not the same shape, and this document's talk of *"the API jar"* on this target is wrong.

    ash pins the aggregator alone for now. Which modules the client actually needs cannot be known until the client exists, so pinning 43 hashes today would be guessing; that is #21's to settle. Confirmed in the same pass: Legacy Fabric's meta serves **upstream `net.fabricmc:fabric-loader:0.19.3`** for 1.8.9, and `maven.legacyfabric.net` accepts a literal `+` in a path unencoded, so no URL escaping is needed.

### Sources are thin, conflicting or out of date

7. **Fabric Loom's README understates its own requirements by several major versions.** It says *"Loom targets the latest version of Gradle 7 or newer"* and *"Supports Java 16 upwards"* **[DOC]**; the repo's own wrapper is Gradle 9.5.0 and its build compiles at `release = 21` **[PRACTICE]**. Trust `fabric-example-mod`, which is maintained in lockstep with Loom, over the README.

8. **Prism Launcher has no Legacy Fabric support at all.** Zero matches for "legacyfabric" anywhere in its source, and its meta service publishes no `net.legacyfabric` package **[PRACTICE]**. For the 1.8.9 target Prism is not a reference implementation. HMCL is — it has a `GameComponentType.LEGACY_FABRIC` and a hard-coded workaround for it (§1, §8).

9. **Stonecutter, one of the two live multi-version Gradle plugins, has migrated off GitHub.** Its GitHub repo is now a mirror redirecting to Codeberg and was last pushed 2025-12-25 **[PRACTICE]**. I evaluated it from the mirror; treat its current state as unverified.

10. **Legacy Fabric's bus factor is one.** Across the last twelve months, one person (`thecatcore` / "Cat Core") authored 69 of 69 commits to the API repo, 82 of 92 to the LWJGL fork, 29 of 41 to the meta service, 26 of 30 to Yarn and 9 of 11 to the Loom companion plugin **[PRACTICE]**. This is not a prediction of failure — the cadence is real and current — but it is the quantification ADR-0003 asked for, and it is a single point of failure. **Mirrored 2026-09-16, closing this exposure** — ash now keeps its own copy of everything that server serves and falls back to it when the server cannot be reached; see `docs/mirror.md`.

11. **Fabric's fallback infrastructure is documented only in a code comment, and it tells you not to use it.** `Reference.java` lists `meta2`/`meta3.fabricmc.net` and `maven2`/`maven3.fabricmc.net` with: *"Do not use these fallback servers to interact with our web services. They can and will be unavailable at times and only support limited throughput."* **[DOC]** All four respond 200 today **[PRACTICE]**. There is no status page and no published availability commitment for the primaries either.

---

## 1. The Fabric profile JSON and `inheritsFrom`

### The exact shape, from the generator

`meta.fabricmc.net`'s profile endpoint is built by `ProfileHandler.buildProfileJson`, whose own comment reads *"This is based of the installer code."* **[DOC — Fabric Meta source]**. It writes exactly seven top-level fields, in this order:

```json
{
  "id": "fabric-loader-0.19.5-1.21.11",
  "inheritsFrom": "1.21.11",
  "releaseTime": "<now, ISO-8601>",
  "time": "<now, ISO-8601>",
  "type": "release",
  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
  "arguments": { "game": [], "jvm": ["-DFabricMcEmu= net.minecraft.client.main.Main "] },
  "libraries": [ ... ]
}
```

Live response for 1.21.11 + loader 0.19.5, verified 2026-09-14 **[PRACTICE]**. Note what is **absent**: no `downloads`, no `assetIndex`, no `javaVersion`, no `logging`, no `minecraftArguments`, no `jar`, no `type`-specific extras. Everything the launcher needs to actually start the game comes from the parent.

Three details are worth pinning down:

- **`id` is not the parent's id, and there is no jar behind it.** The Fabric installer creates `versions/<profileName>/` and then explicitly `Files.deleteIfExists(profileJar)` **[PRACTICE]**; the ZIP form of the same endpoint writes a **zero-byte** `<profileName>.jar` entry **[DOC]**. The real client jar is found through the inheritance chain, not through this id. In Mojang's format that is what the `jar` field is for; Fabric does not set it, so it resolves to the root's id.
- **`arguments.game` is an empty array on purpose.** The source comment: *"I believe this is required to stop the launcher from complaining."* **[DOC]**
- **The only JVM argument Fabric adds is cosmetic.** `-DFabricMcEmu= net.minecraft.client.main.Main ` (note the leading and trailing spaces inside the value) exists *"to emulate vanilla MC presence for programs that check the process command line (discord, nvidia hybrid gpu, ..)"* **[DOC]**. The loader needs **no** other JVM or game argument. It is not a tweaker, not a javaagent, not a `-cp` manipulation. Getting `KnotClient` onto the classpath with its libraries is the whole job.

### Legacy Fabric's profile JSON is materially different

`meta.legacyfabric.net` runs a fork of the same service (`Legacy-Fabric/legacy-meta`). Live response for 1.8.9 + loader 0.19.3 **[PRACTICE]**:

```json
{
  "id": "fabric-loader-0.19.3-1.8.9",
  "inheritsFrom": "1.8.9",
  "releaseTime": "...", "time": "...", "type": "release",
  "mainClass": "net.fabricmc.loader.impl.launch.knot.KnotClient",
  "libraries": [ ... ]
}
```

**There is no `arguments` block at all.** In the fork's source the whole block — including `-DFabricMcEmu` — is commented out, above the comment *"Prevent pre-1.13 from launching in vanilla launcher for some reasons???"* **[DOC — Legacy Fabric meta source]**. So on 1.8.9 the merged version has `minecraftArguments` inherited from vanilla and no structured argument lists whatsoever.

**This is good news for `launch.rs`.** `game_entries` already prefers `arguments.game` and falls back to `minecraft_arguments`; `jvm_entries` already synthesises `-Djava.library.path` / `-cp` when `arguments.jvm` is empty. Both fallbacks fire correctly for a merged 1.8.9 + Legacy Fabric profile with no change. HMCL's `GameInstanceLibraryBuilder` records the rule that makes this safe: *"The official launcher will not parse the `arguments` property when it detects the presence of `mcArgs`."* **[PRACTICE]**

Legacy Fabric's fork also appends libraries that Fabric's does not (`ProfileHelper.enrichProfile`) **[DOC]**:

- If the parent version has any `org.ow2.asm:asm-all` library, it emits **a duplicate entry with the same coordinate and `"rules": [{"action": "disallow"}]`** — a negative entry whose only purpose is to make the launcher drop vanilla's ASM 4/5, which would otherwise collide with ASM 9.10.1. (1.8.9 has no `asm-all`, so this does not fire there; it fires on 1.12.2 and similar.)
- If the parent has any `org.lwjgl.lwjgl:lwjgl:2*`, it adds **three LWJGL 2 replacements** from `maven.legacyfabric.net`: `lwjgl`, `lwjgl_util` and `lwjgl-platform` at `2.9.4+legacyfabric.17`, the last with an `extract.exclude: ["META-INF/"]` and a full `natives` map. **These carry no rules**, so they are active on every platform.

That last point is not an optimisation. Legacy Fabric's LWJGL 2 fork exists to add arm64 natives, raise the macOS floor to 10.11 (x64) / 11.0 (arm64), fix High-DPI outside fullscreen, dispatch calls from the main thread, and *"Fix crash on Java 11+ due to missing symbols"* **[DOC — fork README]**. **On macOS, and on Apple Silicon in particular, taking Legacy Fabric's LWJGL instead of vanilla's is what makes 1.8.9 run at all.**

### The merge, reconstructed

No first-party spec exists (gap 1). Two independent implementations:

**HMCL implements `inheritsFrom` literally.** `GameInstanceManifest.merge(parent)` is a field-by-field record construction **[PRACTICE]**:

| Field | Rule |
| --- | --- |
| `id` | the **child's**, always |
| `inheritsFrom` | cleared |
| `mainClass`, `minecraftArguments`, `jar`, `assetIndex`, `assets`, `javaVersion`, `downloads`, `logging`, `type`, `time`, `releaseTime` | **child wins if non-null, else parent** |
| `arguments` | `Arguments.merge(parent, child)` → **concatenated, parent first** (both `game` and `jvm`) |
| `libraries` | `Lang.merge(child, parent)` → **concatenated, child first** |
| `compatibilityRules` | concatenated, **parent first** |
| `minimumLauncherVersion` | `max` |

Note the asymmetry: **arguments are parent-then-child, libraries are child-then-parent.** That is deliberate — Fabric's libraries must precede vanilla's on the classpath, and Fabric's arguments must come after vanilla's on the command line.

Concatenation alone is not enough, and HMCL knows it. `LaunchManifestNormalizer.uniqueLibraries` then deduplicates **[PRACTICE]**:

- Key is **`groupId:artifactId`** — version and classifier are *not* part of the key.
- **If the two entries' rules differ, keep both** (treated as platform-specific variants).
- If the rules are equal, **the higher version replaces the lower in place**, preserving position.
- Same id and version, and equal: prefer the entry with the longer JSON serialisation ("more metadata wins").
- Same id and version, not equal (a jar vs its natives): keep both.

And then a hard-coded special case, in full:

```java
if (analyzer.has(GameComponentType.LEGACY_FABRIC)) {
    // LegacyFabric adds higher-version ASM dependencies such as org.ow2.asm:asm,
    // which conflict with the original org.ow2.asm:asm-all and cause launch failure.
    repaired = repairDuplicateAsm(repaired, analyzer);
}
```

`repairDuplicateAsm` drops every `org.ow2.asm:asm-all` when any `org.ow2.asm:asm` is present **[PRACTICE]**. **That is a launcher working around Legacy Fabric's disallow-rule trick because the generic merge does not honour it** — the rules differ between vanilla's entry (none) and Legacy Fabric's (`disallow`), so `uniqueLibraries` keeps both and the vanilla one still evaluates to allowed.

**Prism does not implement `inheritsFrom` at all.** It has no code path for the field. Instead it models an instance as an ordered list of *components* and merges them with `LaunchProfile::applyLibrary` **[PRACTICE]**:

- `findLibraryByName` matches on `GradleSpecifier::matchName`, which is **`groupId` + `artifactId` + `classifier`**, ignoring version.
- No match → append. Match → **replace only if the new version compares higher**.
- Natives go into a separate list from ordinary libraries.
- Scalars use `applyString`: *"if `from` is empty, return; `to = from`"* — last non-empty wins.

### Does `inheritsFrom` nest?

**Yes.** HMCL's `DefaultGameRepositorySnapshot.resolve` recurses: it resolves the parent's manifest first, then merges, carrying a `resolvedSoFar` set for cycle detection. A cycle is not fatal — it logs *"Found circular dependency instances"* and breaks the chain by nulling `inheritsFrom` **[PRACTICE]**. A separate pass, `removeInvalidInstances`, walks the whole chain and removes the lot if any link is missing. So multi-level inheritance is supported by at least one shipping launcher, and defensive cycle handling is considered necessary in practice.

Fabric itself never produces a nested profile — `inheritsFrom` is always the vanilla version id **[DOC]**. But ash would be free to use nesting for its own layering if it wanted, and would have to guard against cycles if it accepted imported profiles.

### What this costs `version.rs`, `depot.rs` and `launch.rs`

Concretely:

- **`VersionMetadata` models neither `inheritsFrom` nor `jar`.** Both are needed. `jar` in particular is how the client jar is located once `id` stops naming a real jar.
- **`Library` has no `url` field, and Fabric libraries have no `downloads` block.** A Fabric library entry is `{"name": "net.fabricmc:fabric-loader:0.19.5", "url": "https://maven.fabricmc.net/"}` and nothing else. `depot.rs::plan` reads `library.downloads.artifact.{url,sha1,size,path}` and would silently skip every Fabric library. The Maven path must be constructed: `group.replace('.', '/') + "/" + artifact + "/" + version + "/" + artifact + "-" + version + [ "-" + classifier ] + ".jar"` — Fabric's installer builds exactly this, though its own `split(":", 3)` cannot handle a classifier at all **[PRACTICE]**.
- **`natives_for` reads `downloads.classifiers`, which Legacy Fabric's `lwjgl-platform` entry does not have.** It has a `natives` map and no downloads, so ash would extract no natives for it. Same Maven construction, with the classifier appended.
- **`select_libraries` deduplicates natives by `coordinate()` = `group:artifact:version`.** For 1.8.9 + Legacy Fabric that keeps *both* `org.lwjgl.lwjgl:lwjgl-platform:2.9.4-nightly-20150209` and `:2.9.4+legacyfabric.17` — two LWJGL 2 native sets unpacked into one directory, and two LWJGL 2 jars on the classpath. The merge must replace by **`group:artifact`** (plus classifier), child wins, *before* rules are evaluated. Done that way, Legacy Fabric's rule-less entry correctly displaces both vanilla LWJGL entries — the 2.9.4-nightly one (allow, disallow osx) and the 2.9.2-nightly one (allow osx only) — which is exactly what makes macOS work.
- **`rules_allow` is already correct** for Legacy Fabric's `disallow` trick — *provided* replacement happens by `group:artifact` first. That ordering is the whole of it: replace, then evaluate. HMCL gets this wrong and patches around it; ash can get it right once.
- **`natives::directory` keys on `metadata.id`.** After a merge that is `fabric-loader-0.19.3-1.8.9`, not `1.8.9`, so a Fabric instance and a vanilla instance on the same version target unpack separate native directories. That is probably correct (the native *sets differ* — Legacy Fabric's LWJGL is not vanilla's) but it is a depot-sharing consequence of ADR-0008 worth deciding deliberately rather than inheriting.
- **`classpath()` appends `versions/<id>/<id>.jar` last.** With a merged profile that path does not exist. It must come from the resolved `jar`/root id.

---

## 2. Fabric Meta, and whether a launcher can verify what it downloads

### What is served, at which endpoints

`meta.fabricmc.net`, v2 **[PRACTICE — all exercised live]**:

| Endpoint | Returns |
| --- | --- |
| `/v2/versions/game` | every game version Fabric knows, `{version, stable}`. 526 entries, 47 stable |
| `/v2/versions/loader` | every loader version, `{separator, build, maven, version, stable}`. Current stable **0.19.5** |
| `/v2/versions/loader/:game` | loader × intermediary × `launcherMeta` for one game version |
| `/v2/versions/loader/:game/:loader` | one such entry |
| `/v2/versions/loader/:game/:loader/profile/json` | **the generated profile JSON** |
| `/v2/versions/loader/:game/:loader/profile/zip` | the same, wrapped with a zero-byte jar |
| `/v2/versions/intermediary/:game` | `{maven, version, stable}`; `0.0.0` for unobfuscated versions |
| `/v2/versions/yarn/:game` | Yarn builds |

`meta.legacyfabric.net` serves the same shapes from a fork, plus `/v2/manifest/:version` (referenced by Legacy Fabric's example mod for versions the vanilla launcher does not list).

### Is the profile JSON generated per request? Yes.

Two cache-busted fetches four seconds apart **[PRACTICE]**:

```
releaseTime 2026-09-15T00:53:19+0000   time 2026-09-15T00:53:19+0000
releaseTime 2026-09-15T00:53:23+0000   time 2026-09-15T00:53:23+0000
```

And the source confirms it: `String currentTime = ISO_8601.format(new Date()); profile.addProperty("releaseTime", currentTime);` **[DOC]**. Responses carry `Cache-Control: public, max-age=86400` behind Cloudflare, so a plain fetch usually hits cache and *looks* stable for a day — which is a trap, not a guarantee.

**Consequence for `depot.rs`: `VersionSource { id, url, sha1 }` has nothing to put in `sha1` for this URL, and `resolve_metadata`'s offline fallback — which re-verifies the cached copy against the published hash before trusting it — has no hash to verify against.** Do not special-case it to "hash optional". Build the profile locally instead (below).

### Does Fabric publish hashes? Yes, more than you would expect.

Probed directly against `maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/` **[PRACTICE]**:

| File | Status | Value |
| --- | --- | --- |
| `fabric-loader-0.19.5.jar` | 200, 1 984 980 bytes | |
| `…jar.sha1` | 200 | `ff9e65cffca4a67f31523e1807fe0855940fcbfa` |
| `…jar.sha256` | 200 | `93044e4d…` |
| `…jar.sha512` | 200 | `11b55302…` |
| `…jar.md5` | 200 | `23bf6a8c…` |
| `…jar.asc` | 200, 878 bytes | PGP, BCPG v1.71, key `9E2B2198D20DC6E4B02C703111B891CFE51C003E`, `ci@fabricmc.net` |
| `…pom`, `…pom.sha1` | 200 | |
| `…0.19.5.json` | 200, 4 257 bytes | **the loader's own launcher metadata** |
| `maven-metadata.xml` + `.sha1`/`.sha512` | 200 | |

Downloaded the jar and hashed it: **sha1 and sha512 match their sidecars exactly.** The intermediary jar (`net/fabricmc/intermediary/1.21.11/`) has a `.sha1` sidecar but **no `.asc`**.

`maven.legacyfabric.net` 302-redirects to `repo.legacyfabric.net/legacyfabric/…` (a Reposilite instance). Following redirects, it publishes `.md5`/`.sha1`/`.sha256`/`.sha512` for `net.legacyfabric:intermediary:1.8.9`, for `org.lwjgl.lwjgl:lwjgl:2.9.4+legacyfabric.17`, and for the platform natives jars — sidecar verified against the download. **No `.asc` anywhere** **[PRACTICE]**.

So: **every artifact the loader needs is hash-verifiable from a versioned, immutable URL, on both meta services.** The gap is only the generated wrapper document.

### The pinnable document

`https://maven.fabricmc.net/net/fabricmc/fabric-loader/<version>/fabric-loader-<version>.json` is immutable per loader version and is *the same `launcherMeta` object* that the profile endpoint wraps. Its `libraries.common` entries carry `md5`, `sha1`, `sha256`, `sha512` and `size` inline for ASM 9.10.1 (×5) and `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7` **[PRACTICE]**. It also carries `version: 2`, `min_java_version: 8` and `mainClass`.

Interestingly, Fabric Meta's profile response **preserves those hashes** — the live 1.21.11 profile carries md5/sha1/sha256/sha512/size on every ASM and Mixin entry. Only the two entries Fabric Meta *adds* itself, `net.fabricmc:intermediary:<mc>` and `net.fabricmc:fabric-loader:<loader>`, are bare `{name, url}`, because `formatLibrary` writes only those two fields **[DOC]**. Legacy Fabric's fork is the same, plus its three bare LWJGL entries.

**So exactly two (Fabric) or five (Legacy Fabric) artifacts per instance lack an inline hash — and every one of them has a `.sha1` sidecar on Maven.**

The recommended shape for ash, which preserves the depot invariant end to end:

1. Pin `(game version, loader version)` in the instance. Both are ash's choice, not a "latest" lookup.
2. Fetch `…/fabric-loader-<loader>.json` — immutable, cacheable, and once fetched its own sha256 can be recorded in the instance and checked on every subsequent read, exactly as `resolve_metadata` does for Mojang metadata today.
3. Construct the library list locally from that document plus the two (or five) known coordinates, using the same ordering Fabric Meta uses: `libraries.common` → intermediary (only when the version is obfuscated) → loader → `libraries.client`.
4. For the coordinates with no inline hash, fetch the `.sha1` sidecar once and record it in the instance alongside the loader pin.
5. Merge against the Mojang version metadata ash already verifies.

This replaces a per-request generated document with a set of immutable, hashed inputs, and it makes the whole Fabric install offline-safe on the same terms as the vanilla one. It also means ash never needs `meta.fabricmc.net` at launch time at all — only when the player is *choosing* a loader version.

### What established launchers actually trust

**Prism verifies its metadata and not its artifacts.**

Its meta service `meta.prismlauncher.org` publishes a hash chain: `/v1/index.json` lists every package with a `sha256`; each package index lists every version with a `sha256`; each version file is immutable. I verified the chain for `net.fabricmc.fabric-loader/0.19.5.json` — the file's actual sha256 matches the index entry exactly **[PRACTICE]**. `Meta::BaseEntity` enforces it:

```cpp
if (m_mode == Net::Mode::Online && !m_entity->m_sha256.isEmpty() && !hashMatches) {
    throw Exception(QString("Checksum mismatch, expected sha256: %1, got: %2")...);
}
```

**But the version file it pins contains no artifact hashes at all** — Prism's meta *strips* the md5/sha1/sha256/sha512 that Fabric's own meta provides. Every library is `{"name": ..., "url": ...}`. And `Library::getDownloads` only attaches a validator when a sha1 is present:

```cpp
if (sha1.size()) {
    dl->addValidator(new Net::ChecksumValidator(QCryptographicHash::Sha1, sha1));
    ...
} else {
    out.append(Net::ApiRequest::makeCached(url, entry));   // no validator
}
```

The Fabric branch reaches the `else`: `add_download(raw_storage, raw_dl, QString())` **[PRACTICE]**. **Prism Launcher downloads Fabric Loader, intermediary, ASM and Mixin over TLS with no integrity check.**

**Fabric's own installer verifies nothing either.** `ClientInstaller` iterates the profile's libraries and calls `FabricService.downloadSubstitutedMaven(url, libraryFile)` with the comment *"Downloading the libraries isn't strictly necessary as the launcher will do it for us. Do it anyway in case the launcher fails."* The only `sha1String` comparison in the whole installer is in `MinecraftServerDownloader`, against Mojang's published hash for the server jar **[PRACTICE]**.

**Prism's offline handling is weaker than ash's.** In `Net::Mode::Offline`, `BaseEntityLoadTask` loads the local meta file and succeeds *without* comparing hashes — `wasLoadedOffline` short-circuits the check. Online, a mismatch throws *and deletes the file* (`FS::deletePath(fname)` with the comment *"just make sure it's gone and we never consider it again"*) **[PRACTICE]**. ash's `resolve_metadata` re-verifies the cached copy against the published hash even on the offline path, which is the stronger position; keep it.

### Availability

Fabric publishes fallbacks in code, with a warning against relying on them (gap 11). `FabricService` fails over round-robin through `{meta, maven}`, `{meta2, maven2}`, `{meta3, maven3}` on `IOException`, and remembers the last working index **[PRACTICE]**. Legacy Fabric publishes no fallback.

Design consequence: ash's loader-version *picker* needs the network; ash's *launch* must not. Pinning per §2 above gives you that for free.

---

## 3. Legacy Fabric for 1.8.9

### What it is and who runs it

The `Legacy-Fabric` GitHub organisation, 33 repositories **[PRACTICE]**. It backports the Fabric toolchain to 1.3.2–1.13.2 by publishing its own **intermediary mappings** (`net.legacyfabric:intermediary`), its own **Yarn** (`net.legacyfabric:yarn`, CC0-1.0), its own **Fabric API equivalent** (Legacy Fabric API, Apache-2.0), its own **Loom companion plugin** (`legacy-looming`, MIT), a **LWJGL 2 fork**, an **installer fork**, and a **meta service fork**. It uses upstream Fabric Loader unmodified — `net.fabricmc:fabric-loader` straight off `maven.fabricmc.net`.

### Maintenance cadence, measured

Commits per repository since 2025-09-01, and author concentration **[PRACTICE — GitHub API]**:

| Repository | Commits | Months active | Latest | Distinct authors | Top author's share |
| --- | --- | --- | --- | --- | --- |
| `fabric` (Legacy Fabric API) | 69 | 8 of 12 | 2026-09-09 | 1 | 69 / 69 |
| `lwjgl` | 92 | 2 of 12 | 2026-06-01 | 5 | 82 / 92 |
| `legacy-meta` | 41 | 6 of 12 | 2026-06-01 | 3 | 29 / 41 |
| `yarn` | 30 | 4 of 12 | 2026-02-05 | 3 | 26 / 30 |
| `legacy-looming` | 11 | 4 of 12 | 2025-12-03 (`dev/1.14`); 2026-05-28 (`dev/1.16`) | 2 | 9 / 11 |

The top author is the same person in every row: `thecatcore`. All-time the API repo has 43 contributors (including Fabric's own `asiekierka` and `modmuss50`), but **the last twelve months are effectively one maintainer**.

Release cadence is real: Legacy Fabric API 1.13.0 (2025-09-07) → 1.13.1 → 1.13.2 (2025-10-21) → 1.13.3 (2026-02-10) → 1.14.0 (2026-03-03) → 1.15.0 → 1.15.1 → 1.16.0 → 1.16.1 (2026-03-28). `legacy-looming` tracks upstream Loom minors with roughly a three-month lag: `dev/1.15` in Feb 2026, `dev/1.16` in May 2026.

### Current status for 1.8.9 specifically

**Supported and current.** `meta.legacyfabric.net/v2/versions/game` lists 76 versions including 1.8.9; `/v2/versions/loader/1.8.9` returns loader **0.19.3** (stable) against `net.legacyfabric:intermediary:1.8.9`; `/v2/versions/yarn/1.8.9` returns builds up to **1.8.9+build.604** (stable). Legacy Fabric API on Modrinth lists 1.8.9 among its game versions and is `updated 2026-09-09` **[PRACTICE]**. The exact API version to pin is unresolved — see gap 6.

### Where it diverges from Fabric proper

| | Fabric (1.21.11) | Legacy Fabric (1.8.9) |
| --- | --- | --- |
| Loader | `net.fabricmc:fabric-loader:0.19.5` | `net.fabricmc:fabric-loader:0.19.3` — **the same artifact, one patch behind** |
| Intermediary | `net.fabricmc:intermediary:1.21.11` | `net.legacyfabric:intermediary:1.8.9` (generation 1; a generation 2 exists) |
| Mappings | Yarn (CC0-1.0), deprecated | Legacy Yarn `1.8.9+build.604` (CC0-1.0), maintained |
| API | Fabric API, Apache-2.0, ~45 modules | Legacy Fabric API, Apache-2.0, **14 modules** |
| Meta | `meta.fabricmc.net` + 2 fallbacks | `meta.legacyfabric.net`, no fallback |
| Maven | `maven.fabricmc.net`, sidecars + `.asc` | `maven.legacyfabric.net` → `repo.legacyfabric.net`, sidecars, **no `.asc`** |
| Profile `arguments` | `{game: [], jvm: ["-DFabricMcEmu…"]}` | **absent entirely** |
| Extra libraries | none | LWJGL 2.9.4+legacyfabric.17 (×3), plus `disallow` entries for `asm-all` |
| Mixin | `net.fabricmc:sponge-mixin:0.17.4+mixin.0.8.7` | `net.fabricmc:sponge-mixin:0.17.3+mixin.0.8.7` |
| ASM | `org.ow2.asm:*:9.10.1` | `org.ow2.asm:*:9.10.1` — **identical** |

### Java

`min_java_version: 8` in the launcher metadata both meta services serve; Mojang's 1.8.9 metadata asks for `jre-legacy` / major 8 **[PRACTICE]**. **Provision Java 8 for the 1.8.9 target.** Newer Java may work (the LWJGL fork explicitly fixes a Java 11+ crash) but is unstated — gap 3.

For contrast, ash's three plausible targets want three different runtimes: 1.8.9 → Java 8 (`jre-legacy`), 1.21.11 → Java 21 (`java-runtime-delta`), 26.2 → Java 25 (`java-runtime-epsilon`) **[PRACTICE]**. `runtime.rs` already provisions per component, so this is data, not code.

### Mixin

**Same Mixin, same version, both targets.** Both meta services resolve to `net.fabricmc:sponge-mixin` at `+mixin.0.8.7` — upstream SpongePowered Mixin 0.8.7 — over identical ASM 9.10.1 **[PRACTICE]**. Mixin annotations, injection points, `@Redirect`/`@Inject`/`@ModifyVariable` semantics and mixin-config JSON are therefore the same on 1.8.9 as on 1.21.11. **ADR-0003's "one mixin and build model" claim holds up.**

The differences are not in Mixin. They are: `compatibilityLevel` must be `JAVA_8` on the legacy target rather than `JAVA_17`; refmaps are required on both (both are obfuscated) but would not be on 26.x; and the classes you are mixing into have entirely different names.

### Dormancy risk, quantified

The honest reading: **the project is alive and shipping, and it has a bus factor of one.**

What ash's exposure actually is, if it stopped tomorrow:

- **Launching is unaffected, permanently**, provided ash pins by version and fetches from Maven. Everything the 1.8.9 instance needs — loader 0.19.3, `net.legacyfabric:intermediary:1.8.9`, LWJGL 2.9.4+legacyfabric.17 — is an immutable Maven artifact with a published hash. Nothing regenerates. The only live dependency is the profile-JSON generator, and §2's recommendation removes it.
- **Building is affected within one or two Loom releases.** `legacy-looming` must be version-matched to Loom (*"Version must be the same as fabric-loom's"* **[DOC]**). If it stops tracking, ash freezes its 1.8.9 Loom at whatever is published (currently 1.16.1) and loses access to newer Loom features on that side. That is survivable for years, and it is survivable *independently* of the modern side only if the build is structured for it — see §4.
- **The real exposure is `repo.legacyfabric.net` disappearing.** It is a single self-hosted Reposilite behind Cloudflare with no published mirror. Everything ash needs from it is small: the intermediary jar (147 KB), three LWJGL jars and their natives (~5 MB total), the Legacy Fabric API jar, and Legacy Yarn if building. **Mirror these into ash's own build inputs and record their hashes.** That is a half-day of work and it converts a project-dormancy risk into a nothing.
- Contingency if the *toolchain* dies: Ornithe is the other legacy-era mapping/toolchain family (Legacy Fabric API's own Modrinth entry lists `ornithe` alongside `legacy-fabric` as a supported loader) **[PRACTICE]**. I did not evaluate it; it is a name to have, not a plan.

**ADR-0003's stated risk is real, correctly identified, and cheaply mitigated.** The mitigation is "pin and mirror", not "pick a different loader".

---

## 4. The build toolchain: Fabric Loom vs Legacy Looming

### The modern target

From `FabricMC/fabric-example-mod` at HEAD, which is maintained in lockstep with Loom **[DOC]**:

```properties
minecraft_version=26.2
loader_version=0.19.5
loom_version=1.17-SNAPSHOT
fabric_api_version=0.160.0+26.2
```

- Gradle **9.5.1** (`gradle-wrapper.properties`).
- `tasks.withType(JavaCompile) { it.options.release = 25 }`, `sourceCompatibility = targetCompatibility = VERSION_25`.
- `fabric.mod.json` declares `"depends": { "fabricloader": ">=0.19.5", "minecraft": "~26.2", "java": ">=25", "fabric-api": "*" }`.
- **No `mappings` line in the dependencies block at all** — because 26.2 is unobfuscated, there is nothing to map.
- Plugin repositories: `maven.fabricmc.net`, `mavenCentral()`, `gradlePluginPortal()`.
- Loom is **MIT**; its own repo is on branch `dev/1.17` with `v1.18.0-alpha.*` tags, last pushed 2026-09-05 **[PRACTICE]**.

**For 1.21.11 you would still need mappings**, and the choice has licensing consequences:

- **Yarn** — `FabricMC/yarn`, **CC0-1.0**, last pushed 2026-05-27, not archived but deprecated: *"we can't see a way to justify maintaining Yarn in its current state"* **[DOC]**. CC0 means no obligation of any kind.
- **Mojang mappings** — the `client_mappings` artifact in Mojang's per-version metadata. Its header, verbatim **[DOC]**: *"(c) 2020 Microsoft Corporation. These mappings are provided 'as-is' and you bear the risk of using them. You may copy and use the mappings for development purposes, **but you may not redistribute the mappings complete and unmodified**. Microsoft makes no warranties… Use and modification of this document or the source code (in any form) of Minecraft: Java Edition is governed by the Minecraft End User License Agreement."* Usable to build with; not shippable, and not committable to a public repo as a file.

Fabric's own recommendation is unambiguous: *"We recommend that all new mods should be created using the official Mojang mappings, this will provide an easier upgrade path in the future."* **[DOC]**

### The 1.8.9 target

From `Legacy-Fabric/fabric-example-mod` at HEAD **[DOC]**:

```gradle
plugins {
    id "net.fabricmc.fabric-loom-remap" version "${loom_version}"
    id "legacy-looming" version "${loom_version}"   // Version must be the same as fabric-loom's
}
dependencies {
    minecraft "com.mojang:minecraft:${project.minecraft_version}"
    mappings(legacy.yarn(project.minecraft_version, project.yarn_build))
    modImplementation "net.fabricmc:fabric-loader:${project.loader_version}"
    modImplementation "net.legacyfabric.legacy-fabric-api:legacy-fabric-api:${project.fabric_version}"
}
tasks.withType(JavaCompile).configureEach {
    if (JavaVersion.current().isJava9Compatible()) { it.options.release = 8 }
}
java { sourceCompatibility = JavaVersion.VERSION_1_8; targetCompatibility = JavaVersion.VERSION_1_8 }
```

```properties
minecraft_version=1.8.9
yarn_build=604
loader_version=0.18.3
loom_version=1.16-SNAPSHOT
fabric_version=1.13.2+1.8.9
```

Gradle **9.4.0**. Legacy Fabric's own CI builds with **Temurin JDK 21** **[PRACTICE]**.

Two things to notice:

- **`legacy-looming` is not a Loom fork.** It is a separate Gradle plugin applied *alongside* upstream Loom. Its own `build.gradle` depends on `net.fabricmc:fabric-loom:${baseVersion}-SNAPSHOT` and compiles at `release = 21` **[PRACTICE]**. So the "which Loom fork" framing in the question does not match reality: you use upstream Loom (specifically its `fabric-loom-remap` variant, the remapping-capable one produced by Loom's modularisation) plus a companion plugin that teaches it about legacy intermediaries, Legacy Yarn and legacy run configs.
- **The build JDK and the game JDK are completely different things.** You build 1.8.9 mods with a modern JDK (21 in Legacy Fabric's CI) emitting Java 8 bytecode via `--release 8`. Gradle 9 itself requires a modern JDK. Only the *game* needs Java 8.

### What breaks if you try to use current Loom for 1.8.9

`legacy-looming`'s branches run `dev/1.0` … `dev/1.16`; published versions top out at **1.16.1**. There is **no `dev/1.17`** and no `1.17.x` release. Upstream Loom is on `dev/1.17` with 1.18 alphas **[PRACTICE]**.

So the concrete answer is: **you cannot use Loom 1.17 on the 1.8.9 side today.** You pin Loom 1.16 there. ~~And that is the single hardest constraint on a one-build-two-targets design, because Gradle resolves the plugin classpath once per build.~~

**Corrected 2026-09-17, while implementing #21: it is not a constraint at all.** The second sentence does not follow from the first, because Loom's own modularisation — noted two paragraphs above — means the two sides apply *different artifacts*. `target-1.21.11` applies `fabric-loom` 1.18.2; `target-1.8.9` applies `net.fabricmc.fabric-loom-remap` 1.16.3 alongside `legacy-looming` 1.16.1. There is no version for Gradle to resolve between them, so both sit on one plugin classpath and one `./gradlew build` produces both jars. Verified by building both **[PRACTICE]**, and each project reports its own Loom at configure time:

```
> Configure project :target-1.8.9
Fabric Loom: 1.16.3
Legacy Looming: 1.16.1
> Configure project :target-1.21.11
Fabric Loom: 1.18.2
```

`legacy-looming` is still capped at 1.16.1, so the 1.8.9 side is still pinned back. What is gone is the claim that pinning it back costs the modern side anything.

### Can one Gradle multi-project build produce both target jars?

**Yes — this is a solved problem with a working reference implementation spanning exactly ash's range.**

**ReplayMod** builds 1.7.10 through 26.2 from one repository, one Gradle build **[PRACTICE]**. Its `versions/` directory holds 37 per-version project directories including `1.8.9`, `1.21.11`, `26.1` and `26.2`. Its `settings.gradle.kts` applies `gg.essential.multi-version.root` version 0.7.2; `root.gradle.kts` adds `gg.essential.loom` 1.15.48 (Essential's fork of architectury-loom) and declares a **version graph**:

```kotlin
preprocess {
    strictExtraMappings.set(true)
    val mc26_02_00 = createNode("26.2", 26_02_00, "yarn")
    val mc26_01_00 = createNode("26.1", 26_01_00, "yarn")
    val mc12111    = createNode("1.21.11", 12111, "yarn")
    ...
    val mc10809    = createNode("1.8.9", 10809, "srg")
    mc26_02_00.link(mc26_01_00, file("versions/mapping-fabric-26.2-26.1.txt"))
    mc26_01_00.link(mc12111,    file("versions/mapping-fabric-26.1-1.21.11.txt"))
    ...
    mc10904.link(mc10809, file("versions/mapping-forge-1.9.4-1.8.9.txt"))
}
```

Source is written once against one "core" version and mechanically transformed down the chain by the **ReplayMod preprocessor**, which is a JCP-style comment preprocessor **[DOC]**:

```java
//#if MC>=11200
category.addDetail(name, callable::call);
//#else
//$$ category.setDetail(name, callable::call);
//#endif
```

plus per-version *overwrite files* — *"If entire files are very version specific, they may be overwritten for any version by placing a new file with the same package and name in `versions/$MCVERSION/src/main/java`… This also has the huge advantage that the file may be edited with full IDE support"* **[DOC]** — plus the `mapping-*.txt` files, which are literal rename tables (the 26.1→1.21.11 one is the Yarn-to-Mojmap translation).

One caution: **ReplayMod's 1.8.9 node is `srg` (Forge), not Legacy Fabric.** Nobody I found builds 1.8.9-via-Legacy-Fabric and modern-via-Fabric from one build, so ash would be combining two proven halves rather than copying one proven whole.

### The concrete approaches, and their trade-offs

| Approach | What it is | Evidence | Trade-off |
| --- | --- | --- | --- |
| **essential-gradle-toolkit + ReplayMod preprocessor** | architectury-loom + comment preprocessing + per-version overwrites + rename tables. `gg.essential.multi-version.{root,}` | Used by ReplayMod (1018★, 37 versions, pushed 2026-07-12) and Essential Mod (a 1.8.9-and-modern PvP-adjacent client with cosmetics). Toolkit GPL-3.0, pushed 2026-06-08 **[PRACTICE]** | Highest proven range. Sources become preprocessor-annotated, which hurts readability and review. Puts you on architectury-loom rather than upstream Loom, which sidesteps the `legacy-looming` version pin — but I did not verify architectury-loom's 1.8.9-via-Legacy-Fabric support |
| **Stonecutter** | `//? if` comment conditions, modern Gradle plugin | LGPL-3.0, 99★; GitHub repo is now a mirror, upstream moved to Codeberg, mirror last pushed 2025-12-25 **[PRACTICE]** | Cleaner syntax than the preprocessor. Migration off GitHub means its current health is unverified (gap 9) |
| **Architectury Loom + common/platform source sets** | Compile a common module plus per-platform modules | MIT, 150★, pushed 2026-09-12 **[PRACTICE]** | Designed for Fabric/Forge/NeoForge at *one* MC version, not for spanning MC versions. Does not solve the 1.8.9-vs-26.x problem by itself |
| **Plain Gradle subprojects + a Minecraft-free shared module** | Ordinary composite build; the shared module compiles against no game API | No tooling to adopt | Simplest and most reviewable. The shared module can hold much less than you would hope — see below |

### Is a shared, version-agnostic module realistic?

**Yes, and it is the right shape for ash — but be honest about what fits in it.** The constraint is absolute: *the same game class has a different name on each target.* On 1.8.9 you write Legacy Yarn names; on 1.21.11 you write Yarn or Mojmap names; on 26.x you write Mojmap names that are now the game's actual names. Anything in the shared module that references `net.minecraft.*` cannot compile against both.

A second, quieter constraint: **the shared module must emit Java 8 bytecode** if the 1.8.9 module consumes it. No records, no sealed types, no switch expressions, no `var` in lambda params, no `List.of`.

What can genuinely live there:

- Feature state machines — toggle-sprint's latch, freelook's engaged/released state, hit-indicator decay timers.
- Settings: the model, defaults, validation, serialisation. This is also what ADR-0011's machine-local-override split and the synced-settings blob need, so it is shared with the launcher's vocabulary.
- Pure presentation maths: crosshair geometry, hit-colour interpolation, ping and FPS smoothing and bucketing, HUD anchoring and layout arithmetic.
- The cosmetics protocol and its client, once Phase 4 exists.
- **The version-abstraction seam itself**, as a set of Java interfaces.

What cannot: anything that reads a player, renders, reads a key, or touches a registry.

**This is where Phase 1's ports-and-fakes pattern translates, and it translates cleanly.** Define narrow ports in the shared module — a `HudSurface` (draw a textured quad, draw text, measure text), an `InputSource` (is this binding down, was it pressed this tick), a `PlayerStats` (latency, fps, health), a `CameraControl` (get and set pitch/yaw). Implement each per target in the version modules. Write every feature against the ports. Then the features are plain JUnit-testable against fakes with no loader, no game, no Minecraft on the test classpath — the same bargain `launch.rs` already makes by being a pure function of metadata plus session. The version modules stay thin enough to be reviewed by eye, which is the only verification they will get.

This is exactly what ADR-0005 asks for, and §5's testing section explains why there is no second option.

### CI

Both Fabric and Legacy Fabric ship the same workflow shape, **Linux only** **[PRACTICE]**:

```yaml
runs-on: ubuntu-24.04        # Legacy Fabric: ubuntu-latest
steps:
  - uses: actions/checkout@v6
  - uses: gradle/actions/wrapper-validation@v6
  - uses: actions/setup-java@v5
    with: { java-version: '25', distribution: 'microsoft' }   # Legacy Fabric: 21 / temurin
  - run: ./gradlew build
  - uses: actions/upload-artifact@v7
    with: { name: Artifacts, path: build/libs/ }
```

`./gradlew build` is pure Java and runs on `windows-latest` and `macos-latest` unchanged. The thing that does **not** port is client game tests: Loom's `ClientProductionRunTask` exposes `useXVFB`, documented as *"useful for headless CI environments. Defaults to true only on Linux and when the 'CI' environment variable is set"* **[DOC]**. There is no equivalent for Windows or macOS runners. So headless in-game testing is a Linux-runner capability; Windows and macOS runners give you compilation and unit tests.

**Licensing and auth in the toolchain:** Loom MIT; `legacy-looming` MIT; Yarn and Legacy Yarn CC0-1.0; Fabric API and Legacy Fabric API Apache-2.0; Fabric Loader Apache-2.0. **No authentication is required anywhere** — every repository (`maven.fabricmc.net`, `maven.legacyfabric.net`, `gradlePluginPortal`, `mavenCentral`) is anonymous. The two things to watch: Mojang mappings must not be redistributed, and `fabricApi { configureTests { eula = true } }` is a literal EULA acceptance embedded in a build script — *"By setting this to true, you agree to the Minecraft EULA"* **[DOC]**. That is a real acceptance by whoever runs CI, and it belongs in a deliberate decision rather than a copied template.

---

## 5. Mixin and the version-abstraction seam

### How Mixin actually works here

**Where configs live.** A mixin config is a JSON file at the jar root, declared in `fabric.mod.json`'s `"mixins"` array, which accepts either a bare filename or an object with an environment filter **[DOC — Fabric's example mod]**:

```json
"mixins": [
  "modid.mixins.json",
  { "config": "modid.client.mixins.json", "environment": "client" }
]
```

**What a config contains**, taken from Sodium 0.8.14's shipped `sodium-common.mixins.json` **[PRACTICE]**:

```json
{
  "package": "net.caffeinemc.mods.sodium.mixin",
  "required": true,
  "compatibilityLevel": "JAVA_17",
  "plugin": "net.caffeinemc.mods.sodium.mixin.SodiumMixinPlugin",
  "injectors": { "defaultRequire": 1 },
  "overwrites": { "conformVisibility": true, "requireAnnotations": true },
  "client": [ "core.MinecraftMixin", "core.render.world.LevelRendererMixin", ... ]
}
```

Mixin class names are relative to `package`; `mixins` / `client` / `server` split by side.

**What happens when a target does not exist at runtime.** From `MixinConfig.prepareMixins` **[DOC — Mixin source]**:

```java
for (MixinInfo mixin : this.pendingMixins) {
    try {
        mixin.parseTargets();
        if (mixin.getTargetClasses().size() > 0) { ... this.mixins.add(mixin); }
    } catch (InvalidMixinException ex) {
        if (this.required) { throw ex; }
        this.logger.error(ex.getMessage(), ex);
    }
}
```

So: **a missing target class is fatal if the config sets `"required": true`, and silently skipped otherwise** — logged, and the feature simply does not exist for that player. `MixinConfig` documents `required` as *"Determines whether failures in this mixin config are considered terminal errors. Use this setting to indicate that failing to apply a mixin in this config is a critical error and should cause the game to shutdown."* **[DOC]** It can be globally defeated with `-Dmixin.ignoreRequired=true` (`Option.IGNORE_REQUIRED`) **[DOC]**.

Separately, an injector whose injection point matches zero times is governed by `require` / `injectors.defaultRequire`: *"Setting this value to 1 essentially makes all injectors in the config automatically required. Individual injectors can still be marked optional by explicitly setting their `require` value to 0."* **[DOC]**

For conditional application there is the `plugin` hook — an `IMixinConfigPlugin` whose `shouldApplyMixin` decides per mixin at load time. **Both Sodium and Lithium use one** (`SodiumMixinPlugin`, `LithiumMixinPlugin`). That is the mechanism for "apply this hook only when mod X is absent", and it is what ash will want for graceful degradation rather than `required: false` plus a shrug.

### Refmaps, and why they are painful

A compiled mixin refers to game members by the names you wrote at development time — Yarn or Mojmap. At runtime on an obfuscated version, those members are named by **intermediary**. A **refmap** is the generated translation table shipped inside the jar so Mixin can rewrite the references at load.

Loom exposes two knobs **[DOC — Fabric Loom options reference]**:

```gradle
loom {
  mixin {
    // When disabled tiny remapper will be used to remap Mixins instead of the AP.
    // (Disabled by default in Loom 1.12+)
    useLegacyMixinAp = true
    defaultRefmapName = "example.refmap.json"
  }
}
```

The painful parts, in practice: the refmap is generated by an annotation processor, so it breaks when annotation processing is misconfigured, when a mixin lives in a source set the AP does not see, when Kotlin is involved, or when the same mixin targets members that differ between versions. Symptoms are runtime-only and read as "the injector matched nothing".

**The clean statement of when you need one comes from ReplayMod's build script** **[PRACTICE]**:

```kotlin
if (!platform.isUnobfuscated) {
    loom.mixin.useLegacyMixinAp = true
    loom.mixin.defaultRefmapName.set("mixins.replaymod.refmap.json")
}
```

**Refmaps exist only because of obfuscation.** Both of ash's current targets (1.8.9 and 1.21.11) are obfuscated, so both need refmaps. On 26.1+ the whole mechanism disappears — Sodium's and Lithium's shipped configs already carry no `refmap` key at all **[PRACTICE]**. That is another entry on the ledger for §1 of the Summary: moving the modern target to 26.x deletes an entire class of build failure.

### The state of the art for testing a Fabric mod

There are three tiers, all first-party **[DOC — Fabric's "Automated Testing" documentation]**:

1. **Unit tests via Fabric Loader JUnit.** *"Since Minecraft modding relies on runtime byte-code modification tools such as Mixin, simply adding and using JUnit normally would not work. That's why Fabric provides Fabric Loader JUnit, a JUnit plugin that enables unit testing in Minecraft."* Tests live in `src/test/java` and run on every `./gradlew build`, including CI. Classes that touch registries need a `beforeAll` that initialises the game far enough for registries to exist.
2. **Game tests** — Minecraft's own server-side Gametest framework.
3. **Client game tests** — Fabric's own client test framework (`fabric-client-gametest-api-v1`), wired by Loom:

```gradle
fabricApi {
  configureTests {
    createSourceSet = true
    modId = "example-mod-test"
    enableGameTests = true
    enableClientGameTests = true
    eula = true
  }
}
tasks.register("runProductionClientGameTest", net.fabricmc.loom.task.prod.ClientProductionRunTask) {
    jvmArgs.add("-Dfabric.client.gametest")
    useXVFB = true
}
```

**Can meaningful logic be unit-tested without launching the game? Only the logic that does not touch the game.** Fabric Loader JUnit lets you *load* the loader inside a JUnit JVM, which gets you registries and entrypoints; it does not get you a window, a render pass, a HUD or an input device. There is no headless harness for rendering, and none for Windows or macOS CI at all (§4).

**And it needs a game jar on the class path, which rules it out for a module that has none.** Established 2026-09-16 while implementing #20: the listener constructs `Knot` and `Knot.init` requires at least one game provider. `fabric.skipMcProvider` disables the embedded Minecraft one and Knot then fails outright with `No game providers present on the class path!` **[PRACTICE]**. So this tier belongs to the per-target modules; the shared module's tests are plain JUnit, which is all its content needs.

**So Phase 1's fakes-and-ports approach translates — but only if you build the seam first.** This is not a stylistic preference; it is the only path to a testable client. The shape:

- Features live in the shared module and depend only on ports (`HudSurface`, `InputSource`, `PlayerStats`, `CameraControl`).
- Ports are implemented per version module, thinly — each method should be one or two lines over the game API, because nothing will test them but a human.
- Feature logic gets plain JUnit tests against fakes, with no Minecraft on the test classpath and no loader needed.
- Fabric Loader JUnit covers the small amount of shared code that genuinely needs the loader.
- Client game tests, on a Linux runner, cover "does the HUD element actually appear" — as smoke tests, not as the primary suite.

That is the same bargain the launcher already makes, and it is the reason §4's recommendation for a shared module is an architectural requirement rather than a convenience.

### Injection points for the ADR-0006 features

What is verified, by target. **"Event"** means Fabric API or Legacy Fabric API exposes a supported hook and no mixin is needed; **"mixin"** means you are on your own against a vanilla class.

| Feature | 1.21.x (Fabric API) | 1.8.9 (Legacy Fabric API) |
| --- | --- | --- |
| **Custom crosshair** | **Event.** `HudElementRegistry.replaceElement(VanillaHudElements.CROSSHAIR, replacer)` — replace the vanilla element in place, keeping its identifier and its render condition **[PRACTICE — Fabric API source]** | **Mixin.** `HudRenderCallback` is the only HUD hook and it is **additive only** — it cannot suppress or replace a vanilla element **[PRACTICE]** |
| **Hit indicators** | **Event.** `HudElementRegistry.attachElementAfter(VanillaHudElements.CROSSHAIR, id, element)`. Damage-source signal needs a separate hook | **Event (draw) + mixin (signal).** `HudRenderCallback` for the draw |
| **Ping and FPS readouts** | **Event (draw).** `HudElementRegistry.addLast` / `attachElementAfter`. The *values* come from the game, and reading them is a port, not a hook | **Event (draw).** `HudRenderCallback` |
| **Hit colour** | **Mixin.** No Fabric API event covers the damage-flash overlay. `EntityRendererRegistry`, `FeatureRendererRegistry`, `LivingEntityFeatureRenderEvents` and `LivingEntityRenderLayerRegistrationCallback` exist but none of them alters the overlay **[PRACTICE]** | **Mixin.** Same; Legacy Fabric API has `EntityRendererRegistry` and `LivingEntityFeatureRendererRegistrationCallback` and nothing closer |
| **Toggle sprint** | **Event (binding) + mixin (enforcement).** `KeyMappingHelper` in `fabric-key-mapping-api-v1` registers the binding; holding the sprint state across ticks needs `ClientTickEvents.END_CLIENT_TICK` plus a mixin on the vanilla sprint input | **Event (binding) + mixin.** `KeyBindingHelper` in `legacy-fabric-keybindings-api-v1`; `ClientTickEvents` **[PRACTICE]** |
| **Freelook** | **Mixin.** No camera event exists in Fabric API | **Mixin.** None in Legacy Fabric API either |

**Where the two targets diverge most: the HUD.** Modern Fabric API gives a fine-grained, ordered, *named* element registry — 24 vanilla element identifiers from `MISC_OVERLAYS` through `SUBTITLES`, with `addFirst`, `addLast`, `attachElementBefore`, `attachElementAfter`, `removeElement` and `replaceElement` **[PRACTICE]**. Legacy Fabric API gives **one callback**, `HudRenderCallback`, that fires once after the vanilla HUD and can only draw on top. There is no identifier, no ordering, no replacement, no removal.

That single difference determines the shape of the seam. `HudSurface` must be expressible as "draw these primitives at this position", not as "replace element X" — because the 1.8.9 side cannot honour the second. The custom crosshair therefore becomes "suppress the vanilla crosshair (by whatever means the target allows) and draw ours", with the suppression living entirely inside the version module.

Also note the *class names* diverge in the seam's own API surface: `net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper` versus `net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper`. Fabric's module was renamed from `fabric-key-binding-api-v1` to `fabric-key-mapping-api-v1` in the Mojmap migration **[PRACTICE]**. The shared module must not name either.

### Which hooks ADR-0004's no-Sodium-mixin rule actually rules out

ADR-0004 forbids any ash mixin targeting a Sodium or Lithium class. Reading Sodium 0.8.14's own mixin manifest, here is what that costs on the modern target **[PRACTICE]**:

**Ruled out — ash cannot mixin `net.caffeinemc.mods.sodium.*`, so it cannot:**

- Add an ash section to Sodium's options screen (Sodium owns it via `features.gui.hooks.settings.OptionsScreenMixin`).
- Add ash entries to the F3 debug overlay when Sodium is present (Sodium owns it via `features.gui.hooks.debug.DebugScreenOverlayInsertMixin`, `DebugScreenEntryListMixin`, `DebugEntryMemoryMixin`).
- Hook Sodium's in-game console/toast surface (`features.gui.hooks.console.GameRendererMixin`).
- Touch chunk building, terrain draw, the frustum, or the sky/cloud renderers — all Sodium-owned (`core.render.world.LevelRendererMixin`, `features.render.world.sky.LevelRendererMixin`, `CloudRendererMixin`, `core.render.frustum.FrustumMixin`, `core.render.world.ChunkSectionsToRenderMixin`).
- Add a render pass to Sodium's pipeline, or read its visibility/occlusion results.

**Not ruled out by the licence, but a live conflict risk that ADR-0004 does not name.** Sodium's configs are `"required": true` with `injectors.defaultRequire: 1` and `overwrites: {conformVisibility: true, requireAnnotations: true}` — so a failure to apply is a crash, not a degradation. And Sodium mixes into vanilla classes that sit squarely in the territory ADR-0004 assigns to ash:

| ash's claimed territory | Sodium mixin that is already there |
| --- | --- |
| Text rendering | `features.render.gui.font.BakedGlyphMixin` |
| HUD / GUI graphics | `features.textures.animations.tracking.GuiGraphicsMixin` |
| Entity rendering | `features.render.entity.cull.EntityRendererMixin`, `ModelPartMixin`, `CubeMixin`, `core.render.world.EntityRendererAccessor` |
| Entity shadows | `features.render.entity.shadows.ShadowFeatureRendererMixin` |
| Particles | `features.render.particle.QuadParticleRenderStateMixin`, `core.render.world.ParticleFeatureRendererMixin`, `features.textures.animations.tracking.TextureSheetParticleMixin` |
| Immediate-mode draws (everything HUD and entity goes through) | `core.render.immediate.consumer.*`, `features.render.immediate.buffer_builder.*`, `MultiBufferSourceMixin`, `VertexConsumerMixin` |
| Client tick | Lithium: `experimental.client_tick.*` (12 client mixins) |

**ADR-0004's consequence "ash's own work takes entity rendering, HUD and text rendering, particles, and client-side tick — the hotspots neither covers" is not supported by the evidence.** The correct statement is narrower: *Sodium owns terrain and the immediate-mode buffer implementation; ash must coexist with it in entity, HUD, text and particle rendering, which means every ash mixin in those areas needs an explicit compatibility test against the pinned Sodium build, and a `MixinConfigPlugin` so that ash degrades rather than crashes.*

On the **1.8.9 target** the rule costs nothing: no Sodium, no Lithium, no competitor. The whole pipeline is ash's, as ADR-0004 says.

---

## 6. The EULA boundary on distributing a client

Sources: the [Minecraft EULA](https://www.minecraft.net/en-us/eula) and [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines), both fetched 2026-09-14. Neither carries a revision date (0001's gap 11 stands).

### The mods clauses are unchanged since 2026-09-09

Read verbatim from the live page and compared word for word against 0001 §7. **No change.** In full **[DOC]**:

> If you've bought Minecraft: Java Edition, you may play around with it and modify it by adding modifications, tools, or plugins, which we will refer to collectively as "Mods." By "Mods," we mean something original that you or someone else created that doesn't contain a substantial part of our copyrightable code or content. When you combine your Mod with Minecraft: Java Edition, we will call that combination a "Modded Version" of the game. We have the final say on what constitutes a Mod and what doesn't. **You may not distribute any Modded Versions of our game or software**, and we'd appreciate it if you didn't use Mods for griefing. Basically, Mods are okay to distribute; hacked versions or Modded Versions of the game client or server software are not okay to distribute.
>
> Any Mods you create for Minecraft: Java Edition from scratch belong to you (including pre-run Mods and in-memory Mods) and you can do whatever you want with them, **as long as you don't sell them for money / try to make money from them** and so long as you don't distribute Modded Versions of the game.

> **In order to ensure the integrity of our games, we need all game downloads and updates to come from a source that we authorize.** It's also important for us that 3rd party tools/services don't seem "official" as we can't guarantee their quality.

And the Usage Guidelines' "Extended functionality and modifications" section is likewise identical to 0001's quote, including all three bullets.

### Where the line sits, case by case

0001 §7's conclusion holds and can now be made precise:

| Action | Permitted? | Why |
| --- | --- | --- |
| **Shipping ash's own mod jar inside the installer** | **Yes.** ash's client is "something original… that doesn't contain a substantial part of our copyrightable code or content" — a Mod. "Mods are okay to distribute." The jar contains ash's classes and its mixin configs; it contains no Mojang bytecode | The one constraint is the money clause, which is about the *Mod*, not the *launcher* — see below |
| **The launcher downloading it at runtime** | **Yes.** No clause distinguishes bundled from downloaded. Both are distributing the Mod | |
| **The launcher writing it into the instance's game directory** | **Yes.** This *is* the assembly-on-the-player's-machine pattern the EULA permits. The result is a Modded Version that exists only on that machine and is never distributed | |
| **The launcher downloading Fabric Loader and its libraries** | **Yes.** These are third-party software under Apache-2.0 and BSD-style licences, not Mojang game files. The "source that we authorize" clause is scoped to *"all game downloads and updates"*, which is the client jar, libraries in Mojang's manifest, assets and runtimes — and ash already takes all of those from Mojang's own CDN | |
| **Shipping a pre-merged modded client jar** | **No.** "You may not distribute any Modded Versions of our game or software." Flat | Note Fabric's profile ZIP ships a **zero-byte** jar for exactly this reason |
| **Mirroring client.jar, Mojang libraries, assets or runtimes on ash infrastructure** | **No.** The authorized-source clause. 0001 action item 9 already says this; it extends unchanged to Phase 2 | |
| **Mirroring Fabric/Legacy Fabric artifacts on ash infrastructure** | **Yes**, as far as Mojang is concerned — they are not Mojang's. Subject to each artifact's own licence (Apache-2.0 for the loader and both APIs, CC0-1.0 for the mappings) | §3 recommends exactly this for the Legacy Fabric artifacts |

One nuance worth flagging for ADR-0010's benefit: **the money clause attaches to the Mod, not to the launcher.** "Any Mods you create… you can do whatever you want with them, as long as you don't sell them for money / try to make money from them." A launcher is a "tool" under the summary bullet — *"You may develop tools, plug-ins and services as long as they do not seem official or approved by us"* — and the money clause is not restated there. Whether that distinction survives contact with Mojang is not something the text answers, but it is the seam the "charge for launcher/desktop features" option in 0001's action item 2 would have to sit on.

### Cosmetics the Minecraft server never sees

**The Usage Guidelines now speak to this directly, and the news is not good for capes.**

The "Servers and hosting" section, as served 2026-09-14, lists among permitted monetisation **[DOC]**:

> You may make money by charging for access to your server by:
> …
> - Selling entitlements that affect gameplay provided they don't ruin other players' experience or give a competitive advantage in the game
> - **Selling cosmetics, except for capes or anything that attempts to visually act like the feature of a Minecraft player cape**
> - …

and, in the access conditions immediately above:

> Access to your server:
> - Must only be granted to users who have a genuine paid-for version of Minecraft
> - **Can't be limited to or controlled, directly or indirectly, by a player owning or having access to out-of-game content, products, or services**

Three things follow, and they pull in different directions:

1. **Selling cosmetics is not categorically forbidden.** 0001 §8 concluded *"There is no permitted category for 'sell digital cosmetics that a client-side mod unlocks'"* and *"paid perks that only the payer can use are exactly what is disallowed, even on servers."* **Neither statement is true of the current text.** Server operators may sell cosmetics and may sell gameplay entitlements. That materially weakens 0001's fairness-principle argument, which was built on the donations bullet alone.
2. **Capes are called out by name as the exception.** In the one place Mojang blesses selling cosmetics, it excludes *"capes or anything that attempts to visually act like the feature of a Minecraft player cape."* ash's headline cosmetic is a cape. This is no longer an inference from a general principle — it is a specific prohibition on a specific item, written down.
3. **None of it is directly on point, because this is the server section.** ash's cosmetics are client-side and the Minecraft server never sees them, so ash is not a server operator selling cosmetics. The clauses that *do* govern ash are the mod bullets, which are unchanged, and the EULA's "don't sell them for money" — both as 0001 read them.

The honest summary: **the ground under ADR-0010 has shifted, in both directions, and the net effect is worse for ash specifically.** The general principle 0001 relied on (no paid perks, ever) is weaker than stated. But the specific item ash plans to sell is now named as off-limits in the closest analogous context Mojang publishes. A lawyer reading these two facts together will land somewhere more precise than either 0001 or this document can.

### Anything else new since 2026-09-09

Nothing else material. The "essential guidelines" bullets, the required disclaimer (*"NOT AN OFFICIAL MINECRAFT [PRODUCT/SERVICE/EVENT/etc.]. NOT APPROVED BY OR ASSOCIATED WITH MOJANG OR MICROSOFT"*), the naming guidelines with their worked examples, the definition of commercial use (*"any uses of our name, brand, or assets that you use and share with others (regardless of whether you receive payment or provide it for free)"*), and the catch-all (*"If something isn't covered by these guidelines and we haven't otherwise said it's okay, that probably means we don't want you to do it"*) are all present and unchanged **[DOC]**. Whether the server-monetisation bullets themselves are new is unresolved — gap 2.

---

## 7. Sodium and Lithium as bundled mods

### Current versions

**[PRACTICE — Modrinth API and GitHub releases, 2026-09-14]**

| | 1.21.11 | 26.2 | Earliest supported |
| --- | --- | --- | --- |
| **Sodium** | `0.8.14+mc1.21.11`, released 2026-08-28 | `0.9.2+mc26.2`, released 2026-09-11 | **1.16.3** |
| **Lithium** | `0.21.4+mc1.21.11`, released 2026-03-11 | `0.25.3+mc26.2`, released 2026-07-29 | **1.16.2** |

Sodium 0.9.x for 26.2 adds *"early support for Vulkan"* per the project's own Modrinth description **[PRACTICE]**.

### Licences — the correction

**Sodium: PolyForm Shield License 1.0.0.** Not LGPL. Verified four ways: `LICENSE.md` on branch `dev`, on tag `mc1.21.11-0.8.14`, and on tag `mc26.2-0.9.2` all begin `# PolyForm Shield License 1.0.0`; the shipped jar's `fabric.mod.json` says `"license": "Polyform-Shield-1.0.0"`; Modrinth reports `LicenseRef-Polyform-Shield-1.0.0`; and the README states *"the content of this repository is provided under the Polyform Shield 1.0.0 license by JellySquid"* **[PRACTICE]**. The repo's `LICENSE.txt` history shows a "Split license files into GPL and LGPL parts" commit in Dec 2023 and a "Update information about granting licenses" commit on `LICENSE.md` in April 2024; the current state is PolyForm on every tag ash would ship.

**The clauses that matter [DOC — PolyForm Shield 1.0.0]:**

> **Distribution License.** The licensor grants you an additional copyright license to distribute copies of the software.

> **Notices.** You must ensure that anyone who gets a copy of any part of the software from you also gets a copy of these terms or the URL for them above, as well as copies of any plain-text lines beginning with `Required Notice:` that the licensor provided with the software.

> **Noncompete.** Any purpose is a permitted purpose, except for providing any product that competes with the software or any product the licensor or any of its affiliates provides using the software.

> **Competition.** Goods and services compete even when they provide functionality through different kinds of interfaces or for different technical platforms. Applications can compete with services, libraries with plugins, frameworks with development tools, and so on, even if they're written in different programming languages or for different computer architectures. Goods and services compete even when provided free of charge. If you market a product as a practical substitute for the software or another product, it definitely competes.

> **New Products.** If you are using the software to provide a product that does not compete, but the licensor or any of its affiliates brings your product into competition by providing a new version of the software or another product using the software, you may continue using versions of the software available under these terms beforehand to provide your competing product, but not any later versions.

> **Violations.** The first time you are notified in writing that you have violated any of these terms… your licenses can nonetheless continue if you come into full compliance… within 32 days of receiving notice. Otherwise, all your licenses end immediately.

**What this means for ash, stated plainly and not as legal advice:**

- **Distribution is explicitly granted.** Bundling the unmodified jar is fine on that axis, and the Notices obligation is already satisfied because Sodium ships `LICENSE.md` inside its own jar. I found no `Required Notice:` line in the repository.
- **The Noncompete clause is the problem, and ADR-0004's design does not avoid it.** ADR-0004's plan — "ash's own render and tick work therefore targets territory they do not cover" — is a plan to build a rendering optimisation that ships alongside Sodium. The Competition clause says goods compete *"even when they provide functionality through different kinds of interfaces"* and *"even when provided free of charge"*. Whether an ash client that ships Sodium and also does its own render work "competes with" Sodium is a judgement call that the licence text pushes toward yes rather than no.
- **The keep-it-at-arm's-length architecture does not help here.** LGPL is triggered by linking and derivation, so keeping Sodium as a separate unmodified jar solves it. PolyForm's Noncompete is triggered by *what you build and sell*, not by how you link. Refactoring cannot cure it.
- **The New Products clause is a specific, dated risk.** If CaffeineMC ships something that brings ash into competition, ash may continue on the versions available *beforehand* but not later ones. That is a pinned-forever Sodium, silently, with no notice.
- **There is a 32-day cure window on first written notice**, which is meaningfully better than immediate termination.

**Lithium: LGPL-3.0-only.** Verified: `LICENSE.md` on `CaffeineMC/lithium` (`develop`) is the GNU LGPL v3 text; the repo's GitHub licence field says LGPL-3.0; the shipped jar says `"license": "LGPL-3.0-only"` **[PRACTICE]**. ADR-0004's LGPL analysis applies to Lithium and only to Lithium.

**What LGPL-3.0 actually obliges, quoted [DOC]:**

> **4. Combined Works.** You may convey a Combined Work under terms of your choice… if you also do each of the following:
> a) Give prominent notice with each copy of the Combined Work that the Library is used in it and that the Library and its use are covered by this License.
> b) Accompany the Combined Work with a copy of the GNU GPL and this license document.
> c) For a Combined Work that displays copyright notices during execution, include the copyright notice for the Library among these notices, as well as a reference directing the user to the copies of the GNU GPL and this license document.
> d) Do one of the following:
> **0)** Convey the Minimal Corresponding Source… and the Corresponding Application Code in a form suitable for… the user to recombine or relink…
> **1)** Use a suitable shared library mechanism for linking with the Library. A suitable mechanism is one that (a) uses at run time a copy of the Library already present on the user's computer system, and (b) will operate properly with a modified version of the Library that is interface-compatible with the Linked Version.

**ADR-0004's "user-replaceable" instinct is exactly option 4(d)(1)** — a separate jar in the instance's `mods` folder that the loader picks up at run time and that the player can swap for a modified, interface-compatible build. That is well-founded.

**But 4(a), 4(b) and 4(c) are separate obligations that ADR-0004 mentions only as "attribution and a written offer for their source".** The precise requirements are: a prominent notice that Lithium is used and is LGPL-covered; a copy of **both** the GPL and the LGPL texts shipped with the client; and, if ash displays copyright notices at runtime (a credits screen, an about dialog), Lithium's copyright notice among them with a pointer to those licence texts. A "written offer for source" (GPL §6(b)) is only one of several ways to satisfy 4(d)(0), and it is **not needed at all** if ash takes 4(d)(1). GPL §6(d) — *"offering access from a designated place… and offer equivalent access to the Corresponding Source in the same way through the same place"* — is the other cheap option if ash ever does host the jar.

There is a cleaner move available, and it is the same one the EULA argument turns on: **if ash never hosts the jar, and instead has the player's machine fetch it from Modrinth, ash is arguably not conveying it at all.** That is the strongest position on both the LGPL and the PolyForm axes, and it is free.

### Distribution mechanics

**Modrinth is the right channel, and it publishes hashes.** The v2 API returns per-file `hashes.sha1` and `hashes.sha512` **[PRACTICE]**:

```
sodium-fabric-0.8.14+mc1.21.11.jar   1 909 150 B
  sha1   4a8733fe544168068d12fa94b2ab03e6c0c7bf91
  sha512 04c43f9e8534b87a52c42ffd51b0e344d4ef92dc9cc52da33d13af6a18bf74b0...
lithium-fabric-0.21.4+mc1.21.11.jar    900 462 B
  sha1   203bdcb26e97b3217b045e1182651a7d7b6462ec
```

I downloaded both and hashed them: **both sha1 values match exactly.** So ash's depot invariant is fully satisfiable for bundled mods — `/v2/project/{id}/version?loaders=["fabric"]&game_versions=["1.21.11"]` gives you an immutable `cdn.modrinth.com` URL, a size and two hashes in one call, and the URL never changes for a given version id.

Modrinth's API terms **[DOC]**: rate limit **300 requests per minute per IP**, reported live in `X-Ratelimit-Limit` / `X-Ratelimit-Remaining` / `X-Ratelimit-Reset`, the same with or without a token. A uniquely-identifying `User-Agent` is mandatory: *"Providing a user agent that only identifies your HTTP client library… increases the likelihood that we will block your traffic"*, with the documented best form being `github_username/project_name/1.56.0 (contact@launcher.com)` — the examples name a launcher explicitly. Unique ids, not slugs, are recommended for long-term storage (`AANobbMI` for Sodium, `gvQqBUqZ` for Lithium). There is also a Maven front end at `https://api.modrinth.com/maven` under group `maven.modrinth`, which ReplayMod uses for build-time mod dependencies **[PRACTICE]**.

The alternative channel is each project's own GitHub releases, which carry the same jars. Modrinth is better: it has an API, hashes, and a stated rate limit.

### Dependency constraints on loader pinning

From the shipped jars' `fabric.mod.json` **[PRACTICE]**:

```
sodium 0.8.14 (1.21.11):  fabricloader >=0.16.0
                          fabric-block-view-api-v2 *
                          fabric-rendering-fluids-v1 >=2.0.0
                          fabric-resource-loader-v0 *
sodium 0.9.2 (26.2):      minecraft ~26.2, fabricloader >=0.16.0
                          fabric-block-getter-api-v2 *
                          fabric-rendering-fluids-v1 >=2.0.0
                          fabric-resource-loader-v0 *
lithium 0.21.4 (1.21.11): fabricloader >=0.15.1, minecraft 1.21.11
```

Three consequences ADR-0004 does not currently account for:

1. **Sodium requires Fabric API.** Three modules, hard-depended. In practice that means shipping Fabric API as a **third bundled mod** — it is Apache-2.0, so no licence problem, but it is a third artifact to version, verify and keep in step, and it is the one that changes most often.
2. **The loader floor is `>=0.16.0`, with no ceiling declared.** Fabric's current stable is 0.19.5. Anything from 0.16.0 up satisfies both. That is a wide, comfortable window; ash's loader pin is not constrained in practice.
3. **Lithium pins the game version exactly** (`"minecraft": "1.21.11"`), and Sodium pins a range (`~26.2`). So the bundled-mod set is **per version target**, not per loader, and bumping the modern target from 1.21.11 to 1.21.12 would require new Sodium and Lithium builds, not just a rebuild of ash.

Neither project declares `depends` on the other, and Sodium's `breaks` list is long (embeddium, optifabric, canvas, vulkanmod, old iris, and a dozen others). A third-party mod the player supplies can therefore break a bundled one — worth a diagnostic, not a guard.

### The 1.8.9 side

**Confirmed: nothing to bundle.** Sodium's earliest supported game version is 1.16.3 and Lithium's is 1.16.2, per each project's own Modrinth version list **[PRACTICE]**. ADR-0004's statement that no Sodium equivalent exists on 1.8.9 is correct as of today.

The practical consequence is the one ADR-0004 already draws — the whole pipeline is ash's on that target — plus one it does not: **the 1.8.9 instance has no licence entanglement at all.** No PolyForm, no LGPL, no Fabric API dependency. The only third-party code in a 1.8.9 instance is Fabric Loader (Apache-2.0), Legacy Fabric's intermediary (CC0-1.0), Legacy Fabric's LWJGL fork, and Legacy Fabric API (Apache-2.0) if ash needs it. That is a materially simpler shipping position than the modern target, and it is worth knowing when deciding which target leads.

---

## 8. What established launchers do

### Prism Launcher

Read at `develop`, commit `df96e1c` (2026-09-14) **[PRACTICE]**.

**How it models a loader on an instance.** Not with `inheritsFrom`. An instance is a `PackProfile` — an ordered list of `Component`s, each identified by a `uid`:

- `launcher/minecraft/PackProfile.cpp` — `mmc-pack.json` at the instance root holds the component list (`{uid, version, ...}`); `patches/<uid>.json` holds each component's resolved version file.
- `launcher/minecraft/Component.cpp` — declares which loaders conflict with which: `{"net.fabricmc.fabric-loader", {ModPlatform::Fabric, {"net.minecraftforge", "net.neoforged", "org.quiltmc.quilt-loader"}}}`.
- `launcher/minecraft/VanillaInstanceCreationTask.cpp` — creation is literally `setComponentVersion("net.minecraft", version)` then `setComponentVersion(m_loader, m_loaderVersion)`.
- Known uids from the meta index: `net.minecraft`, `net.fabricmc.fabric-loader`, `net.fabricmc.intermediary`, `org.quiltmc.quilt-loader`, `net.minecraftforge`, `net.neoforged`, `com.mumfrey.liteloader`, `org.lwjgl`, `org.lwjgl3`, plus four Java-runtime packages.

Note `org.lwjgl` (LWJGL 2) as a first-class component: Prism replaces LWJGL wholesale on old versions, exactly as Legacy Fabric does, and `VersionFilterData.cpp` carries an `lwjglWhitelist` of `org.lwjgl.lwjgl:{lwjgl, lwjgl_util, lwjgl-platform}` plus the jinput pair to make that swap safe.

**How it stores the merged version JSON.** It does not store a merged document. It stores each component's version file under `patches/` and merges at launch into a `LaunchProfile` (`applyLibrary`, `applyMainClass`, `applyMinecraftArguments`, `applyTweakers`, …). Merging is therefore repeated per launch and always reflects the current component set.

**How it handles the loader being unreachable.** `Meta::BaseEntityLoadTask` (`launcher/meta/BaseEntity.cpp`): load the local file from `meta/`, hash it sha256, and — in `Net::Mode::Offline` — succeed on the local copy **without** comparing hashes. Online, a mismatch throws and the file is deleted. Library downloads get `Net::Request::Option::MakeEternal` and, when stale, `AcceptLocalFiles`.

**What it does about verification.** Meta documents: sha256-chained and enforced online. Artifacts: `ChecksumValidator(Sha1, sha1)` **only when the metadata carries a sha1** — which Mojang libraries do and Fabric libraries do not. See §2.

**No Legacy Fabric support of any kind** (gap 8).

### HMCL

Read at `main`, commit `6d9ae24` (2026-09-14) **[PRACTICE]**.

**How it models a loader on an instance.** With `inheritsFrom`, literally, plus a patch layer. `GameInstanceManifest` carries `inheritsFrom`, `patches` and `jar`; `DefaultGameRepositorySnapshot.resolve` walks the chain recursively with cycle detection and then applies patches sorted by priority.

**How it stores the merged version JSON.** It stores the *unmerged* documents in the official `versions/<id>/<id>.json` layout and resolves at load. `GameInstanceManifest.merge` (§1) is the field-by-field merge; `LaunchManifestNormalizer.repairForLaunch` is the second, launch-time pass that deduplicates libraries and applies loader-specific repairs — including `repairDuplicateAsm`, which exists specifically for Legacy Fabric.

**How it handles a missing parent.** `removeInvalidInstances` walks the inheritance chain and, on a missing link, logs *"Instance X inherits from missing instance Y"* and removes the whole chain from the snapshot. `resolve` throws `NoSuchGameInstanceException`. A cycle is survivable: it logs and breaks the link rather than failing.

**What it does about verification.** HMCL's `Library` model carries `downloads` with hashes when present and falls back to plain Maven URLs otherwise — the same structural gap as Prism.

### Fabric's own installer

`FabricMC/fabric-installer` at HEAD (2026-07-17) **[PRACTICE]**. Worth reading because it is the reference behaviour ash is replacing:

- `ClientInstaller.install` writes `<mcDir>/versions/<profileName>/<profileName>.json` straight from the meta endpoint, deletes any `<profileName>.jar`, then downloads every library into `<mcDir>/libraries/` — *"Downloading the libraries isn't strictly necessary as the launcher will do it for us. Do it anyway in case the launcher fails."*
- `Library.getURL()` builds the Maven path with `name.split(":", 3)` — which **cannot express a classifier**. Legacy Fabric's `lwjgl-platform` natives entry would be unreachable through it; Legacy Fabric ships its own installer fork.
- `ProfileInstaller` adds an entry to `launcher_profiles.json` (or `launcher_profiles_microsoft_store.json`) with `lastVersionId` set to the profile name.
- `FabricService` fails over across three `{meta, maven}` pairs on `IOException`, with `setFixed` to disable fallbacks.
- Verification: a single sha1 comparison, for the Minecraft server jar.

---

## Action items

Priority order. Items 1–3 are decisions; 4–9 are engineering; 10–11 are legal and should start in parallel because they are not under ash's control.

1. **Decide the modern version target deliberately, and record it.** ADR-0005 says "the latest 1.21.x". That now means 1.21.11 — the *last obfuscated release*, on a toolchain Fabric has announced it is retiring (§1 of the Summary, §4). The alternative is 26.x: no intermediary, no Yarn-or-Mojmap decision, no refmaps, a simpler Loom, and the version Sodium and Lithium are actively developed against. The argument for 1.21.11 is that it is frozen and that is where players are. Both are defensible; "latest 1.21.x" as a phrase is not, because it now points at a historical branch rather than at the front of the line. Amend ADR-0005 either way.
2. **Correct ADR-0004's licence statement, and re-run its reasoning.** Sodium is PolyForm Shield 1.0.0, not LGPL-3.0 (§7). The arm's-length architecture does not address PolyForm's Noncompete clause, because that clause is about what you build and sell rather than how you link. Decide explicitly: ship Sodium and accept the Noncompete exposure; drop Sodium and do the render work yourself; or do not bundle it and let the player install it (which is also the strongest EULA and LGPL position). Lithium's LGPL analysis is sound and should be kept.
3. **Correct ADR-0004's territory claim.** "The hotspots neither covers" is not supported (§5). Replace with the narrower, accurate statement and add the operational consequence: every ash mixin in entity, HUD, text or particle rendering needs a compatibility test against the pinned Sodium build, and an `IMixinConfigPlugin` so ash degrades instead of crashing when Sodium is present.
4. **Do not fetch the Fabric profile JSON at launch.** Build it locally from pinned inputs: the immutable `fabric-loader-<version>.json` from Maven (whose own sha256 ash records at pin time), plus the two-to-five extra coordinates with their `.sha1` sidecars. This keeps `depot.rs`'s invariant intact, keeps `resolve_metadata`'s offline fallback honest, and removes `meta.fabricmc.net` from the launch path entirely. §2.
5. **Implement the merge as replace-by-`group:artifact`-then-evaluate-rules, in that order.** Not concatenate-then-dedupe. Getting the order right is what makes Legacy Fabric's `disallow` entries work, what makes its LWJGL replacement displace *both* vanilla LWJGL entries, and what avoids the bug HMCL patches around with `repairDuplicateAsm`. `version.rs::select_libraries` currently keys natives on `group:artifact:version` and must key on `group:artifact` plus classifier. §1.
6. **Teach `version.rs` and `depot.rs` about Maven-style library entries.** Add `url` to `Library`, add `inheritsFrom` and `jar` to `VersionMetadata`, and construct `group/artifact/version/artifact-version[-classifier].jar` when `downloads` is absent — including for `natives_for`, which currently reads `downloads.classifiers` and would extract nothing for Legacy Fabric's LWJGL natives. §1.
7. **Fix the main-jar path in `launch.rs::classpath`.** It appends `versions/<metadata.id>/<metadata.id>.jar`; after a merge that id is `fabric-loader-0.19.3-1.8.9` and no such jar exists. Resolve it through `jar` / the root of the inheritance chain. Also decide deliberately whether `natives::directory` should key on the merged id (it currently would), since Legacy Fabric's natives genuinely differ from vanilla's. §1.
8. **Mirror the Legacy Fabric artifacts and record their hashes.** The intermediary jar (147 KB), the three LWJGL jars and their natives (~5 MB), and Legacy Fabric API. `repo.legacyfabric.net` is a single self-hosted repository with no published mirror, run by one person. This converts the ADR-0003 dormancy risk from a project risk into a nothing, for about half a day of work. §3.
9. **Build the version-abstraction seam before the first feature.** A Minecraft-free shared module targeting Java 8 bytecode, holding feature logic and a small set of ports (`HudSurface`, `InputSource`, `PlayerStats`, `CameraControl`); thin per-target implementations; plain JUnit tests against fakes. This is not a preference — it is the only way any of the client is testable, because there is no headless harness and none on Windows or macOS CI at all. Design `HudSurface` around "draw primitives at a position", not "replace element X", because Legacy Fabric API's single additive `HudRenderCallback` cannot express the latter. §4, §5.
10. **Get counsel on PolyForm Shield's Noncompete as applied to bundling Sodium in a commercial client.** This is a new question that ADR-0004 never asked, and it has a 32-day cure window rather than immediate termination, which makes it survivable but not ignorable. §7.
11. **Add the cape finding to the ADR-0010 file and to whatever legal question is already in flight.** The Usage Guidelines now name capes specifically as the cosmetic you may not sell, in the closest analogous context Mojang publishes, while simultaneously permitting cosmetic sales generally — which weakens the fairness-principle argument 0001 §8 relied on. Both halves matter and they point in opposite directions. §6.
12. **Plan CI as Linux-for-tests, Windows-and-macOS-for-build.** `./gradlew build` and JUnit run anywhere; client game tests need XVFB, which Loom supports only on Linux CI. Also note that `fabricApi { configureTests { eula = true } }` is a literal EULA acceptance in a build script and should be a recorded decision. §4.

---

## Sources

All retrieved 2026-09-14 unless stated.

### Fabric — first-party

- [FabricMC/fabric-meta](https://github.com/FabricMC/fabric-meta) — `src/main/java/net/fabricmc/meta/web/ProfileHandler.java`. Commit `b40c08d` (2026-07-27). The generator for every profile JSON; the authoritative shape of the document.
- [FabricMC/fabric-installer](https://github.com/FabricMC/fabric-installer) — `client/ClientInstaller.java`, `client/ProfileInstaller.java`, `util/Library.java`, `util/Reference.java`, `util/FabricService.java`, `util/Utils.java`, `server/MinecraftServerDownloader.java`. Commit `6e7d1ac` (2026-07-17).
- [FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod) — `build.gradle`, `gradle.properties`, `settings.gradle`, `gradle/wrapper/gradle-wrapper.properties`, `src/main/resources/fabric.mod.json`, `.github/workflows/build.yml`.
- [FabricMC/fabric-loom](https://github.com/FabricMC/fabric-loom) — `README.md`, `build.gradle`, wrapper properties. Branch `dev/1.17`, MIT, pushed 2026-09-05.
- [FabricMC/fabric](https://github.com/FabricMC/fabric) — `fabric-rendering-v1/.../hud/HudElementRegistry.java` and `VanillaHudElements.java`; `fabric-lifecycle-events-v1/.../ClientTickEvents.java`; `fabric-key-mapping-api-v1/.../KeyMappingHelper.java`; module listing.
- [FabricMC/fabric-loader](https://github.com/FabricMC/fabric-loader) — Apache-2.0, pushed 2026-08-28.
- [FabricMC/yarn](https://github.com/FabricMC/yarn) — CC0-1.0, pushed 2026-05-27.
- [Fabric Documentation — Migrating Mappings](https://docs.fabricmc.net/develop/porting/mappings/) (source: `fabric-docs/develop/porting/mappings/index.md`). *"Minecraft: Java Edition was obfuscated from its release until 1.21.11."*
- [Fabric Documentation — Fabric Loom options reference](https://docs.fabricmc.net/develop/loom/options) — `mixin { useLegacyMixinAp, defaultRefmapName }`, *"Disabled by default in Loom 1.12+"*.
- [Fabric Documentation — Automated Testing](https://docs.fabricmc.net/develop/automatic-testing) and [Fabric API DSL](https://docs.fabricmc.net/develop/loom/fabric-api) — Fabric Loader JUnit, `configureTests`, `ClientProductionRunTask`, `useXVFB`, `eula`.
- [Fabric Documentation — Rendering in the HUD](https://docs.fabricmc.net/develop/rendering/hud).
- [Removing Obfuscation from Fabric](https://fabricmc.net/2025/10/31/obfuscation.html) — 2025-10-31. *"Intermediary will no longer exist"*; Yarn deprecation; Loom plans.

### Fabric — live endpoints exercised

- `https://meta.fabricmc.net/v2/versions/{game, loader, loader/:game, loader/:game/:loader/profile/json, intermediary/:game}`.
- `https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.19.5/` — jar, `.sha1`, `.sha256`, `.sha512`, `.md5`, `.asc`, `.pom`, `.json`, and `maven-metadata.xml` with sidecars. Hashes verified against downloads.
- `https://maven.fabricmc.net/net/fabricmc/intermediary/1.21.11/` — `.sha1` present, `.asc` absent (404).
- `https://meta2.fabricmc.net/`, `meta3`, `maven2`, `maven3` — all 200.

### Legacy Fabric — first-party

- [Legacy-Fabric/legacy-meta](https://github.com/Legacy-Fabric/legacy-meta) — `net/fabricmc/meta/web/ProfileHandler.java` (the commented-out `arguments` block) and `net/legacyfabric/meta/web/ProfileHelper.java` (`enrichProfile`: the `asm-all` disallow entries and the LWJGL 2 replacement). Commit `a71740b` (2026-06-01).
- [Legacy-Fabric/fabric-example-mod](https://github.com/Legacy-Fabric/fabric-example-mod) — `build.gradle`, `gradle.properties`, `settings.gradle`, wrapper, `fabric.mod.json`, `.github/workflows/build.yml`.
- [Legacy-Fabric/legacy-looming](https://github.com/Legacy-Fabric/legacy-looming) — `build.gradle`, branch list (`dev/1.0`…`dev/1.16`), MIT. Default branch `dev/1.14`; `dev/1.16` last pushed 2026-05-28.
- [Legacy-Fabric/lwjgl](https://github.com/Legacy-Fabric/lwjgl) — `README.md`: platform matrix, macOS floors, HiDPI fix, *"Fix crash on Java 11+ due to missing symbols"*.
- [Legacy-Fabric/fabric](https://github.com/Legacy-Fabric/fabric) — Legacy Fabric API, Apache-2.0; branch `1.8.9` module listing and public API classes; releases; contributor and commit statistics via the GitHub API.
- [Legacy-Fabric/yarn](https://github.com/Legacy-Fabric/yarn) — CC0-1.0, pushed 2026-03-16.
- `https://meta.legacyfabric.net/v2/versions/{game, loader, loader/1.8.9, loader/1.8.9/0.19.3/profile/json, yarn/1.8.9}`.
- `https://maven.legacyfabric.net/` → `https://repo.legacyfabric.net/legacyfabric/…` (Reposilite) — intermediary, Legacy Yarn, Legacy Fabric API, `legacy-looming` version listing, LWJGL 2.9.4+legacyfabric.17 and its natives. Sidecars verified; no `.asc`.

### Mojang — first-party

- [`version_manifest_v2.json`](https://launchermeta.mojang.com/mc/game/version_manifest_v2.json) — `latest.release = 26.2`; per-version metadata for 1.8.9, 1.21.9/10/11, 26.1, 26.1.2, 26.2 (`javaVersion`, `downloads` keys, `assetIndex`).
- `https://piston-data.mojang.com/v1/objects/031a68be…/client.txt` — the 1.21.11 client mappings; the licence header quoted in §4.
- [Minecraft EULA](https://www.minecraft.net/en-us/eula) — fetched 2026-09-14, `Last-Modified: Mon, 14 Sep 2026 17:08:10 GMT` (a render timestamp, not a revision date). "USING mods" section verbatim.
- [Minecraft Usage Guidelines](https://www.minecraft.net/en-us/usage-guidelines) — fetched 2026-09-14, `Last-Modified: Mon, 14 Sep 2026 18:42:08 GMT`. "Extended functionality and modifications", "Servers and hosting", "essential guidelines", commercial-use definition.
- [Minecraft — new version numbering system](https://www.minecraft.net/en-us/article/minecraft-new-version-numbering-system) and [Removing Obfuscation in Java Edition](https://www.minecraft.net/en-us/article/removing-obfuscation-in-java-edition) — cited by Fabric's own documentation as the announcements behind both changes. Both time out on direct fetch from this machine; the manifest evidence above is decisive independently.

### Launcher source (evidence of practice)

- [PrismLauncher/PrismLauncher](https://github.com/PrismLauncher/PrismLauncher), branch `develop`, commit `df96e1c` (2026-09-14) — `launcher/minecraft/Library.cpp` (the unvalidated Fabric download path), `LaunchProfile.cpp` (`applyLibrary`, `applyString`), `GradleSpecifier.h` (`matchName`), `PackProfile.cpp` (`mmc-pack.json`, `patches/`), `Component.cpp` (loader conflict table), `VanillaInstanceCreationTask.cpp`, `VersionFilterData.cpp` (`lwjglWhitelist`), `launcher/meta/BaseEntity.cpp` (sha256 chain, offline behaviour).
- `https://meta.prismlauncher.org/v1/` — `index.json`, `net.fabricmc.fabric-loader/index.json`, `net.fabricmc.fabric-loader/0.19.5.json`, `net.fabricmc.intermediary/1.21.11.json`, `org.lwjgl/index.json`. sha256 chain verified.
- [HMCL-dev/HMCL](https://github.com/HMCL-dev/HMCL), branch `main`, commit `6d9ae24` (2026-09-14) — `HMCLCore/.../game/GameInstanceManifest.java` (`merge`), `DefaultGameRepositorySnapshot.java` (`resolve`, `removeInvalidInstances`), `LaunchManifestNormalizer.java` (`uniqueLibraries`, `repairDuplicateAsm`), `GameInstanceLibraryBuilder.java`, `Library.java`, `Arguments.java`, `util/Lang.java`.

### Mixin

- [SpongePowered/Mixin](https://github.com/SpongePowered/Mixin), branch `master` — `transformer/MixinConfig.java` (`required`, `injectors.defaultRequire`, `prepareMixins` failure handling) and `MixinEnvironment.java` (`Option.IGNORE_REQUIRED`).

### Bundled mods

- [CaffeineMC/sodium](https://github.com/CaffeineMC/sodium) — `LICENSE.md` on `dev`, `mc1.21.11-0.8.14` and `mc26.2-0.9.2` (PolyForm Shield 1.0.0); `README.md`; `thirdparty/NOTICE.txt`; releases; `LICENSE.txt`/`LICENSE.md` commit history.
- [CaffeineMC/lithium](https://github.com/CaffeineMC/lithium) (redirected from `lithium-fabric`), branch `develop` — `LICENSE.md` (LGPL-3.0); releases.
- Shipped jars, downloaded and inspected: `sodium-fabric-0.8.14+mc1.21.11.jar`, `sodium-fabric-0.9.2+mc26.2.jar`, `lithium-fabric-0.21.4+mc1.21.11.jar` — `fabric.mod.json` (`license`, `depends`, `breaks`, `mixins`, `entrypoints`) and the mixin configs `sodium-common.mixins.json`, `sodium-fabric.mixins.json`, `sodium-frapi.mixins.json`, `lithium.mixins.json`, `lithium-fabric.mixins.json`.
- [PolyForm Shield License 1.0.0](https://polyformproject.org/licenses/shield/1.0.0) — as shipped in Sodium's `LICENSE.md`.
- [GNU LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.txt) §4 and [GNU GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.txt) §6.
- [Modrinth API documentation](https://docs.modrinth.com/api/) — rate limits, User-Agent requirement, id-vs-slug guidance. Live `api.modrinth.com/v2` calls for `sodium`, `lithium`, `legacy-fabric-api`, `fabric-api`; `X-Ratelimit-*` headers observed.

### Multi-version build tooling

- [ReplayMod/ReplayMod](https://github.com/ReplayMod/ReplayMod), branch `stable`, pushed 2026-07-12 — `settings.gradle.kts`, `root.gradle.kts` (the `preprocess` version graph, 1.7.10 → 26.2), `build.gradle.kts` (the `isUnobfuscated` refmap switch), `gradle.properties`, `versions/` listing, `versions/mapping-fabric-26.1-1.21.11.txt`.
- [ReplayMod/preprocessor](https://github.com/ReplayMod/preprocessor) — `README.md`: `//#if` / `//$$` syntax, per-version overwrite files, `setCoreVersion`. GPL-3.0, pushed 2026-07-07.
- [essential-gradle-toolkit](https://github.com/EssentialGG/essential-gradle-toolkit) (now `SparkUniverse/essential-gradle-toolkit`) — `README.md`: `gg.essential.multi-version` over architectury-loom + preprocessor. GPL-3.0, pushed 2026-06-08.
- [architectury/architectury-loom](https://github.com/architectury/architectury-loom) — MIT, pushed 2026-09-12.
- [stonecutter](https://github.com/stonecutter-versioning/stonecutter) — LGPL-3.0; GitHub repo is now a mirror of a Codeberg upstream, last pushed 2025-12-25.

### Community reference

- [minecraft.wiki — `client.json`](https://minecraft.wiki/w/Client.json) — the de-facto reference for the version JSON format. Read in full via `?action=raw`; **`inheritsFrom` does not appear in it**. **Community, not first-party.**
