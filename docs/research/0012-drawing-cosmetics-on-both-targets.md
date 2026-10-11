# Research 0012 — Drawing cosmetics on both version targets, the art format, and the launcher's preview

*2026-10-10. Settles spec 0004's Further Notes items 3, 4 and 5 well enough to ticket the capes step.*

Game classes were read with `javap -p` from the jars Loom built for this repository: Mojang names on 1.21.11, Legacy Yarn 604 on 1.8.9 [ASH]. Web sources are tagged as in research 0001.

## Summary

- **A cape is one return value on each target.** The game already draws a cape, with its own physics, the player's "Cape" skin option and elytra handling. ash only changes which texture it uses:
  - on 1.21.11, the `cape` of the `PlayerSkin` that `AbstractClientPlayer.getSkin()` returns;
  - on 1.8.9, the method Legacy Yarn 604 calls `AbstractClientPlayerEntity.getSkinId()` (see §1: the names are swapped).

  So an ash cape replaces a Mojang cape (spec 0004) by construction. It also looks identical on both targets, because it is the game's own cape model on both.
- **Worn models are a render layer on the player.**
  - On 1.8.9, `Deadmau5FeatureRenderer` is the precedent: a model drawn relative to the head, as a `FeatureRenderer<AbstractClientPlayerEntity>`.
  - On 1.21.11, it's a `RenderLayer<AvatarRenderState, PlayerModel>` like `CapeLayer`, submitted through the frame's `SubmitNodeCollector`.

  Both attach to a model part, the head or body, so the item follows the player's pose, including emotes.
- **Emotes pose the player model after the game has.** Each target's player model sets its part rotations once per frame from the render state (1.21.11) or the entity (1.8.9); an emote overrides them at the end of that, for that player only. This is the least-certain finding here: the exact methods are named in the emote step's own research, before any emote code.
- **The art format:**
  - Blockbench documents `.bbmodel` as its internal project file, "subject to breaking changes", and recommends against loading it directly [DOC].
  - ash instead takes Blockbench's **Bedrock entity geometry and animation exports**. Those are stable, documented JSON formats: bones with pivots, cubes with UVs, and keyframes.
  - A build-time converter turns them into ash's own small format, which both targets load.
  - Blockbench's README: "All assets created with Blockbench (models, textures, animations, screenshots etc.) are your own!" [DOC]. Blockbench itself is GPL-3.0, but none of its code ships.
- **The launcher's preview is skinview3d** (MIT, three.js) [DOC].
  - It draws the player's skin, a cape, an elytra and ears.
  - Worn models are meshes ash adds to its head or body groups, built from the same ash format.
  - The skin texture is fetched by ash-core and handed over as a `data:` URL, so the web view never makes a cross-origin image request. Whether textures.minecraft.net would allow one is unverified, and this avoids needing to know.

## 1. Capes

### 1.21.11 [ASH]

- `net.minecraft.world.entity.player.PlayerSkin` is a record: `body`, `cape` and `elytra` (each a `ClientAsset$Texture`), `model` and `secure`. There's also `with(PlayerSkin$Patch)`.
- `AbstractClientPlayer.getSkin()` returns the player's `PlayerSkin`. `CapeLayer.submit(PoseStack, SubmitNodeCollector, int, AvatarRenderState, float, float)` draws the cape from the render state, which the renderer fills from `getSkin()`.

**The hook:** at the return of `getSkin()`, when ash knows a cape for that player's UUID, return the skin with `cape` replaced by ash's texture. The texture is registered once with the texture manager, from the downloaded PNG.

**Elytra:** when a skin has no elytra texture, the game falls back to the cape's. So an ash cape shows on an elytra too, as a Mojang cape does. Whether `elytra` is set for players with a Mojang cape decides whether theirs stays. To be checked in the client game test.

### 1.8.9 [ASH]

*Corrected 2026-10-10 from the bytecode, while building the capes step.* Legacy Yarn 604's names are swapped here:
- `getCapeId()` returns the **skin**, the player list entry's skin texture or the default.
- `getSkinId()` returns the **cape**, the entry's cape texture or null.
- `CapeFeatureRenderer.render(AbstractClientPlayerEntity, ...)` checks `canRenderCapeTexture()` (only "is in the player list"), `isInvisible()` and `isPartVisible(PlayerModelPart.CAPE)`, the player's own option. Then it binds `getSkinId()`.

**The hook:** at the return of `getSkinId()`, ash's texture `Identifier` for that player, registered with the texture manager as a `NativeImageBackedTexture` from the PNG.

### Both

- **The texture is the game's own cape layout,** a 64×32 PNG. A 22×17 cape fits at the top left, with the elytra region beside it, so any existing cape template works.
- **The player's own "Cape" option still applies.** A player who hides capes hides ash's too.
- **"Hide other players' cosmetics"** skips the hook for every UUID but the player's own.

## 2. Worn models [ASH]

- **On 1.8.9:** `Deadmau5FeatureRenderer` implements `FeatureRenderer<AbstractClientPlayerEntity>` and draws the ears relative to the head. ash's layer does the same, for the head and back slots, and is added to the player renderer's features.
- **On 1.21.11:** a `RenderLayer<AvatarRenderState, PlayerModel>`, added the way `CapeLayer` is.

Both draw cubes from the ash format, with the part's transform applied first, so a hat turns with the head and a back item with the body. Sodium replaces how entity model cuboids are drawn, so the 1.21.11 client game test runs once with Sodium for this step, as Phase 3 did for the crosshair.

## 3. The art format

Exported from Blockbench as:
- **Bedrock entity geometry** (`*.geo.json`): `minecraft:geometry` → `bones[]`, each with a `name`, `pivot`, `rotation` and `cubes[]` of `origin`, `size` and `uv`;
- **Bedrock animations** (`*.animation.json`) for emotes: per bone, `rotation` and `position` keyframes over time.

These are Microsoft-documented formats that Blockbench writes natively [DOC]. A converter in the client's build turns them into ash's format, which both targets parse identically. The format is versioned from day one, so a cosmetic made today keeps loading. The texture is a PNG beside the model.

## 4. The launcher's preview

skinview3d draws the skin, cape and elytra. Worn models attach as three.js meshes, built from the ash format by the same rules as the client, so the preview and the game agree. The player's own skin comes from ash-core, which already knows its URL, as a `data:` URL, so the content security policy stays as it is.

## What changes for the capes step

1. **Backend:**
   - the catalogue;
   - equipping, signed in;
   - each account's `profiles/<uuid>.json` written to R2 on equip and deleted with the account (ADR-0023).
2. **R2:** a cache rule so `profiles/*.json` is cached at the edge (Cloudflare caches images by default, but not JSON). That's a dashboard step for the product owner.
3. **Client, both targets:**
   - fetch the profile files of the players in view, with a cache;
   - register textures;
   - the two hooks above;
   - "hide others' cosmetics";
   - a real-game test of each.
4. **Launcher:** the Wardrobe, with skinview3d and equipping.
5. **Art:** placeholder capes generated as PNGs. The real ones are the product owner's or commissioned (spec 0004).

## Sources

- [The .bbmodel format](https://blockbench.net/wiki/docs/bbmodel), Blockbench wiki.
- [Blockbench README](https://github.com/JannisX11/blockbench) (GPL-3.0; "All assets created with Blockbench … are your own").
- [skinview3d](https://github.com/bs-community/skinview3d) (MIT).
- [Blockbench format features](https://www.blockbench.net/wiki/blockbench/formats).
