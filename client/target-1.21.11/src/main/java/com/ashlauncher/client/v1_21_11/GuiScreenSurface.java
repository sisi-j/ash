package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.ui.ScreenSurface;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * ash's interface drawn through 1.21.11's GuiGraphics: one line per method,
 * as the seam asks.
 */
final class GuiScreenSurface implements ScreenSurface {

    private final GuiGraphics graphics;
    private final Font font;

    GuiScreenSurface(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        graphics.fill(x, y, x + width, y + height, colour);
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        graphics.drawString(font, text, x, y, colour, false);
    }

    @Override
    public int textWidth(String text) {
        return font.width(text);
    }

    @Override
    public int lineHeight() {
        return font.lineHeight;
    }
}
