package com.ashlauncher.client.settings;

import com.ashlauncher.client.hud.HudItem;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.Target;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;
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
 * <p>The rows are {@link Settings#switches()}, walked: a feature's switch
 * declared there has a row without a line of screen code, and its options
 * - {@link Settings#optionsOf} - a page.
 */
public final class SettingsScreen {

    /** What the screen is called where the game asks - 1.21.11's narrator reads a screen's title. */
    public static final String TITLE = "ash settings";

    /** The key binding's name in Controls, on both targets. As with toggle sprint's, shown as written. */
    public static final String BINDING_NAME = "Open ash settings";

    private final Settings settings;
    private final Predicate<Feature> landed;
    private final Function<Feature, String> offHere;
    private final Runnable changed;
    private final HudLayout hudLayout;
    /** The game this is, when known: a feature it can't have says so on its tile. */
    private final Target target;
    /** Whether beta phase 1's panel is switched on: option search, favourites, the seven tabs (ADR-0029). */
    private final boolean beta;
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
        this(settings, feature -> true, landed, feature -> "", changed);
    }

    /**
     * @param present whether this target has the feature at all; one it does
     *     not have yet has no row, rather than a row saying it did not load
     * @param offHere why a feature is off where the player is now - a server
     *     whose rules ban it - or empty; its row then cannot be switched
     */
    public SettingsScreen(Settings settings, Predicate<Feature> present, Predicate<Feature> landed,
            Function<Feature, String> offHere, Runnable changed) {
        this(settings, present, landed, offHere, changed, new HudLayout(settings));
    }

    /**
     * @param hudLayout where the readouts are, shared with the readouts
     *     themselves, so that Edit HUD moves what the game draws
     */
    public SettingsScreen(Settings settings, Predicate<Feature> present, Predicate<Feature> landed,
            Function<Feature, String> offHere, Runnable changed, HudLayout hudLayout) {
        this(settings, present, null, false, landed, offHere, changed, hudLayout);
    }

    /**
     * The screen as a version target shows it.
     *
     * @param target the game this is: the features it has are its rows, and
     *     with the beta switch on, a feature it can't have is a row saying so
     * @param beta whether this game was started by a test build (ADR-0029)
     */
    public SettingsScreen(Settings settings, Target target, boolean beta, Predicate<Feature> landed,
            Function<Feature, String> offHere, Runnable changed, HudLayout hudLayout) {
        this(settings, feature -> beta || feature.existsOn(target), target, beta, landed, offHere, changed,
                hudLayout);
    }

    private SettingsScreen(Settings settings, Predicate<Feature> present, Target target, boolean beta,
            Predicate<Feature> landed, Function<Feature, String> offHere, Runnable changed, HudLayout hudLayout) {
        this.settings = settings;
        this.landed = landed;
        this.offHere = offHere;
        this.changed = changed;
        this.hudLayout = hudLayout;
        this.target = target;
        this.beta = beta;
        List<Row> rows = new ArrayList<>();
        for (OnOff setting : Settings.switches()) {
            if (present.test(setting.feature())) {
                rows.add(new Row(setting));
            }
        }
        this.rows = Collections.unmodifiableList(rows);
    }

    public List<Row> rows() {
        return rows;
    }

    /** Whether beta phase 1's panel is switched on (ADR-0029): option search, favourites, the seven tabs. */
    public boolean beta() {
        return beta;
    }

    /** The tabs after All: each category with a tile in it, in the screen's order. */
    public List<Category> tabs() {
        List<Category> tabs = new ArrayList<>();
        for (Category category : beta ? Arrays.asList(Category.values()) : Category.ALPHA) {
            for (Row row : rows) {
                if (row.category() == category) {
                    tabs.add(category);
                    break;
                }
            }
        }
        return tabs;
    }

    /**
     * The tiles a search shows, in a tab or in All ({@code null}).
     *
     * <p>A tile is found by its feature's name. With the beta switch on, it
     * is also found by one of its options - by the option's own name, or by
     * its feature's and its own together, "crosshair colour" - and the first
     * option found is named under it. On All with nothing typed, starred
     * tiles come first, in the order they were starred.
     */
    public List<Found> find(String query, Category tab) {
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        List<Found> found = new ArrayList<>();
        boolean favouritesFirst = beta && tab == null && wanted.isEmpty();
        if (favouritesFirst) {
            for (Feature feature : settings.get(Settings.FAVOURITES)) {
                Row row = rowOf(feature);
                if (row != null) {
                    found.add(new Found(row, null, true));
                }
            }
        }
        for (Row row : rows) {
            if ((tab != null && row.category() != tab) || (favouritesFirst && starred(row.feature()))) {
                continue;
            }
            String name = row.name().toLowerCase(Locale.ROOT);
            if (wanted.isEmpty() || name.contains(wanted)) {
                found.add(new Found(row, null, false));
            } else if (beta) {
                for (Setting<?> option : optionsOf(row.feature())) {
                    String label = option.label().toLowerCase(Locale.ROOT);
                    if (label.contains(wanted) || (name + " " + label).contains(wanted)) {
                        found.add(new Found(row, option, false));
                        break;
                    }
                }
            }
        }
        return found;
    }

    private Row rowOf(Feature feature) {
        for (Row row : rows) {
            if (row.feature() == feature) {
                return row;
            }
        }
        return null;
    }

    /** Whether a feature's tile is starred. */
    public boolean starred(Feature feature) {
        return settings.get(Settings.FAVOURITES).contains(feature);
    }

    /**
     * Stars a feature's tile, after those starred already, or unstars it.
     * Saved at once, like any other change, so it syncs.
     */
    public void star(Feature feature) {
        List<Feature> favourites = new ArrayList<>(settings.get(Settings.FAVOURITES));
        if (!favourites.remove(feature)) {
            favourites.add(feature);
        }
        change(Settings.FAVOURITES, favourites);
    }

    /** The settings behind the screen, for what is drawn from them - the crosshair's preview. */
    public Settings settings() {
        return settings;
    }

    /** Where the readouts are, for Edit HUD. */
    public HudLayout hudLayout() {
        return hudLayout;
    }

    /**
     * A feature's options, in the order its page shows them. A HUD item's
     * style is among them only with the beta switch on (ADR-0029).
     */
    public List<Setting<?>> optionsOf(Feature feature) {
        List<Setting<?>> options = new ArrayList<>();
        for (Setting<?> option : Settings.optionsOf(feature)) {
            if (beta || !Settings.isHudItemSetting(option)) {
                options.add(option);
            }
        }
        return options;
    }

    public <T> T value(Setting<T> setting) {
        return settings.get(setting);
    }

    /**
     * Changes one of a feature's options, at once and in the file, as a
     * switch is changed: a problem saving it is the notice's to say.
     */
    public <T> void change(Setting<T> setting, T value) {
        saveProblem = settings.set(setting, value).message();
        changed.run();
    }

    /**
     * Puts a feature back to its defaults - every option, and its switch -
     * as a player who has lost track of what they changed would expect.
     */
    public void resetToDefaults(Feature feature) {
        for (OnOff setting : Settings.switches()) {
            if (setting.feature() == feature) {
                resetOne(setting);
            }
        }
        for (Setting<?> option : optionsOf(feature)) {
            resetOne(option);
        }
    }

    /** Puts every readout back where it started, as Edit HUD's Reset asks. */
    public void resetReadouts() {
        for (HudItem item : HudItem.ALL) {
            resetOne(item.position());
        }
    }

    private <T> void resetOne(Setting<T> option) {
        change(option, option.fallback());
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
            if (row.notHere().isEmpty() && !row.loaded()) {
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
            return beta ? setting.category() : setting.category().inAlpha();
        }

        public String description() {
            return setting.description();
        }

        /** Whether the player has it on this session. */
        public boolean on() {
            return settings.get(setting);
        }

        /** Whether the feature has a page of options beyond its switch. */
        public boolean hasOptions() {
            return !optionsOf(setting.feature()).isEmpty();
        }

        /** Whether the feature's mixins landed this session. */
        public boolean loaded() {
            return landed.test(setting.feature());
        }

        /** Why it is off where the player is now, or empty. */
        public String offHere() {
            return offHere.apply(setting.feature());
        }

        /**
         * "Not on 1.21.11", for a feature this version of the game can't have,
         * which has a tile only with the beta switch on; otherwise empty.
         */
        public String notHere() {
            boolean here = target == null || setting.feature().existsOn(target);
            return here ? "" : "Not on " + target.version();
        }

        /**
         * Whether pressing it does anything. False for a feature this version
         * can't have, for one whose mixins did not land, and for one that is
         * off on this server.
         */
        public boolean available() {
            return notHere().isEmpty() && loaded() && offHere().isEmpty();
        }

        /**
         * Why this feature cannot be switched, in one line: it did not load, in
         * the same terms as {@link #footer()}, or it is off on this server and
         * why. What the screen says when it is pressed. Empty for one that can be.
         */
        public String whyUnavailable() {
            if (!notHere().isEmpty()) {
                List<String> versions = new ArrayList<>();
                for (Target other : setting.feature().targets()) {
                    versions.add(other.version());
                }
                return name() + " is only on " + listed(versions) + ".";
            }
            if (!loaded()) {
                return name() + " did not load. An update to ash will fix it.";
            }
            return offHere();
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

    /** One tile a search shows: its row, and the option it was found by, if it was found by one. */
    public static final class Found {

        private final Row row;
        private final Setting<?> option;
        private final boolean favourite;

        private Found(Row row, Setting<?> option, boolean favourite) {
            this.row = row;
            this.option = option;
            this.favourite = favourite;
        }

        public Row row() {
            return row;
        }

        /** The option it was found by, named under its tile; {@code null} when found by its name, or not searched. */
        public Setting<?> option() {
            return option;
        }

        /** Whether it is in the favourites row: starred, on All, with nothing typed. */
        public boolean favourite() {
            return favourite;
        }
    }
}
