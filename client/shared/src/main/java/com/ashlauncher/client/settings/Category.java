package com.ashlauncher.client.settings;

/**
 * Where a switch's card is filed on the settings screen, in the order the
 * screen lists them.
 */
public enum Category {
    PVP("PvP"),
    HUD("HUD"),
    MOVEMENT("Movement"),
    PERFORMANCE("Performance");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }
}
