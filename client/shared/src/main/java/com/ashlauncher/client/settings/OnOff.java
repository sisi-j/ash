package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;

/**
 * One on/off setting, as it is declared: the feature it switches, its key in
 * the file, its value when the file does not say, and the comment a first run
 * writes above it.
 *
 * <p>The feature is what the settings screen calls it, and how the screen knows
 * a switch would do nothing: a feature that did not load is shown as such.
 *
 * <p>Declared only in {@link Settings}, which is why the constructor is not
 * public - a setting that is not in {@link Settings#declared()} would have no
 * value, and nothing to show it on the settings screen.
 *
 * <p>The only kind of setting so far. A choice, a number, a colour and a
 * position arrive with the first feature that needs each, because a kind
 * nothing uses is a guess about the feature that will.
 */
public final class OnOff {

    private final Feature feature;
    private final String key;
    private final boolean fallback;
    private final String comment;

    OnOff(Feature feature, String key, boolean fallback, String comment) {
        this.feature = feature;
        this.key = key;
        this.fallback = fallback;
        this.comment = comment;
    }

    /** The feature this switches on and off. */
    public Feature feature() {
        return feature;
    }

    /** Its key in the settings file, such as {@code fps-readout.enabled}. */
    public String key() {
        return key;
    }

    /** Its value when the file does not have one, or has one ash cannot read. */
    public boolean fallback() {
        return fallback;
    }

    /** The comment a first run writes above it: what it does, for whoever opens the file. */
    String comment() {
        return comment;
    }

    @Override
    public String toString() {
        return key;
    }
}
