# Research 0016: where ash's hooks are in 26.3

28 October 2026, for #175 (spec 0007). For each place ash's 1.21.11 client reaches into the game, this records where the same place is in 26.3, and what else changed that the port has to meet. Each hook was checked with `javap` in Mojang's own 26.3 client jar, which ships with Mojang's names. The 1.21.11 side was read from the jar Loom built for `target-1.21.11`, `minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2`. Nothing was run against a server or an account.

Sources are tagged in brackets and listed at the end.

## Summary

- **Five of the seven mixins are unchanged.** Capes, the freelook mouse, the hit indicator, toggle sprint and hit colour sit where they did on 1.21.11 [jar].
- **Two moved.**
  - Freelook's camera: `Camera.setup` is gone. The same two reads are in `Camera.alignWithEntity(F)V`.
  - The crosshair: `Gui` no longer draws the HUD. `Hud.extractCrosshair` does, through `GuiGraphicsExtractor`.
- **GLFW is gone.** 26.3 uses SDL3 for the window and input. Every key and mouse-button number ash writes as `GLFW.GLFW_*` has a different value now, and the `GLFW` class isn't on the classpath at all [ver] [jar] [mc] [blog-26.3].
- **Typed characters need SDL text input switched on.** ash's settings panel types its search from `charTyped`. In 26.3 that only fires after something starts SDL text input, and a plain `Screen` doesn't. Fabric's game-test typing skips that step, so a test would pass while players couldn't type [jar] [blog-26.3] [fapi].
- **The rest is renames the compiler finds:** `GuiGraphics` → `GuiGraphicsExtractor`, `render*` → `extract*`, the screen moved from `Minecraft` to `Minecraft.gui`, F1's flag moved to `Hud`, `KeyBindingHelper` → `KeyMappingHelper`.
- **The build changes shape.** The plugin is `net.fabricmc.fabric-loom`. There's no `mappings` line, `modImplementation` becomes `implementation`, `remapJar` becomes `jar`, and the code compiles for Java 25 [blog-26.1] [example].
- **The launcher's pin has no intermediary.** It is the loader jar alone [meta].

## 1. Pins

| | 26.3 | Notes |
| --- | --- | --- |
| Minecraft | 26.3, released 15 September 2026 | Client jar SHA-1 `e877b6a07acd633fb3bb475002175cec036e7b87`, 41,483,720 bytes. No `client_mappings` download: `downloads` names only `client` and `server` [ver]. Class files are major 69, Java 25 [jar]. |
| Java | 25, Mojang's `java-runtime-epsilon` | From the version JSON's `javaVersion` [ver] and the jar's own `version.json`. The runtime manifest ash already pins has it for all four of ash's platforms: 25.0.1 on `windows-x64`, `mac-os` and `mac-os-arm64`, and 25.0.0 on `windows-arm64` [rt]. |
| Fabric Loader | 0.20.1 | Fabric Meta lists it first for 26.3, marked stable [meta]. Published 19 October 2026 [maven]. Fabric's 26.3 example mod uses it [example]. |
| Fabric API | 0.165.0+26.3 | The newest `+26.3` on Fabric's Maven. Published 23 October 2026 [maven]. It declares `fabricloader >=0.19.3`, `java >=25`, `minecraft ~26.3-` [fapi]. |
| Loom | 1.18.2, the build's existing version, under the plugin id `net.fabricmc.fabric-loom` | See section 6. Fabric named Loom 1.17 and Gradle 9.6.0 for 26.3 [blog-26.3]. The wrapper is Gradle 9.7.1 [ash]. |
| Names | Mojang's, from the jar. There are none to download. | Fabric Meta reports intermediary `0.0.0` for 26.3 [meta]. |
| Sodium, tested against | 0.9.2+mc26.3 | Modrinth's release for 26.3, published 15 September 2026. Betas since then: 0.9.3-beta.1 [modrinth]. |

Every hash below was read from Fabric's `.sha1` sidecars on 28 October 2026 and checked against a local hash of the downloaded bytes. They agreed. The `.sha256` and `.sha512` sidecars agreed too [maven].

In `loader.rs`'s form:

```rust
/// Fabric Loader 0.20.1 on 26.3. No intermediary: 26.3 runs on Mojang's
/// names, and Fabric Meta's profile for it names none.
const FABRIC_26_3: LoaderPin = LoaderPin {
    loader: Loader::Fabric,
    version_id: "26.3",
    loader_version: "0.20.1",
    document: PinnedFile {
        url: "https://maven.fabricmc.net/net/fabricmc/fabric-loader/0.20.1/fabric-loader-0.20.1.json",
        sha1: "d1fefa65134f30bd66ea420ff5c9e39f283cfb57",
        size: 4257,
    },
    libraries: &[PinnedLibrary {
        name: "net.fabricmc:fabric-loader:0.20.1",
        repository: FABRIC_MAVEN,
        mirror: None,
        sha1: "7c7f9831e9f27b303d5d08decb5f4258a9f03296",
        size: 1_988_880,
        natives: &[],
    }],
    jvm_arguments: FABRIC_1_21_11.jvm_arguments,
    client_jar: Some("ash-client-26.3.jar"),
    bundled_mods: &[PinnedLibrary {
        name: "net.fabricmc.fabric-api:fabric-api:0.165.0+26.3",
        repository: FABRIC_MAVEN,
        mirror: None,
        sha1: "8f8a23d6c9fbf7b075b31b4fb0a1007b0488d1d2",
        size: 1_778_588,
        natives: &[],
    }],
};
```

