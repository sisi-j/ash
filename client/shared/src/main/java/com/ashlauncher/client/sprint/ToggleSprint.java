package com.ashlauncher.client.sprint;

/**
 * Sprint on a key press rather than a key held for a whole session.
 *
 * <p>It works by answering one question the game already asks every tick -
 * is the sprint key down? - with "yes" while the latch is on. Nothing else is
 * changed, so every rule the game has about sprinting still applies (hunger,
 * blindness, moving forward, hitting a wall), and the server receives exactly
 * what a player holding the sprint key would send. There is no other way for
 * it to differ from vanilla, because it never gets closer to the server than
 * a key.
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
 *       repeat takes the same path as a press. A press only counts if the key
 *       was up at the last tick, so repeats, which can only happen to a held
 *       key, never flip it.
 * </ul>
 */
public final class ToggleSprint {

    private final ToggleKey key;
    private final boolean enabled;
    private boolean latched;
    private boolean wasHeld;

    /**
     * @param enabled whether the player wants it, from their settings. Read
     *     once, at startup; with it off, the game's sprint is untouched.
     */
    public ToggleSprint(ToggleKey key, boolean enabled) {
        this.key = key;
        this.enabled = enabled;
    }

    /** Reads the toggle key. Once a tick, before the game asks about sprint. */
    public void tick() {
        if (!enabled) {
            return;
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
     */
    public boolean sprintKeyDown(boolean reallyDown) {
        return reallyDown || latched;
    }
}
