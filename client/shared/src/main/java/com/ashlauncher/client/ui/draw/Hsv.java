package com.ashlauncher.client.ui.draw;

/**
 * Hue, saturation and brightness, the colour picker's own terms (#68): the
 * square is saturation across and brightness down, the bar beside it hue.
 * Converted to and from the RGB the settings keep, opacity aside.
 */
public final class Hsv {

    private Hsv() {
    }

    /** RGB, opaque bits clear, for a hue in degrees and a saturation and brightness from 0 to 1. */
    public static int toRgb(float hue, float saturation, float brightness) {
        float h = ((hue % 360f) + 360f) % 360f / 60f;
        float s = clamp(saturation);
        float v = clamp(brightness);
        float c = v * s;
        float x = c * (1 - Math.abs(h % 2 - 1));
        float r;
        float g;
        float b;
        switch ((int) h) {
            case 0: r = c; g = x; b = 0; break;
            case 1: r = x; g = c; b = 0; break;
            case 2: r = 0; g = c; b = x; break;
            case 3: r = 0; g = x; b = c; break;
            case 4: r = x; g = 0; b = c; break;
            default: r = c; g = 0; b = x; break;
        }
        float m = v - c;
        return (channel(r + m) << 16) | (channel(g + m) << 8) | channel(b + m);
    }

    /**
     * The hue, saturation and brightness of {@code rgb}. Grey has no hue of
     * its own and black no saturation either; for those, {@code keep}'s hue
     * and saturation stand, so dragging through grey or black loses neither.
     */
    public static float[] fromRgb(int rgb, float[] keep) {
        float r = ((rgb >> 16) & 0xFF) / 255f;
        float g = ((rgb >> 8) & 0xFF) / 255f;
        float b = (rgb & 0xFF) / 255f;
        float max = Math.max(r, Math.max(g, b));
        float min = Math.min(r, Math.min(g, b));
        float delta = max - min;
        float hue;
        if (delta == 0) {
            hue = keep == null ? 0 : keep[0];
        } else if (max == r) {
            hue = 60 * (((g - b) / delta) % 6);
        } else if (max == g) {
            hue = 60 * ((b - r) / delta + 2);
        } else {
            hue = 60 * ((r - g) / delta + 4);
        }
        if (hue < 0) {
            hue += 360;
        }
        float saturation = max == 0 ? (keep == null ? 0 : keep[1]) : delta / max;
        return new float[] {hue, saturation, max};
    }

    private static float clamp(float value) {
        return Math.max(0f, Math.min(1f, value));
    }

    private static int channel(float value) {
        return Math.round(clamp(value) * 255);
    }
}
