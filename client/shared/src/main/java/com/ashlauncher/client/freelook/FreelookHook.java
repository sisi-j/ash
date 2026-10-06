package com.ashlauncher.client.freelook;

/**
 * Where each target's mixins reach freelook from. A mixin can only call out
 * statically, so the feature the client built at startup is handed over here.
 * Until it is - or if it never is - the game behaves as its own.
 */
public final class FreelookHook {

    private static Freelook<?> freelook;

    private FreelookHook() {
    }

    public static void install(Freelook<?> feature) {
        freelook = feature;
    }

    /**
     * The mouse's turn, on its way to the player.
     *
     * @return true when freelook took it, so the player must not get it
     */
    public static boolean turn(double dx, double dy) {
        return freelook != null && freelook.turn(dx, dy);
    }

    /** The camera's yaw: freelook's while it is held, otherwise the player's. */
    public static float yaw(float players) {
        return freelook != null && freelook.active() ? freelook.yaw() : players;
    }

    /** The camera's pitch: freelook's while it is held, otherwise the player's. */
    public static float pitch(float players) {
        return freelook != null && freelook.active() ? freelook.pitch() : players;
    }
}
