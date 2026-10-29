package com.ashlauncher.client.report;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

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
     * ash players' capes (spec 0004, step 4), drawn by the game's own cape in
     * place of its texture. Seen only by other ash players.
     */
    COSMETICS("cosmetics", "Cosmetics"),
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
    /** The targets that have it; empty for a feature every target has. */
    private final List<Target> targets;

    /**
     * @param targets the version targets it runs on, declared with the
     *     feature (spec 0007); none for one every target has
     */
    Feature(String id, String displayName, Target... targets) {
        this.id = id;
        this.displayName = displayName;
        this.targets = Collections.unmodifiableList(Arrays.asList(targets));
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
        return targets.isEmpty() || targets.contains(target);
    }

    /** The targets that have it, in the order declared; empty for a feature every target has. */
    public List<Target> targets() {
        return targets;
    }

    public String displayName() {
        return displayName;
    }
}
