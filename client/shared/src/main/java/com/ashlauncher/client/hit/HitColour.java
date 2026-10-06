package com.ashlauncher.client.hit;

import com.ashlauncher.client.settings.Settings;

/**
 * The flash an entity shows when it is hurt: its colour and how strong it
 * is, from the player's settings, or the game's own red while the feature is
 * off. Only what this client draws changes; nothing is sent anywhere.
 *
 * <p>Each target takes it in its own form ({@code docs/research/0004}, section 4):
 * <ul>
 *   <li>1.21.11 bakes it into the red rows of the game's overlay texture,
 *       whose alpha is how much of the entity's own colour survives - so
 *       strength is stored inverted;
 *   <li>1.8.9 passes four floats per draw, whose alpha is the red's strength.
 * </ul>
 *
 * <p>The defaults are the game's own flash on both, exactly: 30 per cent red,
 * which is 1.8.9's {@code 0.3F} and 1.21.11's texel {@code 0xB2FF0000}.
 */
public final class HitColour {

    /** The game's own flash: red, at 30 per cent. */
    public static final int GAME_RGB = 0xFF0000;

    public static final int GAME_STRENGTH = 30;

    private final Settings settings;

    public HitColour(Settings settings) {
        this.settings = settings;
    }

    /** The flash's colour, {@code 0xRRGGBB}. Read on every call, so a change shows at once. */
    public int rgb() {
        return settings.get(Settings.HIT_COLOUR) ? settings.get(Settings.HIT_COLOUR_COLOUR) & 0xFFFFFF : GAME_RGB;
    }

    /** How strong it is, 0 to 100 per cent. */
    public int strength() {
        return settings.get(Settings.HIT_COLOUR) ? settings.get(Settings.HIT_COLOUR_STRENGTH) : GAME_STRENGTH;
    }

    /** 1.21.11's red-row texel, ARGB: the colour, with alpha the share of the entity's own colour kept. */
    public int overlayTexel() {
        return overlayTexel(rgb(), strength());
    }

    static int overlayTexel(int rgb, int strength) {
        int kept = 255 - Math.round(strength * 2.55F);
        return (kept << 24) | (rgb & 0xFFFFFF);
    }

    /** 1.8.9's constant colour, as red, green, blue and strength, each 0 to 1. */
    public float[] envColour() {
        int rgb = rgb();
        return new float[] {
            ((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F, strength() / 100F};
    }
}
