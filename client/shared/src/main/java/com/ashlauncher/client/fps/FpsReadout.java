package com.ashlauncher.client.fps;

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
     * Top-left, a margin in. Everything vanilla anchors lives at the bottom or
     * the centre - hotbar, status bars, chat, crosshair - and the top-left is
     * empty on both targets until the debug screen opens, which it is the
     * readout's job to make unnecessary.
     */
    static final int MARGIN = 4;

    /**
     * Opaque white, as the marker is, and for the same reason: an unset alpha
     * draws nothing on 1.21.11 and opaque on 1.8.9. See {@link HudSurface#drawText}.
     */
    static final int COLOUR = 0xFFFFFFFF;

    private final FrameRate frameRate;
    private final boolean enabled;

    /**
     * @param enabled whether the player wants it, from their settings. Read
     *     once, at startup: there is no in-game editor yet, so the file only
     *     changes between sessions.
     */
    public FpsReadout(FrameRate frameRate, boolean enabled) {
        this.frameRate = frameRate;
        this.enabled = enabled;
    }

    /**
     * Draws the readout, unless the player has hidden the HUD or opened the
     * debug screen - which draws its own frame rate from this same corner.
     */
    public void draw(HudSurface surface) {
        if (!enabled || surface.hudHidden() || surface.debugScreenShown()) {
            return;
        }
        surface.drawText(frameRate.perSecond() + " FPS", MARGIN, MARGIN, COLOUR);
    }
}
