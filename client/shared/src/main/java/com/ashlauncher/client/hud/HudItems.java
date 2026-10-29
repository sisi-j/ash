package com.ashlauncher.client.hud;

import com.ashlauncher.client.game.GameView;

/**
 * Draws the HUD items, each from the game view, where the layout puts it.
 *
 * <p>The same on every version target, because everything that could differ
 * lives here: the text, the place, the colour, and when to step aside. What a
 * target gives is the game view and a surface to draw on, each a line or two
 * over its own game.
 *
 * <p>Asked of the game every frame rather than once: the game counts its frame
 * rate once a second, and a number held from the moment the world loaded would
 * be wrong for the rest of the session.
 */
public final class HudItems {

    /**
     * Opaque white, as the marker is, and for the same reason: an unset alpha
     * draws nothing on 1.21.11 and opaque on 1.8.9. See {@link HudSurface#drawText}.
     */
    public static final int COLOUR = 0xFFFFFFFF;

    private final HudLayout layout;
    private final GameView game;

    /**
     * @param layout where each item sits, and whether the player wants it -
     *     both from their settings, and asked every frame, so a change on the
     *     settings screen shows at once
     */
    public HudItems(HudLayout layout, GameView game) {
        this.layout = layout;
        this.game = game;
    }

    /** Draws every item, in the registry's order, which is the order they stack in. */
    public void draw(HudSurface surface) {
        for (HudItem item : HudItem.ALL) {
            draw(item, surface);
        }
    }

    /**
     * Draws one item, unless the player has it off, has hidden the HUD, or has
     * the debug screen open - which draws its own frame rate in the same
     * corner. One with nothing to show draws nothing, except in Edit HUD,
     * where it shows its sample so that it can still be found and moved.
     */
    public void draw(HudItem item, HudSurface surface) {
        if (!layout.shown(item) || surface.hudHidden() || surface.debugScreenShown()) {
            return;
        }
        String value = item.value(game);
        if (value == null) {
            if (!layout.editing()) {
                return;
            }
            value = item.sample();
        }
        String text = item.text(value);
        int[] at = layout.place(item, surface.textWidth(text), surface.lineHeight(), surface.width(),
                surface.height());
        surface.drawText(text, at[0], at[1], COLOUR);
    }
}
