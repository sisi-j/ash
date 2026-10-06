package com.ashlauncher.client.hud;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Position;
import com.ashlauncher.client.settings.Settings;

/**
 * Where the readouts are, for the readouts drawing themselves and for the
 * panel's Edit HUD mode moving them - one answer for both, so the box the
 * player drags is where the readout draws.
 *
 * <p>Positions come from the settings, except while a readout is being
 * dragged: then it is wherever the drag has it, and the setting is changed
 * once, when it is dropped. Each readout's size is as it last drew, which is
 * the one thing only the game's font can say.
 *
 * <p>Made once per session by each target and shared between its readouts
 * and its settings screen. Read and written on the render thread only.
 */
public final class HudLayout {

    /** A readout that can be moved. */
    public enum Readout {
        FPS(Feature.FPS_READOUT, Settings.FPS_READOUT_POSITION),
        PING(Feature.PING_READOUT, Settings.PING_READOUT_POSITION);

        private final Feature feature;
        private final Position position;

        Readout(Feature feature, Position position) {
            this.feature = feature;
            this.position = position;
        }

        public Feature feature() {
            return feature;
        }

        /** Its setting, which a drop changes. */
        public Position position() {
            return position;
        }
    }

    private final Settings settings;
    private final int[] widths = new int[Readout.values().length];
    private final int[] heights = new int[Readout.values().length];
    private Readout dragged;
    private Placement dragPlacement;
    private boolean editing;

    public HudLayout(Settings settings) {
        this.settings = settings;
    }

    /** Whether the player has a readout switched on. */
    public boolean shown(Readout readout) {
        return settings.on(readout.feature);
    }

    /**
     * Where a readout sits now. A ping left at its default sits beneath the
     * frame rate at its own default - and in the frame rate's place whenever
     * the frame rate is not there, switched off or moved away - as it did
     * before readouts could be moved.
     */
    public Placement placement(Readout readout) {
        if (readout == dragged) {
            return dragPlacement;
        }
        Placement placement = settings.get(readout.position);
        if (readout == Readout.PING && placement.equals(Readout.PING.position.fallback())) {
            boolean fpsInPlace = shown(Readout.FPS)
                    && placement(Readout.FPS).equals(Readout.FPS.position.fallback());
            if (!fpsInPlace) {
                return Readout.FPS.position.fallback();
            }
        }
        return placement;
    }

    /**
     * A readout's top-left on a screen of this size, for one {@code width} by
     * {@code height} - and remembered as its size, for Edit HUD's box.
     */
    public int[] place(Readout readout, int width, int height, int screenWidth, int screenHeight) {
        widths[readout.ordinal()] = width;
        heights[readout.ordinal()] = height;
        Placement placement = placement(readout);
        return new int[] {placement.x(width, screenWidth), placement.y(height, screenHeight)};
    }

    /** How wide a readout last drew, in GUI units; 0 before it has. */
    public int width(Readout readout) {
        return widths[readout.ordinal()];
    }

    /** How tall a readout last drew, in GUI units; 0 before it has. */
    public int height(Readout readout) {
        return heights[readout.ordinal()];
    }

    /** Where a readout is mid-drag: drawn there, and not yet saved. */
    public void drag(Readout readout, Placement placement) {
        dragged = readout;
        dragPlacement = placement;
    }

    /** The drag is over, and the readout goes back to its setting - which the drop has just changed. */
    public void endDrag() {
        dragged = null;
        dragPlacement = null;
    }

    /**
     * Whether Edit HUD is open. While it is, a readout with nothing to say -
     * the ping in singleplayer - still draws, so that it can be found and
     * moved.
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
