package com.ashlauncher.client.ui;

/**
 * The panel's approved motion as pure functions of time (#66): given how
 * long ago something started, where it is and how visible. Nothing here
 * keeps state or reads a clock, so every animation is tested by asking it
 * about a moment.
 *
 * <p>Lengths and curves are the approved mockup's (branch
 * {@code prototype/final-design}): its ease-out is
 * {@code cubic-bezier(0.16, 1, 0.3, 1)}, fast then easing to a stop.
 * Distances are in the panel's unit, a hundredth of the screen's width,
 * except the panel's own rise, which is a share of its height.
 */
final class Motion {

    static final long MS = 1_000_000L;

    /** Opening: the overlay and the panel fade in while the panel rises into place. */
    static final long OPEN = 350 * MS;
    /** Closing: the reverse, in half the time. */
    static final long CLOSE = 175 * MS;
    /** How far below its place the panel starts, as a share of its height; and how far it sinks on closing. */
    static final float OPEN_RISE = 0.09f;
    static final float CLOSE_SINK = 0.04f;

    /** A tile rises and fades in over this long, each one this much after the one before it. */
    static final long TILE = 300 * MS;
    static final long TILE_FIRST_DELAY = 90 * MS;
    static final long TILE_STAGGER = 28 * MS;
    static final float TILE_RISE_UNITS = 1.3f;

    /** A page leaves - fading and dropping a little - then the next one fades in and rises. */
    static final long PAGE_LEAVE = 110 * MS;
    static final long PAGE_FADE_IN = 180 * MS;
    static final long PAGE_RISE = 220 * MS;
    static final float PAGE_LEAVE_DROP_UNITS = 0.4f;
    static final float PAGE_ENTER_RISE_UNITS = 0.8f;

    /** A switch's knob crosses in this long, fast and then easing out; its colour changes alongside. */
    static final long SWITCH = 300 * MS;

    /** A refused action's shake, and its keyframes: offsets across, in units, at each fifth of it. */
    static final long SHAKE = 320 * MS;
    private static final float[] SHAKE_KEYS = {0, -0.35f, 0.3f, -0.2f, 0.1f, 0};

    private Motion() {
    }

    /** How far through something lasting {@code duration} it is, {@code elapsed} in: 0 to 1. */
    static float progress(long elapsed, long duration) {
        if (duration <= 0 || elapsed >= duration) {
            return 1;
        }
        return elapsed <= 0 ? 0 : (float) elapsed / duration;
    }

    /** The mockup's ease-out, {@code cubic-bezier(0.16, 1, 0.3, 1)}: fast, then slowing to a stop. */
    static float easeOut(float t) {
        return bezier(t, 0.16f, 1f, 0.3f, 1f);
    }

    /** CSS's {@code ease-out}, {@code cubic-bezier(0, 0, 0.58, 1)}: what the mockup fades with. */
    static float fadeOut(float t) {
        return bezier(t, 0f, 0f, 0.58f, 1f);
    }

    /** CSS's {@code ease-in}, {@code cubic-bezier(0.42, 0, 1, 1)}: what the panel closes with. */
    static float easeIn(float t) {
        return bezier(t, 0.42f, 0f, 1f, 1f);
    }

