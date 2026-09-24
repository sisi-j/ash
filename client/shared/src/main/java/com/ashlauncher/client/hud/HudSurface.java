package com.ashlauncher.client.hud;

/**
 * Somewhere ash's features draw, with the game held on the far side of it.
 *
 * <p>Deliberately "draw this primitive at this position", and never "replace
 * element X". Modern Fabric API offers a named, ordered element registry with
 * replacement and removal; Legacy Fabric API offers a single callback that
 * fires after the vanilla HUD and can only draw on top of it. The narrower
 * target has to dictate the seam, or the seam is a 1.21-shaped interface with
 * a second implementation bolted on. See
 * {@code docs/adr/0015-two-client-projects-over-a-shared-module.md}.
 *
 * <p>Implemented once per version target and kept thin: each method is a line
 * or two over the game's own API, because nothing but a human will ever read
 * them. Everything with a decision in it belongs on this side, where a test
 * can reach it.
 *
 * <p>This carries only what a feature already needs. A method nothing calls
 * is a guess about the next feature, and the two targets punish guesses
 * differently.
 */
public interface HudSurface {

    /** Height of the drawable area, in the game's scaled GUI units. */
    int height();

    /**
     * Height of one line of the game's own font, in the same units.
     *
     * <p>A question only the game can answer - it is 9 on both targets today,
     * but that is the game's business and not a number for ash to carry.
     */
    int lineHeight();

    /**
     * Draw one line of text with its top-left corner at ({@code x}, {@code y}).
     *
     * @param colour packed ARGB; a zero alpha draws nothing on either target
     */
    void drawText(String text, int x, int y, int colour);

    /**
     * Whether the game's debug screen is open - F3 on both targets.
     *
     * <p>It fills the top-left with text of its own, so anything ash draws
     * there has to know.
     */
    boolean debugScreenShown();

    /**
     * Whether the player has hidden the HUD - F1 on both targets.
     *
     * <p>A question the surface has to answer rather than one the game answers
     * by not asking, because the two targets disagree. 1.8.9 never fires its
     * HUD callback while the HUD is hidden. 1.21.11's element registry does:
     * Fabric documents that elements added first or last "will not inherit
     * any render condition", and the condition every vanilla element has is
     * this one.
     */
    boolean hidden();
}