- **The document's own libraries:** ASM 9.10.1, the same five jars and hashes as 0.19.5's. Mixin moves to `net.fabricmc:sponge-mixin:0.17.5+mixin.0.8.7` (SHA-1 `0cef8519a91129d0c21fa03bb9378f0e8ca810e5`, 1,551,593 bytes). MixinExtras 0.5.5 is still `development` only [maven].
- **No intermediary.** Fabric Meta's generated profile for 26.3 and 0.20.1 names the document's libraries and then `net.fabricmc:fabric-loader:0.20.1`. Its 1.21.11 profile has `net.fabricmc:intermediary:1.21.11` before the loader [meta]. So `LoaderPin::libraries`' comment, "Always the intermediary and the loader jar", stops being true [ash].
- **The JVM argument is the same.** The profile adds only `-DFabricMcEmu= net.minecraft.client.main.Main ` [meta].
- **The SHA-256s,** for the record: document `b19ad876d4a7f11a78ebf726b1ce88b14a931f91d276f362b2d7e871374f3944`, loader `d0c5a082e07410aaff9de7ab196aa6b4236002870f9ba3ad5c1260fcce5110ad`, Fabric API `18442e2ad466d6ef41c37dbb4cb68495e6ca6658b1f1ee9be76143d155ef2c88` [maven].
- **Sodium,** for `gradle.properties`:
  - URL `https://cdn.modrinth.com/data/AANobbMI/versions/bAZQdGpg/sodium-fabric-0.9.2%2Bmc26.3.jar`;
  - SHA-512 `f3f260f204b8ce5e8777c2c73f537956755ec61f3817b0b78b5e5568e8aa40f14891f146475075109747665c7c88a45ca0ca9a8df3c68d79c58ecdaa8d8c4b93`, checked against the download [modrinth].
- **The loader is per target.** `gradle.properties` shares `loader_version=0.19.5` between 1.21.11 and 1.20.4 [ash]. 26.3 needs its own, `loader_version_26_3=0.20.1`. 0.20.x has nothing 26.3-specific in its notes: Mixin 0.17.5, and a revert of ASM 9.11 [loader-releases]. 0.20.1 is the pin because Meta marks it stable and Fabric's 26.3 template uses it.
- **The runtime needs no code.** `runtime.rs` takes the component from the version's `javaVersion` [ash], and 26.3 names `java-runtime-epsilon` [ver].

## 2. The mixins

| Feature | 1.21.11 | 26.3 | Change |
| --- | --- | --- | --- |
| Capes | `AbstractClientPlayer.getSkin()`, RETURN, replacing `PlayerSkin.cape` | The same method. `PlayerSkin` is the same record: `body`, `cape`, `elytra` (`ClientAsset$Texture`), `model`, `secure`. | None |
| Freelook (camera) | `Camera.setup(Level, Entity, boolean, boolean, float)`, wrapping `Entity.getViewYRot(F)F` and `getViewXRot(F)F` | **`Camera.alignWithEntity(F)V`**, private, called from the new `Camera.update(DeltaTracker)`. It reads both angles twice, in its minecart branch and otherwise, as `setup` did. | **Moved.** `method = "alignWithEntity"`. `setup` no longer exists. |
| Freelook (mouse) | `MouseHandler.turnPlayer(D)V`, wrapping `LocalPlayer.turn(DD)V` | The same | None |
| Hit indicator | `ClientPacketListener.handleDamageEvent`, at `Entity.handleDamageEvent(DamageSource)V` | The same call. `ClientboundDamageEventPacket.entityId()` and `sourceCauseId()` are unchanged. | None |
| Crosshair | `Gui.renderCrosshair(GuiGraphics, DeltaTracker)`, wrapping `GuiGraphics.blitSprite(Lcom/mojang/blaze3d/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V` for `hud/crosshair` | **`Hud.extractCrosshair(GuiGraphicsExtractor, DeltaTracker)`**, wrapping `GuiGraphicsExtractor.blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V` | **Moved and renamed.** `@Mixin(Hud.class)`. The same 15-pixel `hud/crosshair` sprite with `RenderPipelines.CROSSHAIR`, at `((guiWidth - 15) / 2, (guiHeight - 15) / 2)`. The attack indicator's sprites go through the same overload after it, so matching by sprite name still picks the crosshair alone. |
| Toggle sprint | `KeyboardInput.tick()`: HEAD, and wrapping `KeyMapping.isDown()Z` where the key is `keySprint`. `@Shadow options`. | The same. Still the seventh of seven `isDown()` calls. A scan of every class in the jar finds `keySprint` only in `Options` and `KeyboardInput`. | None |
| Hit colour | `OverlayTexture`'s `texture` field (accessor) | The same `DynamicTexture texture` field | None |

