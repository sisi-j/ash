package com.ashlauncher.client.report;

/**
 * One of ash's features, as the load report names it.
 *
 * <p>The id is the report's key and the same word the settings file uses;
 * the name is what the launcher shows a player when one did not load.
 */
public enum Feature {
    FPS_READOUT("fps-readout", "FPS readout"),
    TOGGLE_SPRINT("toggle-sprint", "Toggle sprint"),
    CROSSHAIR("crosshair", "Crosshair"),
    /**
     * The key that opens ash's settings, and so the screen behind it. It has
     * no switch - nothing switches off the way to switch things - but it can
     * still fail to load: on 1.8.9 the key is read by a mixin of ash's own.
     *
     * <p>Named so that it reads mid-sentence, where the launcher's notice puts
     * it: "Toggle sprint and ash's settings screen did not load".
     */
    SETTINGS_SCREEN("settings-screen", "ash's settings screen");

    private final String id;
    private final String displayName;

    Feature(String id, String displayName) {
        this.id = id;
        this.displayName = displayName;
    }

    public String id() {
        return id;
    }

    public String displayName() {
        return displayName;
    }
}
