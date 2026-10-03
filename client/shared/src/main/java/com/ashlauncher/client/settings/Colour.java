package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.Locale;

/**
 * A colour with its opacity, as packed ARGB. Written in the file as
 * {@code #RRGGBBAA}; read as that, or as {@code #RRGGBB} for fully opaque.
 *
 * <p>Never fainter than {@link #MIN_ALPHA}. At an opacity near zero the two
 * targets disagree: 1.8.9 draws a near-transparent text colour fully opaque,
 * and 1.21.11 draws nothing. A colour that faint is invisible anyway, so it
 * is raised to the faintest both targets draw the same way rather than left
 * to look opposite on the two.
 */
public final class Colour extends Setting<Integer> {

    /** About ten per cent: the faintest a colour is kept. */
    public static final int MIN_ALPHA = 0x1A;

    Colour(Feature feature, String key, String label, int fallback, String comment) {
        super(feature, key, label, fallback, comment);
    }

    @Override
    Integer parse(String raw) {
        String text = raw.trim();
        if (!text.startsWith("#") || (text.length() != 7 && text.length() != 9)) {
            return null;
        }
        String hex = text.substring(1);
        for (char c : hex.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return null;
            }
        }
        long rgb = Long.parseLong(hex.substring(0, 6), 16);
        long alpha = hex.length() == 8 ? Long.parseLong(hex.substring(6), 16) : 0xFF;
        return normalise((int) ((alpha << 24) | rgb));
    }

    /** {@code #RRGGBBAA}, in capitals. */
    @Override
    String format(Integer value) {
        int rgb = value & 0xFFFFFF;
        int alpha = value >>> 24;
        return String.format(Locale.ROOT, "#%06X%02X", rgb, alpha);
    }

    @Override
    String expected() {
        return "a colour such as #FFFFFFFF";
    }

    @Override
    Integer normalise(Integer value) {
        int alpha = value >>> 24;
        return alpha >= MIN_ALPHA ? value : (MIN_ALPHA << 24) | (value & 0xFFFFFF);
    }
}
