package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.PieceCache;
import com.ashlauncher.client.ui.draw.Raster;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

/**
 * ash's panel drawn through 1.8.9's GL state, in real pixels: the screen has
 * already scaled the matrix by one over the GUI scale, so one unit here is
 * one pixel on screen (`docs/research/0007`).
 *
 * <p>Which rasters stay uploaded is the shared {@link PieceCache}'s
 * decision; this only says how to upload one - a texture under an id of
 * ash's own - and how to free one. Nothing here decides anything.
 */
final class LegacyCanvas implements Canvas {

    /** At most this many pixels of uploaded rasters are kept: 32 MB. */
    private static final PieceCache<Identifier> TEXTURES = new PieceCache<>(8L * 1024 * 1024,
            new PieceCache.Uploader<Identifier>() {
                private int next;

                @Override
                public Identifier upload(Raster raster) {
                    NativeImageBackedTexture texture = new NativeImageBackedTexture(raster.width(), raster.height());
                    System.arraycopy(raster.argb(), 0, texture.getPixels(), 0, raster.width() * raster.height());
                    texture.upload();
                    Identifier id = new Identifier("ash", "piece/" + next++);
                    MinecraftClient.getInstance().getTextureManager().loadTexture(id, texture);
                    return id;
                }

                @Override
                public void release(Identifier id) {
                    MinecraftClient.getInstance().getTextureManager().close(id);
                }
            });

    private final MinecraftClient client;
    private final int width;
    private final int height;

    /** One frame's worth: made afresh each time the screen renders. */
    LegacyCanvas(MinecraftClient client, int width, int height) {
        TEXTURES.nextFrame();
        this.client = client;
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
        // Corners, not a size. DrawableHelper.fill sets ordinary blending for
        // itself and leaves the GL colour at the fill's: put back to white.
        DrawableHelper.fill(x, y, x + w, y + h, argb);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void draw(Raster raster, int x, int y, float opacity) {
        drawStretched(raster, x, y, raster.width(), raster.height(), opacity);
    }

    @Override
    public void drawStretched(Raster raster, int x, int y, int w, int h, float opacity) {
        client.getTextureManager().bindTexture(TEXTURES.get(raster));
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1.0F, 1.0F, 1.0F, Math.max(0f, Math.min(1f, opacity)));
        DrawableHelper.drawTexture(x, y, 0f, 0f, raster.width(), raster.height(), w, h, raster.width(), raster.height());
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        // Window pixels, counted from the bottom.
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(x, height - (y + h), w, h);
    }

    @Override
    public void unclip() {
        GL11.glDisable(GL11.GL_SCISSOR_TEST);
    }
}
