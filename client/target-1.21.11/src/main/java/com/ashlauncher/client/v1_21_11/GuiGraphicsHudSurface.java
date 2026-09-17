package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.hud.HudSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

/**
 * 1.21.11's answer to {@link HudSurface}, over the {@code GuiGraphics} the
 * game hands to a HUD element.
 *
 * <p>One draw's worth: the game gives a fresh {@code GuiGraphics} each frame,
 * so this is made per frame rather than held.
 *
 * <p>Nothing here decides anything. Every method is a line over the game's own
 * API, because this class is reviewed by eye and nothing else.
 */
final class GuiGraphicsHudSurface implements HudSurface {

    private final GuiGraphics graphics;

    GuiGraphicsHudSurface(GuiGraphics graphics) {
        this.graphics = graphics;
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        graphics.drawString(Minecraft.getInstance().font, text, x, y, colour);
    }
}
