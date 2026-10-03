package com.ashlauncher.client.crosshair;

/** The crosshair shapes a player chooses from, by the ids the settings file uses. */
public enum Shape {
    /** Four arms around the centre. */
    CROSS,
    /** A cross without its top arm. */
    T,
    /** A filled square on the centre. */
    DOT,
    /** A square outline around the centre. */
    BOX;

    /** The shape a settings id names: "cross", "t", "dot" or "box". */
    static Shape of(String id) {
        return valueOf(id.toUpperCase(java.util.Locale.ROOT));
    }
}
