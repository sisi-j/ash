package com.ashlauncher.client.crosshair;

import com.ashlauncher.client.hud.HudSurface;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * ash's crosshair, drawn in place of the game's.
 *
 * <p>It never decides whether a crosshair should show at all: each target's
 * mixin calls it from inside the game's own crosshair drawing, at the one
 * call that draws the game's crosshair, so every rule the game has - first
 * person only on 1.21.11, F1, spectators, the debug screen - has already been
 * applied by the time it runs. All it decides is whether ash draws instead.
 * See {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 1.
 */
public final class Crosshair {

    private final BooleanSupplier on;
    private final Supplier<Cross> cross;

    /**
     * @param on whether the player wants it; asked every frame, so a switch on
     *     the settings screen shows at once
     */
    public Crosshair(BooleanSupplier on, Supplier<Cross> cross) {
        this.on = on;
        this.cross = cross;
    }

    /** A crosshair that never changes shape: for tests, and anything without settings behind it. */
    public Crosshair(BooleanSupplier on, Cross cross) {
        this(on, () -> cross);
    }

    /**
     * Draws ash's crosshair centred on the given pixel, if the player has it
     * on.
     *
     * @param centreX the middle pixel of the game's own crosshair, from the
     *     call the mixin wraps. Not worked out here: the two targets place
     *     theirs differently - 1.8.9 at {@code width / 2 - 7}, 1.21.11 at
     *     {@code (width - 15) / 2}, one pixel up and left on an even-sized
     *     screen - and taking it from the game means agreeing with both.
     * @return whether it drew - and so whether the game's own crosshair must
     *     not. False means the caller draws the game's, so there is always
     *     exactly one.
     */
    public boolean draw(HudSurface surface, int centreX, int centreY) {
        if (!on.getAsBoolean()) {
            return false;
        }
        cross.get().drawOnto(surface::fill, centreX, centreY, 1);
        return true;
    }
}
