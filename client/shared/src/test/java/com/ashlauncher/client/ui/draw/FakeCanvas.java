package com.ashlauncher.client.ui.draw;

import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;

/**
 * A canvas with no game behind it. It records every fill and every raster
 * drawn, and composites them onto real pixels over a flat stand-in for the
 * blurred game - so a test can ask what text was drawn and where, or what
 * colour a pixel ended up, and a person can save the frame and look at it.
 */
public final class FakeCanvas implements Canvas {

    /** The stand-in for the blurred game behind the panel. */
    public static final int BACKDROP = 0xFF46607F;

    /** A raster drawn, where, and at what opacity; stretched ones have their drawn size. */
    public record Drawn(Raster raster, int x, int y, int width, int height, float opacity) {

        public String text() {
            return raster.text();
        }
    }

    public record Fill(int x, int y, int width, int height, int argb) {
    }

    private final int width;
    private final int height;
    private final int[] pixels;
    public final List<Drawn> drawn = new ArrayList<>();
    public final List<Fill> fills = new ArrayList<>();
    private int clipX;
    private int clipY;
    private int clipRight;
    private int clipBottom;

    public FakeCanvas(int width, int height) {
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        java.util.Arrays.fill(pixels, BACKDROP);
        unclip();
    }

    @Override
    public int width() {
        return width;
    }

    @Override
    public int height() {
        return height;
    }

    @Override
    public void fill(int x, int y, int w, int h, int argb) {
        fills.add(new Fill(x, y, w, h, argb));
        for (int py = y; py < y + h; py++) {
            for (int px = x; px < x + w; px++) {
                blend(px, py, argb, 1f);
            }
        }
    }

    @Override
    public void draw(Raster raster, int x, int y, float opacity) {
        drawn.add(new Drawn(raster, x, y, raster.width(), raster.height(), opacity));
        int[] source = raster.argb();
        for (int ry = 0; ry < raster.height(); ry++) {
            for (int rx = 0; rx < raster.width(); rx++) {
                blend(x + rx, y + ry, source[ry * raster.width() + rx], opacity);
            }
        }
    }

    @Override
    public void drawStretched(Raster raster, int x, int y, int w, int h, float opacity) {
        drawn.add(new Drawn(raster, x, y, w, h, opacity));
        int[] source = raster.argb();
        for (int py = 0; py < h; py++) {
            for (int px = 0; px < w; px++) {
                int rx = Math.min(raster.width() - 1, px * raster.width() / w);
                int ry = Math.min(raster.height() - 1, py * raster.height() / h);
                blend(x + px, y + py, source[ry * raster.width() + rx], opacity);
            }
        }
    }

    @Override
    public void clip(int x, int y, int w, int h) {
        clipX = x;
        clipY = y;
        clipRight = x + w;
        clipBottom = y + h;
    }

    @Override
    public void unclip() {
        clipX = 0;
        clipY = 0;
        clipRight = width;
        clipBottom = height;
    }

    private void blend(int x, int y, int argb, float opacity) {
        if (x < clipX || y < clipY || x >= clipRight || y >= clipBottom) {
            return;
        }
        float a = (argb >>> 24) / 255f * opacity;
        if (a <= 0) {
            return;
        }
        int under = pixels[y * width + x];
        int r = Math.round(((argb >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((argb >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (1 - a));
        int b = Math.round((argb & 0xFF) * a + (under & 0xFF) * (1 - a));
        pixels[y * width + x] = 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** The colour a pixel ended up, opaque. */
    public int pixel(int x, int y) {
        return pixels[y * width + x];
    }

    /** Every line of text drawn, in the order it was drawn. */
    public List<String> texts() {
        List<String> texts = new ArrayList<>();
        for (Drawn each : drawn) {
            if (each.text() != null) {
                texts.add(each.text());
            }
        }
        return texts;
    }

    public boolean drew(String text) {
        return texts().contains(text);
    }

    /** Where a line of text was drawn: its raster, margin and all; {@code null} if it was not. */
    public Drawn textDrawn(String text) {
        for (Drawn each : drawn) {
            if (text.equals(each.text())) {
                return each;
            }
        }
        return null;
    }

    /** Writes the frame to a PNG, for a person to look at. */
    public void save(File file) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, width, height, pixels, 0, width);
        file.getParentFile().mkdirs();
        ImageIO.write(image, "png", file);
    }
}
