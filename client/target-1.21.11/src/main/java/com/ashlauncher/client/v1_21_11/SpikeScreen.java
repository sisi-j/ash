package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.spike.SpikePanel;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.platform.Window;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * THROWAWAY spike for research 0007: the Java 2D panel uploaded once and drawn
 * 1:1 at the screen's real resolution, over the game's own menu blur.
 */
public final class SpikeScreen extends Screen {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("ash", "spike_panel");

    public static volatile long lastRenderNanos;
    public static volatile long lastUploadNanos;
    public static volatile String lastSize = "";

    private int textureWidth;
    private int textureHeight;

    public SpikeScreen() {
        super(Component.literal("ash spike"));
    }

    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        // The game's own blur, and none of its darkening: the panel is the dark part.
        renderBlurredBackground(graphics);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float tickDelta) {
        Window window = minecraft.getWindow();
        int width = window.getWidth();
        int height = window.getHeight();
        if (width != textureWidth || height != textureHeight) {
            upload(width, height);
        }
        float scale = (float) window.getGuiScale();
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, 0, 0, 0f, 0f, width, height, width, height);
        graphics.pose().popMatrix();
    }

    private void upload(int width, int height) {
        BufferedImage image = SpikePanel.render(width, height);
        lastRenderNanos = SpikePanel.lastRenderNanos;
        long started = System.nanoTime();
        int[] pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
        NativeImage nativeImage = new NativeImage(width, height, false);
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                nativeImage.setPixel(x, y, pixels[y * width + x]);
            }
        }
        minecraft.getTextureManager().register(TEXTURE, new DynamicTexture(() -> "ash spike panel", nativeImage));
        lastUploadNanos = System.nanoTime() - started;
        lastSize = width + "x" + height + " at GUI scale " + minecraft.getWindow().getGuiScale();
        textureWidth = width;
        textureHeight = height;
    }

    @Override
    public void removed() {
        minecraft.getTextureManager().release(TEXTURE);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
