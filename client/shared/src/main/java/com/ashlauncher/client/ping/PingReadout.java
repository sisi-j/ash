package com.ashlauncher.client.ping;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.HudSurface;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * The player's latency to the server, on screen, beneath the FPS readout.
 *
 * <p>The number is the server's: the latency it reports for the player's own
 * tab-list entry, the number behind the tab list's signal bars. ash never
 * measures latency itself, because that would mean sending the server
 * something vanilla does not. A vanilla server refreshes it about every 30
 * seconds, as a smoothed average, so it moves about twice a minute and not
 * with each spike ({@code docs/research/0004}, section 3).
 *
 * <p>Hidden while the player hosts the world - singleplayer, or opened to LAN
 * - where it would mean nothing. Each target answers that, and reads the
 * number, in a line over its own API.
 */
public final class PingReadout {

    /** The same corner and colour as the FPS readout, so the two read as one block. */
    static final int MARGIN = FpsReadout.MARGIN;

    static final int COLOUR = FpsReadout.COLOUR;

    /** Between the FPS line and this one, so the two never touch. */
    static final int GAP = 2;

    private final Supplier<Integer> latency;
    private final BooleanSupplier on;
    private final BooleanSupplier fpsShown;

    /**
     * @param latency the server's latency for the player, in milliseconds;
     *     null while the player hosts the world or before the server has
     *     listed them
     * @param on whether the player wants it, asked every frame
     * @param fpsShown whether the FPS readout is switched on, so this sits
     *     beneath it, or in its place when it is off
     */
    public PingReadout(Supplier<Integer> latency, BooleanSupplier on, BooleanSupplier fpsShown) {
        this.latency = latency;
        this.on = on;
        this.fpsShown = fpsShown;
    }

    public void draw(HudSurface surface) {
        if (!on.getAsBoolean() || surface.hudHidden() || surface.debugScreenShown()) {
            return;
        }
        Integer ms = latency.get();
        if (ms == null || ms < 0) {
            return;
        }
        int y = fpsShown.getAsBoolean() ? MARGIN + surface.lineHeight() + GAP : MARGIN;
        surface.drawText(text(ms), MARGIN, y, COLOUR);
    }

    /** What it says: the server's number, in milliseconds. */
    static String text(int ms) {
        return ms + " ms";
    }
}
