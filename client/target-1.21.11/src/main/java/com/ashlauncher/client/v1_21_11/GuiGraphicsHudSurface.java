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
public final class GuiGraphicsHudSurface implements HudSurface {

    private final GuiGraphics graphics;

    public GuiGraphicsHudSurface(GuiGraphics graphics) {
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
        graphics.drawString(Minecraft.getInstance().font, text, x, y, colour);
    }

    @Override
    public void drawText(String text, int x, int y, int colour, float scale, boolean shadow) {
        // The GUI's pose is a 2D matrix stack here: moved to the text's
        // corner and scaled about it, so the corner stays where it was asked.
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(scale, scale);
        graphics.drawString(Minecraft.getInstance().font, text, 0, 0, colour, shadow);
        graphics.pose().popMatrix();
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        // GuiGraphics.fill takes corners, not a size, and draws with the GUI
        // pipeline's ordinary blending - never the crosshair's invert.
        graphics.fill(x, y, x + width, y + height, colour);
    }

    @Override
    public boolean debugScreenShown() {
        return MinecraftGameView.INSTANCE.debugScreenShown();
    }

    @Override
    public boolean hudHidden() {
        return Minecraft.getInstance().options.hideGui;
    }
}
