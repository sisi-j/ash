package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.ui.ScreenSurface;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawableHelper;

/**
 * ash's interface drawn through 1.8.9's DrawableHelper and TextRenderer: a
 * line or two per method, as the seam asks.
 */
final class LegacyScreenSurface implements ScreenSurface {

    private final TextRenderer textRenderer;

    LegacyScreenSurface(TextRenderer textRenderer) {
        this.textRenderer = textRenderer;
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        // Corners, not a size. DrawableHelper.fill leaves the GL colour at the
        // fill's, which would tint whatever is drawn next: put back to white.
        DrawableHelper.fill(x, y, x + width, y + height, colour);
        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        textRenderer.draw(text, x, y, colour);
    }

    @Override
    public int textWidth(String text) {
        return textRenderer.getStringWidth(text);
    }

    @Override
    public int lineHeight() {
        return textRenderer.fontHeight;
    }
}
