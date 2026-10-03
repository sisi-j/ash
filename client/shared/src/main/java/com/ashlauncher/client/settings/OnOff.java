package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.Locale;

/**
 * An on/off setting. Either a feature's switch - it then has a card on the
 * settings screen, filed under a category with one sentence on what the
 * feature does - or one of a feature's options, such as the crosshair's
 * outline.
 *
 * <p>The card's sentence is for a player looking at the screen; the comment is
 * for one reading the file, and says how to write the value.
 */
public final class OnOff extends Setting<Boolean> {

    private final Category category;
    private final String description;

    /** A feature's switch: labelled with the feature's own name, with a card of its own. */
    OnOff(Feature feature, Category category, String description, String key, boolean fallback, String comment) {
        super(feature, key, feature.displayName(), fallback, comment);
        this.category = category;
        this.description = description;
    }

    /** One of a feature's options. */
    OnOff(Feature feature, String label, String key, boolean fallback, String comment) {
        super(feature, key, label, fallback, comment);
        this.category = null;
        this.description = null;
    }

    /** Whether this is the switch for its whole feature, rather than one of its options. */
    public boolean isSwitch() {
        return category != null;
    }

    /** Where its card is filed on the settings screen; {@code null} for an option. */
    public Category category() {
        return category;
    }

    /** One sentence on its card, for a player, on what the feature does; {@code null} for an option. */
    public String description() {
        return description;
    }

    /**
     * {@code true} or {@code false}, in any case, and nothing else.
     *
     * <p>Strict because the lenient reading is a trap: {@code Boolean.parseBoolean}
     * calls anything that is not "true" false, so a player who writes "yes"
     * would switch the feature off without a word.
     */
    @Override
    Boolean parse(String raw) {
        String trimmed = raw.trim().toLowerCase(Locale.ROOT);
        if (trimmed.equals("true")) {
            return true;
        }
        if (trimmed.equals("false")) {
            return false;
        }
        return null;
    }

    @Override
    String format(Boolean value) {
        return value.toString();
    }

    @Override
    String expected() {
        return "true or false";
    }
}
