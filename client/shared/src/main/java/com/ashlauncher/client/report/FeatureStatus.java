package com.ashlauncher.client.report;

/** How a feature came out of this session's start, in the report's words. */
public enum FeatureStatus {
    LOADED("loaded"),
    /** Its mixins did not apply, and the game is running without it. */
    DEGRADED("degraded"),
    /** The player switched it off in their settings. Not a problem. */
    OFF("off");

    private final String word;

    FeatureStatus(String word) {
        this.word = word;
    }

    /**
     * A feature's status from whether the player has it on and whether its
     * mixins landed. Off wins: a feature the player did not want is never a
     * problem to tell them about, whatever became of its mixins.
     */
    public static FeatureStatus of(boolean on, boolean landed) {
        if (!on) {
            return OFF;
        }
        return landed ? LOADED : DEGRADED;
    }

    String word() {
        return word;
    }
}
