package com.ashlauncher.client.sprint;

import java.lang.ref.WeakReference;
import java.util.function.BooleanSupplier;

/**
 * Sprint on a key press rather than a key held for a whole session.
 *
 * <p>It works by answering one question the game already asks every tick -
 * is the sprint key down? - with "yes" while the latch is on. Nothing else is
 * changed, so every rule the game has about sprinting still applies (hunger,
 * blindness, moving forward, hitting a wall).
 *
 * <p>The answer is also what reaches the server: since 1.21.2 the game sends
 * the player's keys every tick. So the latch has to read exactly as a held key
 * reads, including the two places a held key stops reading as held - a menu,
 * where the game releases every key, and a new player after a death or a
 * world change, where the game resets its own toggle keys. Anywhere else the
 * server sees exactly what a player holding the sprint key would send.
 *
 * <p>The latch is fed two facts about the toggle key each tick, and needs
 * both:
 *
 * <ul>
 *   <li><b>Presses</b>, so that a tap shorter than a 50ms tick still counts.
 *       Watching only whether the key is held would miss it, and on a
 *       rapid-trigger keyboard that is an ordinary press.
 *   <li><b>Whether it was already held</b>, because on 1.21.11 the game counts
 *       every key repeat as another press - read from its bytecode, where a
 *       repeat takes the same path as a press - and repeats arrive one or two
 *       a tick. A press only counts if the key was up at the last tick, so
 *       repeats, which can only happen to a held key, never flip it.
 * </ul>
 */
public final class ToggleSprint {

    /**
     * What the binding is called in Controls, on both targets.
     *
     * <p>Display text rather than a translation key: 1.8.9 loads no mod assets
     * without another Legacy Fabric module, so a key would be shown there raw.
     * A name the game has no translation for is shown as itself.
     */
    public static final String BINDING_NAME = "Toggle Sprint";

    private final ToggleKey key;
    private final BooleanSupplier on;
    private boolean latched;
    private boolean wasHeld;

    /** Held weakly: the last player must not keep a finished world alive. */
    private WeakReference<Object> player = new WeakReference<>(null);

    /**
     * @param on whether the player wants it, from their settings. Asked every
     *     tick, so it can be switched mid-sprint; with it off, the game's
     *     sprint is untouched.
     */
    public ToggleSprint(ToggleKey key, BooleanSupplier on) {
        this.key = key;
        this.on = on;
    }

    /**
     * Reads the toggle key. Once a tick, before the game asks about sprint.
     *
     * @param player whatever object the game uses for the local player - only
     *     its identity is used. A player this has not seen before starts with
     *     the latch off.
     */
    public void tick(Object player) {
        if (!on.getAsBoolean()) {
            // Off releases the latch at once, so the sprint key reads as up -
            // exactly as letting go of a held sprint key does, and with the
            // same result: the game keeps a sprint that has started until the
            // player stops or turns. The key stays bound while the feature is
            // off - bindings are registered once, at startup - so presses made
            // now are drained here rather than left to flip the latch the
            // moment it is switched back on.
            latched = false;
            key.takePresses();
            wasHeld = key.held();
            return;
        }
        if (this.player.get() != player) {
            this.player = new WeakReference<>(player);
            latched = false;
        }
        int presses = key.takePresses();
        if (presses > 0 && !wasHeld) {
            latched = !latched;
        }
        wasHeld = key.held();
    }

    /**
     * What the game should see when it asks whether the sprint key is down.
     *
     * <p>Asked where the game asks it, which is not always once a tick - 1.8.9
     * asks twice, once for each way its player can start sprinting - so this
     * only answers and never changes the latch.
     *
     * @param reallyDown whether the game's own sprint key is down, which
     *     always sprints: toggle sprint adds a way to sprint and takes none away
     * @param menuOpen whether a screen is open, in which case the game has
     *     released every key and the latch reads as released with them
     */
    public boolean sprintKeyDown(boolean reallyDown, boolean menuOpen) {
        return reallyDown || (latched && !menuOpen && on.getAsBoolean());
    }
}
