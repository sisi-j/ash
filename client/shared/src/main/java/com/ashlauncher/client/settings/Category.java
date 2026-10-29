package com.ashlauncher.client.settings;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Where a switch's card is filed on the settings screen, in the order the
 * screen lists them (spec 0007, The settings screen). A category with no card
 * in it has no tab.
 */
public enum Category {
    HUD("HUD"),
    PVP("PvP"),
    VISUAL("Visual"),
    CHAT("Chat"),
    MOVEMENT("Movement"),
    PERFORMANCE("Performance"),
    COSMETICS("Cosmetics");

    /**
     * The alpha's tabs, in the alpha's order: what a release build shows
     * until the beta (ADR-0029). Visual's one card, hit colour, was PvP's.
     */
    static final List<Category> ALPHA = Collections.unmodifiableList(
            Arrays.asList(PVP, HUD, MOVEMENT, PERFORMANCE, COSMETICS));

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    /** Where a release build files a card the beta files here. */
    Category inAlpha() {
        return this == VISUAL ? PVP : this;
    }
}