`AshClient` names the crosshair's target class for `MixinFeature.landed`. That becomes `Hud.class` [ash].

**The trap 1.20.4 had is here too.** `net.minecraft.client.gui.Gui` still exists in 26.3, as a different class: the owner of the screen, the overlay and the `Hud` [jar]. A 1.21.11 crosshair mixin left on `Gui.renderCrosshair` compiles, then silently doesn't land, because ash's config is `required: false` with `defaultRequire: 0` [ash]. `AshMixinsLandTest` is what catches it, as it was for 1.20.4 (research 0015, section 6).

## 3. Fabric API

| | 1.21.11 (0.141.6) | 26.3 (0.165.0) |
| --- | --- | --- |
| HUD | `HudElementRegistry.addLast(id, element)`, where `HudElement.render(GuiGraphics, DeltaTracker)` | The same registry. The element's method is now **`extractRenderState(GuiGraphicsExtractor, DeltaTracker)`**. ash passes lambdas, so only the parameter type changes. |
| Ticks | `ClientTickEvents.END_CLIENT_TICK`, `onEndTick(Minecraft)` | The same |
| Key bindings | `net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper.registerKeyBinding(KeyMapping)` | **`net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper.registerKeyMapping(KeyMapping)`**, in the new module `fabric-key-mapping-api-v1` 2.0.8 |
| Client game tests | `fabric-client-gametest-api-v1` 4.3.5 | 6.0.11, named in the API's POM rather than nested in its jar. `TestDedicatedServerContext.connect()` now returns **`TestDedicatedServerConnection`**. `TestServerConnection` is no longer `AutoCloseable`. |

All of this is from the jars [fapi].

- **Render condition.** `HudElementRegistry`'s documentation on the 26.3 branch still says vanilla elements, all but the sleep overlay, render only when the HUD isn't hidden (`Hud#isHidden()`), and `addLast` inherits no condition [fapi-src]. So ash's readouts keep checking `hudHidden()` themselves, as on 1.21.11.
- **Fabric wraps one level up from ash.** Fabric's own `HudMixin` wraps the call to `Hud.extractCrosshair` from `Hud.extractRenderState`. ash wraps a call inside `extractCrosshair`. That's the same layering as on 1.21.11 [fapi-src].
- **Modules removed** since 0.141.6: `fabric-block-view-api-v2`, `fabric-convention-tags-v1`, `fabric-item-group-api-v1`, `fabric-key-binding-api-v1`, `fabric-loot-api-v2` and `fabric-screen-handler-api-v1` [fapi]. Fabric renamed its API to Mojang's names in 26.1 [blog-26.1]. Only the key-binding module touches ash.
- **The mod id is still `fabric-api`,** which is what ash's load report lists as bundled [fapi] [ash].

## 4. Everything else that moved

Not mixin targets, so the compiler finds them. Listed so the port isn't surprised. All are from the jar.

