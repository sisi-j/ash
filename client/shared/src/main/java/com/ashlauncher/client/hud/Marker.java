package com.ashlauncher.client.hud;

/**
 * The one visible fact the walking skeleton is for: ash's own code ran inside
 * the game and put something on the screen.
 *
 * <p>Deliberately trivial. What is being proved is the chain from a Gradle
 * build to pixels on both version targets, not the marker - so the marker is
 * the smallest thing that can be seen and the least that can go wrong.
 *
 * <p>Bottom left, because the top left is the vanilla debug overlay's and the
 * top right is where a readout will want to go. Nothing here is a decision
 * about where a real feature belongs; the FPS readout picks its own place
 * against the hotbar, the crosshair and the chat.
 */
public final class Marker {

    /** Short, and the same on both targets, so seeing it says which build ran. */
    public static final String TEXT = "ash";

    /** Clear of the very edge, where a scaled GUI can crop a pixel or two. */
    static final int MARGIN = 4;

    /** Opaque white. A zero alpha here would draw nothing at all. */
    static final int COLOUR = 0xFFFFFFFF;

    public void draw(HudSurface surface) {
        int y = surface.height() - surface.lineHeight() - MARGIN;
        surface.drawText(TEXT, MARGIN, y, COLOUR);
    }
}
