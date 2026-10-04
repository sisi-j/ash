package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Raster;
import com.mojang.blaze3d.platform.GlStateManager;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>Each raster becomes a texture the first time it is drawn and stays one
 * while it is in use, up to a cap, after which the least recently drawn are
 * freed. 1.8.9 draws immediately, so freeing one is safe at any time.
 * Nothing here decides anything else.
 */
final class LegacyCanvas implements Canvas {

    /** At most this many pixels of uploaded rasters are kept: 32 MB. */
    private static final long MAX_PIXELS = 8L * 1024 * 1024;

    private static final Map<Raster, Identifier> TEXTURES = new LinkedHashMap<>(256, 0.75f, true);
    private static long pixels;
    private static int next;

    private final MinecraftClient client;
    private final int width;
    private final int height;

    LegacyCanvas(MinecraftClient client, int width, int height) {
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
        client.getTextureManager().bindTexture(texture(raster));
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

    private Identifier texture(Raster raster) {
        Identifier id = TEXTURES.get(raster);
        if (id != null) {
            return id;
        }
        NativeImageBackedTexture texture = new NativeImageBackedTexture(raster.width(), raster.height());
        System.arraycopy(raster.argb(), 0, texture.getPixels(), 0, raster.width() * raster.height());
        texture.upload();
        id = new Identifier("ash", "piece/" + next++);
        client.getTextureManager().loadTexture(id, texture);
        TEXTURES.put(raster, id);
        pixels += (long) raster.width() * raster.height();
        Iterator<Map.Entry<Raster, Identifier>> oldest = TEXTURES.entrySet().iterator();
        while (pixels > MAX_PIXELS && oldest.hasNext()) {
            Map.Entry<Raster, Identifier> entry = oldest.next();
            if (entry.getKey() == raster) {
                break;
            }
            client.getTextureManager().close(entry.getValue());
            pixels -= (long) entry.getKey().width() * entry.getKey().height();
            oldest.remove();
        }
        return id;
    }
}
