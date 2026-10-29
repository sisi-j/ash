package com.ashlauncher.client.hud;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.HudItemSettings;
import com.ashlauncher.client.settings.Position;
import com.ashlauncher.client.settings.Settings;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * One thing a feature draws on the HUD, declared once (spec 0007, HUD items).
 *
 * <p>The declaration is the whole of an item. Drawing ({@link HudItems}),
 * placing ({@link HudLayout}) and Edit HUD all read it, so a new item is a
 * declaration here and a fact in the {@link GameView} it reads, with no code
 * of its own anywhere else.
 *
 * <p>An item reads as a value and a label, "144 FPS", kept apart so that the
 * label can be restyled on its own.
 */
public final class HudItem {

    /** The frame rate, as the game counts it, top-left: where it has always been. */
    public static final HudItem FPS = new HudItem(Feature.FPS_READOUT, Settings.FPS_READOUT_POSITION,
            Settings.FPS_READOUT_ITEM, "FPS", "FPS", game -> String.valueOf(game.framesPerSecond()), "120", false,
            EnumSet.allOf(HudStyle.class));

    /**
     * The player's latency to the server, beneath the frame rate. The number
     * is the server's, the one behind the tab list's signal bars: ash never
     * measures latency itself, because that would mean sending the server
     * something vanilla doesn't ({@code docs/research/0004}, section 3). So
     * there is nothing to show while the player hosts the world, or before
     * the server has listed them.
     */
    public static final HudItem PING = new HudItem(Feature.PING_READOUT, Settings.PING_READOUT_POSITION,
            Settings.PING_READOUT_ITEM, "ms", "Ping", game -> {
                Integer ms = game.latency();
                return ms == null || ms < 0 ? null : String.valueOf(ms);
            }, "--", true, EnumSet.allOf(HudStyle.class));

    /**
     * Every HUD item, in the order they are drawn and stacked down a column.
     * A new one goes at the end, so it never moves one a player already has.
     */
    public static final List<HudItem> ALL = Collections.unmodifiableList(Arrays.asList(FPS, PING));

    private final Feature feature;
    private final Position position;
    private final HudItemSettings settings;
    private final String label;
    private final String name;
    private final Function<GameView, String> value;
    private final String sample;
    private final boolean onlyWhenRelevant;
    private final Set<HudStyle> styles;

    /**
     * @param position its setting; the setting's default is the item's home,
     *     its default anchor and offset
     * @param settings its style, declared for every item alike
     * @param label what follows its value: "FPS", "ms"
     * @param name what goes before its value when the player puts the label
     *     there: "FPS: 120", "Ping: 42 ms"
     * @param value what it shows, from the game; {@code null} when there is
     *     nothing to show just now
     * @param sample a value to show in Edit HUD when there is nothing to show,
     *     so that it can still be found and placed
     * @param onlyWhenRelevant whether it has times with nothing to show
     * @param styles the style options it takes
     */
    private HudItem(Feature feature, Position position, HudItemSettings settings, String label, String name,
            Function<GameView, String> value, String sample, boolean onlyWhenRelevant, Set<HudStyle> styles) {
        this.feature = feature;
        this.position = position;
        this.settings = settings;
        this.label = label;
        this.name = name;
        this.value = value;
        this.sample = sample;
        this.onlyWhenRelevant = onlyWhenRelevant;
        this.styles = Collections.unmodifiableSet(styles);
    }

    /** The feature whose switch shows it. */
    public Feature feature() {
        return feature;
    }

    /** Where it sits, a setting the player changes by dragging it in Edit HUD. */
    public Position position() {
        return position;
    }

    /** Its default anchor and offset: where it starts, and where Reset puts it back. */
    public Placement home() {
        return position.fallback();
    }

    /** What follows its value: "FPS", "ms". */
    public String label() {
        return label;
    }

    /** What it shows just now, without its label; {@code null} when there is nothing to show. */
    public String value(GameView game) {
        return value.apply(game);
    }

    /** What it shows in Edit HUD when {@link #value} has nothing. */
    public String sample() {
        return sample;
    }

    /** Its style's settings, the same group every item has. */
    public HudItemSettings settings() {
        return settings;
    }

    /** A value with its label, as it is drawn by default: "144 FPS". */
    public String text(String value) {
        return text(value, "after");
    }

    /**
     * A value with its label where the player put it: after, "144 FPS";
     * before, "FPS: 144" or "Ping: 42 ms"; or none, "144".
     */
    public String text(String value, String labelAt) {
        if ("none".equals(labelAt)) {
            return value;
        }
        if ("before".equals(labelAt)) {
            return name.equals(label) ? name + ": " + value : name + ": " + value + " " + label;
        }
        return value + " " + label;
    }

    /** Whether it has times with nothing to show, when it draws nothing - the ping in singleplayer. */
    public boolean onlyWhenRelevant() {
        return onlyWhenRelevant;
    }

    /** The style options it takes. */
    public Set<HudStyle> styles() {
        return styles;
    }

    @Override
    public String toString() {
        return feature.id();
    }
}
