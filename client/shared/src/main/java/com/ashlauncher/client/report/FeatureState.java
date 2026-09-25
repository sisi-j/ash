package com.ashlauncher.client.report;

/** How a feature came out of this session's start, in the report's words. */
public enum FeatureState {
    LOADED("loaded"),
    /** Its mixins did not apply, and the game is running without it. */
    DEGRADED("degraded"),
    /** The player switched it off in their settings. Not a problem. */
    OFF("off");

    private final String word;

    FeatureState(String word) {
        this.word = word;
    }

    String word() {
        return word;
    }
}