| What | 1.21.11 | 26.3 | ash uses it in |
| --- | --- | --- | --- |
| The open screen | `Minecraft.screen` (field) | `Minecraft.gui.screen()` | `AshClient` (freelook, snaplook, settings key, `BootClock`), `KeyboardInputMixin`, the game test |
| Opening one | `Minecraft.setScreen(Screen)` | `Minecraft.gui.setScreen(Screen)` | `AshClient`, `AshSettingsScreen` (Controls) |
| The action-bar message | `Minecraft.gui.setOverlayMessage(Component, boolean)` | `Minecraft.gui.hud.setOverlayMessage(Component, boolean)` | Freelook's "off on this server" |
| F1 | `Options.hideGui`, a field | `Minecraft.gui.hud.isHidden()`. There's no setter, only `Hud.toggle()`, which `Options.keyToggleGui` (default `KEY_F1`) now calls. | `GuiGraphicsHudSurface.hudHidden()`, the game test |
| GUI drawing | `GuiGraphics` | `GuiGraphicsExtractor`. Same `guiWidth`, `guiHeight`, `fill`, `blit` (the 13-argument tinted one too), `blitSprite`, `enableScissor`, `disableScissor`, `pose()` (`Matrix3x2fStack`). | The HUD surface, `GuiCanvas`, the settings screen, the crosshair |
| Text | `drawString(Font, String, int, int, int)` | **`text(Font, String, int, int, int)`**. It still passes `true` for the shadow, checked in the bytecode. | `GuiGraphicsHudSurface` |
| Screen drawing | `render`, `renderBackground`, `renderBlurredBackground` | `extractRenderState`, `extractBackground`, `extractBlurredBackground`. Called in the same order: background, then the screen, then deferred elements. | `AshSettingsScreen` |
| Render pipelines | `com.mojang.blaze3d.pipeline.RenderPipeline` | `com.mojang.renderpearl.api.pipeline.RenderPipeline`. `RenderPipelines.GUI_TEXTURED` and `CROSSHAIR` keep their names. Nothing called `RenderPipeline` is left in the old package, so the old import fails to compile. | The crosshair mixin |
| Key numbers | `GLFW.GLFW_KEY_*`, `GLFW_MOUSE_BUTTON_LEFT` | `InputConstants.KEY_*`, `MOUSE_BUTTON_LEFT` (section 5.5) | `AshClient`, `AshSettingsScreen`, the game test |
| Key type | `InputConstants.Type.KEYSYM` (and `SCANCODE`) | `InputConstants.Type.KEYBOARD`, and `SCANCODE` is gone | `AshClient` |
| The main camera | `GameRenderer.getMainCamera()` | `GameRenderer.mainCamera()` | The game test |
| Resizing the GUI | `Minecraft.resizeDisplay()` | `Minecraft.resizeGui()`: the GUI scale and the screen's size. Render targets resize elsewhere now. | The game test |
| The arm swing | `LivingEntity.swinging` | `LivingEntity.isSwinging()` | The game test |
| Rendered sections | `LevelRenderer.countRenderedSections()`, `getSectionStatistics()` | `Minecraft.levelExtractor.countRenderedSections()`, `sectionStatistics()`. `LevelRenderer.hasRenderedAllSections()` stays. | The benchmark |
| GPU and CPU strings | `RenderSystem.getDevice()` (`com.mojang.blaze3d.systems.GpuDevice`): `getRenderer`, `getVendor`, `getVersion`. `GLX._getCpuInfo()`. | `RenderSystem.getDevice()` returns `com.mojang.renderpearl.api.device.GpuDevice`, with `getDeviceInfo()`: `name()`, `vendorName()`, `driverInfo()`, `backendName()`. `GLX` is gone. Use `Minecraft.getCpuInfo()`. | The benchmark |
| A new world | `LevelSettings(String, GameType, boolean, Difficulty, boolean, GameRules, WorldDataConfiguration)`. `ServerLevel.setDayTime`, `setWeatherParameters`. | `LevelSettings(String, GameType, LevelSettings$DifficultySettings, boolean, WorldDataConfiguration)`. Weather is `MinecraftServer.setWeatherParameters`. Time goes through a clock manager, which I didn't trace. | The benchmark's singleplayer scene |

**Unchanged, checked one by one:**
- the input records `MouseButtonEvent` and `CharacterEvent.codepoint()`;
- `KeyMapping`: its constructors, `Category.MOVEMENT` and `MISC`, `isDown`, `consumeClick`, `matches(KeyEvent)`, `getTranslatedKeyMessage`, `getName`, `getDefaultKey`;
- `KeyBindsScreen(Screen, Options)`;
- `Window`'s size and scale getters;
- `Options.getCameraType` and `setCameraType`, `CameraType`, `guiScale()`, `keyMappings`, `keyAttack`;
- `DebugScreenEntryList.isOverlayVisible()`;
- `Font.width` and `lineHeight`;
- `Identifier.fromNamespaceAndPath`;
- `TextureManager.register` and `release`;
- `DynamicTexture(Supplier<String>, NativeImage)`, `getPixels`, `upload`;
- `NativeImage(int, int, boolean)`, `read(byte[])`, `setPixel` and `getPixel`, still ARGB converted to ABGR;
- `ServerData.ip`, `serverBrand()`, `PlayerInfo.getLatency()`, `ServerList`, `User.getProfileId()`, `getFps()`, `hasSingleplayerServer()`;
- `MouseHandler`'s `accumulatedDX` and `turnPlayer(double)`, which the test reaches by reflection.

## 5. By area

### 5.1 Rendering and the HUD

- **The frame is extract, then render.**
  - `GameRenderer.extract(DeltaTracker, boolean)` runs, then `render()`.
  - The level is extracted by the new `Minecraft.levelExtractor` (`renderer.extract.LevelExtractor`).
  - The GUI by `Gui.extractRenderState`, through `GuiGraphicsExtractor` into a `GuiRenderState`.
  - ash only adds to what the HUD and screens record, so this changes names, not how ash works [jar].
