package com.ashlauncher.client.ui.draw;

/**
 * Shapes built from {@link Ink}'s small pieces and plain fills, so nothing
 * the size of a panel is ever rasterised: a rounded rectangle is four
 * anti-aliased corners and three fills, and its shadow is four corners and
 * four stretched strips.
 */
public final class Paint {

    private Paint() {
    }

    /** {@code argb} with its alpha scaled by {@code opacity}. */
    public static int fade(int argb, float opacity) {
        if (opacity >= 1f) {
            return argb;
        }
        int alpha = Math.round((argb >>> 24) * Math.max(0f, opacity));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    /** A rounded rectangle, every corner rounded. */
    public static void roundRect(Canvas canvas, int x, int y, int width, int height, int radius, int argb, float opacity) {
        roundRect(canvas, x, y, width, height, radius, argb, opacity, true, true);
    }

    /**
     * A rectangle with its left corners, right corners or both rounded - the
     * panel's strip is rounded on the left and meets the rest of the panel
     * square on the right.
     */
    public static void roundRect(Canvas canvas, int x, int y, int width, int height, int radius, int argb, float opacity,
            boolean roundLeft, boolean roundRight) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        int fill = fade(argb, opacity);
        if (r == 0) {
            canvas.fill(x, y, width, height, fill);
            return;
        }
        canvas.fill(x + r, y, width - 2 * r, height, fill);
        canvas.fill(x, y + r, r, height - 2 * r, fill);
        canvas.fill(x + width - r, y + r, r, height - 2 * r, fill);
        corner(canvas, x, y, r, 0, argb, opacity, roundLeft);
        corner(canvas, x + width - r, y, r, 1, argb, opacity, roundRight);
        corner(canvas, x + width - r, y + height - r, r, 2, argb, opacity, roundRight);
        corner(canvas, x, y + height - r, r, 3, argb, opacity, roundLeft);
    }

    private static void corner(Canvas canvas, int x, int y, int r, int quadrant, int argb, float opacity, boolean round) {
        if (round) {
            canvas.draw(Ink.corner(r, quadrant, argb), x, y, opacity);
        } else {
            canvas.fill(x, y, r, r, fade(argb, opacity));
        }
    }

    /**
     * A soft shadow fading out to {@code reach} past a rounded rectangle's
     * edges, drawn only outside it, so a translucent rectangle on top is not
     * darkened twice.
     */
    public static void shadow(Canvas canvas, int x, int y, int width, int height, int radius, int reach, int alpha) {
        int r = Math.max(0, Math.min(radius, Math.min(width, height) / 2));
        canvas.draw(Ink.shadow(r, reach, alpha, 0), x - reach, y - reach, 1f);
        canvas.draw(Ink.shadow(r, reach, alpha, 1), x + width - r, y - reach, 1f);
        canvas.draw(Ink.shadow(r, reach, alpha, 2), x + width - r, y + height - r, 1f);
        canvas.draw(Ink.shadow(r, reach, alpha, 3), x - reach, y + height - r, 1f);
        int across = width - 2 * r;
        int down = height - 2 * r;
        if (across > 0) {
            canvas.drawStretched(Ink.shadow(r, reach, alpha, 4), x + r, y - reach, across, reach, 1f);
            canvas.drawStretched(Ink.shadow(r, reach, alpha, 6), x + r, y + height, across, reach, 1f);
        }
        if (down > 0) {
            canvas.drawStretched(Ink.shadow(r, reach, alpha, 5), x + width, y + r, reach, down, 1f);
            canvas.drawStretched(Ink.shadow(r, reach, alpha, 7), x - reach, y + r, reach, down, 1f);
        }
    }
}
