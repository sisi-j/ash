package com.ashlauncher.client.ui;

/**
 * The final design's colours, as packed ARGB (`docs/specs/0003`, *The final
 * design*). Text and highlights are white; surfaces are translucent black and
 * white over the blurred game; green and red each mean one thing.
 */
final class Palette {

    /** Over the whole screen, under the panel: the blurred game, a touch darker. */
    static final int OVERLAY = 0x29000000;
    /** The same where the game cannot blur, darker to make up for it. */
    static final int OVERLAY_UNBLURRED = 0x52000000;
    /** The panel: 55% black. */
    static final int PANEL = 0x8C000000;
    /** The faint edge round the panel, as the mockup's one-pixel ring. */
    static final int PANEL_EDGE = 0x0FFFFFFF;
    /** The strip down the panel's left: darker than the panel, about 79% black. */
    static final int STRIP = 0xC9000000;
    /** Things raised off the panel - tiles, buttons, an options row: a faint white. */
    static final int RAISED = 0x0FFFFFFF;
    /** The same, under the mouse. */
    static final int RAISED_HOVER = 0x1AFFFFFF;
    /** The same again, for something raised on something raised - a button on an options page, under the mouse. */
    static final int RAISED_STRONG = 0x29FFFFFF;
    /** Hairlines between parts, such as the strip's divider. */
    static final int LINE = 0x17FFFFFF;
    /** Text, and every highlight. */
    static final int TEXT = 0xFFFFFFFF;
    /** Secondary text. */
    static final int MUTED = 0xA3FFFFFF;
    /** A placeholder, such as the search box's when it is empty: white at 40%. */
    static final int PLACEHOLDER = 0x66FFFFFF;
    /** The ring round a field being typed in: white at 70%. */
    static final int FOCUS = 0xB3FFFFFF;
    /** Icons: white at 80%. */
    static final int ICON = 0xCCFFFFFF;
    /** On, or faster. */
    static final int GREEN = 0xFF22C55E;
    /** Off, or slower. */
    static final int RED = 0xFFF0433A;
    /** A feature that did not load: the design's grey, faded. */
    static final int UNAVAILABLE = 0x59A7ADA6;
    /** A message over the panel. */
    static final int TOAST = 0xEB141416;
    /** Over the game in Edit HUD: light enough that the readouts read as they will. */
    static final int HUD_EDIT_SCRIM = 0x610E0E0F;
    /** A readout's box in Edit HUD: a faint white. */
    static final int HUD_BOX = 0x0FFAFAFA;
    /** The same, under the mouse or being dragged. */
    static final int HUD_BOX_HOVER = 0x29FAFAFA;
    /** Behind Edit HUD's hint and each box's tag. */
    static final int SCRIM = 0xC70E0E0F;
    /** A primary button - Edit HUD's Done - is white with dark text. */
    static final int PRIMARY_TEXT = 0xFF0E0E0F;
    /** The panel's soft shadow, and a tile's, at their darkest. */
    static final int PANEL_SHADOW_ALPHA = 0x59;
    static final int TILE_SHADOW_ALPHA = 0x1F;

    private Palette() {
    }
}
