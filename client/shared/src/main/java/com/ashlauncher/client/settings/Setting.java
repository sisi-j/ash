package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;

/**
 * One setting, as it is declared: the feature it belongs to, its key in the
 * file, what the settings screen labels it, its value when the file does not
 * say, and the comment a first run writes above it.
 *
 * <p>Each kind - on/off, a choice, a whole number, a colour - says how its
 * value is written in the file, how it is read back, and what a value must
 * look like. Everything else - reading the file, appending to it, changing
 * a value in place - is {@link Settings}', the same for every kind.
 *
 * <p>Declared only in {@link Settings}, which is why the constructors are not
 * public: a setting that is not in {@link Settings#declared()} would have no
 * value.
 */
public abstract class Setting<T> {

    private final Feature feature;
    private final String key;
    private final String label;
    private final T fallback;
    private final String comment;

    Setting(Feature feature, String key, String label, T fallback, String comment) {
        this.feature = feature;
        this.key = key;
        this.label = label;
        this.fallback = fallback;
        this.comment = comment;
    }

    /** The feature it belongs to: its switch, or one of its options. */
    public Feature feature() {
        return feature;
    }

    /** Its key in the settings file, such as {@code crosshair.size}. */
    public String key() {
        return key;
    }

    /** What the settings screen calls it. */
    public String label() {
        return label;
    }

    /** Its value when the file does not have one, or has one ash cannot read. */
    public T fallback() {
        return fallback;
    }

    /** The comment a first run writes above it: what it does, for whoever opens the file. */
    String comment() {
        return comment;
    }

    /** The value {@code raw} stands for, or {@code null} when it is not a value of this setting. */
    abstract T parse(String raw);

    /** How a value is written in the file. */
    abstract String format(T value);

    /** What a value must look like, for the sentence that says one did not: "true or false". */
    abstract String expected();

    /** A value as it is kept: the same, unless the kind has limits a value must be brought within. */
    T normalise(T value) {
        return value;
    }

    @Override
    public String toString() {
        return key;
    }
}
