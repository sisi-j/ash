package com.ashlauncher.client.report;

/**
 * One of ash's features, as the load report names it.
 *
 * <p>The id is the report's key and the same word the settings file uses;
 * the name is what the launcher shows a player when one did not load, and
 * what the settings screen titles its card.
 */
public enum Feature {
    FPS_READOUT("fps-readout", "FPS readout"),
    TOGGLE_SPRINT("toggle-sprint", "Toggle sprint"),
    CROSSHAIR("crosshair", "Crosshair"),
    HIT_INDICATOR("hit-indicator", "Hit indicator"),
    FREELOOK("freelook", "Freelook"),
    SNAPLOOK("snaplook", "Snaplook"),
    PING_READOUT("ping-readout", "Ping readout"),
    HIT_COLOUR("hit-colour", "Hit colour"),
    /**
     * 1.8.9's fancy clouds, drawn with half the work and not a pixel changed
     * (#45). Only 1.8.9 has it: 1.21.11 draws its clouds another way, and
     * leaves it out of its report and its settings.
     */
    FASTER_CLOUDS("faster-clouds", "Faster clouds", Target.V1_8_9),
    /**
     * 1.8.9's scan of the blocks round the camera, which its chunk culling
     * makes every frame, reused until one of them could have changed (#105).
     * 1.8.9's alone, as faster clouds is.
     */
    FASTER_VIEW_SCAN("faster-view-scan", "Faster view scan", Target.V1_8_9),
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
    /** The one target that has it, or null for a feature both have. */
    private final Target only;

    Feature(String id, String displayName) {
        this(id, displayName, null);
    }

    Feature(String id, String displayName, Target only) {
        this.id = id;
        this.displayName = displayName;
        this.only = only;
    }

    public String id() {
        return id;
    }

    /**
     * Whether this target has the feature at all. One it does not has no tile
     * in its settings and no line in its load report, rather than one that
     * says it did not load.
     */
    public boolean existsOn(Target target) {
        return only == null || only == target;
    }

    public String displayName() {
        return displayName;
    }
}
