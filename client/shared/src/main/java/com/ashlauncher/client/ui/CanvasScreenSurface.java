package com.ashlauncher.client.ui;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Ink;

/**
 * Today's options pages, drawn on the new canvas: {@link OptionsPage} lays out
 * in units of its own and draws through a {@link ScreenSurface}, and this
 * turns each unit into {@code scale} real pixels from an origin, with text in
 * Inter. So the pages keep working, and read in the final typeface, until
 * #67 redraws them in the final look.
 */
final class CanvasScreenSurface implements ScreenSurface {

    /** A line of text, in the page's units: the game's font's height, which the page's layout was built around. */
    static final int LINE = 9;

    private final Canvas canvas;
    private final float scale;
    private final int originX;
    private final int originY;

    CanvasScreenSurface(Canvas canvas, float scale, int originX, int originY) {
        this.canvas = canvas;
        this.scale = scale;
        this.originX = originX;
        this.originY = originY;
    }

    /** Inter's size for a {@link #LINE}-unit line: its line box is about 1.21 of its size. */
    private float textSize() {
        return LINE * scale / 1.21f;
    }

    @Override
    public void fill(int x, int y, int width, int height, int colour) {
        int left = toPixelsX(x);
        int top = toPixelsY(y);
        int right = toPixelsX(x + width);
        int bottom = toPixelsY(y + height);
        if (right > left && bottom > top) {
            canvas.fill(left, top, right - left, bottom - top, colour);
        }
    }

    @Override
    public void drawText(String text, int x, int y, int colour) {
        Ink.text(text, Ink.Weight.REGULAR, textSize(), colour).drawAt(canvas, toPixelsX(x), toPixelsY(y), 1f);
    }

    @Override
    public int textWidth(String text) {
        return (int) Math.ceil(Ink.width(text, Ink.Weight.REGULAR, textSize()) / scale);
    }

    @Override
    public int lineHeight() {
        return LINE;
    }

    int toPixelsX(int units) {
        return originX + Math.round(units * scale);
    }

    int toPixelsY(int units) {
        return originY + Math.round(units * scale);
    }

    /** A rectangle in the page's units, in real pixels: for anything that has to point at what the page drew. */
    Rect toPixels(Rect units) {
        int left = toPixelsX(units.x);
        int top = toPixelsY(units.y);
        return new Rect(left, top, toPixelsX(units.x + units.width) - left, toPixelsY(units.y + units.height) - top);
    }

    int toUnitsX(int pixels) {
        return (int) Math.floor((pixels - originX) / scale);
    }

    int toUnitsY(int pixels) {
        return (int) Math.floor((pixels - originY) / scale);
    }
}
