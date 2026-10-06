package com.ashlauncher.client.ping;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.HudSurface;
import java.util.function.Supplier;

/**
 * The player's latency to the server, on screen, beneath the FPS readout
 * unless moved.
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

    /** The FPS readout's colour, so the two read as one block. */
    static final int COLOUR = FpsReadout.COLOUR;

    /** What it shows in Edit HUD when there is no number, so it can still be found and moved. */
    static final String NO_NUMBER = "-- ms";

    private final Supplier<Integer> latency;
    private final HudLayout layout;

    /**
     * @param latency the server's latency for the player, in milliseconds;
     *     null while the player hosts the world or before the server has
     *     listed them
     * @param layout where it sits, and whether the player wants it, asked
     *     every frame
     */
    public PingReadout(Supplier<Integer> latency, HudLayout layout) {
        this.latency = latency;
        this.layout = layout;
    }

    public void draw(HudSurface surface) {
        if (!layout.shown(HudLayout.Readout.PING) || surface.hudHidden() || surface.debugScreenShown()) {
            return;
        }
        Integer ms = latency.get();
        boolean known = ms != null && ms >= 0;
        if (!known && !layout.editing()) {
            return;
        }
        String text = known ? text(ms) : NO_NUMBER;
        int[] at = layout.place(HudLayout.Readout.PING, surface.textWidth(text), surface.lineHeight(),
                surface.width(), surface.height());
        surface.drawText(text, at[0], at[1], COLOUR);
    }

    /** What it says: the server's number, in milliseconds. */
    static String text(int ms) {
        return ms + " ms";
    }
}
