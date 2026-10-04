package com.ashlauncher.client.ui;

/**
 * The shapes ash's interface is made of, from nothing but filled rectangles:
 * a rounded rectangle is a stack of rows, each inset by as much as the corner
 * takes away, which is how a pixel display draws a curve anyway.
 */
final class Shapes {

    private Shapes() {
    }

    /**
     * A rectangle with corners of radius {@code radius}, drawn as stepped
     * rows. Radius 2 cuts one pixel from each corner, radius 3 a two-step
     * curve, and half the height a pill.
     */
    static void rounded(ScreenSurface surface, Rect at, int radius, int colour) {
        int r = Math.min(radius, Math.min(at.width, at.height) / 2);
        if (r <= 0) {
            surface.fill(at.x, at.y, at.width, at.height, colour);
            return;
        }
        for (int row = 0; row < r; row++) {
            int inset = inset(r, row);
            surface.fill(at.x + inset, at.y + row, at.width - 2 * inset, 1, colour);
            surface.fill(at.x + inset, at.y + at.height - 1 - row, at.width - 2 * inset, 1, colour);
        }
        surface.fill(at.x, at.y + r, at.width, at.height - 2 * r, colour);
    }

    /**
     * A rounded rectangle with a one-unit border: the panel, the search box
     * and every card are drawn this way.
     */
    static void bordered(ScreenSurface surface, Rect at, int radius, int border, int fill) {
        // The border as an edge, never under the fill: the final design's
        // fills are translucent, and a border painted beneath one shows
        // through it.
        int r = Math.max(1, Math.min(radius, Math.min(at.width, at.height) / 2));
        rounded(surface, new Rect(at.x + 1, at.y + 1, at.width - 2, at.height - 2), radius - 1, fill);
        surface.fill(at.x + r, at.y, at.width - 2 * r, 1, border);
        surface.fill(at.x + r, at.y + at.height - 1, at.width - 2 * r, 1, border);
        surface.fill(at.x, at.y + r, 1, at.height - 2 * r, border);
        surface.fill(at.x + at.width - 1, at.y + r, 1, at.height - 2 * r, border);
        if (r >= 2) {
            surface.fill(at.x + 1, at.y + 1, 1, 1, border);
            surface.fill(at.x + at.width - 2, at.y + 1, 1, 1, border);
            surface.fill(at.x + 1, at.y + at.height - 2, 1, 1, border);
            surface.fill(at.x + at.width - 2, at.y + at.height - 2, 1, 1, border);
        }
    }

    /**
     * How far in a row is at the corner, {@code row} rows from the edge: how
     * much of the row lies outside the circle of the corner, rounded up a
     * little, so a small radius still visibly rounds.
     */
    private static int inset(int radius, int row) {
        double dy = radius - row - 0.5;
        double outside = radius - Math.sqrt(Math.max(0, radius * radius - dy * dy));
        return Math.max(0, (int) Math.ceil(outside - 0.25));
    }

    /**
     * An on/off switch: a pill with a white knob. On is green with the knob
     * at the right; off is a faint white with the knob at the left; a switch
     * that cannot be pressed is fainter still.
     */
    static void onOffSwitch(ScreenSurface surface, Rect at, boolean on, boolean available) {
        int track = !available ? Palette.LINE : on ? Palette.GREEN : Palette.RAISED_HOVER;
        int knob = !available ? Palette.MUTED : Palette.TEXT;
        rounded(surface, at, at.height / 2, track);
        int size = at.height - 4;
        int x = on && available ? at.x + at.width - 2 - size : at.x + 2;
        rounded(surface, new Rect(x, at.y + 2, size, size), size / 2, knob);
    }
}
