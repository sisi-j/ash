package com.ashlauncher.client.hit;

import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.Settings;
import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.LongSupplier;

/**
 * A mark at the crosshair when the server confirms the player's hit landed.
 *
 * <p>It is told only that a hit was confirmed - never by whom, for how much,
 * or what was left - so it cannot show a damage amount or anyone's health,
 * which the game does not give a player and ADR-0006 rules out. What counts
 * as confirmed is each target's business: see {@link HitHook}.
 *
 * <p>The mark is four short diagonals around the crosshair's centre, clear of
 * its arms, so it reads as a different thing from the crosshair whatever
 * shape that is. Solid for the first half of the time the player set, then
 * fading out.
 */
public final class HitIndicator {

    /** How far from the centre each diagonal starts, in GUI pixels: clear of a default crosshair's arms. */
    static final int FROM = 3;

    /** How far from the centre each diagonal ends, in GUI pixels, inclusive. */
    static final int TO = 5;

    private final BooleanSupplier on;
    private final IntSupplier colour;
    private final IntSupplier durationMillis;
    private final LongSupplier clockMillis;
    private long confirmedAt;
    private boolean confirmed;

    /**
     * @param on whether the player wants it, asked at each hit and each frame
     * @param colour packed ARGB, read as it draws so a change shows at once
     * @param durationMillis how long a mark shows, read as it draws
     * @param clockMillis a clock that only goes forward - not the wall clock,
     *     which a player can change mid-session
     */
    public HitIndicator(BooleanSupplier on, IntSupplier colour, IntSupplier durationMillis, LongSupplier clockMillis) {
        this.on = on;
        this.colour = colour;
        this.durationMillis = durationMillis;
        this.clockMillis = clockMillis;
    }

    /** The hit indicator as the player has set it up, read afresh at every hit and every frame. */
    public static HitIndicator from(Settings settings, LongSupplier clockMillis) {
        return new HitIndicator(() -> settings.get(Settings.HIT_INDICATOR),
                () -> settings.get(Settings.HIT_INDICATOR_COLOUR), () -> settings.get(Settings.HIT_INDICATOR_DURATION),
                clockMillis);
    }

    /**
     * The server confirmed one of the player's hits: the mark starts again
     * from solid. Ignored while it is switched off, so switching it on never
     * shows a hit from before.
     */
    public void confirmed() {
        if (on.getAsBoolean()) {
            confirmedAt = clockMillis.getAsLong();
            confirmed = true;
        }
    }

    /** Draws the mark around the crosshair's centre pixel, if a hit is still showing. */
    public void draw(HudSurface surface, int centreX, int centreY) {
        if (!confirmed || !on.getAsBoolean() || surface.hudHidden()) {
            return;
        }
        int duration = Math.max(1, durationMillis.getAsInt());
        long elapsed = clockMillis.getAsLong() - confirmedAt;
        if (elapsed < 0 || elapsed >= duration) {
            return;
        }
        int argb = colour.getAsInt();
        double left = Math.min(1.0, (duration - elapsed) / (duration / 2.0));
        int alpha = (int) Math.round((argb >>> 24) * left);
        // Below this the two targets disagree - 1.8.9's alpha test drops what
        // 1.21.11 still draws faintly - so the fade ends here on both.
        if (alpha < Colour.MIN_ALPHA) {
            return;
        }
        int faded = (alpha << 24) | (argb & 0xFFFFFF);
        for (int d = FROM; d <= TO; d++) {
            surface.fill(centreX + d, centreY + d, 1, 1, faded);
            surface.fill(centreX - d, centreY + d, 1, 1, faded);
            surface.fill(centreX + d, centreY - d, 1, 1, faded);
            surface.fill(centreX - d, centreY - d, 1, 1, faded);
        }
    }
}
