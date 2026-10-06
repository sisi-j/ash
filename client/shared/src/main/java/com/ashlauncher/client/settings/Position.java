package com.ashlauncher.client.settings;

import com.ashlauncher.client.hud.Placement;
import com.ashlauncher.client.report.Feature;

/**
 * Where a readout sits on screen: an anchor, then how far from it in GUI
 * units, as {@code top-left 4 4}. Set by dragging the readout in the panel's
 * Edit HUD mode, not on an options page.
 */
public final class Position extends Setting<Placement> {

    Position(Feature feature, String key, String label, Placement fallback, String comment) {
        super(feature, key, label, fallback, comment);
    }

    @Override
    Placement parse(String raw) {
        return Placement.parse(raw);
    }

    @Override
    String format(Placement value) {
        return value.format();
    }

    @Override
    String expected() {
        return "an anchor - top-left, top, top-right, left, centre, right, bottom-left, bottom or bottom-right -"
                + " then two whole numbers, as in top-left 4 4";
    }
}
