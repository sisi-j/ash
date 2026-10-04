package com.ashlauncher.client.ui.draw;

import com.ashlauncher.client.ui.draw.Ink.Corner;
import com.ashlauncher.client.ui.draw.Ink.Edge;

/**
 * Shapes built from {@link Ink}'s small pieces and plain fills, so nothing
 * the size of a panel is ever rasterised: a rounded rectangle is four
 * anti-aliased corners and three fills, its outline four ring corners and
 * four one-pixel fills, and its shadow four corners and four stretched
 * strips.
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
        int r = clamp(radius, width, height);
        int fill = fade(argb, opacity);
        if (r == 0) {
            canvas.fill(x, y, width, height, fill);
            return;
        }
        canvas.fill(x + r, y, width - 2 * r, height, fill);
        canvas.fill(x, y + r, r, height - 2 * r, fill);
        canvas.fill(x + width - r, y + r, r, height - 2 * r, fill);
        for (Corner corner : Corner.values()) {
            int cx = corner.left() ? x : x + width - r;
            int cy = corner.top() ? y : y + height - r;
            if (corner.left() ? roundLeft : roundRight) {
                canvas.draw(Ink.corner(r, corner, argb), cx, cy, opacity);
            } else {
                canvas.fill(cx, cy, r, r, fill);
            }
        }
    }

    /** A rounded rectangle's one-pixel outline, just inside its edge. */
    public static void outline(Canvas canvas, int x, int y, int width, int height, int radius, int argb) {
        int r = clamp(radius, width, height);
        canvas.fill(x + r, y, width - 2 * r, 1, argb);
        canvas.fill(x + r, y + height - 1, width - 2 * r, 1, argb);
        canvas.fill(x, y + r, 1, height - 2 * r, argb);
        canvas.fill(x + width - 1, y + r, 1, height - 2 * r, argb);
        if (r > 0) {
            for (Corner corner : Corner.values()) {
                canvas.draw(Ink.cornerRing(r, corner, argb), corner.left() ? x : x + width - r,
                        corner.top() ? y : y + height - r, 1f);
            }
        }
    }

    /**
     * A soft shadow under a rounded rectangle, lowered by {@code drop} as light
     * from above casts it and fading out to {@code reach}: nine pieces from
     * {@link Ink}. None of it is drawn where the rectangle is, so a
     * translucent rectangle on top is not darkened, and none of it leaves a
     * gap where the rectangle's lowered copy shows below it.
     */
    public static void shadow(Canvas canvas, int x, int y, int width, int height, int radius, int reach, int drop,
            int alpha) {
        int r = clamp(radius, width, height);
        for (Corner corner : Corner.values()) {
            canvas.draw(Ink.shadowCorner(r, reach, drop, alpha, corner), corner.left() ? x - reach : x + width - r,
                    corner.top() ? y + drop - reach : y + height - r, 1f);
        }
        int across = width - 2 * r;
        int down = height - 2 * r - drop;
        if (across > 0) {
            if (reach > drop) {
                canvas.drawStretched(Ink.shadowEdge(r, reach, drop, alpha, Edge.TOP), x + r, y + drop - reach, across,
                        reach - drop, 1f);
            }
            canvas.drawStretched(Ink.shadowEdge(r, reach, drop, alpha, Edge.BOTTOM), x + r, y + height, across,
                    drop + reach, 1f);
        }
        if (down > 0) {
            canvas.drawStretched(Ink.shadowEdge(r, reach, drop, alpha, Edge.LEFT), x - reach, y + drop + r, reach, down, 1f);
            canvas.drawStretched(Ink.shadowEdge(r, reach, drop, alpha, Edge.RIGHT), x + width, y + drop + r, reach, down,
                    1f);
        }
    }

    private static int clamp(int radius, int width, int height) {
        return Math.max(0, Math.min(radius, Math.min(width, height) / 2));
    }
}
