package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.hud.HudSurface;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.util.Window;

/**
 * 1.8.9's answer to {@link HudSurface}.
 *
 * <p>One frame's worth. {@code Window} reads the display size and the player's
 * GUI scale when it is constructed, so holding one would keep answering with
 * the size of a window that has since been resized.
 *
 * <p>Nothing here decides anything. Every method is a line over the game's own
 * API, because this class is reviewed by eye and nothing else.
 */
public final class LegacyHudSurface implements HudSurface {

    private final MinecraftClient minecraft;
    private final Window window;

    public LegacyHudSurface(MinecraftClient minecraft) {
        this.minecraft = minecraft;
        this.window = new Window(minecraft);
    }

    @Override
    public int width() {
        return window.getWidth();
    }

    @Override
    public int height() {
        // Scaled units, matching what the modern target's `guiHeight` gives -
        // the raw display height is several times larger and would put the
        // marker off the bottom of the screen.
        return window.getHeight();
    }

    @Override
    public int lineHeight() {
        return minecraft.textRenderer.fontHeight;
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        minecraft.textRenderer.drawWithShadow(text, x, y, colour);
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        // Corners, not a size. DrawableHelper.fill sets ordinary blending for
        // itself - so it draws the colour even where the game has set the
        // crosshair's invert - and leaves the GL colour at the fill's, which
        // would tint whatever the game draws next. Put back to white.
        DrawableHelper.fill(x, y, x + width, y + height, colour);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public boolean debugScreenShown() {
        return minecraft.options.debugEnabled;
    }

    @Override
    public boolean hudHidden() {
        // The game does fire the HUD callback under F1, while a screen is
        // open - it draws the vanilla HUD behind menus even then. What ash
        // does about that is the shared module's decision, not this line's.
        return minecraft.options.hudHidden;
    }
}
