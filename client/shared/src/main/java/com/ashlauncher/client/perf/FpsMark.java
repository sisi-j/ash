package com.ashlauncher.client.perf;

/**
 * A tile's FPS mark (#70): whether a feature raises the frame rate, lowers it,
 * or leaves it within 3% either way. It is measured, never guessed: see
 * {@link FpsMeasurements}.
 */
public enum FpsMark {
    /** A green triangle pointing up. */
    RAISES,
    /** A grey bar. */
    LEVEL,
    /** A red triangle pointing down. */
    LOWERS;

    /** How far either way a change can go and still be shown as no change, in per cent. */
    public static final double LEVEL_WITHIN_PERCENT = 3.0;

    /** The mark for a feature that changes the frame rate by this much, in per cent, when on. */
    public static FpsMark of(double changePercent) {
        if (changePercent > LEVEL_WITHIN_PERCENT) {
            return RAISES;
        }
        if (changePercent < -LEVEL_WITHIN_PERCENT) {
            return LOWERS;
        }
        return LEVEL;
    }

    /** Which way it points, for drawing: up above zero, down below. */
    public int direction() {
        return this == RAISES ? 1 : this == LOWERS ? -1 : 0;
    }
}
