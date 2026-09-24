package com.ashlauncher.client.hud;

/**
 * The one visible fact the walking skeleton is for: ash's own code ran inside
 * the game and put something on the screen.
 *
 * <p>Deliberately trivial. What is being proved is the chain from a Gradle
 * build to pixels on both version targets, not the marker - so the marker is
 * the smallest thing that can be seen and the least that can go wrong.
 *
 * <p>Bottom left, out of the FPS readout's corner. Nothing here is a decision
 * about where a real feature belongs; the readout picks its own place against
 * the hotbar, the crosshair and the chat.
 */
public final class Marker {

    /** Short, and the same on both targets, so seeing it says which build ran. */
    public static final String TEXT = "ash";

    /** Clear of the very edge, where a scaled GUI can crop a pixel or two. */
    static final int MARGIN = 4;

    /**
     * Opaque white, and opaque on purpose: at zero alpha 1.21.11 draws nothing
     * and 1.8.9 draws it opaque anyway, so an unset alpha would look different
     * on the two targets - see {@link HudSurface#drawText}.
     */
    static final int COLOUR = 0xFFFFFFFF;

    public void draw(HudSurface surface) {
        if (surface.hudHidden()) {
            return;
        }
        int y = surface.height() - surface.lineHeight() - MARGIN;
        surface.drawText(TEXT, MARGIN, y, COLOUR);
    }
}
