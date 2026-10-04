package com.ashlauncher.client.ui.draw;

/**
 * One piece of ash's interface, rasterised once by {@link Ink}: a run of
 * text, a rounded corner, an icon. Each target uploads it the first time it
 * is drawn and keeps the texture while it is in use, keyed by this object's
 * identity - so a raster is never changed after it is made.
 *
 * <p>Pixels are ARGB, not premultiplied, row by row. A raster can have an
 * origin inside it: text, for one, is rasterised with a margin so that no
 * glyph's overhang is cut off, and its origin is where the text itself
 * starts.
 */
public final class Raster {

    private final int width;
    private final int height;
    private final int[] argb;
    private final int originX;
    private final int originY;
    private final String text;

    Raster(int width, int height, int[] argb, int originX, int originY, String text) {
        this.width = width;
        this.height = height;
        this.argb = argb;
        this.originX = originX;
        this.originY = originY;
        this.text = text;
    }

    public int width() {
        return width;
    }

    public int height() {
        return height;
    }

    /** The pixels, shared rather than copied: a target reads them to upload, and never writes them. */
    public int[] argb() {
        return argb;
    }

    /** The words this raster shows, if it is text; {@code null} otherwise. For tests, and for reading a frame back. */
    public String text() {
        return text;
    }

    /** Draws it with its origin at ({@code x}, {@code y}). */
    public void drawAt(Canvas canvas, int x, int y, float opacity) {
        canvas.draw(this, x - originX, y - originY, opacity);
    }
}
