package com.ashlauncher.client.hud;

import com.ashlauncher.client.settings.Settings;
import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Where the HUD items are, for the items drawing themselves and for the
 * panel's Edit HUD mode moving them - one answer for both, so the box the
 * player drags is where the item draws.
 *
 * <p>An item the player has moved is exactly where they put it. One left at
 * its home is packed into its column: the items whose homes share its anchor
 * and its distance across stack from the column's start, in the registry's
 * order, leaving out any switched off or moved away. So the ping sits beneath
 * the frame rate, and takes its place when the frame rate is off or moved, as
 * it always has. One that would then overlap an item the player has placed
 * moves on down its column, past it, to the first free spot (spec 0007,
 * Default placement).
 *
 * <p>Positions come from the settings, except while an item is being
 * dragged: then it is wherever the drag has it, and the setting is changed
 * once, when it is dropped. Each item's size is as it last drew, which is
 * the one thing only the game's font can say.
 *
 * <p>Made once per session by each target and shared between its items and
 * its settings screen. Read and written on the render thread only.
 */
public final class HudLayout {

    /** The gap between two items stacked in a column, in GUI units: the ping's 15 is 4, a line of 9, and this. */
    static final int GAP = 2;

    private final Settings settings;
    /** Each item's width and height, as it last drew. */
    private final Map<HudItem, int[]> sizes = new IdentityHashMap<>();
    private int screenWidth;
    private int screenHeight;
    private HudItem dragged;
    private Placement dragPlacement;
    private boolean editing;

    public HudLayout(Settings settings) {
        this.settings = settings;
    }

    /** Whether the player has an item switched on. */
    public boolean shown(HudItem item) {
        return settings.on(item.feature());
    }

    /** Where an item sits now. */
    public Placement placement(HudItem item) {
        if (item == dragged) {
            return dragPlacement;
        }
        Placement saved = settings.get(item.position());
        return saved.equals(item.home()) ? packed(item) : saved;
    }

    /** Whether an item is left at its home, to be packed, rather than placed by the player. */
    private boolean atHome(HudItem item) {
        return item != dragged && settings.get(item.position()).equals(item.home());
    }

    private static boolean sameColumn(Placement a, Placement b) {
        return a.anchor() == b.anchor() && a.dx() == b.dx();
    }

    /** Where an item left at home goes: stacked in its column, then clear of what the player has placed. */
    private Placement packed(HudItem item) {
        Placement home = item.home();
        int start = home.dy();
        for (HudItem other : HudItem.ALL) {
            if (sameColumn(other.home(), home)) {
                start = Math.min(start, other.home().dy());
            }
        }
        int dy = start;
        HudItem previous = null;
        for (HudItem other : HudItem.ALL) {
            if (other == item) {
                break;
            }
            if (shown(other) && atHome(other) && sameColumn(other.home(), home)) {
                if (previous != null) {
                    dy += step(previous, other);
                }
                previous = other;
            }
        }
        if (previous != null) {
            dy += step(previous, item);
        }
        return clear(item, new Placement(home.anchor(), home.dx(), dy));
    }

    /**
     * How far down its column {@code below} sits from {@code above}: past what
     * {@code above} last drew, and never closer than their homes are, so the
     * first frame - before anything has drawn to say how big it is - is
     * already right.
     */
    private int step(HudItem above, HudItem below) {
        return Math.max(height(above), below.home().dy() - above.home().dy() - GAP) + GAP;
    }

    /**
     * {@code at}, or further down its column, until it overlaps nothing the
     * player has placed. Items at home are clear of each other by how their
     * homes are declared, so only placed ones need asking - which also means
     * no item's place depends on one that depends on it.
     */
    private Placement clear(HudItem item, Placement at) {
        int[] size = sizes.get(item);
        if (size == null || screenWidth == 0) {
            return at;
        }
        for (int tries = 0; tries < HudItem.ALL.size(); tries++) {
            Placement past = null;
            int x = at.x(size[0], screenWidth);
            int y = at.y(size[1], screenHeight);
            for (HudItem other : HudItem.ALL) {
                int[] otherSize = sizes.get(other);
                if (other == item || otherSize == null || !shown(other) || atHome(other)) {
                    continue;
                }
                Placement placed = placement(other);
                int ox = placed.x(otherSize[0], screenWidth);
                int oy = placed.y(otherSize[1], screenHeight);
                boolean overlaps = x < ox + otherSize[0] && ox < x + size[0] && y < oy + otherSize[1]
                        && oy < y + size[1];
                if (overlaps) {
                    past = below(at, size[1], oy, otherSize[1]);
                    break;
                }
            }
            if (past == null) {
                return at;
            }
            at = past;
        }
        return at;
    }

    /**
     * {@code at} moved on down its column, away from its anchor, just past an
     * item {@code otherHeight} tall whose top is at {@code otherY}: below it
     * for an item anchored at the top or the middle, above it for one at the
     * bottom.
     */
    private Placement below(Placement at, int height, int otherY, int otherHeight) {
        int dy;
        switch (at.anchor().row) {
            case 0:
                dy = otherY + otherHeight + GAP;
                break;
            case 1:
                dy = otherY + otherHeight + GAP - (screenHeight - height) / 2;
                break;
            default:
                dy = screenHeight - otherY + GAP;
                break;
        }
        return new Placement(at.anchor(), at.dx(), dy);
    }

    /**
     * An item's top-left on a screen of this size, for one {@code width} by
     * {@code height} - and remembered as its size, for packing and Edit HUD's
     * box.
     */
    public int[] place(HudItem item, int width, int height, int screenWidth, int screenHeight) {
        sizes.put(item, new int[] {width, height});
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        Placement placement = placement(item);
        return new int[] {placement.x(width, screenWidth), placement.y(height, screenHeight)};
    }

    /** How wide an item last drew, in GUI units; 0 before it has. */
    public int width(HudItem item) {
        int[] size = sizes.get(item);
        return size == null ? 0 : size[0];
    }

    /** How tall an item last drew, in GUI units; 0 before it has. */
    public int height(HudItem item) {
        int[] size = sizes.get(item);
        return size == null ? 0 : size[1];
    }

    /** Where an item is mid-drag: drawn there, and not yet saved. */
    public void drag(HudItem item, Placement placement) {
        dragged = item;
        dragPlacement = placement;
    }

    /** The drag is over, and the item goes back to its setting - which the drop has just changed. */
    public void endDrag() {
        dragged = null;
        dragPlacement = null;
    }

    /**
     * Whether Edit HUD is open. While it is, an item with nothing to say - the
     * ping in singleplayer - still draws its sample, so that it can be found
     * and moved.
     */
    public boolean editing() {
        return editing;
    }

    public void setEditing(boolean editing) {
        this.editing = editing;
        if (!editing) {
            endDrag();
        }
    }
}
