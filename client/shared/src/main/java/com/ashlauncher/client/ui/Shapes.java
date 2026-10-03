package com.ashlauncher.client.ui;

/**
 * The shapes ash's interface is made of, from nothing but filled rectangles:
 * a rounded rectangle is a stack of rows, each inset by as much as the corner
 * takes away, which is how a pixel display draws a curve anyway.
 */
final class Shapes {

    private Shapes() {
    }

    /** A rectangle with corners of radius {@code radius}, drawn as stepped rows. */
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

    /** How far in a row is at the corner, {@code row} rows from the edge. */
    private static int inset(int radius, int row) {
        double dy = radius - row - 0.5;
        return (int) Math.round(radius - Math.sqrt(Math.max(0, radius * radius - dy * dy)));
    }

    /**
     * An on/off switch: a pill with a knob. On is the brief's primary colour
     * with the knob at the right; off is its emphasis grey with the knob at
     * the left; a switch that cannot be pressed is dimmer still.
     */
    static void toggle(ScreenSurface surface, Rect at, boolean on, boolean available) {
        int track = !available ? Palette.LINE : on ? Palette.TEXT : Palette.EMPHASIS;
        int knob = !available ? Palette.EMPHASIS : on ? Palette.BACKGROUND : Palette.MUTED;
        rounded(surface, at, at.height / 2, track);
        int size = at.height - 4;
        int x = on && available ? at.x + at.width - 2 - size : at.x + 2;
        rounded(surface, new Rect(x, at.y + 2, size, size), size / 2, knob);
    }
}
