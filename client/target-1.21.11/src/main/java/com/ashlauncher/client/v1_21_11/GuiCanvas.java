package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.PieceCache;
import com.ashlauncher.client.ui.draw.Raster;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

/**
 * ash's panel drawn through 1.21.11's GuiGraphics, in real pixels: the
 * screen has already scaled the pose by one over the GUI scale, so one unit
 * here is one pixel on screen (`docs/research/0007`).
 *
 * <p>Which rasters stay uploaded is the shared {@link PieceCache}'s
 * decision; this only says how to upload one - a {@code DynamicTexture} under
 * an id of ash's own - and how to free one. Nothing here decides anything.
 */
final class GuiCanvas implements Canvas {

    /** At most this many pixels of uploaded rasters are kept: 32 MB of RGBA. */
    private static final PieceCache<Identifier> TEXTURES = new PieceCache<>(8L * 1024 * 1024,
            new PieceCache.Uploader<Identifier>() {
                private int next;

                @Override
                public Identifier upload(Raster raster) {
                    NativeImage image = new NativeImage(raster.width(), raster.height(), false);
                    int[] argb = raster.argb();
                    for (int y = 0; y < raster.height(); y++) {
                        for (int x = 0; x < raster.width(); x++) {
                            image.setPixel(x, y, argb[y * raster.width() + x]);
                        }
                    }
                    Identifier id = Identifier.fromNamespaceAndPath("ash", "piece/" + next++);
                    Minecraft.getInstance().getTextureManager().register(id,
                            new DynamicTexture(() -> "ash interface piece", image));
                    return id;
                }

                @Override
                public void release(Identifier id) {
                    Minecraft.getInstance().getTextureManager().release(id);
                }
            });

    private final GuiGraphics graphics;
    private final int width;
    private final int height;

    /** One frame's worth: made afresh each time the screen renders. */
    GuiCanvas(GuiGraphics graphics, int width, int height) {
        TEXTURES.nextFrame();
        this.graphics = graphics;
        this.width = width;
        this.height = height;
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void fill(int x, int y, int w, int h, int argb) {
        graphics.fill(x, y, x + w, y + h, argb);
    }

    @Override
    public void draw(Raster raster, int x, int y, float opacity) {
        drawStretched(raster, x, y, raster.width(), raster.height(), opacity);
    }

    @Override
    public void drawStretched(Raster raster, int x, int y, int w, int h, float opacity) {
        int tint = (Math.round(255 * Math.max(0f, Math.min(1f, opacity))) << 24) | 0xFFFFFF;
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURES.get(raster), x, y, 0f, 0f, w, h, raster.width(),
                raster.height(), raster.width(), raster.height(), tint);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        graphics.enableScissor(x, y, x + w, y + h);
    }

    @Override
    public void unclip() {
        graphics.disableScissor();
    }
}
