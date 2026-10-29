package com.ashlauncher.client.game;

/**
 * The game, as ash's shared code reads it: read-only facts, answered by each
 * version target from its own game (spec 0007, The game view).
 *
 * <p>Tests fill in a fake one, so a feature that depends on what the player is
 * doing is tested without a game. Where {@link com.ashlauncher.client.hud.HudSurface}
 * is somewhere to draw, this is what's going on.
 *
 * <p>It grows with the features that need it: keys held, position, armour and
 * effects arrive with the HUD items that show them (beta phase 3). Like the
 * HUD surface, it carries nothing a feature doesn't use yet.
 */
public interface GameView {

    /**
     * Whether the game's debug text is on screen: the F3 overlay itself, and
     * nothing wider. On 1.21.11 the game's own "debug screen showing" is also
     * true while F3+B hitboxes are on, which a PvP player leaves on, with no
     * debug text anywhere.
     */
    boolean debugScreenShown();

    /** Whether chat is open, with the player typing in it. */
    boolean chatOpen();

    /**
     * The address of the server being played on, as the player wrote it, port
     * and case included: {@code mc.hypixel.net}. Null in singleplayer and out
     * of a world.
     */
    String serverAddress();
}
