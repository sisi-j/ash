package com.ashlauncher.client.freelook;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Holding a key turns the camera and not the player; letting go returns the
 * camera to where the player is looking.
 *
 * <p>The player's own rotation - what movement packets carry, so what the
 * server sees - is never touched: the mouse's turn comes here instead of
 * reaching the player, and the camera reads its angles from here. A leak of
 * these angles into the player would fail ADR-0006, and the real-game test
 * asks the server to be sure.
 *
 * <p>Held in first person, the view moves to the game's own third-person
 * view from behind and orbits there, and goes back on release, as freelook
 * mods do. Switching camera mode is the client's alone; the server never hears
 * of it.
 *
 * <p>On a server whose published rules ban it ({@link BlockList}), the key
 * does nothing but say so.
 *
 * @param <M> the target's own camera mode
 */
public final class Freelook<M> {

    /** The key binding's name in Controls, on both targets. */
    public static final String BINDING_NAME = "Freelook (hold)";

    /** What the game turns the player by, per unit of mouse movement. Kept the same so freelook turns at the same speed. */
    private static final float DEGREES_PER_UNIT = 0.15F;

    private final CameraModes<M> modes;
    private final BooleanSupplier on;
    private final Supplier<BlockList.Server> blockedHere;
    private final Consumer<String> tell;

    private boolean active;
    private boolean wasHeld;
    private float yaw;
    private float pitch;
    /** The mode the player had before freelook moved the camera behind them, if it did. */
    private M before;
    /** The mode freelook set, to restore only if the player has not changed it since. */
    private M ours;

    /**
     * @param on whether the player has freelook switched on
     * @param blockedHere the listed server being played on, or null
     * @param tell shows the player one line, for a press that does nothing
     */
    public Freelook(CameraModes<M> modes, BooleanSupplier on, Supplier<BlockList.Server> blockedHere,
            Consumer<String> tell) {
        this.modes = modes;
        this.on = on;
        this.blockedHere = blockedHere;
        this.tell = tell;
    }

    /**
     * Once a tick.
     *
     * @param held whether the key is down
     * @param screenOpen whether a screen is open; one opening mid-hold ends freelook
     * @param playerYaw the player's own yaw, where the camera starts
     * @param playerPitch the player's own pitch
     */
    public void tick(boolean held, boolean screenOpen, float playerYaw, float playerPitch) {
        boolean pressed = held && !wasHeld;
        wasHeld = held;
        if (active) {
            if (!held || screenOpen || !on.getAsBoolean() || blockedHere.get() != null) {
                stop();
            }
            return;
        }
        if (!pressed || screenOpen || !on.getAsBoolean()) {
            return;
        }
        BlockList.Server blocked = blockedHere.get();
        if (blocked != null) {
            tell.accept(blocked.whyOff());
            return;
        }
        start(playerYaw, playerPitch);
    }

    private void start(float playerYaw, float playerPitch) {
        yaw = playerYaw;
        pitch = playerPitch;
        before = modes.current();
        ours = null;
        if (modes.isFirstPerson(before)) {
            ours = modes.behind();
            modes.set(ours);
        }
        active = true;
    }

    private void stop() {
        active = false;
        // Only if the player has not changed view themselves meanwhile.
        if (ours != null && ours.equals(modes.current())) {
            modes.set(before);
        }
        ours = null;
        before = null;
    }

    /**
     * The mouse's turn, as the game would have given it to the player.
     *
     * @return true when freelook took it, so the player must not get it
     */
    public boolean turn(double dx, double dy) {
        if (!active) {
            return false;
        }
        yaw += (float) dx * DEGREES_PER_UNIT;
        pitch = Math.max(-90.0F, Math.min(90.0F, pitch + (float) dy * DEGREES_PER_UNIT));
        return true;
    }

    public boolean active() {
        return active;
    }

    /** The camera's yaw while freelook is held. */
    public float yaw() {
        return yaw;
    }

    /** The camera's pitch while freelook is held. */
    public float pitch() {
        return pitch;
    }
}
