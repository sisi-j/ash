# Where freelook's block list and snaplook meet the game

Research date: 2026-10-06.

This answers item 8 of Phase 3's Further Notes (`docs/specs/0003-phase-3-the-full-client.md`), for #38. It asks three things:
- where each target keeps the address the player connected with, and the server's own name for itself;
- whether the name each listed server gives identifies it reliably;
- where each target keeps its camera mode.

It also records the camera and mouse hooks freelook uses on 1.21.11.

Every claim is labelled as `docs/research/0004` labels them:
- **[PRACTICE]**: read directly from this build's mapped game jars with `javap`, or observed from the server itself.
- **[DOC]**: the owner's own published text.
- **[COMMUNITY]**: a secondary source only, used as a lead.

Where this document gives an opinion, it says **Judgement**.

The jars are Loom's mapped merged jars in `client/.gradle/loom-cache`:
- 1.21.11, Mojang's names;
- 1.8.9, Legacy Yarn build 604.

---

## Summary

| Need | 1.21.11 | 1.8.9 |
| --- | --- | --- |
| The address the player connected with | `Minecraft.getCurrentServer().ip`, public, set for every join (list, direct connect, quick play) | `MinecraftClient.getCurrentServerEntry().address` for a list or direct join; for a join from launch arguments (`--server`), the private field `MinecraftClient.serverAddress`, read with an accessor |
| The server's own name for itself (its brand) | `ClientPacketListener.serverBrand()`, public | `ClientPlayerEntity.getServerBrand()`, public, set from the `MC\|Brand` channel |
| What each listed server's brand is | **Not established.** It arrives only after logging in. ash logs it on every join, so one join per server establishes it | the same |
| Camera mode | `Options.getCameraType()` / `setCameraType(CameraType)` | `GameOptions.perspective`, a public `int` (0 first person, 1 back, 2 front) |
| Freelook's camera | `Camera.setup` takes its rotation from `Entity.getViewYRot(float)` and `getViewXRot(float)` | not this ticket (#39, and `docs/research/0004`) |
| Freelook's mouse | `MouseHandler.turnPlayer(double)` hands the mouse to the player through one `LocalPlayer.turn(double, double)` call | not this ticket |

**Judgement:** match listed servers by the address the player connected with, including subdomains, as the spec says. Add a brand match for a server only once its brand has been seen. All three listed servers keep every address the player is likely to type, and every SRV target, inside one domain each (§3).

## 1. The address the player connected with

### 1.21.11

- `Minecraft.getCurrentServer()` returns the `ServerData` of the server being played. Its `ip` field is the address as the player wrote it **[PRACTICE]**.
- `ConnectScreen.startConnecting(Screen, Minecraft, ServerAddress, ServerData, boolean, TransferState)` is the one way into a server, and it is handed that `ServerData` **[PRACTICE]**. Every join goes through it:
  - the server list;
  - direct connect;
  - `--quickPlayMultiplayer`, which is ash's Join.
- `isLocalServer()` / `hasSingleplayerServer()` tell singleplayer apart **[PRACTICE]**.

No mixin is needed.

### 1.8.9

- **A join from the server list or direct connect** goes through `ConnectScreen(Screen, MinecraftClient, ServerInfo)` **[PRACTICE]**. That constructor:
  - calls `setCurrentServerEntry(info)` first;
  - only then resolves `info.address` with `ServerAddress.parse`, which follows SRV;
  - and connects to the result.

  So `getCurrentServerEntry().address` is the address as typed, before any SRV redirect.
- **A join from launch arguments** goes through `ConnectScreen(Screen, MinecraftClient, String, int)`, built at startup from the private fields `serverAddress` and `serverPort` **[PRACTICE]**. No server entry is set. This is how ash's Join reaches 1.8.9.
  - ash resolves any SRV record itself first, because 1.8.9's `--server` connects to exactly the host it is given (#74). So for a server reached through SRV, this field holds the SRV **target**, not what the player typed.
  - For the three listed servers that target is still inside the listed domain (§3).
- `isInSingleplayer()` tells singleplayer apart **[PRACTICE]**.

