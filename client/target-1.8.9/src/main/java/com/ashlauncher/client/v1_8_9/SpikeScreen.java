package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.spike.SpikePanel;
import com.ashlauncher.client.v1_8_9.mixin.GameRendererInvoker;
import com.mojang.blaze3d.platform.GlStateManager;
import java.awt.image.BufferedImage;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;

/**
 * THROWAWAY spike for research 0007: the Java 2D panel uploaded once and drawn
 * 1:1 at the screen's real resolution, over the game's own blur shader, which
 * the game runs after the world and before the HUD and any screen.
 */
public final class SpikeScreen extends Screen {

    private static final Identifier TEXTURE = new Identifier("ash", "spike_panel");
    private static final Identifier BLUR = new Identifier("shaders/post/blur.json");

    public static volatile long lastRenderNanos;
    public static volatile long lastUploadNanos;
    public static volatile String lastSize = "";
    public static volatile boolean blurLoaded;

    private int textureWidth;
    private int textureHeight;

    @Override
    public void init() {
        ((GameRendererInvoker) client.gameRenderer).ash$loadShader(BLUR);
        blurLoaded = client.gameRenderer.getShader() != null;
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        int width = client.width;
        int height = client.height;
        if (width != textureWidth || height != textureHeight) {
            upload(width, height);
        }
        int scale = new Window(client).getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(1f / scale, 1f / scale, 1f);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        GlStateManager.color(1f, 1f, 1f, 1f);
        client.getTextureManager().bindTexture(TEXTURE);
        DrawableHelper.drawTexture(0, 0, 0f, 0f, width, height, (float) width, (float) height);
        GlStateManager.popMatrix();
    }

    private void upload(int width, int height) {
        BufferedImage image = SpikePanel.render(width, height);
        lastRenderNanos = SpikePanel.lastRenderNanos;
        long started = System.nanoTime();
        client.getTextureManager().loadTexture(TEXTURE, new NativeImageBackedTexture(image));
        lastUploadNanos = System.nanoTime() - started;
        lastSize = width + "x" + height + " at GUI scale " + new Window(client).getScaleFactor();
        textureWidth = width;
        textureHeight = height;
    }

    @Override
    public void removed() {
        client.gameRenderer.disableShader();
        client.getTextureManager().close(TEXTURE);
    }

    @Override
    public boolean shouldPauseGame() {
        return false;
    }
}
