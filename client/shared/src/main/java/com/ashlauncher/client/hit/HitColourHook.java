package com.ashlauncher.client.hit;

/**
 * Where 1.8.9's renderer mixin reaches hit colour from. A mixin can only call
 * out statically, so the feature the client built at startup is handed over
 * here; until it is, the game's own flash is left as it is.
 */
public final class HitColourHook {

    private static HitColour hitColour;

    private HitColourHook() {
    }

    public static void install(HitColour feature) {
        hitColour = feature;
    }

    /** The flash as red, green, blue and strength, 0 to 1; null to leave the game's own. */
    public static float[] envColour() {
        return hitColour == null ? null : hitColour.envColour();
    }
}
