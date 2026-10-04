package com.ashlauncher.client.ui;

/**
 * The final design's colours, as packed ARGB (`docs/specs/0003`, *The final
 * design*). Text and highlights are white; surfaces are translucent black and
 * white over the blurred game; green, red and grey each mean one thing.
 */
final class Palette {

    /** Over the whole screen, under the panel: the blurred game, a touch darker. */
    static final int OVERLAY = 0x29000000;
    /** The panel: 55% black. */
    static final int PANEL = 0x8C000000;
    /** The strip down the panel's left: darker than the panel, about 79% black. */
    static final int STRIP = 0xC9000000;
    /** Things raised off the panel - tiles, buttons, an options row: a faint white. */
    static final int RAISED = 0x0FFFFFFF;
    /** The same, under the mouse. */
    static final int RAISED_HOVER = 0x1AFFFFFF;
    /** Hairlines between parts, such as the strip's divider. */
    static final int LINE = 0x17FFFFFF;
    /** Text, and every highlight. */
    static final int TEXT = 0xFFFFFFFF;
    /** Secondary text. */
    static final int MUTED = 0xA3FFFFFF;
    /** Icons: white at 80%. */
    static final int ICON = 0xCCFFFFFF;
    /** On, or faster. */
    static final int GREEN = 0xFF22C55E;
    /** Off, or slower. */
    static final int RED = 0xFFF0433A;
    /** No change; and, faded, a feature that did not load. */
    static final int GREY = 0xFFA7ADA6;
    static final int UNAVAILABLE = 0x59A7ADA6;
    /** The panel's soft shadow, at its darkest. */
    static final int SHADOW_ALPHA = 0x59;

    private Palette() {
    }
}
