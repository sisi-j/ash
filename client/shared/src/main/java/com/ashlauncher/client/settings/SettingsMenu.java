package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/**
 * What ash's settings screen shows and does, decided once for both targets.
 *
 * <p>Each version target draws these rows with its own vanilla buttons - one
 * button per row, labelled {@link Row#label()}, inactive unless
 * {@link Row#available()}, calling {@link Row#press()} - and draws
 * {@link #footer()} beneath them. It decides nothing else, so the two targets
 * cannot come to disagree about what the screen says.
 *
 * <p>The rows are {@link Settings#declared()}, walked: a setting declared
 * there has a row without a line of screen code.
 */
public final class SettingsMenu {

    /** The screen's title, on both targets. */
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
    public SettingsMenu(Settings settings, Predicate<Feature> landed, Runnable changed) {
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
        for (Row row : rows) {
            if (!row.available()) {
                return row.setting.feature().displayName() + " did not load. That is a problem with ash, not with"
                        + " your game or your setup, and an update to ash will fix it.";
            }
        }
        return "";
    }

    /** One setting's switch. */
    public final class Row {

        private final OnOff setting;

        private Row(OnOff setting) {
            this.setting = setting;
        }

        /** "FPS readout: On", "FPS readout: Off" or "FPS readout: Did not load". */
        public String label() {
            String state = !available() ? "Did not load" : settings.get(setting) ? "On" : "Off";
            return setting.feature().displayName() + ": " + state;
        }

        /** Whether pressing it does anything. False for a feature whose mixins did not land. */
        public boolean available() {
            return landed.test(setting.feature());
        }

        /**
         * Flips the setting, at once and in the file. Does nothing for a row
         * that is not {@link #available()}: the button for it is inactive,
         * and this does not rely on that.
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
