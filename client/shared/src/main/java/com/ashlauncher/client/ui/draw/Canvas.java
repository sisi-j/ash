package com.ashlauncher.client.ui.draw;

/**
 * Where ash's panel draws, in the screen's real pixels rather than the game's
 * scaled GUI units: that is what keeps its text and corners crisp at every GUI
 * scale (`docs/research/0007`).
 *
 * <p>Deliberately small. Everything with a decision in it - layout, what to
 * rasterise, what is cached - is the shared module's; a target only fills,
 * draws rasters it was handed, and clips. Each target implements it once,
 * thinly, over its own drawing calls, with the real-pixel scale already set.
 * Rasters are drawn 1:1, and the target caches each one's texture by the
 * raster's identity.
 */
public interface Canvas {

    /** The screen's width in real pixels. */
    int width();

    /** The screen's height in real pixels. */
    int height();

    /**
     * Fills a rectangle with one colour, blended over what is there.
     *
     * @param argb packed ARGB, with the alpha set
     */
    void fill(int x, int y, int width, int height, int argb);

    /** Draws a raster 1:1 with its top-left corner at ({@code x}, {@code y}), its alpha scaled by {@code opacity}. */
    void draw(Raster raster, int x, int y, float opacity);

    /**
     * Draws a raster stretched to {@code width} by {@code height}, sampling the
     * nearest pixel. Only for rasters that are uniform along the stretch, such
     * as a shadow's straight edge, so that nothing blurs or blocks.
     */
    void drawStretched(Raster raster, int x, int y, int width, int height, float opacity);

    /** Draws nothing outside this rectangle until {@link #unclip}. Not nested. */
    void clip(int x, int y, int width, int height);

    void unclip();
}
