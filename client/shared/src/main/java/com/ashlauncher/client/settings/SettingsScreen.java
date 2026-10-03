package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * What ash's settings screen shows and does, decided once for both targets.
 *
 * <p>The model behind the panel ash draws ({@code ui.Panel}): which switches
 * there are, which can be pressed, what pressing does, and what the notice
 * says. The panel decides how they look. The version targets only map the
 * game's input to ash's own names and pass it on - so the two cannot come to
 * disagree about what the screen says.
 *
 * <p>The rows are {@link Settings#declared()}, walked: a setting declared
 * there has a row without a line of screen code.
 */
public final class SettingsScreen {

    /** What the screen is called where the game asks - 1.21.11's narrator reads a screen's title. */
    public static final String TITLE = "ash settings";

    /** The key binding's name in Controls, on both targets. As with toggle sprint's, shown as written. */
    public static final String BINDING_NAME = "Open ash settings";

    private final Settings settings;
    private final Predicate<Feature> landed;
    private final Runnable changed;
    private final List<Row> rows;
    private String saveProblem = "";

    /**
     * @param landed whether a feature's mixins landed this session; a switch
     *     for one that did not would do nothing, so its row is not a switch
     * @param changed run after each change, with the new value already in
     *     {@code settings} - to rewrite the load report, so that it says what
     *     the session ended with
     */
    public SettingsScreen(Settings settings, Predicate<Feature> landed, Runnable changed) {
        this.settings = settings;
        this.landed = landed;
        this.changed = changed;
        List<Row> rows = new ArrayList<>();
        for (OnOff setting : Settings.declared()) {
            rows.add(new Row(setting));
        }
        this.rows = Collections.unmodifiableList(rows);
    }

    public List<Row> rows() {
        return rows;
    }

    /**
     * One line beneath the rows, or empty: why the last change was not saved,
     * or else why a feature is unavailable - in the words the launcher's
     * notice uses, so the player hears the same thing in both places.
     */
    public String footer() {
        if (!saveProblem.isEmpty()) {
            return saveProblem;
        }
        List<String> unavailable = new ArrayList<>();
        for (Row row : rows) {
            if (!row.available()) {
                unavailable.add(row.name());
            }
        }
        if (unavailable.isEmpty()) {
            return "";
        }
        return listed(unavailable) + " did not load. That is a problem with ash, not with your game or your setup,"
                + " and an update to ash will fix it.";
    }

    /** "A", "A and B", "A, B and C" - as the launcher's notice names them. */
    private static String listed(List<String> names) {
        if (names.size() == 1) {
            return names.get(0);
        }
        StringBuilder sentence = new StringBuilder();
        for (int i = 0; i < names.size() - 1; i++) {
            if (i > 0) {
                sentence.append(", ");
            }
            sentence.append(names.get(i));
        }
        return sentence.append(" and ").append(names.get(names.size() - 1)).toString();
    }

    /** One setting's switch. */
    public final class Row {

        private final OnOff setting;

        private Row(OnOff setting) {
            this.setting = setting;
        }

        /** The feature this row switches. */
        public Feature feature() {
            return setting.feature();
        }

        /** What its card is titled: the feature's own name. */
        public String name() {
            return setting.feature().displayName();
        }

        public Category category() {
            return setting.category();
        }

        public String description() {
            return setting.description();
        }

        /** Whether the player has it on this session. */
        public boolean on() {
            return settings.get(setting);
        }

        /** Whether pressing it does anything. False for a feature whose mixins did not land. */
        public boolean available() {
            return landed.test(setting.feature());
        }

        /**
         * Flips the setting, at once and in the file. Does nothing for a row
         * that is not {@link #available()}: its switch is drawn as one that
         * cannot be pressed, and this does not rely on that.
         */
        public void press() {
            if (!available()) {
                return;
            }
            Saved saved = settings.set(setting, !settings.get(setting));
            saveProblem = saved.message();
            changed.run();
        }
    }
}