    /**
     * A CSS cubic Bézier timing function at {@code t}: the curve from (0,0)
     * to (1,1) with control points (x1, y1) and (x2, y2), solved for the
     * point whose x is {@code t}.
     */
    static float bezier(float t, float x1, float y1, float x2, float y2) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        // Newton's method on x(s) = t, then bisection if it wanders.
        double s = t;
        for (int i = 0; i < 8; i++) {
            double x = cubic(s, x1, x2) - t;
            double slope = cubicSlope(s, x1, x2);
            if (Math.abs(x) < 1e-6) {
                return (float) cubic(s, y1, y2);
            }
            if (Math.abs(slope) < 1e-6) {
                break;
            }
            s -= x / slope;
        }
        double low = 0;
        double high = 1;
        s = t;
        for (int i = 0; i < 40; i++) {
            double x = cubic(s, x1, x2);
            if (Math.abs(x - t) < 1e-6) {
                break;
            }
            if (x < t) {
                low = s;
            } else {
                high = s;
            }
            s = (low + high) / 2;
        }
        return (float) cubic(s, y1, y2);
    }

    /** One coordinate of the Bézier at {@code s}, its ends 0 and 1. */
    private static double cubic(double s, double p1, double p2) {
        double inverse = 1 - s;
        return 3 * inverse * inverse * s * p1 + 3 * inverse * s * s * p2 + s * s * s;
    }

    private static double cubicSlope(double s, double p1, double p2) {
        double inverse = 1 - s;
        return 3 * inverse * inverse * p1 + 6 * inverse * s * (p2 - p1) + 3 * s * s * (1 - p2);
    }

    // ---- the panel opening and closing ----

    /** How visible the overlay and the panel are, {@code elapsed} into opening. */
    static float openOpacity(long elapsed) {
        return fadeOut(progress(elapsed, OPEN));
    }

    /** How far below its place the panel is, as a share of its height, {@code elapsed} into opening. */
    static float openRise(long elapsed) {
        return OPEN_RISE * (1 - easeOut(progress(elapsed, OPEN)));
    }

    /** How visible the overlay and the panel still are, {@code elapsed} into closing. */
    static float closeOpacity(long elapsed) {
        return 1 - easeIn(progress(elapsed, CLOSE));
    }

    /** How far the panel has sunk, as a share of its height, {@code elapsed} into closing. */
    static float closeSink(long elapsed) {
        return CLOSE_SINK * easeIn(progress(elapsed, CLOSE));
    }

    static boolean closed(long elapsed) {
        return elapsed >= CLOSE;
    }

    // ---- tiles ----

    /** When the tile at {@code index} starts to rise, after the tiles were shown: staggered, or all at once. */
    static long tileDelay(int index, boolean staggered) {
        return staggered ? TILE_FIRST_DELAY + index * TILE_STAGGER : 0;
    }

    /** How visible the tile at {@code index} is, {@code elapsed} after the tiles were shown. */
    static float tileOpacity(int index, long elapsed, boolean staggered) {
        return fadeOut(progress(elapsed - tileDelay(index, staggered), TILE));
    }

    /** How far below its place, in units, the tile at {@code index} is. */
    static float tileRise(int index, long elapsed, boolean staggered) {
        return TILE_RISE_UNITS * (1 - easeOut(progress(elapsed - tileDelay(index, staggered), TILE)));
    }

    /** Whether every one of {@code count} tiles has finished arriving. */
    static boolean tilesSettled(int count, long elapsed, boolean staggered) {
        return elapsed >= tileDelay(Math.max(0, count - 1), staggered) + TILE;
    }

    // ---- pages ----

    /** Whether the page that was showing is still leaving, {@code elapsed} after the change. */
    static boolean pageLeaving(long elapsed) {
        return elapsed < PAGE_LEAVE;
    }

    /** How visible the leaving page still is. */
    static float leavingOpacity(long elapsed) {
        return 1 - fadeOut(progress(elapsed, PAGE_LEAVE));
    }

    /** How far it has dropped, in units. */
    static float leavingDrop(long elapsed) {
        return PAGE_LEAVE_DROP_UNITS * fadeOut(progress(elapsed, PAGE_LEAVE));
    }

    /** How visible the entering page is, {@code elapsed} after the change: it starts once the old one has left. */
    static float enteringOpacity(long elapsed) {
        return fadeOut(progress(elapsed - PAGE_LEAVE, PAGE_FADE_IN));
    }

    /** How far below its place, in units, the entering page is. */
    static float enteringRise(long elapsed) {
        return PAGE_ENTER_RISE_UNITS * (1 - easeOut(progress(elapsed - PAGE_LEAVE, PAGE_RISE)));
    }

    /** Whether a page change, {@code elapsed} ago, has finished. */
    static boolean pageSettled(long elapsed) {
        return elapsed >= PAGE_LEAVE + Math.max(PAGE_FADE_IN, PAGE_RISE);
    }

    // ---- switches and shakes ----

    /** How far a switch's knob is towards on: 0 off, 1 on, {@code elapsed} after it was pressed. */
    static float switchPosition(boolean on, long elapsed) {
        float travelled = easeOut(progress(elapsed, SWITCH));
        return on ? travelled : 1 - travelled;
    }

    /** An ENABLED button's colour eases from red to green, or back, over this long. */
    static final long TOGGLE = 220 * MS;

    /** How far an ENABLED button's colour is towards green, {@code elapsed} after it was pressed. */
    static float toggle(boolean on, long elapsed) {
        float travelled = easeOut(progress(elapsed, TOGGLE));
        return on ? travelled : 1 - travelled;
    }

    /** How far across, in units, something refused is, {@code elapsed} into its shake: 0 before and after. */
    static float shake(long elapsed) {
        if (elapsed <= 0 || elapsed >= SHAKE) {
            return 0;
        }
        // CSS runs its ease-out over each stretch between keyframes.
        float position = (float) elapsed / SHAKE * (SHAKE_KEYS.length - 1);
        int from = (int) position;
        float within = fadeOut(position - from);
        return SHAKE_KEYS[from] + (SHAKE_KEYS[from + 1] - SHAKE_KEYS[from]) * within;
    }
}
