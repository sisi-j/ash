package com.ashlauncher.client.report;

/**
 * One of ash's features, as the load report names it.
 *
 * <p>The id is the report's key and the same word the settings file uses;
 * the name is what the launcher shows a player when one did not load.
 */
public enum Feature {
    FPS_READOUT("fps-readout", "FPS readout"),
    TOGGLE_SPRINT("toggle-sprint", "Toggle sprint");

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
