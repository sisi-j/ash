# How each target can draw the final in-game design

Research date: 2026-10-03.

This answers item 9 of Phase 3's Further Notes (`docs/specs/0003-phase-3-the-full-client.md`), added with the final design. The design itself is in the spec's *The settings model and the settings screen*.

Every claim below is labelled:

- **[DOC]**: the owner's own published text says this.
- **[PRACTICE]**: observed directly, in the bytes of a mapped game jar, in a game run, or in a file's own licence.

Where this document gives an opinion rather than a source, it says **Judgement**.

A throwaway spike tested the approach in both real games, on branch `spike/final-drawing` (draft PR #61, never merged). It draws a slice of the approved panel with Java 2D and Inter at the screen's real resolution, uploads the result once, and draws it 1:1 over each game's own blur. Both real-game tests took a screenshot of it, in CI and, for 1.21.11, on a Windows machine as well.

---

## Summary

**It works on both targets, and both can look the same.**

1. **Text is ash's own, rasterised with Java 2D from Inter's static TTFs.**
   - Both games' Java runtimes include `java.desktop`: 1.21.11's `java-runtime-delta` 21.0.7 lists it, and 1.8.9's `jre-legacy` 1.8.0_51 is a full JRE **[PRACTICE]**.
   - The spike drew Inter crisply inside both games **[PRACTICE]**.
   - 1.21.11's own TTF support is the weaker route: it samples font textures with `NEAREST` and fixes the oversampling per font definition, so text would be crisp at one GUI scale and blocky at the others **[PRACTICE]**.
2. **Drawing is at real resolution.**
   - 1.21.11: `GuiGraphics.pose()` is a `Matrix3x2fStack`, and scaling it by `1/guiScale` puts one unit on one real pixel **[PRACTICE]**.
   - 1.8.9: `GlStateManager.scale(1/scale, 1/scale, 1)` does the same **[PRACTICE]**.
   - At 1:1, a texture's `NEAREST` sampling is exact, so nothing blurs.
3. **Rounded, anti-aliased shapes are drawn by Java 2D** as part of the same rasterisation. The spike's corners and triangles are smooth in both games **[PRACTICE]**.
4. **Blur is each game's own.**
   - **1.21.11:** `Screen.renderBlurredBackground(GuiGraphics)` blurs everything drawn before the screen, the HUD included, at the player's own "Menu Background Blur" strength **[PRACTICE]**.
   - **1.8.9:** `GameRenderer` has a private `loadShader(Identifier)` and runs its post shader right after `renderWorld`, before the HUD and any screen **[PRACTICE]**. Its bundled `shaders/post/blur.json` is a two-pass Gaussian with `Radius` 20 **[PRACTICE]**. The spike loaded it through an `@Invoker`, and the world blurred behind the panel **[PRACTICE]**.
5. **Icons are Lucide's**: ISC, with the Feather-derived icons under MIT. Inter is under the SIL OFL 1.1 **[PRACTICE]**. Both ship with their notices.
6. **Cost decides the architecture.** A full repaint of the panel is too slow for every frame, so pieces are rasterised once and moved by the GPU (section 3).

## 1. Text

**Inter, from its 4.1 release.** `Inter-4.1.zip` has sha256 `9883fdd4a49d4fb66bd8177ba6625ef9a64aa45899767dde3d36aa425756b11e`. It holds static TTFs under `extras/ttf/`, among them `Inter-Regular`, `-SemiBold`, `-Bold` and `-ExtraBold`, and variable fonts at the top level **[PRACTICE]**. Java 2D does not drive variable-font axes, so ash ships the static weights it uses. They are about 410 KB each **[PRACTICE]**.

**1.21.11's own TTF provider.** `TrueTypeGlyphProviderDefinition` takes a file, a `size`, an `oversample` and a `shift` **[PRACTICE]**. `FontTexture` creates its textures with `FilterMode.NEAREST` **[PRACTICE]**. Glyphs are rasterised once at `size × oversample`, so they are sharp only where the screen's scale equals the oversample. Interface size and GUI scale both vary that. It also exists on 1.21.11 only. **Judgement:** not used.

**Java 2D in the game.** The spike called `Font.createFont(TRUETYPE_FONT, …)` and drew with anti-aliasing and fractional metrics on. That worked in 1.21.11 (Java 21) on Windows and in CI on Linux, and in 1.8.9 (Java 8) in CI **[PRACTICE]**.

- The first draw in each session includes loading the fonts. It took 126 ms in 1.8.9 and 755 ms in 1.21.11 in CI, and 148 ms in 1.21.11 on a desktop **[PRACTICE]**.
- **Judgement:** load the fonts when the client starts, off the render thread, so the first open of the panel does not pay for it.

## 2. Drawing at real resolution, and blur

- **1.21.11.** In `render`, push `pose()`, scale by `1/getGuiScale()`, and draw. The spike drew with `GuiGraphics.blit(RenderPipelines.GUI_TEXTURED, id, 0, 0, 0, 0, w, h, w, h)` **[PRACTICE]**.
  - Textures are a `DynamicTexture` over a `NativeImage`, registered with `TextureManager.register` and released with `release` **[PRACTICE]**. `NativeImage.setPixel` takes ARGB: it calls `ARGB.toABGR` **[PRACTICE]**.
  - `renderWithTooltipAndSubtitles` calls `renderBackground` and then `render` in separate strata, so overriding `renderBackground` to call only `renderBlurredBackground` gives blur without the game's darkening **[PRACTICE]**.
- **1.8.9.** In `render`, push the matrix, scale by `1/scaleFactor`, enable blending, and draw with `DrawableHelper.drawTexture(x, y, u, v, w, h, texW, texH)` **[PRACTICE]**. Textures are a `NativeImageBackedTexture(BufferedImage)`, registered with `TextureManager.loadTexture` and freed with `close` **[PRACTICE]**.
  - Blur: `loadShader(new Identifier("shaders/post/blur.json"))` on opening, and `disableShader()` on closing **[PRACTICE]**.
  - The game uses the same slot for spectator views and its "super secret settings". **Judgement:** keep the panel's open time short and restore whatever shader was there before.
  - It needs framebuffer support, which is the game's own `areShadersSupported()`. Without it, the panel falls back to a darker panel with no blur.
- **One visible difference.** On 1.21.11 the HUD is blurred with the world. On 1.8.9 the blur runs before the HUD, so the hotbar, hearts and crosshair stay sharp under the panel **[PRACTICE]**, as the spike's screenshots show.
  - **Judgement:** hide the HUD while the panel is open on 1.8.9, so both look the same. ash already wraps HUD drawing there.
- **Blur strength.** 1.21.11's comes from the player's own setting. 1.8.9's `Radius` uniform can be set per pass. **Judgement:** the open animation fades the blur in through the panel's own overlay. ash does not override the 1.21.11 player's choice.

## 3. Cost, and what it means for the architecture

Measured with the spike's panel, best of ten, on a desktop JDK **[PRACTICE]**:

| Screen | Full repaint with Java 2D |
| --- | --- |
| 1920×1080 | 18.3 ms |
| 2560×1440 | 19.7 ms |
| 3840×2160 | 38.9 ms |

Uploading the 854×480 result took 5 ms in 1.21.11 on a desktop, and 13–18 ms in CI's software renderer **[PRACTICE]**.

A full repaint every frame would cost 18–39 ms, which the open animation, a hover and a dragged slider cannot afford. **Judgement:** ash draws the panel from cached pieces, and Java 2D only rasterises those pieces:

- **What is cached.** A rasterised piece is a texture: a tile's body, a run of text at a size and weight, an icon at a size, a rounded rectangle at a size and radius. Each is drawn once and redrawn only when it changes, such as a renamed label, a new interface size or a resize.
- **What moves.** Each frame the GPU draws those textures at positions and opacities. That is everything the motion needs: rise and fade, tile stagger, cross-fades, switch knobs, the shake.
- **Plain fills** (slider tracks, bars) are fills.
- **Where the code sits.** The shared module owns layout, rasterisation and caching, all plain Java 8 with no game types. Each target only uploads a piece, draws a piece at a place and opacity, fills, clips and blurs. That keeps the targets thin, as the spec requires, and makes both games draw identical pixels.

The FPS cost while the panel is open is measured with the frame-time measurement once the drawing layer exists. It is part of that ticket's acceptance.

## 4. Icons

Lucide is under the ISC licence. Its icons derived from Feather are under MIT (Cole Bemis); the list is in Lucide's `LICENSE` **[PRACTICE]**. Both notices ship with the icons ash uses.

Lucide's icons are 24-unit stroked paths. **Judgement:** ash rasterises them with Java 2D at the exact pixel size needed, like text, so they stay crisp at any interface size. This needs a small SVG-path reader: move, line, curve and arc. Building it is part of the drawing layer.

## 5. What the tickets should take from this

- A drawing layer in the shared module: fonts, text, shapes, icons, a piece cache, opacity. Behind it, a target seam: upload, draw a piece, fill, clip, blur, real-resolution scale.
- Each target's implementation of that seam, with its blur, and on 1.8.9 hiding the HUD while the panel is open.
- The frame-time cost while the panel is open, measured.
- Fonts loaded at startup, off the render thread.
- Inter's and Lucide's notices shipped in the client jar.

## Sources

- The mapped 1.21.11 and 1.8.9 game jars in Loom's cache, read with `javap` **[PRACTICE]**.
- The runtimes' `release` files in ash's depot **[PRACTICE]**.
- Inter 4.1, https://github.com/rsms/inter/releases/tag/v4.1, and its `LICENSE.txt` **[PRACTICE]**.
- Lucide's `LICENSE`, https://github.com/lucide-icons/lucide/blob/main/LICENSE **[PRACTICE]**.
- The spike: branch `spike/final-drawing`, draft PR #61, its CI run 37159782354, and a local 1.21.11 game-test run **[PRACTICE]**.