- **The renderer sits behind `com.mojang.renderpearl`,** with an OpenGL and a Vulkan backend (`renderpearl.backend.opengl`, `renderpearl.backend.vulkan`) [jar].
  - The option is `preferredGraphicsBackend`: `DEFAULT`, `OPENGL` or `VULKAN`.
  - `DEFAULT` and `OPENGL` try OpenGL, then Vulkan. Only `VULKAN` tries Vulkan first [jar].
  - Fabric calls Vulkan experimental since 26.2, with OpenGL due to go once it's stable [blog-26.2].
  - Shaders are now compiled by ShaderC on both backends [mc]. ash ships no shaders [ash].
- **ash makes no raw OpenGL calls.** Every texture ash uploads goes through `DynamicTexture.upload()`, which writes through `GpuDevice.createCommandEncoder().writeToTexture` on whichever backend is running [jar].
- **The HUD:** `Gui` and `Hud` were split in 26.2, and the screen moved out of `Minecraft` [blog-26.2]. ash's elements still go in `HudElementRegistry.addLast` (section 3).
- **Order-independent transparency.** "Improved Transparency" now turns on OIT [mc]. It doesn't touch the GUI.

### 5.2 Skins and capes

Unchanged.
- `AbstractClientPlayer.getSkin()` is still where a skin comes from.
- `AvatarRenderer` still fills `AvatarRenderState.skin` from `ClientAvatarEntity.getSkin()`, and `CapeLayer.submit(PoseStack, SubmitNodeCollector, int, AvatarRenderState, float, float)` still draws from it.
- `PlayerSkin`'s constructor, `ClientAsset$ResourceTexture(Identifier, Identifier)`, `NativeImage.read(byte[])` and texture registration are as they were [jar].

`CapeTextures` ports unchanged.

### 5.3 Entity rendering for hit colour

Unchanged [jar]:
- `OverlayTexture` still builds a 16×16 `DynamicTexture` named "Entity Color Overlay".
- Its red rows 0 to 7 are still `-1291911168`, which is `0xB2FF0000`, ash's `GAME_TEXEL`.
- `LivingEntityRenderer.getOverlayCoords` still packs `OverlayTexture.v(hasRedOverlay)`.
- The entity render setup still binds `GameRenderer.overlayTexture().getTextureView()`. The view's type is renderpearl's now, and ash never names it.

So rewriting the red rows and uploading them works as on 1.21.11. That's read from the bytecode. It hasn't been run, on either backend.

### 5.4 Clouds and culling

- **Faster clouds and faster view scan don't apply.** They're declared for 1.8.9 only (`Feature.FASTER_CLOUDS` and `FASTER_VIEW_SCAN`, `Target.V1_8_9`) [ash]. 26.3 gets them no more than 1.21.11 does.
- **Clouds:** `CloudRenderer` is now `prepare(...)`, then `render(CloudStatus, RenderPass)` or, under OIT, `renderOit(...)`. `LevelRenderer.addCloudsPass` is gone [jar].
- **Culling moved onto the camera.**
  - 1.21.11's `LevelRenderer.prepareCullFrustum`, `cullTerrain`, `captureFrustum` and `killFrustum` are gone.
  - The frustum is built in `Camera.prepareCullFrustum` and read with `Camera.getCullFrustum()`.
  - Entities are culled in `LevelExtractor.extractVisibleEntities` [jar].
  - `Camera.update` calls `alignWithEntity` before it builds the frustum. So freelook's angles still decide what's culled, as on 1.21.11, and turning to look behind doesn't show unculled holes [jar].
- **Terrain uses MultiDrawIndirect** on devices that support it [mc].
- **Sodium:** Sodium 0.9.2's own `CameraMixin` injects at the return of `Camera.createProjectionMatrixForCulling`, not a method ash touches [sodium]. Section 7 has the rest.

### 5.5 Key bindings

**GLFW is gone and SDL3 replaces it.**
- 26.3's libraries have no `lwjgl-glfw`, and do have `lwjgl-sdl` 3.4.3 [ver].
- No class in the jar refers to `org/lwjgl/glfw` [jar].
- Mojang: "Minecraft now uses SDL3 instead of GLFW for window management, input, and platform integration" [mc].
- Fabric: "Some of the constants used (including all mouse buttons) were also changed, so do not attempt to hardcode your inputs to avoid using `InputConstants`" [blog-26.3].

| ash writes, on 1.21.11 | 1.21.11's value | 26.3's `InputConstants` | 26.3's value |
| --- | --- | --- | --- |
| `GLFW_KEY_R` (toggle sprint) | 82 | `KEY_R` | 21 |
| `GLFW_KEY_Z` (snaplook) | 90 | `KEY_Z` | 29 |
| `GLFW_KEY_LEFT_ALT` (freelook) | 342 | `KEY_LALT` | 226 |
| `GLFW_KEY_RIGHT_SHIFT` (settings) | 344 | `KEY_RSHIFT` | 229 |
| `GLFW_KEY_ESCAPE` | 256 | `KEY_ESCAPE` | 41 |
| `GLFW_KEY_ENTER` | 257 | `KEY_RETURN` | 40 |
| `GLFW_KEY_KP_ENTER` | 335 | `KEY_NUMPADENTER` | 88 |
| `GLFW_KEY_BACKSPACE` | 259 | `KEY_BACKSPACE` | 42 |
| `GLFW_MOUSE_BUTTON_LEFT` | 0 | `MOUSE_BUTTON_LEFT` | 1 |

