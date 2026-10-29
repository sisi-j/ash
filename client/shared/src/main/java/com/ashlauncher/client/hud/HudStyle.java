package com.ashlauncher.client.hud;

/**
 * The style options a HUD item can take, the same for every item that takes
 * them (spec 0007, HUD items). An item declares which: one that draws no text,
 * for one, takes no label.
 */
public enum HudStyle {
    /** 50% to 200%, in steps of 10. */
    SIZE,
    /** Its text's colour, or chroma. */
    TEXT_COLOUR,
    /** A box behind it, in a colour of its own. */
    BACKGROUND,
    /** Its text's shadow, on or off. */
    SHADOW,
    /** Brackets round it: {@code [120 FPS]}. */
    BRACKETS,
    /** Its label, changed or hidden. */
    LABEL
}
