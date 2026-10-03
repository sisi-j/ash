package com.ashlauncher.client.report;

/**
 * One of ash's features: what the load report calls it, and how the settings
 * screen shows it.
 *
 * <p>The id is the report's key and the same word the settings file uses;
 * the name is what the launcher shows a player when one did not load, and
 * what its card is titled. The category and the description are the card's.
 */
public enum Feature {
    FPS_READOUT("fps-readout", "FPS readout", Category.HUD, "Your frame rate on screen."),
    TOGGLE_SPRINT("toggle-sprint", "Toggle sprint", Category.MOVEMENT,
            "Sprint on a key press instead of a held key."),
    CROSSHAIR("crosshair", "Crosshair", Category.PVP, "Your own crosshair in place of the game's."),
    /**
     * The key that opens ash's settings, and so the screen behind it. It has
     * no switch - nothing switches off the way to switch things - but it can
     * still fail to load: on 1.8.9 the key is read by a mixin of ash's own.
     *
     * <p>Named so that it reads mid-sentence, where the launcher's notice puts
     * it: "Toggle sprint and ash's settings screen did not load".
     */
    SETTINGS_SCREEN("settings-screen", "ash's settings screen", null, "The key that opens these settings.");

    /**
     * Where a feature's card is filed on the settings screen, in the order the
     * screen lists them.
     */
    public enum Category {
        PVP("PvP"),
        HUD("HUD"),
        MOVEMENT("Movement");

        private final String displayName;

        Category(String displayName) {
            this.displayName = displayName;
        }

        public String displayName() {
            return displayName;
        }
    }

    private final String id;
    private final String displayName;
    private final Category category;
    private final String description;

    Feature(String id, String displayName, Category category, String description) {
        this.id = id;
        this.displayName = displayName;
        this.category = category;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }

    /** Where its card is filed; {@code null} for a feature with no card, which has no switch. */
    public Category category() {
        return category;
    }

    /** One sentence, for a player, on what it does. */
    public String description() {
        return description;
    }
}
