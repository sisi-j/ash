package com.ashlauncher.client.settings;

import com.ashlauncher.client.report.Feature;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * The settings every HUD item takes, declared once as a group and made once
 * for each item (spec 0007, HUD items): its style. They are the item's
 * feature's own options, so its options page shows them with no screen code
 * of the item's, and their keys start with the feature's id:
 * {@code fps-readout.size}.
 *
 * <p>Their defaults are how the readouts have always looked: full size,
 * white, shadowed, no box, no brackets, the label after the number.
 */
public final class HudItemSettings {

    /** 50% to 200%, in steps of 10. */
    public final Whole size;
    /** Its text's colour, kept for when chroma is off. */
    public final Colour textColour;
    /** Its text cycling through the rainbow, in place of its colour. */
    public final OnOff chroma;
    public final OnOff background;
    /** The box's colour and opacity. */
    public final Colour backgroundColour;
    public final OnOff shadow;
    /** Brackets round it: {@code [120 FPS]}. */
    public final OnOff brackets;
    /** Where its label goes: {@code after}, {@code before} or {@code none}. */
    public final Choice label;

    private final List<Setting<?>> all;

    /**
     * @param what what the item is, for the settings file's comments: "the
     *     frame rate"
     * @param after how it reads with its label after, before, and with none,
     *     for the label's comment: "120 FPS", "FPS: 120" and "120"
     */
    HudItemSettings(Feature feature, String what, String after, String before, String none) {
        String prefix = feature.id() + ".";
        size = new Whole(feature, prefix + "size", "Size", 100, 50, 200, 10, "%",
                "How large " + what + " draws, in per cent: a whole number from 50 to 200, in steps of 10.");
        textColour = new Colour(feature, prefix + "text-colour", "Text colour", 0xFFFFFFFF, false,
                "The colour of " + what + ", as #RRGGBB; kept for when chroma is off.");
        chroma = new OnOff(feature, prefix + "chroma", "Chroma", false,
                "Cycle " + what + " through the colours of the rainbow, in place of its text colour. true or false.");
        background = new OnOff(feature, prefix + "background", "Background", false,
                "A box behind " + what + ", in its background colour. true or false.");
        backgroundColour = new Colour(feature, prefix + "background-colour", "Background colour", 0x80000000,
                "The colour and opacity of the box behind " + what + ", as #RRGGBBAA. #00000080 is half-clear black.");
        shadow = new OnOff(feature, prefix + "shadow", "Text shadow", true,
                "Draw " + what + " with the game's text shadow. true or false.");
        brackets = new OnOff(feature, prefix + "brackets", "Brackets", false,
                "Brackets round " + what + ": [" + after + "]. true or false.");
        label = new Choice(feature, prefix + "label", "Label", "after",
                "Where the label goes: after (" + after + "), before (" + before + ") or none (" + none + ").",
                "after", "After", "before", "Before", "none", "None");
        all = Collections.unmodifiableList(Arrays.<Setting<?>>asList(size, textColour, chroma, background,
                backgroundColour, shadow, brackets, label));
    }

    /** Every one, in the order the file lists them and the options page shows them. */
    public List<Setting<?>> all() {
        return all;
    }

    /** Whether an option does anything as the rest are set: no box, no box colour; chroma, no text colour. */
    boolean applies(Setting<?> option, Settings settings) {
        if (option == backgroundColour) {
            return settings.get(background);
        }
        if (option == textColour) {
            return !settings.get(chroma);
        }
        return true;
    }
}
