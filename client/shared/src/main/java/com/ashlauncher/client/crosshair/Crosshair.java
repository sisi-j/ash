package com.ashlauncher.client.crosshair;

import com.ashlauncher.client.hud.HudSurface;
import java.util.function.BooleanSupplier;

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
    private final Cross cross;

    /**
     * @param on whether the player wants it; asked every frame, so a switch on
     *     the settings screen shows at once
     */
    public Crosshair(BooleanSupplier on, Cross cross) {
        this.on = on;
        this.cross = cross;
    }

    /**
     * Draws ash's crosshair centred where the game's would be, if the player
     * has it on.
     *
     * @return whether it drew - and so whether the game's own crosshair must
     *     not. False means the caller draws the game's, so there is always
     *     exactly one.
     */
    public boolean draw(HudSurface surface) {
        if (!on.getAsBoolean()) {
            return false;
        }
        // Half the GUI size, rounded down: the pixel the game's own 15-wide
        // crosshair has in its middle (it is drawn from width / 2 - 7).
        int centreX = surface.width() / 2;
        int centreY = surface.height() / 2;
        for (Cross.Piece piece : cross.pieces()) {
            surface.fill(centreX + piece.x, centreY + piece.y, piece.width, piece.height, piece.colour);
        }
        return true;
    }
}
