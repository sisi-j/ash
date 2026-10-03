package com.ashlauncher.client.ui;

/**
 * ash's grayscale, as packed ARGB: the brief's four values, plus the steps
 * between them that a panel needs to separate its parts. No hue anywhere -
 * contrast and weight carry the hierarchy.
 */
final class Palette {

    /** The brief's background. */
    static final int BACKGROUND = 0xFF0E0E0F;
    /** The panel itself, a step up from the background. */
    static final int PANEL = 0xFF141416;
    /** Things raised off the panel: cards, the search box, the chosen category. */
    static final int RAISED = 0xFF1C1C1F;
    /** Hairlines between parts. */
    static final int LINE = 0xFF2A2A2E;
    /** The brief's emphasis: an off switch's track, a card under the mouse. */
    static final int EMPHASIS = 0xFF3A3A3C;
    /** The brief's primary text, and an on switch. */
    static final int TEXT = 0xFFFAFAFA;
    /** The brief's secondary text. */
    static final int MUTED = 0xFF8B8C90;
    /** Over the game, light enough that the HUD stays readable behind the panel. */
    static final int DIM = 0x600E0E0F;

    private Palette() {
    }
}