The 26.3 values are SDL's scancodes and SDL's button numbers [jar]. Because the `GLFW` class is absent, every one of these fails to compile rather than quietly meaning another key.

- **`KeyEvent`:**
  - `scancode()` is gone.
  - `key()` is now the SDL scancode. It's the number `KeyMapping.matches(KeyEvent)` and `InputConstants.KEY_*` use.
  - `keycode()` is SDL's layout keycode, for text-editing shortcuts.
  - `SDLEventHandler` builds the event as `KeyEvent(scancode, key, mod)` [jar].
  - So the settings screen keeps comparing `event.key()`, now against `InputConstants`.
- **Bindings are physical keys.** Mojang: "Key bindings now use physical keys instead of keyboard-layout-specific key codes" [mc].
  - On a US layout, ash's defaults are the same keys as before.
  - On other layouts a default can carry another label: the US Z position is labelled W on AZERTY.
  - Not checked in a game. Controls shows the layout's label.
- **Repeats still count as clicks.** SDL's repeat arrives as action `-1`, where GLFW's was `2`. `KeyboardHandler.keyPress` still sets the key down and calls `KeyMapping.click` for anything but a release [jar]. So `KeyMappingToggleKey`'s "a repeat counts as a click" holds.
- **Mouse:** the Raw Input setting is gone, and the mouse is always relative in game [mc]. `turnPlayer`'s path to `LocalPlayer.turn` is unchanged [jar].
- **Registration:** `KeyMappingHelper.registerKeyMapping` (section 3).

### 5.6 The settings screen

- **Renames:** `GuiGraphicsExtractor`, `extractRenderState`, `extractBackground`, `extractBlurredBackground`, `minecraft.gui.setScreen` (section 4).
- **The mouse:** `MouseHandler.xpos()` is in the same units. `getScaledXPos` is the same formula as on 1.21.11 [jar]. So the panel's real-pixel mapping ports.
- **Typed characters need text input started.**
  - In 26.3, `Screen.charTyped` is reached only from SDL's text-input event: `SDLEventHandler.handleTextInputEvent`, then `KeyboardHandler.textInput`, then `charTyped` [jar]. SDL sends those only between `SDL_StartTextInput` and `SDL_StopTextInput`: "Text input events are not received by default" [sdl].
  - Vanilla starts text input only in `EditBox`, `MultiLineEditBox` and the sign editor, and `Gui.setScreen` stops it [jar].
  - Fabric's 26.3 notes say a custom text widget that doesn't tell `TextInputManager` its focus leaves "character input" to "entirely break" [blog-26.3].
  - ash's panel takes its search from `charTyped`, with no `EditBox` [ash].
  - So `AshSettingsScreen` has to call `minecraft.textInputManager().startTextInput(this)` when it opens and `stopTextInput(this)` in `removed()`, or `minecraft.onTextInputFocusChange(this, true/false)`. Read from the bytecode and Fabric's note. Not yet run.
- **A test blind spot.** Fabric's `TestInput.typeChar` and `typeChars` call `KeyboardHandler.charTyped` directly, past SDL [fapi-src].
  - Spec 0007's "a game test types a search on each modern target" would pass on 26.3 even if ash never starts text input.
  - The test should also ask whether text input is on. `TextInputManager` keeps that in a private field with no getter. SDL's `SDL_TextInputActive(window)` answers it [sdl].
- **Raw key codes in the game test** are documented as "the SDL scancode of the key" and "the SDL mouse button" [fapi-src].

## 6. Building it

- **The plugin is `net.fabricmc.fabric-loom`.** Fabric: switch "to the new `net.fabricmc.fabric-loom` plugin, which does not remap Minecraft or mods". Use `implementation` for `modImplementation` and `jar` for `remapJar` [blog-26.1]. Fabric's 26.3 example mod has no `mappings` line and compiles with `release = 25` [example].
- **It fits in ash's one build.**
  - The ids `fabric-loom`, `net.fabricmc.fabric-loom` and `net.fabricmc.fabric-loom-remap` all resolve to the same artifact, `net.fabricmc:fabric-loom` [maven].
  - Inside Loom 1.18.2, `net.fabricmc.fabric-loom` maps to `LoomNoRemapGradlePlugin`, and `fabric-loom` to `LoomGradlePlugin` [loom].
  - Loom 1.14 added the new ids. Loom 1.15 fixed "remapJar failures in mixed obfuscated/unobfuscated multi-project builds" [loom-releases].
  - So `target-26.3` can declare `id 'net.fabricmc.fabric-loom' version "${loom_version}"` beside the others' `fabric-loom`, at the same 1.18.2.
