package com.ashlauncher.client.fps;

import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.HudSurface;

/**
 * The frame rate, on screen, without the debug screen.
 *
 * <p>It reads and looks the same on both version targets because everything
 * that could differ lives here: the text, the place, the colour, and when to
 * step aside. What each target contributes is a frame count and a surface to
 * draw on, and both are one line over the game's own API. The font is the
 * game's, and the two ship the same one.
 *
 * <p>Read from the game every frame rather than once: the game updates its
 * count once a second, and a number held from the moment the world loaded
 * would be wrong for the rest of the session.
 */
public final class FpsReadout {

    /**
     * Opaque white, as the marker is, and for the same reason: an unset alpha
     * draws nothing on 1.21.11 and opaque on 1.8.9. See {@link HudSurface#drawText}.
     */
    public static final int COLOUR = 0xFFFFFFFF;

    private final FrameRate frameRate;
    private final HudLayout layout;

    /**
     * @param layout where it sits, and whether the player wants it - both
     *     from their settings, and asked every frame, so a change on the
     *     settings screen shows at once
     */
    public FpsReadout(FrameRate frameRate, HudLayout layout) {
        this.frameRate = frameRate;
        this.layout = layout;
    }

    /**
     * Draws the readout, unless the player has hidden the HUD or opened the
     * debug screen - which draws its own frame rate.
     */
    public void draw(HudSurface surface) {
        if (!layout.shown(HudLayout.Readout.FPS) || surface.hudHidden() || surface.debugScreenShown()) {
            return;
        }
        String text = frameRate.perSecond() + " FPS";
        int[] at = layout.place(HudLayout.Readout.FPS, surface.textWidth(text), surface.lineHeight(),
                surface.width(), surface.height());
        surface.drawText(text, at[0], at[1], COLOUR);
    }
}
