package com.ashlauncher.client.crosshair;

import com.ashlauncher.client.hud.HudSurface;

/**
 * Where each target's crosshair mixin reaches ash's {@link Crosshair}.
 *
 * <p>Static because a mixin can only reach the rest of ash through something
 * static. Installed by the target's entrypoint once the mixin is known to
 * have landed; until then, and if it never is, the game draws its own.
 */
public final class CrosshairHook {

    private static Crosshair crosshair;

    private CrosshairHook() {
    }

    public static void install(Crosshair feature) {
        crosshair = feature;
    }

    /**
     * Draws ash's crosshair if there is one and it is on.
     *
     * @return whether it drew; false means the game must draw its own
     */
    public static boolean draw(HudSurface surface) {
        return crosshair != null && crosshair.draw(surface);
    }
}
