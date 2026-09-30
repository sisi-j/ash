# Freelook's standing with PvP servers, Lithium for 1.21.11, and the launcher's typefaces

Research date: 2026-09-29. All sources retrieved on that date unless stated otherwise.

This answers items 2, 3 and 7 of Phase 3's Further Notes ([#30](https://github.com/sisi-j/ash/issues/30), `docs/specs/0003-phase-3-the-full-client.md`).

Every claim below is labelled:

- **[DOC]**: the owner's own published text says this (a rules page, a staff post, first-party docs, a README, a licence).
- **[PRACTICE]**: observed directly, in a live API response, the bytes of a shipped jar or font, or source code at a named commit.
- **[COMMUNITY]**: only a secondary source says this. Used as a lead, never as the basis of a finding.

Where this document gives an opinion rather than a source, it says **Judgement** in so many words.

---

## Summary

**1. Freelook: cut it.** Hypixel prohibits it. Its *Allowed Modifications* page says aesthetic mods must not *"change the player's perspective (e.g. allowing them to see around or over objects they normally wouldn't be able to)"*, and anything outside the allowed categories *"should be assumed that it is disallowed by default"* **[DOC]**. On 2021-08-20 the Hypixel Team said they had *"blocked the use of the perspective or freelook mods"* because *"they provide a significant unfair advantage"*, and that using them was already against the rules **[DOC]**. This is a ban-risk rule, not only a request to client makers. The Server Rules list *"Disallowed modifications"* under *Unfair Advantages*, with suspension from the server as the consequence **[DOC]**. Hypixel also says that any forced disabling is best effort, and *"you, as the player, are responsible"* **[DOC]**.

Lunar disabled freelook on Hypixel in 2021 *"as it is now against their rules"*, and disables it on Hoplite. Lunar, Badlion and Feather all give servers an API to switch the mod off, and ash has no such API (it is out of scope) **[DOC]**. MCC Island and Hoplite also prohibit freelook by name. CubeCraft, PikaNetwork and PvPHQ allow it or a close equivalent **[DOC]**.

**Judgement:** freelook passes the first half of ADR-0006's test, because the server sees nothing different. It fails the second half: servers that draw a line draw it at what vanilla's F5 camera can do, and freelook goes past that. It also fails the spec's own condition that no major server prohibits it. Under the spec's rule, freelook is cut and ADR-0006 is amended. ADR-0006 currently names freelook as a feature ash ships.

**2. Lithium: pin `0.21.4+mc1.21.11`. It needs no Fabric API, and it does not touch anything ash's Phase 3 features touch.**

| | |
| --- | --- |
| Pin | Lithium `0.21.4+mc1.21.11` (Modrinth `mc1.21.11-0.21.4-fabric`, id `Ow7wA0kG`), released 2026-03-11 |
| File | `https://cdn.modrinth.com/data/gvQqBUqZ/versions/Ow7wA0kG/lithium-fabric-0.21.4%2Bmc1.21.11.jar`, 900,462 bytes |
| sha1 | `203bdcb26e97b3217b045e1182651a7d7b6462ec` |
| sha512 | `f14a5c3d2fad786347ca25083f902139694f618b7c103947f2fd067a7c5ee88a63e1ef8926f7d693ea79ed7d00f57317bae77ef9c2d630bf5ed01ac97a752b94` |
| Depends on | `fabricloader >=0.15.1` and `minecraft 1.21.11`. There is no Fabric API dependency. |

- The pin is the same one research 0002 recorded on 2026-09-14. The GitHub release is byte-identical **[PRACTICE]**.
- The loader range accepts both of ash's pins: 0.19.5 on 1.21.11 and 0.19.3 on 1.8.9. The brief said ash pins 0.19.3; on 1.21.11 it pins 0.19.5 (`client/gradle.properties`, `launcher/core/src/loader.rs`).
- The licence is **LGPL-3.0-only**, so ADR-0013 is confirmed. The jar carries **only the LGPL text**. LGPL-3.0 §4(b) also requires the GPL text, so ash has to supply that itself.
- The spec's "written offer" is GPL §6(b), which applies to physical products. For a download, the route is §6(d).
- Of Lithium's 280 mixins, none targets the HUD, `LivingEntityRenderer` or the overlay, `Camera`, `MouseHandler`, `KeyboardInput`, damage or hurt packet handling, or `PlayerInfo`. One mixin touches `ClientPacketListener`, on `updateLevelChunk`, and it is off by default. Every other client-side mixin is chunk, entity-storage or block-entity bookkeeping **[PRACTICE]**.
- Lithium's own documentation claims frame-rate gains only through the integrated server in single-player. It makes no claim for a client connected to a multiplayer server, and it points to Sodium for rendering **[DOC]**.

**3. Typefaces: all three are SIL OFL 1.1, and none declares a Reserved Font Name in its licence.** Bundling them in the launcher is permitted. Condition 1 forbids selling the fonts by themselves. Condition 2 requires the copyright notice and licence text to travel with them **[DOC]**. Two things differ from the spec's assumptions:

- **Sora has no static Medium (500) woff2 upstream.** The only official woff2 that renders 500 is the variable `Sora[wght].woff2`. Sora also has no releases or tags, so it is pinned by commit.
- **Inter's README says "Inter" is a Reserved Font Name, but its licence has not declared one since 2020-08-25.** Its author has also said in writing that he is *"totally fine with subsetting and reusing the name"* **[DOC]**.

Subsetting counts as modification under the OFL FAQ. With no RFN declared, a subset may keep its name, but the trademark notices in Inter and JetBrains Mono must stay in the file. **Judgement:** ship the official woff2 files unmodified, about 380 KB in all. Subsetting is legal, but it saves too little to be worth creating a Modified Version.

---

## 1. Freelook's standing with major PvP servers

### 1.1 Hypixel

**The current written rule.** *Hypixel Allowed Modifications* lives at [support.hypixel.net/hc/en-us/articles/6472550754962](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications). The HTML page returns 403 to a non-browser client, so it was read through the same Help Center's JSON API at `support.hypixel.net/api/v2/help_center/en-us/articles/6472550754962.json`. That record gives created 2022-07-06, last edited 2023-02-03, updated 2025-02-24 **[PRACTICE]**. The relevant text **[DOC]**:

> If a modification does not fit into one of these categories, it should be assumed that it is disallowed by default.

> **Aesthetic Modifications** — Modifications that change only the look and feel of the game without modifying gameplay … However, these must not change the properties of blocks (e.g. make non-transparent blocks transparent) or change the player's perspective (e.g. allowing them to see around or over objects they normally wouldn't be able to).

> **Gameplay Recorders** — … allowing for the changing of perspective during playback (but not in live gameplay).

> **Clients / Mod Packs** — Badlion Client, Lunar Client, etc. … please check that each individual feature of these clients is permitted under our rules and make sure that any disallowed modifications can be fully disabled. In some cases, and where possible, we may forcefully disable disallowed modifications to prevent their use, but please do not rely on this and do ensure you are only using allowed modifications (you, as the player, are responsible for this).

The page does not use the word "freelook". The perspective clause and the disallowed-by-default rule cover it, and the staff post below names it.