- **Java 25:**
  - `options.release = 25`;
  - `"java": ">=25"` in `fabric.mod.json`;
  - `compatibilityLevel: "JAVA_25"` in the mixins config. Fabric API's own 26.3 configs declare `JAVA_25`, and Mixin 0.17.5 has that level [fapi] [maven].
  - Gradle itself must run on Java 25 [blog-26.1]. ash's CI already runs it on Temurin 25 [ash].
- **No refmap and no remapping.** The game runs on the names in the jar [blog-26.1], so a mixin's string target is the name `javap` shows.
- **`checkJarHasNoBenchmark` reads `remapJar`'s output** [ash]. On 26.3 it reads `jar`'s.
- **`fabric-loader-junit` 0.20.1 is published,** for `AshMixinsLandTest` [maven].
- **`settings.gradle`** gains `include 'target-26.3'`, and `Target` gains `V26_3` for `present(...)` and the FPS marks [ash].

## 7. Testing

- **`AshMixinsLandTest`** runs unchanged and catches the `Gui`/`Hud` trap (section 2).
- **The client game test ports with the changes in section 4:**
  - `TestDedicatedServerConnection` in the try-with-resources;
  - `InputConstants.KEY_ESCAPE` and `MOUSE_BUTTON_LEFT`;
  - `isSwinging()`, `mainCamera()`, `resizeGui()`;
  - F1 through `Hud.toggle()`;
  - `gui.screen()`.
- **The search test needs the text-input check** in section 5.6.
- **The game test with Sodium** can run with 0.9.2+mc26.3 (section 1).
  - Scanning Sodium 0.9.2's classes, none of its mixins names `Camera.alignWithEntity`, `Hud.extractCrosshair`, `KeyboardInput.tick`, `MouseHandler.turnPlayer`, `ClientPacketListener.handleDamageEvent`, `AbstractClientPlayer.getSkin` or `OverlayTexture`'s texture.
  - Its `CameraMixin` is on `createProjectionMatrixForCulling`. Its `ClientPacketListenerMixin` is on light and chunk packets [sodium].
  - That's by name only. The run is the check.
  - The 1.21.11 run's note that "the loader remaps a mod there at launch" no longer holds: nothing is remapped on 26.3.
- **The benchmark** needs the renderer and world changes in section 4. Its singleplayer scene could set time and weather by command, as its server scene already does [ash].
- **FPS marks** would be measured on the default backend, which is OpenGL first (section 5.1).

## 8. The launcher

- **The pin** is section 1's. Fabric's Maven isn't mirrored, as for the other Fabric pins [ash].
- **First run** makes the 26.3 instance from that pin. That's the target ticket's.
- **26.3's own JVM arguments** add:
  - `-XX:StackShadowPages=32`;
  - `--add-exports java.base/jdk.internal.misc=ALL-UNNAMED`;
  - `--enable-native-access=ALL-UNNAMED`;
  - natives subdirectories such as `${natives_directory}/lwjgl`, new in 26.2 [ver].
  - They use no placeholder ash doesn't already fill [ash].
- **Mojang dropped `windows-x86` natives.** ash doesn't build for it [ver].

## 9. Open questions

1. **Loom 1.18.2 on 26.3 hasn't been built.** Fabric named 1.17 for 26.3 [blog-26.3], and 1.18.2 is later. Fabric's template uses `1.19-SNAPSHOT`, and 1.19.3 is the newest release [example] [maven]. One version for the whole build is the safe reading. I didn't try two. Whether to move every target to 1.19.3 is a separate decision.
2. **`fabricApi { configureTests { ... } }` under the no-remap plugin** isn't confirmed. Fabric's template doesn't use it. The target's first build will say.
3. **Vulkan.** Everything ash uploads goes through the backend-neutral API (section 5.1), but nothing of ash's has run on Vulkan. CI will get OpenGL unless told otherwise. Whether ash tests Vulkan at all is a decision.
4. **The text-input fix** (section 5.6) is from the bytecode and Fabric's note. It needs a real key press in a real game to prove it, which Fabric's typing helper can't give.
5. **Other keyboard layouts.** Defaults are physical positions now. Whether that matters for ash's four defaults outside QWERTY hasn't been checked in a game.
6. **Time in the benchmark.** `ServerLevel.setDayTime` is gone, and I didn't trace its replacement. Commands avoid the question.

## 10. Method

- **Hooks:** disassembled with `javap -c -p` in Mojang's 26.3 jar and in Loom's mapped 1.21.11 jar, at each method a 1.21.11 mixin names. The calls each mixin wraps were read in both.
- **Constant pools:** searched in every class, for `keySprint`, GLFW, text-input callers and getters that moved.
- **Fabric:**
  - read from the jars on its Maven, nested modules included;
  - from Fabric Meta's generated 26.3 profile;
  - from the `26.3` branch of `FabricMC/fabric-api` for documentation.
