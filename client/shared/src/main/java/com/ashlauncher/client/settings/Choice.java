package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * One of a fixed set, such as the crosshair's shape. Written in the file by
 * its id, a lower-case word; shown on the settings screen by its label.
 */
public final class Choice extends Setting<String> {

    /** One thing it can be: its id in the file, and its label on screen. */
    public static final class Option {
        private final String id;
        private final String label;

        Option(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public String label() {
            return label;
        }
    }

    private final List<Option> options;

    /** @param options id, label, id, label... - the first is not the default unless {@code fallback} says so */
    Choice(Feature feature, String key, String label, String fallback, String comment, String... options) {
        super(feature, key, label, fallback, comment);
        List<Option> list = new ArrayList<>();
        for (int i = 0; i < options.length; i += 2) {
            list.add(new Option(options[i], options[i + 1]));
        }
        this.options = Collections.unmodifiableList(list);
    }

    /** What it can be, in the order the screen shows them. */
    public List<Option> options() {
        return options;
    }

    @Override
    String parse(String raw) {
        String wanted = raw.trim().toLowerCase(Locale.ROOT);
        for (Option option : options) {
            if (option.id.equals(wanted)) {
                return option.id;
            }
        }
        return null;
    }

    @Override
    String format(String value) {
        return value;
    }

    /** An id it does not offer is its default: whatever draws from it can rely on a known id. */
    @Override
    String normalise(String value) {
        String known = value == null ? null : parse(value);
        return known == null ? fallback() : known;
    }

    /** "cross, t, dot or box". */
    @Override
    String expected() {
        StringBuilder list = new StringBuilder();
        for (int i = 0; i < options.size(); i++) {
            if (i > 0) {
                list.append(i == options.size() - 1 ? " or " : ", ");
            }
            list.append(options.get(i).id);
        }
        return list.toString();
    }
}