**The staff announcement.** *Update - Perspective/Freelook Mods* is posted by the **Hypixel Team** account ("Official Account", "Hypixel Staff") in *Official Communications › Community Information and Changes*. It is dated **2021-08-20T10:20:39-04:00** and closed to replies ([hypixel.net/threads/update-perspective-freelook-mods.4487862](https://hypixel.net/threads/update-perspective-freelook-mods.4487862/)) **[DOC]**:

> As you all have seen recently, we have blocked the use of the perspective or freelook mods on our server. Although the usage of these mods has been disallowed based on our rules, we decided to also manually block these mods based on the amount players using them. They provide a significant unfair advantage when it comes to playing many of our minigames, hence the reason for them being blocked from the server. We understand that many of you were using these modifications, however, based on our rules you should not have been using them in the first place. We will be continuously reviewing modifications as they come up, and working with the client developers as we see fit.

I found no later staff statement that reverses or softens it.

**Ban risk, or a request to clients? Both, and the ban risk is the part that binds a player.** *Hypixel Server Rules* ([support.hypixel.net/hc/en-us/articles/4427624493330](https://support.hypixel.net/hc/en-us/articles/4427624493330-Hypixel-Server-Rules), last edited 2024-12-09) lists under **Unfair Advantages**: *"Disallowed modifications, macros, bug abuse, exploiting, boosting, scripting, or punishment evading"*. Under **Consequences** it says: *"Players found breaking these rules can expect a suspension of chat privileges or access to the Hypixel Server and Discord"* **[DOC]**.

On top of the rule, Hypixel has the major clients switch the mod off ("working with the client developers") and blocks it itself. The Allowed Modifications page says that enforcement is best effort, and puts the responsibility on the player.

How Hypixel detects a freelook mod in a client with no server API is reported only by players, as a kick for a known mod in a Forge mods folder. No first-party source describes it, and none is relied on here **[COMMUNITY]**.

### 1.2 How Lunar, Badlion and Feather switch freelook off, and where

**Lunar Client.**

- *Patch Notes #14*, dated 2021-08-29 in the page's own data: *"Disabled Freelook on the Hypixel Network as it is now against their rules"* ([lunarclient.com/news/patch-notes-14](https://www.lunarclient.com/news/patch-notes-14)) **[DOC]**.
- *Why Lunar Client is the best for playing Hoplite*, 2024-01-10: *"some Lunar Client mods are disabled on Hoplite because they are seen as unfair advantages"*; *"The current disabled Lunar Client mods on Hoplite include the Freelook mod and the Minimap mod"*; *"there is nothing the Lunar Client team can do when a mod is disabled on a server"* ([source](https://www.lunarclient.com/news/why-lunar-client-is-the-best-for-playing-hoplite)) **[DOC]**.
- *Freelook Mod vs. Snaplook Mod*, 2024-03-21: *"Lunar Client provides a server-side API for Minecraft Server owners to manually disable mods"*; *"We do our best to ensure that all mods within Lunar Client comply with the majority of server rules, but we can not account for every server"* ([source](https://www.lunarclient.com/news/free-look-mod-vs-snap-look-mod)) **[DOC]**.

**The mechanism is Apollo**, Lunar's server integration (MIT, [github.com/LunarClient/Apollo](https://github.com/LunarClient/Apollo), latest release v1.2.9 on 2026-08-17). Its *Mod Setting* module lets a server *"enable/disable mods"* per player and *"change, enable or disable mod settings within a mod"*, and players are notified when a server disables a mod ([docs](https://lunarclient.dev/apollo/developers/modules/modsetting); source `docs/developers/modules/modsetting.mdx`) **[DOC]**. Freelook is `ModFreelook`, with the option `ENABLED` on node `freelook.enabled`, default `true` (`api/src/main/java/com/lunarclient/apollo/mods/impl/ModFreelook.java`; [docs](https://lunarclient.dev/apollo/developers/mods/freelook)) **[PRACTICE]**. A server switches freelook off by setting `ModFreelook.ENABLED` to `false` for a player.

**Badlion Client.** The *Badlion Client Mod API* (MIT, [github.com/BadlionClient/BadlionClientModAPI](https://github.com/BadlionClient/BadlionClientModAPI), last pushed 2025-11-05) exists to *"remove the ability for a user to activate certain mods or features of mods while they are playing on your server"*. It is configured with `"modsDisallowed": { "<Mod>": { "disabled": true } }` in `plugins/BadlionClientModAPI/config.json`. Its list of mods that can be disabled includes **`Perspective`**, which is Badlion's freelook (`readme.md`, updated for Badlion Client 4.4.4 on 2025-02-24) **[DOC]**.

I could not retrieve a first-party Badlion statement naming the servers that disable it. `badlion.net`'s forum returns a Cloudflare challenge, and `support.badlion.net` now redirects to `support.lunarclient.com`. Lunar acquired Badlion from ESL FACEIT Group, announced 2025-03-12 ([source](https://www.lunarclient.com/news/lunar-client-acquires-badlion-client)), and has launched Badlion from the Lunar launcher since 2025-07-22 ([source](https://www.lunarclient.com/news/how-to-launch-badlion-client-on-lunar-client-step-by-step-guide)) **[DOC]**.

**Feather Client.** The *Feather Server API* ([github.com/FeatherMC/feather-server-api](https://github.com/FeatherMC/feather-server-api), `docs/docs/server-api/mods.md`, published at [docs.feathermc.com/server-api/mods](https://docs.feathermc.com/server-api/mods/)) lets a server call `player.blockMods(...)`. *"Blocked mods cannot be enabled by the player at all."* Its mod table lists `Perspective` (id `perspective`) with the description **"Freelook"**, and every blocking example in the page uses `new FeatherMod("perspective")` **[DOC]**. I found no first-party Feather statement naming the servers that block it.

### 1.3 Other PvP servers

| Server | Freelook | Source and date |
| --- | --- | --- |
| **MCC Island** | **Prohibited by name.** Disallowed: *"Better F5 (Allowing you to manipulate your camera whilst your movement remains static)"*. Aesthetic mods *"must not … alter the player's POV (e.g. allowing them to see around or over objects they normally wouldn't be able to, such as freecam or perspective mods)"*. Allowed: *"Mods that allow you to skip any mode of the perspective toggle (F5)"*. | [mcchampionship.com/help/mods](https://mcchampionship.com/help/mods/), "Last updated - 27th August 2026" **[DOC]**. Read through a reader proxy (`r.jina.ai`), because the site refuses non-browser clients. |
| **Hoplite** (battle royale) | **Prohibited by name.** *"Camera perspective mods that exceed the capabilities of the F5 camera function are prohibited. Freelook / Freecam"*. | [hoplite.gg/rules](https://www.hoplite.gg/rules), undated, "©2026 SpeedSilver Ltd." **[DOC]**. Lunar confirms it disables its Freelook there (§1.2). |
| **CubeCraft** (Java) | **Allowed.** Allowed list: *"Perspective mod (by celebistrial) and others included by allowed clients like Labymod and Lunar"*, *"Freelook mod"*. Not allowed: *"Perspective Mod Redux (by cynosphere)"*, *"Perspektive (by r0yzer)"*, *"Shoulder Surfing and Shoulder Surfing Reloaded"*, *"FreeCam"*. | *Allowed Mods and Clients*, posted by Capitan (Volunteers Coordinator, Admin Team), locked and pinned, "Updated: Sept 23, 2026" ([cubecraft.net/threads/allowed-mods-and-clients.228596](https://www.cubecraft.net/threads/allowed-mods-and-clients.228596/)) **[DOC]**. Read through the same reader proxy. |
| **PikaNetwork** | **Allowed.** *"Free Look Mod"* is on *Examples of Allowed Modifications*. | *Allowed and Disallowed Modifications*, by Arrly and Surbate, `dateModified` 2026-09-25 ([support.pika-network.net/articles/allowed-and-disallowed-modifications](https://support.pika-network.net/articles/allowed-and-disallowed-modifications)) **[DOC]** |
| **PvPHQ** (*"backed by Lunar Client"*, formed by FlowPvP and MCLeagues merging, per Lunar's own news pages) | **A freelook-type mod is allowed by name.** Allowed: *"Better Third Person"*, which its own page describes as *"independent rotation of the camera from a third person view … freely rotate camera for 360 degrees"* ([Modrinth](https://modrinth.com/mod/better-third-person)). Disallowed: *"Freecam"*, *"Shoulder Surfing Reloaded"*. | [pvphq.com/rules](https://pvphq.com/rules), "Last updated July 2026". The rule text is in the page's own bundle, `pvphq.com/assets/RulesPage-BG_4Tz6R.js` **[DOC]** |
| **PvP Land** | **Not named.** Allowed: *"Any mod bundled by default with Lunar Client or Badlion Client"*. Disallowed list names *"Freecam"*, not freelook. | [pvp.land/rules](https://pvp.land/rules), "LAST UPDATED 7/26/26" **[DOC]**. Whether PvP Land switches Lunar's freelook off through Apollo cannot be seen from outside. |
| **Minemen Club** | **Not named.** The rules list *"Use of Cheat(s) or Program(s) to gain an Unfair Advantage"* and nothing about mods specifically. | [minemen.club/rules](https://minemen.club/rules), undated, "© 2015-2026 Manthe Industries" **[DOC]** |
| **BlocksMC** | **Not named.** *"Using any kind of Hack/Mod that give you an advantage over other players is Forbidden"*, with FPS, aesthetic, armour and effect HUD, and brightness mods allowed. | Live site returns a Cloudflare challenge. Wayback capture of [blocksmc.com/rules](https://blocksmc.com/rules) from 2025-03-19, whose page reads "Last updated: September 29, 2024" ([capture](https://web.archive.org/web/20250319225815/https://blocksmc.com/rules)) **[DOC, archived]** |
| **Lunar Network** (`lunar.gg`) | **Closed.** *"as of June 1st, 2023, the server will be whitelisted"*. It is not a current PvP server. | [lunar.gg](https://www.lunar.gg/), *Farewell - Onto The Next Chapter* **[DOC]** |

The pattern: the servers that prohibit freelook say why, and the line they draw is vanilla's F5 camera. MCC Island allows skipping between F5's modes and bans camera movement *"whilst your movement remains static"*. Hoplite bans anything that *"exceed[s] the capabilities of the F5 camera function"*. Hypixel bans seeing *"around or over objects"*. The servers that allow it list it without comment.

### 1.4 Against ADR-0006's test

ADR-0006 asks: *"does the server's view of the game change, or would a fair player be disadvantaged by not having it? If either, it doesn't ship."* The spec adds a second condition: no major PvP server prohibits it.

**What the sources say.** No first-party source claims freelook changes what the server receives. Every objection is about what the player sees:

- Hypixel: *"a significant unfair advantage"*.
- Lunar, on Hoplite: *"seen as unfair advantages"*.
- MCC Island: *"alter the player's POV"*.
- Hoplite: *"exceed the capabilities of the F5 camera function"*.

**Judgement, part 1: the server's view.** A correct implementation passes. The server receives the player's body rotation, and freelook exists to leave that untouched. An implementation that leaked the camera's rotation into movement packets would fail, and would be a bug.

**Judgement, part 2: a fair player without it.** It fails. Vanilla already lets a player see directly behind them, through F5's front-facing third-person view. What freelook adds is a free choice of view angle, in any direction and under the mouse, while movement keeps its heading. A player without freelook can only get that view by turning, and turning changes where WASD takes them. That is exactly the capability MCC Island and Hoplite name, and the one Hypixel bans.

**The spec's own condition.** It fails too. Hypixel prohibits freelook, and so do MCC Island and Hoplite.

### 1.5 Recommendation: cut

**Judgement:** cut freelook from Phase 3, as the spec already provides:

- Remove user stories 21–23.
- Remove the freelook bullet from *The features*.
- Remove freelook's line from the shared-module test list.
- Amend ADR-0006 so that freelook no longer appears in its list of features ash ships, and record Hypixel's rule and the 2021-08-20 staff post as the reason.

Constraints that were considered and do not rescue it:

- **Behind a switch.** Rejected by the spec. Hypixel puts the responsibility on the player, so a switch left on is a banned player.
- **Off by default, on only where a server opts in.** This is the only constraint consistent with Hypixel's text. It needs a server-to-client permission channel, which the spec puts out of scope ("A protocol for servers to switch ash's features off"). No server speaks ash's protocol, so in Phase 3 this ships nothing.
- **Off on a list of known server addresses.** This is a protocol in all but name. It is brittle: networks have many addresses and proxies. A miss is a ban.

One narrower feature does stay inside every rule quoted here: a key that jumps straight to vanilla's F5 front view ("snaplook"). MCC Island explicitly allows *"Mods that allow you to skip any mode of the perspective toggle (F5)"*, and it stays within Hoplite's F5 line. It is not freelook and it is not in the brief, so it is noted here only as an option for a later brief.

---

## 2. Lithium for 1.21.11

### 2.1 The version to pin

The query was `GET https://api.modrinth.com/v2/project/lithium/version?game_versions=["1.21.11"]&loaders=["fabric"]`, with the `X-Ratelimit-Limit: 300` header observed **[PRACTICE]**. It returns five versions, all `release`, all `listed`:

- 0.21.0: 2025-12-09
- 0.21.1: 2025-12-12
- 0.21.2: 2025-12-24
- 0.21.3: 2026-02-07
- **0.21.4: 2026-03-11**

| Field | Value |
| --- | --- |
| Modrinth project | `gvQqBUqZ` (`lithium`) |
| Modrinth version | `mc1.21.11-0.21.4-fabric`, id `Ow7wA0kG`, "Lithium 0.21.4 for Fabric" |
| Mod version (`fabric.mod.json`) | `0.21.4+mc1.21.11` |
| Published | 2026-03-11T00:39:03Z |
| File | `lithium-fabric-0.21.4+mc1.21.11.jar` (primary, the only file) |
| URL | `https://cdn.modrinth.com/data/gvQqBUqZ/versions/Ow7wA0kG/lithium-fabric-0.21.4%2Bmc1.21.11.jar` |
| Size | 900,462 bytes |
| sha1 | `203bdcb26e97b3217b045e1182651a7d7b6462ec` |
| sha512 | `f14a5c3d2fad786347ca25083f902139694f618b7c103947f2fd067a7c5ee88a63e1ef8926f7d693ea79ed7d00f57317bae77ef9c2d630bf5ed01ac97a752b94` |
| Loaders | `fabric`, `quilt` |
| Environment (Modrinth) | `client_or_server_prefers_both` |
| Modrinth `dependencies` | `[]` |

I downloaded the file and hashed it. Both hashes match **[PRACTICE]**.

**Declared dependencies, from the shipped `fabric.mod.json` [PRACTICE]:**

```json
"environment": "*",
"depends":  { "fabricloader": ">=0.15.1", "minecraft": "1.21.11" },
"breaks":   { "optifabric": "*" },
"mixins":   [ "lithium.mixins.json", "lithium-fabric.mixins.json" ],
"accessWidener": "lithium.accesswidener"
```

- **No Fabric API.** Lithium's Modrinth page says the same: *"No other mods or additional setup (not even Fabric API!) is required"* ([modrinth.com/mod/lithium](https://modrinth.com/mod/lithium)) **[DOC]**.
- **Fabric Loader `>=0.15.1`, with no ceiling.** That accepts 0.19.3 and 0.19.5 alike. ash pins **0.19.5 on 1.21.11** (`client/gradle.properties` `loader_version=0.19.5`; `launcher/core/src/loader.rs`) and 0.19.3 on 1.8.9, where Lithium never runs. The jar's manifest records that it was built against `Fabric-Loader-Version: 0.18.2` with Loom 1.14.10 and Mixin `0.16.5+mixin.0.8.7`, in the `intermediary` namespace **[PRACTICE]**.
- **Minecraft is pinned exactly** to `1.21.11`, as research 0002 §7 found.

**The GitHub release exists and matches.**

- Tag `mc1.21.11-0.21.4` is commit `cdb5f8a8768446294797e2ca451d3fe1f1c29f62` (2026-03-10T22:45:46+01:00).
- The release, *"Lithium 0.21.4 for Minecraft 1.21.11 Fabric and Neoforge"*, was published 2026-03-11T00:38:57Z and is not a pre-release.
- Its assets are `LICENSE.md` (7,652 B), `lithium-fabric-0.21.4+mc1.21.11.jar` (900,462 B), an `-api` jar, and the NeoForge pair.
- The release jar has the same sha1 as Modrinth's: **the two are byte-identical** ([github.com/CaffeineMC/lithium/releases/tag/mc1.21.11-0.21.4](https://github.com/CaffeineMC/lithium/releases/tag/mc1.21.11-0.21.4)) **[PRACTICE]**.

**Maintenance.** Lithium's *Support Policy* wiki page (updated 2026-07-29) lists 1.21.11 as **"Long-term support"**: *"officially supported … but will generally only receive updates for bug fixes and maintenance"* ([wiki](https://github.com/CaffeineMC/lithium/wiki/Support-Policy)) **[DOC]**. The `1.21.11` branch is two commits ahead of the tag, both dated 2026-09-25: *"Fix movement notification for inventory minecarts"* and *"Fix two incorrect ChunkStatusTracker conditions"* **[PRACTICE]**. A 0.21.5 may follow, so re-query the pin when the ticket is implemented.

### 2.2 The licence as shipped

| Where | What it says |
| --- | --- |
| Jar `fabric.mod.json` | `"license": "LGPL-3.0-only"` **[PRACTICE]** |
| Jar `LICENSE.md` | The GNU LGPL v3 text, 165 lines, sha1 `a8a12e6867d7ee39c21d9b11a984066099b6fb6b`. **Byte-identical to `https://www.gnu.org/licenses/lgpl-3.0.txt`** **[PRACTICE]** |
| Repo `LICENSE.md` at tag `mc1.21.11-0.21.4` | Byte-identical to the jar's copy. It is the only licence file in the repo root **[PRACTICE]** |
| GitHub repo licence field | `LGPL-3.0` **[PRACTICE]** |
| Modrinth project | `LGPL-3.0-only`, "GNU Lesser General Public License v3.0 only" **[PRACTICE]** |
| README | *"Lithium is licensed under GNU LGPLv3"* **[DOC]** |

**ADR-0013's "LGPL-3.0-only" is confirmed.**

Three details matter for what ash ships:

1. **The jar contains no copy of the GNU GPL.** The only licence-like file in it is `LICENSE.md`, the LGPL text **[PRACTICE]**.
2. **Lithium carries no copyright notice anywhere.** No source file in the tag has a copyright header, and `LICENSE.md` is the bare FSF text **[PRACTICE]**. The only attribution is `fabric.mod.json`'s `"authors": ["JellySquid", "2No2Name"]`.
3. The jar also ships `lithium.accesswidener`. It widens only server and game-logic classes: `ChunkMap`, `ServerChunkCache`, `ServerLevel`, `PalettedContainer`, `VoxelShape`, `PoiSection`, `WalkNodeEvaluator` and similar **[PRACTICE]**.

### 2.3 What LGPL-3.0 requires when ash ships the jar unmodified

Quoted from [the LGPL-3.0 text](https://www.gnu.org/licenses/lgpl-3.0.txt) and [the GPL-3.0 text](https://www.gnu.org/licenses/gpl-3.0.txt) **[DOC]**. This is a reading of the text, not legal advice.

**If ash's client and Lithium form a "Combined Work"** (LGPL §0: *"a work produced by combining or linking an Application with the Library"*), LGPL §4 applies:

> a) Give prominent notice with each copy of the Combined Work that the Library is used in it and that the Library and its use are covered by this License.
> b) Accompany the Combined Work with a copy of the GNU GPL and this license document.
> c) For a Combined Work that displays copyright notices during execution, include the copyright notice for the Library among these notices, as well as a reference directing the user to the copies of the GNU GPL and this license document.
> d) … 1) Use a suitable shared library mechanism for linking with the Library …

§4(d)(1) is the separate, user-replaceable jar that ADR-0004 describes.

**Conveying Lithium's own object code**, the jar itself, falls under the GPL terms the LGPL incorporates:

- **§4 (verbatim copies)** requires ash to *"keep intact all notices … and give all recipients a copy of this License along with the Program"*.
- **§6** requires the Corresponding Source, *"in one of these ways"*:
  - **§6(a) and §6(b)**, the written offer, cover object code *"in, or embodied in, a physical product (including a physical distribution medium)"*.
  - **§6(d)** is the route for a download: *"Convey the object code by offering access from a designated place … and offer equivalent access to the Corresponding Source in the same way through the same place at no further charge. … If the place to copy the object code is a network server, the Corresponding Source may be on a different server (operated by you or a third party) … provided you maintain clear directions next to the object code saying where to find the Corresponding Source. Regardless of what server hosts the Corresponding Source, you remain obligated to ensure that it is available for as long as needed."*

What that means for the spec's line *"its licence text and a written offer for its source ship with ash"*:

- **Ship both texts.** LGPL §4(b) and GPL §4 require the GPL as well as the LGPL, and Lithium's jar supplies only the LGPL. ash must add `gpl-3.0.txt` itself.
- **A "written offer" is the physical-product mechanism.** For a launcher that downloads the jar, the matching obligation is §6(d): clear directions, next to where ash offers the jar, to Lithium's source at tag `mc1.21.11-0.21.4`, and responsibility for keeping it available. **Judgement:** if ash mirrors the jar (the spec leaves that to `docs/mirror.md`), mirror the tag's source archive with it, because §6(d) makes ash answerable if GitHub's copy disappears.
- **§4(c) has no notice to copy.** Lithium publishes no copyright notice, so ash can only attribute by name (JellySquid, 2No2Name and contributors) and link the project.
- **Whether ash "conveys" at all** when its launcher has the player's machine fetch the jar from `cdn.modrinth.com`, as opposed to hosting it, turns on GPL §0: *"To 'convey' a work means any kind of propagation that enables other parties to make or receive copies"*. Research 0002 §7 raised the same question. It is a lawyer's question, and nothing here settles it.

### 2.4 Its mixins

**Method.** I read the `@Mixin` target of every class listed in the two configs out of the shipped jar's bytecode (`javap -v`, the `RuntimeInvisibleAnnotations` attribute). I mapped the intermediary names to Mojang names with the 1.21.11 mappings Loom has cached for this repo (`~/.gradle/caches/fabric-loom/1.21.11/loom.mappings.1_21_11.layered+hash.2198-v2/mappings.tiny`). I then cross-checked the result against the source at the tag **[PRACTICE]**.

**The configs:**

| Config | `package` | `required` | `injectors.defaultRequire` | Other | Mixins |
| --- | --- | --- | --- | --- | --- |
| `lithium.mixins.json` | `net.caffeinemc.mods.lithium.mixin` | `true` | `1` | `plugin: LithiumMixinPlugin`, `compatibilityLevel: JAVA_21` | 260 common + 12 `client` |
| `lithium-fabric.mixins.json` | `net.caffeinemc.mods.lithium.fabric.mixin` | `true` | `1` | same plugin; `overwrites.conformVisibility: true` | 8 common, 0 client |

That is 280 mixins against 160 distinct vanilla classes. In the source, 40 mixin files use `@Overwrite` **[PRACTICE]**.

**What they target, grouped by Lithium's own option groups [PRACTICE]:**

| Group | Mixins | Where they land |
| --- | --- | --- |
| `ai` | 37 | Mob AI: `Brain`, behaviours, sensors, goals, `PoiManager`, pathfinding, raids |
| `alloc` | 15 | Allocation: `CompoundTag`, `ComposterBlock`, `ChunkMap`, `ClassInstanceMultiMap`, `EntitySection` |
| `block` | 33 | Hoppers, block entities, `FluidState` and `FlowingFluid`, container menus |
| `block_pattern_matching` | 2 | `BlockPattern`, `EndDragonFight` |
| `cached_hashcode` | 1 | `FlowingFluid` |
| `chunk` | 11 | `PalettedContainer`, bit storage, `LevelChunkSection`, `ClientLevel` (entity storage accessor) |
| `collections` | 11 | `ClassInstanceMultiMap`, `AttributeMap`, `SortedArraySet`, `EntityTickList` |
| `compat` | 1 | `LevelChunk` (WorldEdit compatibility, off by default) |
| `debug` | 3 | Chunk-packet palette checks: `ClientPacketListener`, `ClientboundLevelChunkPacketData`, `PalettedContainer` (**off by default**) |
| `entity` | 32 | `Entity` and `LivingEntity` collision and movement, equipment tracking, minecarts, boats |
| `experimental` | 11 | Client-tick skips and entity block caching (**off by default**) |
| `gen` | 1 | `NoiseBasedChunkGenerator` |
| `math` | 7 | `Direction`, `AxisCycle`, `BlockPos`, `AABB`, `Mth` |
| `minimal_nonvanilla` | 13 | Spawning, collisions, entity sections |
| `shapes` | 8 | `VoxelShape` and `Shapes` |
| `util` | 45 | Block-entity and entity-section tracking, inventory change listening |
| `world` | 49 | Block-entity ticking, chunk access, explosions, game events, `ServerLevel` |

Lithium is overwhelmingly server and game-logic code. By package, the targets sit in `net.minecraft.world.*` and `net.minecraft.server.*`, apart from:

- `net.minecraft.client.multiplayer` (3 mixins);
- `net.minecraft.network.protocol.game` (1);
- `net.minecraft.core`, `nbt`, `util` (utility maths and collections).

Nothing targets any class under `net.minecraft.client.renderer`, `net.minecraft.client.gui`, `net.minecraft.client.Camera`, `net.minecraft.client.MouseHandler`, `net.minecraft.client.player`, or `net.minecraft.client.multiplayer.PlayerInfo` **[PRACTICE]**.

**Against the areas ash's Phase 3 features touch:**

| ash's area | Any Lithium mixin? |
| --- | --- |
| HUD crosshair drawing (`Gui`, `GuiGraphics`) | **No** |
| `LivingEntityRenderer` or the overlay texture (`OverlayTexture`) | **No** |
| `Camera` | **No** |
| `MouseHandler` | **No** |
| `KeyboardInput` | **No** |
| Client handling of damage or hurt packets (`ClientPacketListener`) | **Not those methods.** The class has exactly one Lithium mixin, `debug.palette.ClientPacketListenerMixin`, on `updateLevelChunk(IILnet/minecraft/network/protocol/game/ClientboundLevelChunkPacketData;)V`. That is chunk data, not damage, and `mixin.debug` is `false` by default. |
| `PlayerInfo` latency | **No** |

**Adjacent, not overlapping.** Two vanilla classes are touched by Lithium, and a later ash ticket could plausibly touch them too:

- **`LivingEntity`**, 9 mixins, for example `entity.fast_hand_swing` → `updateSwingTime()`, equipment tracking, elytra and powder-snow checks.
- **`Entity`**, 12 mixins, including an `@Overwrite` of the static `collideBoundingBox(...)` from `entity.collisions.movement`.

Hit colour's hook lives in the renderer, not in `LivingEntity`, so neither is in Phase 3's path. Both configs are `required` with `defaultRequire: 1`, so an ash mixin that later disturbs one of these methods would crash Lithium on launch, not degrade.

**Client-only mixins on by default.** Of the 12 in the `client` list, only 4 run with a default config **[PRACTICE]**:

- `chunk.entity_class_groups.ClientLevelMixin`: exposes `ClientLevel`'s entity storage;
- `util.accessors.TransientEntitySectionManagerAccessor`;
- `world.block_entity_ticking.sleeping.chest_animation.ChestBlockEntityMixin`;
- `world.block_entity_ticking.sleeping.chest_animation.EnderChestBlockEntityMixin`.

The other 8 sit under `mixin.debug` (3) or `mixin.experimental` (5), and both are `false`.

This corrects item 3 of research 0002's Summary, which said *"Lithium's client list includes `experimental.client_tick.*` (12 client mixins)"*. Twelve is the size of the whole client list. `experimental.client_tick` has 6 mixins (5 in the client list, 1 common), and all of them are off by default.

### 2.5 Disabling a mixin group

**The player's file.** `LithiumMixinPlugin.onLoad` reads `new File("./config/lithium.properties")`, which is relative to the game process's working directory, not Fabric's config-dir API. If the file is missing, Lithium writes one that holds only a comment. Keys are `mixin.<group>=true|false` **[PRACTICE]**.

- The full list of 171 options, each with its default, ships in the jar as `lithium-fabric-mixin-config.md`.
- The defaults ship as `assets/lithium/lithium-mixin-config-default.properties`. Only three default to `false`: `mixin.compat.worldedit`, `mixin.debug` and `mixin.experimental` **[PRACTICE]**.
- A disabled parent disables everything beneath it. `LithiumConfig.getEffectiveOptionForMixin` walks the mixin's package path and returns the first disabled rule **[PRACTICE]**.
- Option dependencies are enforced from `lithium-mixin-config-dependencies.properties`. For example, `mixin.ai.pathing` requires `mixin.util.chunk_access` **[DOC]**.
- *"configuration options require a game restart to take effect"* **[DOC]**.

**Another mod's `fabric.mod.json`.** This is the useful route for ash, because it needs no player file. Lithium's wiki page *Disabling Lithium's Mixins using your mod's fabric.mod.json* ([wiki](https://github.com/CaffeineMC/lithium/wiki/Disabling-Lithium's-Mixins-using-your-mod's-fabric.mod.json-or-neoforge.mods.toml)) says **[DOC]**:

```json
"custom": {
  "lithium:options": {
    "mixin.alloc.composter": false
  }
}
```

It describes this as intended *"to avoid mod incompatibilities when there is no other way to make the mods work together"*. In the source (`FabricMixinOverrides.applyModOverrides`, `LithiumConfig.load` and `LithiumConfig.applyModOverride`):

- mod overrides are applied **after** the player's file, so a mod's `false` wins over a player's `true`;
- *"disabling the option takes precedence over enabling"* **[PRACTICE]**.

This does not conflict with ADR-0004's "no ash mixin targets a Lithium class", because it configures Lithium rather than patching it.

### 2.6 Is Lithium's client-side effect on frame rate meaningful?

From Lithium's own words only:

- Modrinth summary: *"No-compromises game logic optimization mod, useful for both single-player games and multi-player servers."* **[DOC]**
- Modrinth description: *"For multiplayer servers, administrators can expect a sizeable improvement to tick times … Even in single-player, Lithium helps to improve performance by optimizing the internal game server, which is used for 'ticking' the world. This can free up your computer's processor to focus on other tasks, resulting in **improved frame rates and increased responsiveness**."* And: *"you may want to check out Sodium, which improves rendering performance."* ([modrinth.com/mod/lithium](https://modrinth.com/mod/lithium)) **[DOC]**
- README: it works to *"optimize many areas of the game in order to provide better overall performance. It works on both the client and server"*, and *"It doesn't change any game mechanics or visuals"* **[DOC]**.
- Config docs, `mixin.experimental.client_tick`: *"Client-side only optimizations"*, in a group that is off by default **[DOC, PRACTICE]**.

**What Lithium claims:** a frame-rate benefit that comes indirectly, from the integrated server in single-player. **What it does not claim:** any frame-rate benefit for a client connected to a remote multiplayer server, where there is no integrated server. It sends readers to Sodium for rendering. Its own documentation gives no measurement either way.

**Judgement:** for ash's players, who mostly play on multiplayer servers, Lithium's own documentation does not support "the game runs faster" as a frame-rate claim. The spec's frame-time measurement is the place to find out. See *Confidence and gaps*.

---

## 3. The launcher's typefaces

### 3.1 Licence, Reserved Font Name, upstream and release

| | **Sora** | **Inter** | **JetBrains Mono** |
| --- | --- | --- | --- |
| Upstream repo | [github.com/sora-xor/sora-font](https://github.com/sora-xor/sora-font). The font's own copyright line names it, and Google Fonts' `ofl/sora/METADATA.pb` gives it as `source.repository_url` | [github.com/rsms/inter](https://github.com/rsms/inter) | [github.com/JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono) |
| Licence file | `OFL.txt` | `LICENSE.txt` | `OFL.txt` |
| Licence | SIL Open Font License **1.1** (the 26 February 2007 text) | SIL OFL **1.1** | SIL OFL **1.1** |
| Copyright line | *"Copyright 2019 The Sora Project Authors (https://github.com/sora-xor/sora-font)"* | *"Copyright (c) 2016 The Inter Project Authors (https://github.com/rsms/inter)"* | *"Copyright 2020 The JetBrains Mono Project Authors (https://github.com/JetBrains/JetBrainsMono)"* |
| Reserved Font Name in the licence | **None** | **None**, but see below | **None** |
| Trademark notice in the font (name ID 7) | None | *"Inter UI and Inter is a trademark of rsms."* | *"JetBrains Mono is a trademark of JetBrains s.r.o."* |
| Latest release | **No releases and no tags.** Head of `master` is `7f9a9c5d0ccd1c099cfac420aa27133df1c5fdc4` (2022-09-05). The woff2 files last changed on 2020-06-10, font version *"Version 2.000"*. Google Fonts builds from the same commit. | **v4.1**, 2024-11-16 (`Inter-4.1.zip`, 33,707,794 B). Fonts report *"Version 4.001;git-9221beed3"*. | **v2.304**, 2023-01-14 (`JetBrainsMono-2.304.zip`, 5,622,857 B). Fonts report *"Version 2.304"*. |
| Official woff2 | In the repo: `fonts/woff2/` | In the release zip: `web/`, with `inter.css` | In the release zip: `fonts/webfonts/` |

All from the repos' licence files, release listings and the font files' `name` tables, read with fontTools **[PRACTICE]**. GitHub identifies all three licences as `OFL-1.1`. The OFL body is identical in all three. Inter's differs only in a heading, which reads `PERMISSION AND CONDITIONS` for `PERMISSION & CONDITIONS` **[PRACTICE]**.

**Inter's Reserved Font Name: the README and the licence disagree.** Issue [#282](https://github.com/rsms/inter/issues/282) asked whether "Inter" is an RFN, because RFNs restrict subsetting. The author's reply, 2020-08-19: *"In regards to Inter, I'm totally fine with subsetting and reusing the name."* **[DOC]** The licence file's history **[PRACTICE]**:

- 2020-08-17 (`d94b865`): added `"Inter" is a Reserved Font Name.`
- 2020-08-25 (`ca221fe`): *"remove RFN from license, add trademark notice"*. The line became `"Inter" is trademark of Rasmus Andersson.`
- 2023-04-08 (`3ac1bd3`): moved the trademark notice out of `LICENSE.txt` into the README.

The README now reads: *"Inter a trademark of Rasmus Andersson (DBA: RSMS)"* and *'"Inter" is a Reserved Font Name by Rasmus Andersson'* **[DOC]**.

The OFL defines an RFN as *"any names specified as such after the copyright statement(s)"* in the licence **[DOC]**. The licence names none, so the licence reserves no name. The README's claim and the trademark still stand as statements of the author's wishes.

### 3.2 The weights ash needs, as woff2

| Need | Official static woff2 | Official variable woff2 | Use |
| --- | --- | --- | --- |
| **Sora 500** | **None.** `fonts/woff2/` has Thin, ExtraLight, Light, Regular, SemiBold, Bold and ExtraBold, and no Medium. The only static Medium is `fonts/ttf/v2.1beta/Sora-Medium.ttf`, a TTF in a beta folder whose name table says *"Version 1.000"*. | `fonts/woff2/Sora[wght].woff2`: `wght` 100–800, 7 named instances, none at 500. The axis is continuous, so 500 renders. 59,912 B, 378 codepoints. | **The variable file**, pinned by commit: `https://raw.githubusercontent.com/sora-xor/sora-font/7f9a9c5d0ccd1c099cfac420aa27133df1c5fdc4/fonts/woff2/Sora%5Bwght%5D.woff2`, sha256 `ab33ad8fceb925a49379a69245892da448b49229c3c97ffb28cc9d74aa95fc36` |
| **Inter 400** | `web/Inter-Regular.woff2`: 111,268 B, weight class 400, 2,852 codepoints. sha256 `e06f6b1bc553aaea4e4668023ed0ab0a147129c3107f511bc7d03d361b0ae085` | `web/InterVariable.woff2`: `opsz` 14–32 and `wght` 100–900, named Regular and Medium instances. 352,240 B. sha256 `693b77d4f32ee9b8bfc995589b5fad5e99adf2832738661f5402f9978429a8e3` | Either. The two static files come to 225,616 B. |
| **Inter 500** | `web/Inter-Medium.woff2`: 114,348 B, weight class 500. sha256 `0ff3e94614e1493eb556314fd247ae6c4a85a7783b4cc86be539940cf83f2a48` | (as above) | |
| **JetBrains Mono 500** | `fonts/webfonts/JetBrainsMono-Medium.woff2` in the v2.304 zip: 93,824 B, weight class 500, 1,363 codepoints. sha256 `086c48dfbea9ddaff1320f7e09399b8e2924e88ce67453721255db3bdbb5a353` | Not in the v2.304 release. That zip has the variable font only as TTF (`fonts/variable/JetBrainsMono[wght].ttf`, `wght` 100–800). | **The v2.304 static Medium.** |

All **[PRACTICE]**. The release zips' sha256s are:

- `Inter-4.1.zip`: `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`
- `JetBrainsMono-2.304.zip`: `6f6376c6ed2960ea8a963cd7387ec9d76e3f629125bc33d1fdcd7eb7012f7bbf`

Every file has `fsType` 0, meaning installable embedding with no restriction.

**Do not take JetBrains Mono from `master`.** The repo's `fonts/webfonts/` on `master` does carry a variable `JetBrainsMono[wght].woff2`, but `master` holds an **unreleased 2.305**. Its `JetBrainsMono-Medium.woff2` reports weight class **436** rather than 500 **[PRACTICE]**. Pin the release.

### 3.3 What the OFL 1.1 requires of an app that bundles the fonts

From the licence text (identical in all three files, and at [openfontlicense.org](https://openfontlicense.org/open-font-license-official-text/)) and the *OFL-FAQ* version 1.1-update7, November 2023 ([openfontlicense.org/ofl-faq](https://openfontlicense.org/ofl-faq/)). `scripts.sil.org/OFL` and `scripts.sil.org/OFL-FAQ_web` now redirect to `openfontlicense.org`. All **[DOC]**.

- **Bundling is permitted.** Condition 2: *"Original or Modified Versions of the Font Software may be bundled, redistributed and/or sold with any software, provided that each copy contains the above copyright notice and this license."*
  - FAQ 1.3: the program itself need not be open source; *"The intent of the license is to allow aggregation or bundling with software under restricted licensing as well."*
  - FAQ 1.4: selling a software package that includes the fonts is allowed.
- **Ship the licence.** Condition 2 allows the notice and licence as *"stand-alone text files, human-readable headers or in the appropriate machine-readable metadata fields … as long as those fields can be easily viewed by the user"*. The fonts' own metadata carries only a one-line licence description and URL, not the licence text. So ash must ship the three licence files.
  - FAQ 1.20: *"At a minimum you must include the copyright statement, the license notice and the license text"*, and *"You do not, however, need to include the full contents of the font package - only the fonts you use"*.
- **Never sell the fonts by themselves.** Condition 1: *"Neither the Font Software nor any of its individual components, in Original or Modified Versions, may be sold by itself."*
  - FAQ 1.6: *"The intent is to keep people from making money by simply redistributing the fonts."*
  - Relevant to Phase 4's paid cosmetics: a font can never be a purchasable item on its own.
- **No endorsement.** Condition 4: the authors' names *"shall not be used to promote, endorse or advertise any Modified Version, except to acknowledge the contribution(s)"*. FAQ 1.19 allows a thanks list, but not marketing copy such as "designed by …".
- **The fonts stay OFL.** Condition 5: *"must be distributed entirely under this license"*. FAQ 1.3: only the font portions, not the app.
- **Reserved Font Names.** Condition 3: *"No Modified Version of the Font Software may use the Reserved Font Name(s) unless explicit written permission is granted by the corresponding Copyright Holder. This restriction only applies to the primary font name as presented to the users."*
- **Trademarks survive modification.** FAQ 3.7: *"Any trademark notices must remain in any derivative fonts … The OFL does not grant any rights under trademark law."*

### 3.4 Does subsetting to Latin count as a modification?

**Yes, under the OFL, for all three fonts.**

- The licence defines a Modified Version as *"any derivative made by adding to, deleting, or substituting … any of the components of the Original Version, by changing formats or by porting"*.
- FAQ 2.6: *"Is subsetting a webfont considered modification? Yes. Removing any parts of the font … including unused glyphs and smart font code, is considered modification. This is permitted by the OFL but would not normally allow the use of RFNs."*
- FAQ 2.5: optimised webfonts *"are Modified Versions and so must follow OFL requirements like appropriate renaming"*, where an RFN exists.
- Format conversion: FAQ 2.2 says *"A change in font format normally is considered modification"*. FAQ 2.2.1 says a WOFF or WOFF2 made with nothing changed but compression, and with the original metadata intact, is not.

**Whether renaming follows depends on an RFN, and none of the three licences declares one.**

- FAQ 5.6: an author may *"not declare any Reserved Font Names"* and let the font *"be changed and modified … without having to change the original name"*.
- FAQ 3.8: renaming on change is required *"Only if there are declared RFNs"*.

| | Subsetting is a Modified Version? | May the subset keep the name? | What must stay |
| --- | --- | --- | --- |
| **Sora** | Yes | Yes. No RFN is declared. | Copyright and OFL. Condition 5: it stays OFL. |
| **Inter** | Yes | Yes, under the licence, which declares no RFN. The README asserts an RFN, but the author wrote on 2020-08-19 that he is *"totally fine with subsetting and reusing the name"*. | Copyright, OFL, and the trademark notice in name ID 7 (FAQ 3.7) |
| **JetBrains Mono** | Yes | Yes. No RFN is declared. | Copyright, OFL, and the trademark notice in name ID 7 (FAQ 3.7) |

**Judgement:** do not subset.

- Sora's file already covers only 378 codepoints, and JetBrains Mono's Medium is 94 KB. The saving is small next to a desktop installer.
- Unmodified official files avoid every naming and metadata question.
- They also avoid the Inter README-versus-licence ambiguity entirely.

If a later size budget forces subsetting, the licence allows it under the original names. Keep each font's name table intact, including the trademark lines.

### 3.5 Recommendation

Bundle these files unmodified, with each project's licence file beside them:

| Typeface | File | Source |
| --- | --- | --- |
| Sora 500 | `Sora[wght].woff2` | `sora-font` at `7f9a9c5`, with `OFL.txt` |
| Inter 400 and 500 | `Inter-Regular.woff2` and `Inter-Medium.woff2` (or `InterVariable.woff2`) | Inter v4.1, with `LICENSE.txt` |
| JetBrains Mono 500 | `JetBrainsMono-Medium.woff2` | JetBrains Mono v2.304, with `OFL.txt` |

List them where the launcher lists shipped licences (user story 46 covers mods, and the same screen should cover fonts). Pin each file by sha256, as the depot does for everything else.

---

## Sources

### Freelook

- Hypixel, *Hypixel Allowed Modifications*: [support.hypixel.net/hc/en-us/articles/6472550754962](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications). Read via `support.hypixel.net/api/v2/help_center/en-us/articles/6472550754962.json` (created 2022-07-06, edited 2023-02-03, updated 2025-02-24).
- Hypixel, *Hypixel Server Rules*: [support.hypixel.net/hc/en-us/articles/4427624493330](https://support.hypixel.net/hc/en-us/articles/4427624493330-Hypixel-Server-Rules), edited 2024-12-09. Read via the same API.
- Hypixel Team, *Update - Perspective/Freelook Mods*, 2021-08-20: [hypixel.net/threads/update-perspective-freelook-mods.4487862](https://hypixel.net/threads/update-perspective-freelook-mods.4487862/).
- Lunar Client, *Patch Notes #14* (2021-08-29): [lunarclient.com/news/patch-notes-14](https://www.lunarclient.com/news/patch-notes-14).
- Lunar Client, *Why Lunar Client is the best for playing Hoplite* (2024-01-10): [source](https://www.lunarclient.com/news/why-lunar-client-is-the-best-for-playing-hoplite).
- Lunar Client, *Freelook Mod vs. Snaplook Mod* (2024-03-21): [source](https://www.lunarclient.com/news/free-look-mod-vs-snap-look-mod).
- Lunar Client, *Lunar Client acquires Badlion Client* (2025-03-12): [source](https://www.lunarclient.com/news/lunar-client-acquires-badlion-client).
- Lunar Client, *Badlion Acquisition: Next Steps* (2025-03-28): [source](https://www.lunarclient.com/news/badlion-acquisition-next-steps).
- Lunar Client, *How to Launch Badlion Client on Lunar Client* (2025-07-22): [source](https://www.lunarclient.com/news/how-to-launch-badlion-client-on-lunar-client-step-by-step-guide).
- Apollo: [github.com/LunarClient/Apollo](https://github.com/LunarClient/Apollo) (MIT, v1.2.9 2026-08-17). `ModFreelook.java`, `docs/developers/modules/modsetting.mdx`. [Mod Setting module](https://lunarclient.dev/apollo/developers/modules/modsetting), [Freelook options](https://lunarclient.dev/apollo/developers/mods/freelook).
- Badlion Client Mod API: [github.com/BadlionClient/BadlionClientModAPI](https://github.com/BadlionClient/BadlionClientModAPI), `readme.md` (MIT, pushed 2025-11-05).
- Feather Server API: [github.com/FeatherMC/feather-server-api](https://github.com/FeatherMC/feather-server-api), `docs/docs/server-api/mods.md`, commit `c960e24` (2025-09-21). Published at [docs.feathermc.com/server-api/mods](https://docs.feathermc.com/server-api/mods/).
- MCC Island, *Approved Mods* (updated 2026-08-27): [mcchampionship.com/help/mods](https://mcchampionship.com/help/mods/). Retrieved through `r.jina.ai`.
- Hoplite, *Rules*: [hoplite.gg/rules](https://www.hoplite.gg/rules).
- CubeCraft, *Allowed Mods and Clients* (updated 2026-09-23): [cubecraft.net/threads/allowed-mods-and-clients.228596](https://www.cubecraft.net/threads/allowed-mods-and-clients.228596/). Retrieved through `r.jina.ai`.
- PikaNetwork, *Allowed and Disallowed Modifications* (modified 2026-09-25): [support.pika-network.net/articles/allowed-and-disallowed-modifications](https://support.pika-network.net/articles/allowed-and-disallowed-modifications). Linked from [pika-network.net/rules](https://pika-network.net/rules).
- PvPHQ, *Rules* (July 2026): [pvphq.com/rules](https://pvphq.com/rules), with its bundle `assets/RulesPage-BG_4Tz6R.js`. Better Third Person's own description: [modrinth.com/mod/better-third-person](https://modrinth.com/mod/better-third-person).
- PvP Land, *Rules* (2026-07-26): [pvp.land/rules](https://pvp.land/rules).
- Minemen Club, *Rules*: [minemen.club/rules](https://minemen.club/rules), and [minemen.club/support](https://minemen.club/support).
- BlocksMC, *Rules* (page dated 2024-09-29): Wayback capture 2025-03-19, [web.archive.org/web/20250319225815/https://blocksmc.com/rules](https://web.archive.org/web/20250319225815/https://blocksmc.com/rules).
- Lunar Network: [lunar.gg](https://www.lunar.gg/), *Farewell - Onto The Next Chapter*.
- The vanilla 1.21.11 client, `net.minecraft.client.Options`, read with `javap` from Loom's mapped jar. It has `toggleSprint` (used in *Confidence and gaps*).

### Lithium

- Modrinth API: `https://api.modrinth.com/v2/project/lithium/version?game_versions=["1.21.11"]&loaders=["fabric"]` and `https://api.modrinth.com/v2/project/lithium`.
- The jar itself, `lithium-fabric-0.21.4+mc1.21.11.jar`, downloaded from Modrinth and from the GitHub release. Read: `fabric.mod.json`, `META-INF/MANIFEST.MF`, `LICENSE.md`, `lithium.mixins.json`, `lithium-fabric.mixins.json`, `lithium.accesswidener`, `lithium-fabric-mixin-config.md`, `assets/lithium/lithium-mixin-config-default.properties`, and every mixin class's bytecode.
- [CaffeineMC/lithium](https://github.com/CaffeineMC/lithium) at tag `mc1.21.11-0.21.4` (`cdb5f8a`). Read: `README.md`, `LICENSE.md`, `LithiumMixinPlugin.java`, `common/config/LithiumConfig.java`, `common/config/Option.java`, `fabric/FabricMixinOverrides.java`, and the mixin sources. Also releases, tags, and the `1.21.11` branch compared with the tag.
- Lithium wiki ([github.com/CaffeineMC/lithium.wiki](https://github.com/CaffeineMC/lithium/wiki)): *Configuration-File*, *Disabling Lithium's Mixins using your mod's fabric.mod.json or neoforge.mods.toml*, *Support-Policy* (updated 2026-07-29).
- [GNU LGPL-3.0](https://www.gnu.org/licenses/lgpl-3.0.txt) §0, §4. [GNU GPL-3.0](https://www.gnu.org/licenses/gpl-3.0.txt) §0, §1, §4, §5, §6.
- ash's own pins: `client/gradle.properties`, `launcher/core/src/loader.rs`.

### Typefaces

- [sora-xor/sora-font](https://github.com/sora-xor/sora-font) at `7f9a9c5`. Read: `OFL.txt`, `README.md`, the file tree, commit history for `OFL.txt` and `fonts/woff2`, and the fonts `Sora[wght].woff2`, `Sora-Regular.woff2`, `Sora-SemiBold.woff2` and `v2.1beta/Sora-Medium.ttf`.
- [google/fonts `ofl/sora/METADATA.pb`](https://github.com/google/fonts/blob/main/ofl/sora/METADATA.pb): upstream repository and commit.
- [rsms/inter](https://github.com/rsms/inter). Read: `LICENSE.txt` and its history (`d94b865`, `ca221fe`, `3ac1bd3`), `README.md`, [issue #282](https://github.com/rsms/inter/issues/282), and release v4.1 (`Inter-4.1.zip`: `help.txt`, `web/`).
- [JetBrains/JetBrainsMono](https://github.com/JetBrains/JetBrainsMono). Read: `OFL.txt` and its history, release v2.304 (`JetBrainsMono-2.304.zip`), and `master`'s `fonts/webfonts/`.
- SIL, *SIL Open Font License 1.1* ([openfontlicense.org](https://openfontlicense.org/open-font-license-official-text/)) and *OFL-FAQ* 1.1-update7, November 2023 ([openfontlicense.org/ofl-faq](https://openfontlicense.org/ofl-faq/)), entries 1.3, 1.4, 1.6, 1.19, 1.20, 2.2, 2.2.1, 2.5, 2.6, 3.7, 3.8 and 5.6.
- Font metadata read with fontTools 4.66.1: name IDs 0, 1, 2, 5, 7, 13, 14, 16 and 17; `OS/2` `usWeightClass` and `fsType`; `fvar` axes and instances; `cmap` size.

---

## Confidence and gaps

### Could not establish

1. **Minemen Club has no public mod policy that I could reach.** Its rules page lists only *"Use of Cheat(s) or Program(s) to gain an Unfair Advantage"*. A Google Sites page titled *"Minemen | Rules & FAQ"* requires a Google sign-in, and I could not tell whether it is official. Its Discord was not examined.
2. **BlocksMC's current page could not be read.** The live site returns a Cloudflare challenge to every non-browser client, including a reader proxy. The newest readable copy is a Wayback capture from 2025-03-19 of a page dated 2024-09-29. It has a general "no advantage" rule and does not name freelook.
3. **No first-party Badlion or Feather statement names the servers that disable their freelook.** Both publish the mechanism: Badlion's `Perspective` in its Mod API, and Feather's `perspective` ("Freelook") in its Server API. Badlion's forum is behind a Cloudflare challenge, and its support site now redirects to Lunar's. Lunar is the only one of the three that names servers in its own publications: Hypixel and Hoplite.
4. **Whether PvP Land, PvPHQ or Minemen Club switch Lunar's freelook off through Apollo** is invisible from outside. Their rules allow Lunar's bundled mods (PvP Land) or a freelook-type mod (PvPHQ), but a server can still disable a mod per player.
5. **How Hypixel blocks freelook in clients without a server API** is described only by players **[COMMUNITY]**. It does not change the finding, because the written rule binds the player regardless.
6. **The perspective clause on Hypixel's Allowed Modifications page cannot be dated more precisely** than the article's own metadata allows: created 2022-07-06, last edited 2023-02-03. The staff post that applies it to freelook is dated 2021-08-20.
7. **Three rules pages (MCC Island, CubeCraft, PvPHQ) could not be fetched directly.** MCC Island and CubeCraft refuse non-browser clients, so their text was read through a reader proxy (`r.jina.ai`) that renders the live page. PvPHQ's rules were read from the site's own JavaScript bundle. The text is the sites' own, but it did not come straight from their servers.
8. **Lithium has no copyright notice to reproduce.** LGPL-3.0 §4(c) speaks of *"the copyright notice for the Library"*, and Lithium publishes none. Attribution by author name and link is the most that can be done.
9. **Whether ash "conveys" Lithium when its launcher downloads it from Modrinth** rather than hosting it is a legal question. §2.3 quotes the definitions, and they do not decide it.

### Contradicts or corrects the spec, the ADRs, or earlier research

10. **ADR-0006 names freelook among the features ash ships.** The finding requires the amendment the spec already anticipates. User stories 21–23, the *Freelook* feature bullet and the freelook line in the shared-module tests go with it.
11. **The spec says Lithium's "licence text and a written offer for its source ship with ash".** Two corrections:
    - LGPL-3.0 §4(b), and GPL §4 for the jar itself, require the **GPL text as well**, and the Lithium jar supplies only the LGPL.
    - A "written offer" is GPL §6(b), which applies to object code *"in, or embodied in, a physical product"*. For a download, the matching route is §6(d): clear directions to the source, next to where the jar is offered, and responsibility for keeping it available.
12. **The brief said ash pins Fabric Loader 0.19.3.** On 1.21.11 ash pins **0.19.5**, and 0.19.3 is the 1.8.9 pin. Lithium accepts both (`>=0.15.1`).
13. **The spec expects Sora 500 as a bundled local file.** Upstream publishes no static Sora Medium woff2. The official route to 500 is the variable `Sora[wght].woff2`.
14. **User story 39 ("the game runs faster") is not supported by Lithium's own documentation for multiplayer play.** Lithium attributes frame-rate gains to the integrated server in single-player. Its client-only optimisations are experimental and off by default, and it sends readers to Sodium for rendering. **Judgement:** run the spec's frame-time measurement on 1.21.11 with and without Lithium on a multiplayer server before claiming a frame-rate benefit. Otherwise describe Lithium's benefit as tick and single-player performance.
15. **Research 0002 said "Lithium's client list includes `experimental.client_tick.*` (12 client mixins)".** Twelve is the whole client list. `experimental.client_tick` has 6 mixins, and all are off by default, because `mixin.experimental=false`. With a default config, Lithium runs 4 client-only mixins, all chunk, entity-storage or block-entity bookkeeping.

### Outside the three questions, found in the same sources

16. **Hypixel's page lists "auto-sprint" as disallowed automation.** Its Disallowed section reads: *"anything which automates any player gameplay action is strictly disallowed … This includes things such as (but not limited to) auto/burst clicking buttons or macros, auto-sprint, and aim assists."* ash shipped toggle sprint in Phase 2.
    - Toggle sprint is not named either way on Hypixel's page, and "auto-sprint" is not defined there.
    - Vanilla 1.21.11 has its own toggle-sprint option (`Options.toggleSprint`, read from the game jar) **[PRACTICE]**.
    - CubeCraft and PikaNetwork list "Toggle sprint" as allowed, and Badlion's Mod API treats `ToggleSprint` as a mod whose sub-features a server can disable **[DOC]**.
    - This document does not settle whether ash's toggle sprint on 1.8.9 is "auto-sprint" in Hypixel's sense. ADR-0006's test deserves an explicit answer on it.
17. **The same Hypixel page is the bar for the hit indicator.** Cosmetic HUD mods must work *"without adding extra information which would normally be unavailable to the player"*, and it names *"other player health/armor indicators, player distance/range"* as disallowed. The spec's design, which confirms a hit the client already receives and never shows health or damage, is consistent with that wording.
18. **Inter's README asserts a Reserved Font Name that its licence does not declare**, and has not since 2020-08-25. The licence governs, and the author has permitted subsetting under the name in writing. Shipping the official files unmodified avoids the question altogether.
19. **JetBrains Mono's `master` is ahead of its latest release.** It holds an unreleased 2.305 whose Medium reports weight class 436. Take the fonts from the v2.304 release, not from the repository head.
20. **Lithium's `1.21.11` branch has two unreleased fixes** (2026-09-25). Re-query Modrinth when the Lithium ticket is implemented, in case 0.21.5 has shipped.
