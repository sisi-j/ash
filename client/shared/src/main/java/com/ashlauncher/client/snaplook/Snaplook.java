package com.ashlauncher.client.snaplook;

import com.ashlauncher.client.freelook.CameraModes;
import java.util.function.BooleanSupplier;

/**
 * Holding a key shows the player from the front, the way the game's own F5
 * does on its second press; letting go puts back whatever view they had,
 * third person included.
 *
 * <p>Only the game's own camera modes are used, so it shows nothing F5 cannot,
 * which is where the servers that ban freelook draw their line. It needs no
 * block list (ADR-0006, 2026-09-30 amendment).
 *
 * <p>A screen opening ends it too: the game releases every key for one, and a
 * key let go is a view put back.
 *
 * @param <M> the target's own camera mode
 */
public final class Snaplook<M> {

    /** The key binding's name in Controls, on both targets. */
    public static final String BINDING_NAME = "Snaplook (hold)";

    private final CameraModes<M> modes;
    private final BooleanSupplier on;

    private boolean active;
    private boolean wasHeld;
    /** The view the player had before the key went down. */
    private M before;
    /** The front view, if snaplook set it - restored only if the player has not changed view since. */
    private M ours;

    /** @param on whether the player has snaplook switched on */
    public Snaplook(CameraModes<M> modes, BooleanSupplier on) {
        this.modes = modes;
        this.on = on;
    }

    /**
     * Once a tick.
     *
     * @param held whether the key is down
     * @param screenOpen whether a screen is open; a press there does nothing
     */
    public void tick(boolean held, boolean screenOpen) {
        boolean pressed = held && !wasHeld;
        wasHeld = held;
        if (active) {
            if (!held || !on.getAsBoolean()) {
                stop();
            }
            return;
        }
        if (pressed && !screenOpen && on.getAsBoolean()) {
            start();
        }
    }

    private void start() {
        before = modes.current();
        M front = modes.front();
        ours = null;
        if (!front.equals(before)) {
            ours = front;
            modes.set(front);
        }
        active = true;
    }

    private void stop() {
        active = false;
        if (ours != null && ours.equals(modes.current())) {
            modes.set(before);
        }
        ours = null;
        before = null;
    }

    public boolean active() {
        return active;
    }
}
