# Research 0015: where ash's hooks are in 1.20.4

24 October 2026, for #147 (spec 0005, ADR-0027). For each place ash's 1.21.11 client reaches into the game, this records where the same place is in 1.20.4. Each was checked with `javap` in the named 1.20.4 jar that Loom builds from Mojang's mappings, not taken from names. That jar is `minecraft-merged-1.20.4-loom.mappings.1_20_4…`, made by `target-1.20.4`.

## 1. Pins

| | 1.20.4 | Notes |
| --- | --- | --- |
| Fabric Loader | 0.19.5 | The same build as 1.21.11. Fabric Meta lists it for 1.20.4. |
| Fabric API | 0.97.3+1.20.4 | The newest for 1.20.4, from Fabric's Maven metadata. |
| Java | 17 | What Mojang names for 1.20.4. The client builds with `--release 17`. |
| Names | Mojang's (`loom.officialMojangMappings()`) | As on 1.21.11. |

## 2. The mixins

| Feature | 1.21.11 | 1.20.4 | Change |
| --- | --- | --- | --- |
| Capes | `AbstractClientPlayer.getSkin()`, RETURN, replacing `PlayerSkin.cape` (a `ClientAsset.ResourceTexture`) | `AbstractClientPlayer.getSkin()` returns `PlayerSkin(ResourceLocation texture, String textureUrl, ResourceLocation capeTexture, ResourceLocation elytraTexture, Model model, boolean secure)` | A new `PlayerSkin` with `capeTexture` replaced, a `ResourceLocation` registered with `TextureManager.register(ResourceLocation, AbstractTexture)` for a `DynamicTexture(NativeImage.read(byte[]))`. |
| Freelook | `Camera.setup`, wrapping `Entity.getViewYRot(F)F` and `getViewXRot(F)F` | `Camera.setup(BlockGetter, Entity, boolean, boolean, float)` calls both | None |
| Freelook (mouse) | `MouseHandler.turnPlayer`, wrapping `LocalPlayer.turn(DD)V` | `MouseHandler.turnPlayer()` calls it | None |
| Hit indicator | `ClientPacketListener.handleDamageEvent`, at `Entity.handleDamageEvent(DamageSource)V` | The same packet and call (`ClientboundDamageEventPacket`, since 1.19.4) | None |
| Crosshair | `Gui.renderCrosshair`, wrapping `GuiGraphics.blitSprite(RenderPipeline, Identifier, IIII)V` for `hud/crosshair` | `Gui.renderCrosshair(GuiGraphics)` draws `CROSSHAIR_SPRITE` with `GuiGraphics.blitSprite(ResourceLocation, IIII)V`, at `((screenWidth - 15) / 2, (screenHeight - 15) / 2)` | No `RenderPipeline` argument, and `ResourceLocation` rather than `Identifier`. The same 15-pixel sprite and centre. The attack indicator's three later `blitSprite` calls are other sprites, so the check on the sprite's name still picks the crosshair alone. |
| Toggle sprint | `KeyboardInput.tick`, wrapping `KeyMapping.isDown()` where the key is `keySprint` | **Not in `KeyboardInput`.** 1.20.4's `KeyboardInput.tick(boolean, float)` reads up, down, left, right, jump and shift only. The sprint key is read in `LocalPlayer.aiStep()`, twice (`keySprint.isDown()`). | The wrap moves to `LocalPlayer.aiStep`, on `KeyMapping.isDown()` where the key is `keySprint`. The per-tick latch read moves to the same method's head. |
| Hit colour | `OverlayTexture`'s `texture` field (accessor) | `OverlayTexture` has the same `DynamicTexture texture` field | Pixel writes follow 1.20.4's `NativeImage` API (`setPixelRGBA`), which the compiler checks. |

## 3. Fabric API

| | 1.21.11 (0.141.6) | 1.20.4 (0.97.3) |
| --- | --- | --- |
| HUD | `HudElementRegistry.addLast(id, element)`: named, ordered elements | **Only `HudRenderCallback.EVENT`.** `HudElementRegistry` came with 1.21.6. This is the shape ash's 1.8.9 target already uses: one callback after the vanilla HUD, drawing on top. |
| Ticks | `ClientTickEvents.END_CLIENT_TICK` | The same |
| Key bindings | `KeyBindingHelper.registerKeyBinding(new KeyMapping(name, key, KeyMapping.Category))` | The same helper. The category is a `String` (`"key.categories.movement"`), as on 1.8.9. |

## 4. Names that differ across the port

- **`ResourceLocation`**, not `Identifier`. Mojang renamed it in 1.21.11's mappings. 1.20.4 has `new ResourceLocation(namespace, path)`, `tryBuild` and `of`.
- **Key categories** are strings.
- **`GuiGraphics`** exists since 1.20, so ash's `GuiGraphicsHudSurface` and its settings screen's drawing port with signature changes only. The compiler finds them.

## 5. What doesn't apply

- **Faster clouds and faster view scan** are 1.8.9-only features (their FPS marks exist only for 1.8.9), so 1.20.4 doesn't get them, as 1.21.11 doesn't.
- **Sodium:** 1.21.11's game test runs a second time with Sodium in its mods folder. 1.20.4 can do the same with Sodium's 1.20.4 build, pinned by URL and SHA-512 from Modrinth like 1.21.11's. That's left to the target ticket.

## 6. Testing

Fabric's client game tests (`fabric-client-gametest-api-v1`) aren't in Fabric API 0.97.3: they begin with 1.21.4. So 1.20.4 has two tiers instead:
- **`AshMixinsLandTest`,** on every build, applies each mixin against the real 1.20.4 classes. It's what would have caught the crosshair hook still naming 1.21.11's `blitSprite`. It compiled, because 1.20.4 happens to have an unrelated class with the same name.
- **`AshSmokeTest`,** as 1.8.9 has, runs a real client in CI. It checks that every feature loaded, reads the launcher's server list, walks into a flat world, opens ash's settings with their key, and waits for the test CDN's cape, keeping screenshots. It passed on the first run on 24 October 2026.

## 7. Method

Hooks were found by disassembling the named jar (`javap -c -p`) at each method 1.21.11's mixins name, and reading the calls they wrap. The port then compiles every 1.21.11 class against 1.20.4. The compiler is the check for everything that isn't a mixin target. The client game test is the check for the mixins, because a mixin that stops matching only shows when the game loads.
