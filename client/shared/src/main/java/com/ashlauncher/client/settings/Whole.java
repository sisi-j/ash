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

    Whole(Feature feature, String key, String label, int fallback, int min, int max, String comment) {
        super(feature, key, label, fallback, comment);
        this.min = min;
        this.max = max;
    }

    public int min() {
        return min;
    }

    public int max() {
        return max;
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