- **Not done:** no game was launched, and nothing was compiled.
- **What still checks it:** the compiler for everything that isn't a mixin target, and `AshMixinsLandTest` and the client game test for the mixins, once the target exists.

## Sources

- **[jar]** Mojang's 26.3 client jar, `https://piston-data.mojang.com/v1/objects/e877b6a07acd633fb3bb475002175cec036e7b87/client.jar`, SHA-1 checked against the version JSON, read with `javap` (JDK 26.0.1). Compared with Loom's `minecraft-merged-1.21.11-loom.mappings.1_21_11.layered+hash.2198-v2.jar` from this repository's build.
- **[ver]** Mojang's [version manifest](https://piston-meta.mojang.com/mc/game/version_manifest_v2.json) and the [26.3 version JSON](https://piston-meta.mojang.com/v1/packages/5df1795e16eea19d75b923d66ecca9ce183353c5/26.3.json), compared with 1.21.11's, 26.1's and 26.2's.
- **[rt]** Mojang's [runtime manifest](https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json), the one `runtime.rs` pins.
- **[mc]** Mojang, [Minecraft Java Edition 26.3](https://www.minecraft.net/en-us/article/minecraft-java-edition-26-3): "UI", "Technical Changes", "Windowing and Input Backend", "Order-independent Transparency".
- **[meta]** Fabric Meta: [loader versions for 26.3](https://meta.fabricmc.net/v2/versions/loader/26.3), [game versions](https://meta.fabricmc.net/v2/versions/game), and the [26.3 + 0.20.1 profile](https://meta.fabricmc.net/v2/versions/loader/26.3/0.20.1/profile/json) against the [1.21.11 + 0.19.5 one](https://meta.fabricmc.net/v2/versions/loader/1.21.11/0.19.5/profile/json).
- **[maven]** Fabric's Maven, `https://maven.fabricmc.net/`:
  - `fabric-loader` 0.20.1's `.json`, `.jar` and their sidecars;
  - `fabric-api` 0.165.0+26.3's jar, sidecars and POM, and its `maven-metadata.xml`;
  - `sponge-mixin` 0.17.5;
  - `fabric-loader-junit` 0.20.1;
  - Loom's `maven-metadata.xml` and plugin-marker POMs.
- **[fapi]** The Fabric API 0.165.0+26.3 and 0.141.6+1.21.11 jars, their `fabric.mod.json` and nested modules, and `fabric-client-gametest-api-v1` 6.0.11 and 4.3.5, read with `javap`.
- **[fapi-src]** [FabricMC/fabric-api](https://github.com/FabricMC/fabric-api), branch `26.3`: `HudElementRegistry.java`, `HudMixin.java`, `TestInput.java`, `TestInputImpl.java`.
- **[blog-26.1]** Fabric, [Fabric for Minecraft 26.1](https://fabricmc.net/2026/03/14/261.html).
- **[blog-26.2]** Fabric, [Fabric for Minecraft 26.2](https://fabricmc.net/2026/06/15/262.html).
- **[blog-26.3]** Fabric, [Fabric for Minecraft 26.3](https://fabricmc.net/2026/09/15/263.html).
- **[example]** [FabricMC/fabric-example-mod](https://github.com/FabricMC/fabric-example-mod), branch `26.3`: `build.gradle` and `gradle.properties`, as deployed on 24 October 2026.
- **[loom]** The Loom 1.18.2 jar's `META-INF/gradle-plugins/*.properties`.
- **[loom-releases]** [FabricMC/fabric-loom releases](https://github.com/FabricMC/fabric-loom/releases), 1.14 to 1.19.
- **[loader-releases]** [FabricMC/fabric-loader releases](https://github.com/FabricMC/fabric-loader/releases), 0.19.3 to 0.20.1.
- **[modrinth]** Modrinth's API, [Sodium's versions for 26.3](https://api.modrinth.com/v2/project/AANobbMI/version?game_versions=%5B%2226.3%22%5D&loaders=%5B%22fabric%22%5D).
- **[sodium]** The Sodium 0.9.2+mc26.3 jar, its SHA-512 checked against Modrinth's, its mixin classes read with `javap`.
- **[sdl]** SDL3's [SDL_StartTextInput](https://wiki.libsdl.org/SDL3/SDL_StartTextInput) and [SDL_TextInputActive](https://wiki.libsdl.org/SDL3/SDL_TextInputActive).
- **[ash]** This repository:
  - `client/target-1.21.11` (its sources, `build.gradle`, `ash.mixins.json`);
  - `client/gradle.properties`, `client/gradle/wrapper/gradle-wrapper.properties`;
  - `client/shared/.../report/Feature.java`, `client/shared/.../ui/Panel.java`;
  - `launcher/core/src/loader.rs`, `runtime.rs`, `launch.rs`;
  - `.github/workflows/checks.yml`.
