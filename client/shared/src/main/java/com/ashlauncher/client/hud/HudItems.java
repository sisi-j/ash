package com.ashlauncher.client.hud;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.settings.HudItemSettings;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.ui.draw.Hsv;
import java.util.function.LongSupplier;

/**
 * Draws the HUD items, each from the game view, in its style, where the
 * layout puts it.
 *
 * <p>The same on every version target, because everything that could differ
 * lives here: the text, the place, the style, and when to step aside. What a
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

    /** How long chroma takes to go once round the rainbow: the same for every item, so they cycle together. */
    static final long CHROMA_MILLIS = 4000;

    /** The space between an item's text and the edge of its background box, in GUI units. */
    static final int PAD = 2;

    private final HudLayout layout;
    private final GameView game;
    private final boolean beta;
    private final LongSupplier millis;

    /**
     * As a release build draws them: in the readouts' own look, whatever the
     * settings say of style (ADR-0029).
     *
     * @param layout where each item sits, and whether the player wants it -
     *     both from their settings, and asked every frame, so a change on the
     *     settings screen shows at once
     */
    public HudItems(HudLayout layout, GameView game) {
        this(layout, game, false, () -> 0L);
    }

    /**
     * @param beta whether this game was started by a test build, whose items
     *     take their style from the settings
     * @param millis a clock in milliseconds, for chroma
     */
    public HudItems(HudLayout layout, GameView game, boolean beta, LongSupplier millis) {
        this.layout = layout;
        this.game = game;
        this.beta = beta;
        this.millis = millis;
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
        Settings settings = layout.settings();
        HudItemSettings style = item.settings();
        String text = item.text(value, beta ? settings.get(style.label) : "after");
        if (beta && settings.get(style.brackets)) {
            text = "[" + text + "]";
        }
        float scale = beta ? settings.get(style.size) / 100f : 1f;
        boolean background = beta && settings.get(style.background);
        int pad = background ? PAD : 0;
        int width = Math.round(surface.textWidth(text) * scale) + 2 * pad;
        int height = Math.round(surface.lineHeight() * scale) + 2 * pad;
        int[] at = layout.place(item, width, height, surface.width(), surface.height());
        if (background) {
            surface.fill(at[0], at[1], width, height, settings.get(style.backgroundColour));
        }
        surface.drawText(text, at[0] + pad, at[1] + pad, beta ? colour(style, settings) : COLOUR, scale,
                !beta || settings.get(style.shadow));
    }

    /** Its text's colour, opaque: the one the player picked, or, under chroma, the rainbow's just now. */
    private int colour(HudItemSettings style, Settings settings) {
        if (settings.get(style.chroma)) {
            float turn = Math.floorMod(millis.getAsLong(), CHROMA_MILLIS) / (float) CHROMA_MILLIS;
            return 0xFF000000 | Hsv.toRgb(turn * 360f, 1f, 1f);
        }
        return 0xFF000000 | settings.get(style.textColour);
    }
}
