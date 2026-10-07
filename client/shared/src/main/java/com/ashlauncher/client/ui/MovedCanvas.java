package com.ashlauncher.client.ui;

import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Paint;
import com.ashlauncher.client.ui.draw.Raster;

/**
 * A canvas whose drawing lands moved and faded: everything drawn through it
 * is shifted by ({@code dx}, {@code dy}) and drawn at {@code opacity} of
 * what it asked for. How the panel animates (#66) - the same cached pieces,
 * placed elsewhere and fainter, never drawn afresh for a frame of motion
 * ({@code docs/research/0007}).
 */
final class MovedCanvas implements Canvas {

    private final Canvas canvas;
    private final int dx;
    private final int dy;
    private final float opacity;

    private MovedCanvas(Canvas canvas, int dx, int dy, float opacity) {
        this.canvas = canvas;
        this.dx = dx;
        this.dy = dy;
        this.opacity = opacity;
    }

    /** {@code canvas} moved and faded; itself when there is nothing to change. */
    static Canvas of(Canvas canvas, int dx, int dy, float opacity) {
        if (dx == 0 && dy == 0 && opacity >= 1f) {
            return canvas;
        }
        if (canvas instanceof MovedCanvas) {
            MovedCanvas inner = (MovedCanvas) canvas;
            return new MovedCanvas(inner.canvas, inner.dx + dx, inner.dy + dy, inner.opacity * opacity);
        }
        return new MovedCanvas(canvas, dx, dy, opacity);
    }

    @Override
    public int width() {
        return canvas.width();
    }

    @Override
    public int height() {
        return canvas.height();
    }

    @Override
    public void fill(int x, int y, int width, int height, int argb) {
        if (opacity > 0f) {
            canvas.fill(x + dx, y + dy, width, height, Paint.fade(argb, opacity));
        }
    }

    @Override
    public void draw(Raster raster, int x, int y, float opacity) {
        if (this.opacity > 0f) {
            canvas.draw(raster, x + dx, y + dy, opacity * this.opacity);
        }
    }

    @Override
    public void drawStretched(Raster raster, int x, int y, int width, int height, float opacity) {
        if (this.opacity > 0f) {
            canvas.drawStretched(raster, x + dx, y + dy, width, height, opacity * this.opacity);
        }
    }

    @Override
    public void clip(int x, int y, int width, int height) {
        canvas.clip(x + dx, y + dy, width, height);
    }

    @Override
    public void unclip() {
        canvas.unclip();
    }
}
