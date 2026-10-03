package com.ashlauncher.client.ui;

/**
 * Somewhere ash's own interface draws, with the game held on the far side of
 * it: rectangles and text, and nothing else. Both targets can draw exactly
 * that, so everything ash shows on a screen is made of it - rounded corners
 * included, as stepped rectangles.
 *
 * <p>Implemented once per version target and kept thin, like
 * {@link com.ashlauncher.client.hud.HudSurface}. All text goes through
 * {@link #drawText} and {@link #textWidth}: the game's own font today, and
 * whatever face the final design chooses later, changed here and nowhere
 * else.
 */
public interface ScreenSurface {

    /**
     * Fills a rectangle with one colour, in GUI units.
     *
     * @param colour packed ARGB, with the alpha set
     */
    void fill(int x, int y, int width, int height, int colour);

    /**
     * Draws one line of text with its top-left corner at ({@code x}, {@code y}),
     * without a shadow.
     *
     * @param colour packed ARGB, opaque
     */
    void drawText(String text, int x, int y, int colour);

    /** How wide {@code text} draws, in GUI units. */
    int textWidth(String text);

    /** The height of one line of text, in GUI units. */
    int lineHeight();
}
