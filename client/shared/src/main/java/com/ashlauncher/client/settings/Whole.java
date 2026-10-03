package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;

/**
 * A whole number in a range, such as the crosshair's size. A number outside
 * the range in the file is not quietly brought into it: it is reported, and
 * the default used, as any value ash cannot use is.
 */
public final class Whole extends Setting<Integer> {

    private final int min;
    private final int max;
    private final int step;
    private final String unit;

    Whole(Feature feature, String key, String label, int fallback, int min, int max, String comment) {
        this(feature, key, label, fallback, min, max, 1, "", comment);
    }

    /**
     * @param step what the settings screen's slider moves in, from {@code min}; the file takes any value in range
     * @param unit shown after the number on the settings screen, such as "ms"; never written in the file
     */
    Whole(Feature feature, String key, String label, int fallback, int min, int max, int step, String unit,
            String comment) {
        super(feature, key, label, fallback, comment);
        this.min = min;
        this.max = max;
        this.step = step;
        this.unit = unit;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
    }

    /** What the settings screen's slider moves in: a slider with hundreds of values is one no one can set. */
    public int step() {
        return step;
    }

    /** What the number counts, such as "ms", or "" for a plain number. */
    public String unit() {
        return unit;
    }

    @Override
    Integer parse(String raw) {
        try {
            int value = Integer.parseInt(raw.trim());
            return value >= min && value <= max ? value : null;
        } catch (NumberFormatException notAWholeNumber) {
            return null;
        }
    }

    @Override
    String format(Integer value) {
        return value.toString();
    }

    @Override
    String expected() {
        return "a whole number from " + min + " to " + max;
    }

    /** Set from the screen, a value is kept within the range; the screen's slider cannot go outside it anyway. */
    @Override
    Integer normalise(Integer value) {
        return Math.max(min, Math.min(max, value));
    }
}
