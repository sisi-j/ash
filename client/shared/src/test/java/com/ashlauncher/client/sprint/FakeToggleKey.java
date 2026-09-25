package com.ashlauncher.client.sprint;

/**
 * The toggle key, driven by a test rather than a keyboard.
 *
 * <p>Models the two things the game reports about a key, because the latch
 * needs both: whether it is down at the moment the tick samples it, and how
 * many times it went down since the last tick - which, on 1.21.11, the game
 * also counts once for every key repeat of a key held down.
 */
final class FakeToggleKey implements ToggleKey {

    private boolean held;
    private int presses;

    /** Goes down and stays down, as a real press does until released. */
    void press() {
        held = true;
        presses++;
    }

    /** Goes down and comes up again before the next tick samples it. */
    void tap() {
        presses++;
    }

    void release() {
        held = false;
    }

    /**
     * The player picks another key in Controls. From then on the binding
     * reports that key, which is not down - whatever the old one is doing.
     */
    void rebindToAKeyThatIsUp() {
        held = false;
    }

    /**
     * The player picks another key in Controls, and it is already down - the
     * key they pressed to choose it, still under their finger.
     */
    void rebindToAKeyThatIsDown() {
        held = true;
    }

    /**
     * One key repeat: another press on a key that is still down. The system
     * sends them after about half a second held, then around thirty a second.
     */
    void repeat() {
        presses++;
    }

    @Override
    public boolean held() {
        return held;
    }

    @Override
    public int takePresses() {
        int taken = presses;
        presses = 0;
        return taken;
    }
}
