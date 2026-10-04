package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Raster;
import com.mojang.blaze3d.platform.NativeImage;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
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
 * <p>Each raster becomes a texture the first time it is drawn and stays one
 * while it is in use, up to a cap, after which the least recently drawn are
 * released - never one drawn this frame, because the GUI is rendered after
 * it is submitted, and a texture released before then would draw as missing.
 * Nothing here decides anything else.
 */
final class GuiCanvas implements Canvas {

    /** At most this many pixels of uploaded rasters are kept: 32 MB of RGBA. */
    private static final long MAX_PIXELS = 8L * 1024 * 1024;

    /** An uploaded raster, and the frame it was last drawn in. */
    private static final class Uploaded {
        final Identifier id;
        long frame;

        Uploaded(Identifier id) {
            this.id = id;
        }
    }

    private static final Map<Raster, Uploaded> TEXTURES = new LinkedHashMap<>(256, 0.75f, true);
    private static long pixels;
    private static int next;
    private static long frame;

    private final GuiGraphics graphics;
    private final int width;
    private final int height;

    /** One frame's worth: made afresh each time the screen renders. */
    GuiCanvas(GuiGraphics graphics, int width, int height) {
        frame++;
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
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture(raster), x, y, 0f, 0f, w, h, raster.width(), raster.height(),
                raster.width(), raster.height(), tint);
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        graphics.enableScissor(x, y, x + w, y + h);
    }

    @Override
    public void unclip() {
        graphics.disableScissor();
    }

    private static Identifier texture(Raster raster) {
        Uploaded uploaded = TEXTURES.get(raster);
        if (uploaded != null) {
            uploaded.frame = frame;
            return uploaded.id;
        }
        NativeImage image = new NativeImage(raster.width(), raster.height(), false);
        int[] argb = raster.argb();
        for (int y = 0; y < raster.height(); y++) {
            for (int x = 0; x < raster.width(); x++) {
                image.setPixel(x, y, argb[y * raster.width() + x]);
            }
        }
        Identifier id = Identifier.fromNamespaceAndPath("ash", "piece/" + next++);
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ash interface piece", image));
        uploaded = new Uploaded(id);
        uploaded.frame = frame;
        TEXTURES.put(raster, uploaded);
        pixels += (long) raster.width() * raster.height();
        evict();
        return id;
    }

    private static void evict() {
        Iterator<Map.Entry<Raster, Uploaded>> oldest = TEXTURES.entrySet().iterator();
        while (pixels > MAX_PIXELS && oldest.hasNext()) {
            Map.Entry<Raster, Uploaded> entry = oldest.next();
            if (entry.getValue().frame == frame) {
                return;
            }
            Minecraft.getInstance().getTextureManager().release(entry.getValue().id);
            pixels -= (long) entry.getKey().width() * entry.getKey().height();
            oldest.remove();
        }
    }
}