**Judgement:** on 1.8.9 the address is the server entry's when there is one, else `serverAddress`, read through an `@Accessor`. An accessor cannot miss the way an injector can: it either applies or the mixin fails to load, and `MixinFeature` already reports that.

## 2. The server's own name for itself

- **1.21.11:** `ClientCommonPacketListenerImpl.serverBrand()`, inherited by `ClientPacketListener` **[PRACTICE]**.
- **1.8.9:** `ClientPlayNetworkHandler.onCustomPayload` reads the `MC|Brand` channel and calls `ClientPlayerEntity.setServerBrand`. `getServerBrand()` is public **[PRACTICE]**.

**What the listed servers send is not established.**
- The brand only arrives after login, so finding it out means joining each server with an account. Doing that on the product owner's account is theirs to decide, not this research's.
- Published sources describe the brand only in general terms. A proxy such as BungeeCord or Velocity sends its own long version string unless the network replaces it, and plugins exist to replace it **[COMMUNITY]** ([Custom F3 Brand](https://hangar.papermc.io/LoreSchaeffer/CustomF3Brand), [F3Name](https://dev.curseforge.com/projects/f3name-edit-your-server-brand-in-debug-screen)).

So the block list **does not match by brand yet**. ash's client logs the brand on every join, with the address, so the manual acceptance pass can read each listed server's brand from the game's log. A brand match is added only from that.

**Judgement:** not matching by brand costs nothing on the addresses players actually use (§3). Guessing a brand could switch freelook off on a server that allows it. The brand is the fallback for an address the domain match misses, such as a raw IP.

## 3. The listed servers, as they answer

Each was looked up in DNS and sent the game's own status request (protocol 774) on 2026-10-06 **[PRACTICE]**. That is what the game's multiplayer screen sends, so it tells each server nothing a player opening that screen would not.

| Server | Rules page that bans freelook | Addresses seen | SRV | Status: version name / MOTD |
| --- | --- | --- | --- | --- |
| Hypixel | [Hypixel Allowed Modifications](https://support.hypixel.net/hc/en-us/articles/6472550754962-Hypixel-Allowed-Modifications) **[DOC]** | `mc.hypixel.net` | `hypixel.net` → `mc.hypixel.net:25565` | "Requires MC 1.8 / 1.21" / "Hypixel Network …" |
| MCC Island | [MCC Island Approved Mods](https://mcchampionship.com/help/mods/) **[DOC]** | `play.mccisland.net` | `mccisland.net` → `play.mccisland.net:25565` | "1.21.11+" / "MCCISLAND …" |
| Hoplite | [Hoplite Rules](https://www.hoplite.gg/rules) **[DOC]** | `play.hoplite.gg` | `hoplite.gg` → `java.hoplite.gg:25565`, which did not answer within 6 s from here | "Velocity 1.7.2-26.3" / "Hoplite Network …" |

So the block list's domains are `hypixel.net`, `mccisland.net` and `hoplite.gg`. A domain matches itself and its subdomains, and never a look-alike such as `nothypixel.net` or `hypixel.net.example`.

**The status response is not the brand.**
- Its version name is free text the server chooses, and two of the three put a version range in it.
- Its MOTD is decorated text that changes with events.
- Neither is available once in game.

So neither is used for matching.

## 4. Camera mode, for snaplook (#40)

- **1.21.11:** `Options.getCameraType()` and `Options.setCameraType(CameraType)`. `MouseHandler.turnPlayer` asks `getCameraType().isFirstPerson()` for its smoothing **[PRACTICE]**.
- **1.8.9:** `GameOptions.perspective`, a public `int`, and `togglePerspectiveKey` **[PRACTICE]**.

## 5. Freelook's hooks on 1.21.11

**The mouse.**
- `MouseHandler.turnPlayer(double)` works out the turn from sensitivity, smoothing and inversion.
- It then makes exactly one call that moves the player: `LocalPlayer.turn(double, double)` **[PRACTICE]**.
- Wrapping that call is the only place freelook needs: while it is held, the turn goes to freelook's own camera angles instead.
- The player's rotation, which movement packets carry, is never written.

**The camera.**
- `Camera.setup(Level, Entity, boolean detached, boolean mirror, float partialTick)` takes its rotation from `Entity.getViewYRot(float)` and `Entity.getViewXRot(float)`. It does so twice, once in the minecart branch and once otherwise, and passes the result to `setRotation` **[PRACTICE]**.
- Its front-view mirror and its third-person pull-back (`move(-getMaxZoom(…), 0, 0)`) both work from that rotation.

So wrapping those two calls turns the camera, and the third-person orbit follows.

**Judgement:** while freelook is held, switch a first-person view to the game's own third-person back view and orbit it, restoring the player's view on release. That is what freelook mods do, and it is the capability the listed servers ban by name. Switching the camera mode is client-only; the server never hears of it.

## Sources

- The mapped 1.21.11 and 1.8.9 jars in Loom's cache, read with `javap -p -c`:
  - 1.21.11: `Camera`, `MouseHandler`, `Minecraft`, `ServerData`, `ConnectScreen`, `ClientCommonPacketListenerImpl`, `Options`;
  - 1.8.9: `MinecraftClient`, `ConnectScreen`, `ClientPlayNetworkHandler`, `ClientPlayerEntity`, `ServerInfo`, `GameOptions`.
- DNS lookups and the status request, sent with a small Node script on 2026-10-06.
- The three rules pages in §3, as cited by `docs/research/0006`.

## Addendum: freelook's default key

- Left Alt (GLFW key 342). 1.21.11's `Options` builds every default `KeyMapping` with its key code as a constant. Across `Options` and `ToggleKeyMapping`:
  - Left Shift (340, Sneak) and Left Control (341, Sprint) each appear once;
  - Left Alt never does **[PRACTICE]**.
- Players can rebind it in Controls, under Misc.

## 6. Freelook on 1.8.9 (#39)

**The mouse.**
- `GameRenderer.render(float, long)` turns the player with `ClientPlayerEntity.increaseTransforms(float, float)` at two sites: the smooth-camera branch and the normal one **[PRACTICE]**.
- `Entity.increaseTransforms` adds `yaw * 0.15` to yaw, **subtracts** `pitch * 0.15` from pitch, and clamps pitch to ±90 **[PRACTICE]**.
- So freelook takes the call at both sites and negates the pitch, and the mouse turns the same way on both targets.

**The view.**
- `GameRenderer.renderWorld(float, long)` first calls `updateTargetedEntity(float)`, then draws the world through `renderWorld(int, float, long)` **[PRACTICE]**.
- Everything that decides the view reads the camera entity's own `yaw`, `pitch`, `prevYaw` and `prevPitch` fields (`docs/research/0004`, section 5):
  - the view transform;
  - which chunks are visible (`WorldRenderer.setupTerrain`);
  - particle facing.
- So for 1.8.9, freelook swaps freelook's angles into those four fields right after `updateTargetedEntity`, and puts the entity's own values back when `renderWorld` returns. Two consequences:
  - **Targeting is untouched.** It runs before the swap, so a click still hits where the player faces.
  - **No tick ever sees the swap.** The swap is undone before any tick, where movement packets are sent. The start of every client tick also puts the fields back, in case a frame ever ended early.
- **Judgement:** swapping the fields is simpler and safer than wrapping each read, and it brings the chunk check along for free. Wrapping `getfield` sites would also depend on MixinExtras supporting field access, which `docs/research/0004` left unchecked.
- **The one visible difference from 1.21.11:** in the third-person view the player's own model tilts its head with the camera's pitch, because the model is drawn inside the swap. It is local to the player's own screen.

**Camera mode.**
- `GameOptions.perspective`: 0 is first person, 1 behind **[PRACTICE]**.
- Setting it also calls `WorldRenderer.scheduleTerrainUpdate()`, as the game's own F5 does.

**Key.** Left Alt, `Keyboard.KEY_LMENU` (56). 1.8.9's `GameOptions` builds every default binding with a `bipush` of its key code:
- Left Shift (42, Sneak) and Left Control (29) appear among them;
- 56 never does **[PRACTICE]**.
