package com.ashlauncher.client.v26_3;

import com.ashlauncher.client.hud.HudSurface;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 26.3's answer to {@link HudSurface}, over the {@code GuiGraphicsExtractor} the
 * game hands to a HUD element.
 *
 * <p>One draw's worth: the game gives a fresh {@code GuiGraphicsExtractor} each frame,
 * so this is made per frame rather than held.
 *
 * <p>Nothing here decides anything. Every method is a line over the game's own
 * API, because this class is reviewed by eye and nothing else.
 */
public final class GuiGraphicsHudSurface implements HudSurface {

    private final GuiGraphicsExtractor graphics;

    public GuiGraphicsHudSurface(GuiGraphicsExtractor graphics) {
        this.graphics = graphics;
    }

    @Override
    public int width() {
        return graphics.guiWidth();
    }

    @Override
    public int height() {
        return graphics.guiHeight();
    }

    @Override
    public int textWidth(String text) {
        return Minecraft.getInstance().font.width(text);
    }

    @Override
    public int lineHeight() {
        return Minecraft.getInstance().font.lineHeight;
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        // This overload passes `true` for the drop shadow - checked in the
        // bytecode, not assumed - which is what 1.8.9's `drawWithShadow` does,
        // so the two targets draw the same text the same way.
        graphics.text(Minecraft.getInstance().font, text, x, y, colour);
    }

    @Override
    public void drawText(String text, int x, int y, int colour, float scale, boolean shadow) {
        // The GUI's pose is a 2D matrix stack here: moved to the text's
        // corner and scaled about it, so the corner stays where it was asked.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.text(Minecraft.getInstance().font, text, 0, 0, colour, shadow);
        graphics.pose().popMatrix();
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        // GuiGraphicsExtractor.fill takes corners, not a size, and draws with the GUI
        // pipeline's ordinary blending - never the crosshair's invert.
        graphics.fill(x, y, x + width, y + height, colour);
    }


    @Override
    public boolean hudHidden() {
        return Minecraft.getInstance().gui.hud.isHidden();
    }
}
