package com.ashlauncher.client.ui;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.hit.HitIndicator;
import com.ashlauncher.client.hud.HudSurface;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Choice;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.Setting;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.settings.Whole;
import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Ink;
import com.ashlauncher.client.ui.draw.Paint;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.LongSupplier;

/**
 * A feature's page of options in ash's panel, in place of the tiles, as the
 * approved mockup has it (#67): a header with the way back, the feature's
 * icon and name and its ENABLED button; a rounded row per option - a row of
 * chips for one of a set, a slider for a whole number, a switch for on and
 * off, swatches and a code for a colour - and "Reset to defaults"; and a live
 * preview beside them where one helps.
 *
 * <p>Laid out in the panel's real pixels from its unit, a hundredth of the
 * screen's width, so it keeps its proportions at any size. Every frame
 * records where it drew each thing that can be clicked, and a click is
 * matched against that record, so what can be clicked is exactly what is on
 * screen. Rows scroll when there are more than fit.
 */
final class OptionsPage {

    /** The colours a swatch offers: ash's grayscale, then a few that stand out against most worlds. */
    static final int[] SWATCHES = {0xFAFAFA, 0x0E0E0F, 0x8B8C90, 0xFF4D4D, 0x4DFF88, 0x4DC3FF, 0xFFE14D, 0xFF4DE1};

    /** The preview's scenes, each a gradient from its top colour to its bottom, as the mockup paints them. */
    private static final String[] SCENES = {"Sky", "Snow", "Night"};
    private static final int[][] SCENE_COLOURS = {
        {0xFF7AA7F0, 0xFFB9D1F8}, {0xFFF2F5F8, 0xFFDFE6EE}, {0xFF0F1424, 0xFF1D2540}};

    /** The preview's grid, in game pixels across: the crosshair is drawn at the size it has in a 44-pixel-wide view. */
    private static final int PREVIEW_PIXELS_WIDE = 44;

    /** How often the hit indicator's preview shows a hit by itself, so it is never empty for long. */
    private static final long REPLAY_NANOS = 1_600_000_000L;

    /** A slider's track, the white fill's colour behind it: white at 16%. */
    private static final int TRACK = 0x29FFFFFF;
    /** A switch's off colour: the same faint white. */
    private static final int SWITCH_OFF = 0x29FFFFFF;
    /** A preview scene's name, over the scene: white at 85%. */
    private static final int SCENE_LABEL = 0xD9FFFFFF;
    /** Its shadow: black at 50%. */
    private static final int SCENE_SHADOW = 0x80000000;

    /** What a click on something does, given where in it the click landed. */
    private interface Action {
        void at(int x, int y);
    }

    /** Something drawn that can be clicked, and what clicking it does. */
    private static final class Target {
        final String id;
        final Rect rect;
        final Action action;

        Target(String id, Rect rect, Action action) {
            this.id = id;
            this.rect = rect;
            this.action = action;
        }
    }

    /** What one row of the page holds. */
    private enum Kind { CHOICE, WHOLE, COLOUR, OPACITY, FLAG }

    private static final class Row {
        final Kind kind;
        final Setting<?> setting;
        final String label;

        Row(Kind kind, Setting<?> setting, String label) {
            this.kind = kind;
            this.setting = setting;
            this.label = label;
        }
    }

    private final SettingsScreen model;
    private final SettingsScreen.Row feature;
    private final Runnable back;
    private final LongSupplier clock;
    private final Consumer<String> say;
    private final BooleanSupplier animated;
    /** When each switch on the page last changed, by its target, so it eases across (#66). */
    private final Map<String, Long> switchedAt = new HashMap<>();
    /** When the colour box last refused what was typed in it, so it shakes. */
    private long hexRefusedAt = Long.MIN_VALUE;
    private final List<Row> rows = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private final HitIndicator previewHit;
    private long lastHit = Long.MIN_VALUE;
    private Target dragging;
    private Colour editing;
    private String hexText = "";
    private int scroll;
    private int maxScroll;
    private float unit = 19.2f;

    /**
     * @param clock nanoseconds, only ever going forward: for the hit indicator's preview and for motion
     * @param say puts a message up over the panel
     * @param animated whether the page moves: its switches ease, a refused code shakes
     */
    OptionsPage(SettingsScreen model, SettingsScreen.Row feature, Runnable back, LongSupplier clock,
            Consumer<String> say, BooleanSupplier animated) {
        this.model = model;
        this.feature = feature;
        this.back = back;
        this.clock = clock;
        this.say = say;
        this.animated = animated;
        for (Setting<?> option : model.optionsOf(feature.feature())) {
            if (option instanceof Choice) {
                rows.add(new Row(Kind.CHOICE, option, option.label()));
            } else if (option instanceof Whole) {
                rows.add(new Row(Kind.WHOLE, option, option.label()));
            } else if (option instanceof Colour) {
                rows.add(new Row(Kind.COLOUR, option, option.label()));
                if (((Colour) option).withOpacity()) {
                    rows.add(new Row(Kind.OPACITY, option, "Opacity"));
                }
            } else if (option instanceof OnOff) {
                rows.add(new Row(Kind.FLAG, option, option.label()));
            }
        }
        // The preview shows a hit whatever the switch says: it is there to
        // show what a hit would look like.
        previewHit = new HitIndicator(() -> true, () -> model.value(Settings.HIT_INDICATOR_COLOUR),
                () -> model.value(Settings.HIT_INDICATOR_DURATION), () -> clock.getAsLong() / 1_000_000L);
    }

    Feature feature() {
        return feature.feature();
    }

    // ---- input, in real pixels ----

    /** A click. Typing in a colour's box ends when anything else is clicked; what was typed is already applied if it was a colour. */
    boolean click(int x, int y) {
        Target hit = targetAt(x, y);
        if (editing != null && (hit == null || !hit.id.equals("hex:" + editing.key()))) {
            finishTyping();
        }
        if (hit == null) {
            return false;
        }
        hit.action.at(x, y);
        if (hit.id.startsWith("slider:") || hit.id.startsWith("opacity:")) {
            dragging = hit;
        }
        return true;
    }

    void drag(int x, int y) {
        if (dragging != null) {
            dragging.action.at(x, y);
        }
    }

    void release() {
        dragging = null;
    }

    void scroll(double amount) {
        if (amount > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (amount < 0) {
            scroll = Math.min(maxScroll, scroll + 1);
        }
    }

    /** Whether a colour's box is taking typing, so keys and characters are the page's. */
    boolean typing() {
        return editing != null;
    }

    /**
     * A key while typing a colour: Backspace deletes, and Enter or Escape stop
     * typing. A whole colour has already been applied as it was typed.
     */
    boolean keyPressed(Key key) {
        if (editing == null) {
            return false;
        }
        if (key == Key.ENTER) {
            finishTyping();
        } else if (key == Key.ESCAPE) {
            // Escape is a change of mind: no complaint about what was half typed.
            editing = null;
        } else if (key == Key.BACKSPACE && !hexText.isEmpty()) {
            hexText = hexText.substring(0, hexText.length() - 1);
            applyWholeColour();
        }
        return true;
    }

    void charTyped(int codePoint) {
        boolean hexDigit = Character.digit(codePoint, 16) >= 0 && codePoint < 128;
        if (editing != null && (hexDigit || codePoint == '#') && hexText.length() < 7) {
            hexText += (char) codePoint;
            applyWholeColour();
        }
    }

    /**
     * Ends typing a colour. A whole colour has already been applied; anything
     * else is refused - the box shakes, and the panel says what a colour
     * looks like - and the colour stays as it was.
     */
    private void finishTyping() {
        if (wholeColour(hexText) < 0) {
            hexRefusedAt = clock.getAsLong();
            say.accept("That isn't a colour. Use six hex digits, like #FA3A2F.");
        }
        editing = null;
    }

    /** The colour {@code typed} is, as RGB, or -1 when it is not six hex digits with or without a {@code #}. */
    static int wholeColour(String typed) {
        String digits = typed.startsWith("#") ? typed.substring(1) : typed;
        if (digits.length() != 6) {
            return -1;
        }
        for (char c : digits.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return -1;
            }
        }
        return Integer.parseInt(digits, 16);
    }

    /**
     * Applies what has been typed the moment it is a whole colour - six hex
     * digits, with or without the {@code #} - keeping the opacity it had, so
     * the change shows as it is typed and nothing typed is lost to a click
     * elsewhere. Half a colour changes nothing.
     */
    private void applyWholeColour() {
        String digits = hexText.startsWith("#") ? hexText.substring(1) : hexText;
        if (digits.length() != 6) {
            return;
        }
        for (char c : digits.toCharArray()) {
            if (Character.digit(c, 16) < 0) {
                return;
            }
        }
        int wanted = (model.value(editing) & 0xFF000000) | Integer.parseInt(digits, 16);
        if (model.value(editing) != wanted) {
            model.change(editing, wanted);
        }
    }

    // ---- where things are, in real pixels ----

    Rect target(String id) {
        for (Target target : targets) {
            if (target.id.equals(id)) {
                return target.rect;
            }
        }
        return null;
    }

    private Target targetAt(int x, int y) {
        for (Target target : targets) {
            if (target.rect.contains(x, y)) {
                return target;
            }
        }
        return null;
    }

    /** The point along a slider's track that stands for {@code value}, as a one-pixel-wide rectangle. */
    Rect pointOn(String id, int value, int min, int max) {
        Rect track = target(id);
        if (track == null) {
            return null;
        }
        int x = track.x + (int) Math.round((value - min) * (track.width - 1) / (double) (max - min));
        return new Rect(x, track.y, 1, track.height);
    }

    private int units(double amount) {
        return Math.round((float) (amount * unit));
    }

    private float textSize(float units) {
        return units * unit;
    }

    // ---- drawing ----

    /**
     * Draws the page into {@code area}, the panel's main area, at {@code unit}
     * pixels to the panel's unit; the mouse, in real pixels, decides what is
     * highlighted.
     */
    void render(Canvas canvas, Rect area, float unit, int mouseX, int mouseY) {
        this.unit = unit;
        targets.clear();
        int headerBottom = drawHeader(canvas, area, mouseX, mouseY);

        boolean preview = hasPreview();
        int previewWidth = preview ? units(16) : 0;
        Rect list = new Rect(area.x, headerBottom, area.width - previewWidth - (preview ? units(1.6) : 0),
                area.y + area.height - headerBottom);
        if (rows.isEmpty()) {
            float size = textSize(0.92f);
            Ink.text(feature.name() + " has no options yet.", Ink.Weight.REGULAR, size, Palette.MUTED)
                    .drawAt(canvas, list.x + units(0.2), list.y + units(1.2), 1f);
        } else {
            drawRows(canvas, list, mouseX, mouseY);
        }
        if (preview) {
            drawPreview(canvas, new Rect(area.x + area.width - previewWidth, headerBottom, previewWidth,
                    area.y + area.height - headerBottom), mouseX, mouseY);
        }
    }

    private boolean hasPreview() {
        return feature.feature() == Feature.CROSSHAIR || feature.feature() == Feature.HIT_INDICATOR;
    }

    /** Back, the feature's icon and name, and its ENABLED button. Returns where the rows start. */
    private int drawHeader(Canvas canvas, Rect area, int mouseX, int mouseY) {
        int height = units(2.4);
        Rect backButton = new Rect(area.x, area.y, height, height);
        Paint.roundRect(canvas, backButton.x, backButton.y, backButton.width, backButton.height, height / 2,
                backButton.contains(mouseX, mouseY) ? Palette.RAISED_HOVER : Palette.RAISED, 1f);
        int arrow = units(1.2);
        canvas.draw(Ink.icon(Ink.Icon.BACK, arrow, Palette.ICON), backButton.x + (height - arrow) / 2,
                backButton.y + (height - arrow) / 2, 1f);
        targets.add(new Target("back", backButton, (x, y) -> back.run()));

        int x = backButton.x + backButton.width + units(0.8);
        Ink.Icon icon = Panel.iconOf(feature.feature());
        if (icon != null) {
            int glyph = units(1.8);
            canvas.draw(Ink.icon(icon, glyph, Palette.ICON), x, area.y + (height - glyph) / 2, 1f);
            x += glyph + units(0.8);
        }

        Rect toggle = new Rect(area.x + area.width - units(8.5), area.y + (height - units(2.3)) / 2, units(8.5),
                units(2.3));
        float nameSize = textSize(1.25f);
        String name = Panel.fit(feature.name(), Ink.Weight.BOLD, nameSize, toggle.x - units(0.8) - x);
        Ink.text(name, Ink.Weight.BOLD, nameSize, Palette.TEXT)
                .drawAt(canvas, x, area.y + (height - Ink.lineHeight(Ink.Weight.BOLD, nameSize)) / 2, 1f);

        String switchId = "switch:" + feature.feature().id();
        Panel.drawToggle(canvas, toggle, feature.available(), feature.on(), eased(switchId, feature.on(), true),
                units(0.55), textSize(0.74f), 1f);
        targets.add(new Target(switchId, toggle, (cx, cy) -> {
            feature.press();
            switchedAt.put(switchId, clock.getAsLong());
        }));
        return area.y + height + units(1.2);
    }

    private void drawRows(Canvas canvas, Rect list, int mouseX, int mouseY) {
        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            if (model.settings().applies(row.setting)) {
                shown.add(row);
            }
        }
        int rowHeight = units(3);
        int gap = units(0.35);
        int resetHeight = units(2.3);
        int capacity = Math.max(1, (list.height - resetHeight - units(0.6) + gap) / (rowHeight + gap));
        maxScroll = Math.max(0, shown.size() - capacity);
        scroll = Math.min(scroll, maxScroll);
        int width = list.width - (maxScroll > 0 ? units(1) : 0);

        int y = list.y;
        for (int i = scroll; i < shown.size() && i < scroll + capacity; i++) {
            Rect row = new Rect(list.x, y, width, rowHeight);
            drawRow(canvas, shown.get(i), row, mouseX, mouseY);
            y += rowHeight + gap;
        }
        if (maxScroll > 0) {
            int height = capacity * (rowHeight + gap) - gap;
            int thumb = Math.max(units(2), height * capacity / shown.size());
            int barX = list.x + list.width - units(0.3);
            Paint.roundRect(canvas, barX, list.y, units(0.3), height, units(0.15), Palette.RAISED, 1f);
            Paint.roundRect(canvas, barX, list.y + (height - thumb) * scroll / maxScroll, units(0.3), thumb,
                    units(0.15), Palette.MUTED, 1f);
        }

        // Reset to defaults: a quiet link under the rows.
        String text = "Reset to defaults";
        float size = textSize(0.92f);
        int padX = units(0.9);
        Rect link = new Rect(list.x, y + units(0.6) - gap,
                Ink.width(text, Ink.Weight.SEMIBOLD, size) + 2 * padX, resetHeight);
        boolean over = link.contains(mouseX, mouseY);
        if (over) {
            Paint.roundRect(canvas, link.x, link.y, link.width, link.height, units(0.55), Palette.RAISED, 1f);
        }
        Ink.text(text, Ink.Weight.SEMIBOLD, size, over ? Palette.TEXT : Palette.MUTED).drawAt(canvas, link.x + padX,
                link.y + (link.height - Ink.lineHeight(Ink.Weight.SEMIBOLD, size)) / 2, 1f);
        targets.add(new Target("reset", link, (cx, cy) -> model.resetToDefaults(feature.feature())));
    }

    /** One option: a rounded row, its name on the left in a column of its own, its control after it. */
    private void drawRow(Canvas canvas, Row row, Rect at, int mouseX, int mouseY) {
        Paint.roundRect(canvas, at.x, at.y, at.width, at.height, units(0.7), Palette.RAISED, 1f);
        float size = textSize(0.92f);
        int padX = units(0.9);
        Ink.text(Panel.fit(row.label, Ink.Weight.REGULAR, size, units(9.5)), Ink.Weight.REGULAR, size, Palette.MUTED)
                .drawAt(canvas, at.x + padX, at.y + (at.height - Ink.lineHeight(Ink.Weight.REGULAR, size)) / 2, 1f);
        int controlX = at.x + padX + units(9.5) + units(1);
        Rect control = new Rect(controlX, at.y, at.x + at.width - padX - controlX, at.height);

        switch (row.kind) {
            case CHOICE:
                drawChips(canvas, (Choice) row.setting, control, mouseX, mouseY);
                break;
            case WHOLE: {
                Whole whole = (Whole) row.setting;
                String unitText = whole.unit().isEmpty() ? "" : " " + whole.unit();
                drawSlider(canvas, "slider:" + whole.key(), control, model.value(whole), whole.min(), whole.max(),
                        whole.step(), unitText, value -> {
                            // Only when it moves: a drag along one value is not a save per frame.
                            if (model.value(whole) != value) {
                                model.change(whole, value);
                            }
                        });
                break;
            }
            case COLOUR:
                drawColour(canvas, (Colour) row.setting, control);
                break;
            case OPACITY: {
                Colour colour = (Colour) row.setting;
                // Rounded both ways, so the faintest colour reads as the
                // slider's lowest step and that step sets exactly it.
                int minPercent = (int) Math.round(Colour.MIN_ALPHA * 100 / 255.0);
                int percent = Math.max(minPercent, (int) Math.round((model.value(colour) >>> 24) * 100 / 255.0));
                drawSlider(canvas, "opacity:" + colour.key(), control, percent, minPercent, 100, 1, "%", value -> {
                    int alpha = Math.max(Colour.MIN_ALPHA, (int) Math.round(value * 255 / 100.0));
                    int wanted = (alpha << 24) | (model.value(colour) & 0xFFFFFF);
                    if (model.value(colour) != wanted) {
                        model.change(colour, wanted);
                    }
                });
                break;
            }
            case FLAG: {
                OnOff flag = (OnOff) row.setting;
                Rect toggle = new Rect(control.x, control.y + (control.height - units(1.7)) / 2, units(3.1), units(1.7));
                String flagId = "flag:" + flag.key();
                drawSwitch(canvas, toggle, eased(flagId, model.value(flag), false));
                targets.add(new Target(flagId, toggle, (x, y) -> {
                    model.change(flag, !model.value(flag));
                    switchedAt.put(flagId, clock.getAsLong());
                }));
                break;
            }
            default:
                break;
        }
    }

    /** One of a set: a row of chips, the chosen one white with dark text. */
    private void drawChips(Canvas canvas, Choice choice, Rect area, int mouseX, int mouseY) {
        float size = textSize(0.92f);
        int lineHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, size);
        int height = lineHeight + 2 * units(0.45);
        int x = area.x;
        String current = model.value(choice);
        for (Choice.Option option : choice.options()) {
            int width = Ink.width(option.label(), Ink.Weight.SEMIBOLD, size) + 2 * units(0.9);
            Rect chip = new Rect(x, area.y + (area.height - height) / 2, width, height);
            boolean chosen = option.id().equals(current);
            Paint.roundRect(canvas, chip.x, chip.y, chip.width, chip.height, units(0.55),
                    chosen ? Palette.TEXT : Palette.RAISED, 1f);
            int colour = chosen ? Palette.PRIMARY_TEXT : chip.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
            Ink.text(option.label(), Ink.Weight.SEMIBOLD, size, colour)
                    .drawAt(canvas, chip.x + units(0.9), chip.y + (chip.height - lineHeight) / 2, 1f);
            targets.add(new Target("choice:" + choice.key() + ":" + option.id(), chip,
                    (cx, cy) -> model.change(choice, option.id())));
            x += width + units(0.35);
        }
    }

    /** What a slider does with the value under the mouse. */
    private interface Slide {
        void to(int value);
    }

    /** A track with a white fill up to a round knob, and the value with its unit after it. */
    private void drawSlider(Canvas canvas, String id, Rect area, int value, int min, int max, int step,
            String unitText, Slide slide) {
        float size = textSize(0.92f);
        int valueWidth = Math.max(units(4.6), Ink.width(max + unitText, Ink.Weight.SEMIBOLD, size));
        int trackHeight = units(1.6);
        Rect track = new Rect(area.x, area.y + (area.height - trackHeight) / 2,
                Math.max(units(2), area.width - valueWidth - units(0.9)), trackHeight);
        int line = Math.max(1, units(0.3));
        int lineY = track.centreY() - line / 2;
        int knobX = track.x + (int) Math.round((value - min) * (track.width - 1) / (double) (max - min));
        Paint.roundRect(canvas, track.x, lineY, track.width, line, line / 2, TRACK, 1f);
        Paint.roundRect(canvas, track.x, lineY, Math.max(line, knobX - track.x), line, line / 2, Palette.TEXT, 1f);
        int knob = units(1.2);
        Paint.roundRect(canvas, knobX - knob / 2, track.centreY() - knob / 2, knob, knob, knob / 2, Palette.TEXT, 1f);

        String shown = value + unitText;
        int shownWidth = Ink.width(shown, Ink.Weight.SEMIBOLD, size);
        Ink.text(shown, Ink.Weight.SEMIBOLD, size, Palette.TEXT).drawAt(canvas, area.x + area.width - shownWidth,
                area.y + (area.height - Ink.lineHeight(Ink.Weight.SEMIBOLD, size)) / 2, 1f);
        targets.add(new Target(id, track, (x, y) -> {
            // The nearest step to the point under the mouse, inside the range.
            double along = (x - track.x) * (max - min) / (double) Math.max(1, track.width - 1);
            int under = min + (int) Math.round(along / step) * step;
            slide.to(Math.max(min, Math.min(max, under)));
        }));
    }

    /** A colour's swatches, the chosen one ringed, then its code in a box that takes typing. */
    private void drawColour(Canvas canvas, Colour colour, Rect area) {
        int side = units(1.6);
        int current = model.value(colour);
        int x = area.x;
        int y = area.y + (area.height - side) / 2;
        for (int rgb : SWATCHES) {
            if ((current & 0xFFFFFF) == rgb) {
                int ring = Math.max(2, units(0.18));
                Paint.outline(canvas, x - ring, y - ring, side + 2 * ring, side + 2 * ring, units(0.45) + ring,
                        Palette.TEXT);
            }
            Paint.roundRect(canvas, x, y, side, side, units(0.45), 0xFF000000 | rgb, 1f);
            Rect swatch = new Rect(x, y, side, side);
            targets.add(new Target("swatch:" + colour.key() + ":" + hex(rgb), swatch,
                    (cx, cy) -> model.change(colour, (model.value(colour) & 0xFF000000) | rgb)));
            x += side + units(0.4);
        }

        boolean typing = editing == colour;
        float size = textSize(0.92f);
        // Refused, it shakes; the place it is clicked is where it settles.
        long sinceRefused = hexRefusedAt == Long.MIN_VALUE ? Long.MAX_VALUE : clock.getAsLong() - hexRefusedAt;
        int shake = animated.getAsBoolean() ? units(Motion.shake(sinceRefused)) : 0;
        Rect box = new Rect(x + units(0.5) + shake, area.y + (area.height - units(2.2)) / 2, units(8.5), units(2.2));
        Paint.roundRect(canvas, box.x, box.y, box.width, box.height, units(0.55), Palette.RAISED_HOVER, 1f);
        if (typing) {
            Paint.outline(canvas, box.x, box.y, box.width, box.height, units(0.55), Palette.FOCUS);
        }
        String text = typing ? hexText : "#" + hex(current & 0xFFFFFF);
        int lineHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, size);
        int textY = box.y + (box.height - lineHeight) / 2;
        Ink.text(text, Ink.Weight.SEMIBOLD, size, Palette.TEXT).drawAt(canvas, box.x + units(0.7), textY, 1f);
        if (typing) {
            int caretX = box.x + units(0.7) + Ink.width(text, Ink.Weight.SEMIBOLD, size) + Math.max(1, units(0.08));
            canvas.fill(caretX, textY + lineHeight / 8, Math.max(1, units(0.08)), lineHeight * 3 / 4, Palette.TEXT);
        }
        targets.add(new Target("hex:" + colour.key(), box, (cx, cy) -> {
            if (editing != colour) {
                editing = colour;
                hexText = "#" + hex(model.value(colour) & 0xFFFFFF);
            }
        }));
    }

    /** On and off: a pill, green when on, its knob at the end it is set to. */
    /**
     * How far a switch on the page is towards on just now: eased since its
     * last press (#66) - an ENABLED button's colour over its own time, a
     * switch's knob over its.
     */
    private float eased(String id, boolean on, boolean button) {
        Long pressed = switchedAt.get(id);
        if (!animated.getAsBoolean() || pressed == null) {
            return on ? 1f : 0f;
        }
        long elapsed = clock.getAsLong() - pressed;
        return button ? Motion.toggle(on, elapsed) : Motion.switchPosition(on, elapsed);
    }

    /** On and off: a pill, green when on, its knob {@code position} of the way across: 0 off, 1 on. */
    private void drawSwitch(Canvas canvas, Rect at, float position) {
        Paint.roundRect(canvas, at.x, at.y, at.width, at.height, at.height / 2,
                Panel.blend(SWITCH_OFF, Palette.GREEN, position), 1f);
        int inset = units(0.2);
        int knob = at.height - 2 * inset;
        int knobX = at.x + inset + Math.round((at.width - 2 * inset - knob) * position);
        Paint.roundRect(canvas, knobX, at.y + inset, knob, knob, knob / 2, Palette.TEXT, 1f);
    }

    /**
     * The crosshair as set, over sky, snow and night, at the size it has in a
     * view 44 game pixels across - and on the hit indicator's page, a hit
     * around it every so often, and on "Test a hit".
     */
    private void drawPreview(Canvas canvas, Rect column, int mouseX, int mouseY) {
        boolean hits = feature.feature() == Feature.HIT_INDICATOR;
        long now = clock.getAsLong();
        if (hits && (lastHit == Long.MIN_VALUE || now - lastHit >= REPLAY_NANOS)) {
            showHit(now);
        }

        float labelSize = textSize(0.7f);
        Ink.text("PREVIEW", Ink.Weight.SEMIBOLD, labelSize, Palette.PLACEHOLDER, 0.06f)
                .drawAt(canvas, column.x, column.y, 1f);
        int y = column.y + Ink.lineHeight(Ink.Weight.SEMIBOLD, labelSize) + units(0.3);
        int height = units(7.6);
        int scale = Math.max(1, Math.round(column.width / (float) PREVIEW_PIXELS_WIDE));
        Cross cross = Cross.of(model.settings());
        for (int i = 0; i < SCENES.length; i++) {
            Rect scene = new Rect(column.x, y, column.width, height);
            scene(canvas, scene, units(0.7), SCENE_COLOURS[i][0], SCENE_COLOURS[i][1]);
            // Not clickable: recorded so a test can find what it draws.
            targets.add(new Target("preview:" + SCENES[i], scene, (cx, cy) -> { }));
            int centreX = scene.centreX() - scale / 2;
            int centreY = scene.centreY() - scale / 2;
            cross.drawOnto(canvas::fill, centreX, centreY, scale);
            if (hits) {
                previewHit.draw(new PreviewSurface(canvas, centreX, centreY, scale), 0, 0);
            }
            // White with a soft dark shadow, so it reads on snow as on night.
            float nameSize = textSize(0.68f);
            int nameX = scene.x + units(0.6);
            int nameY = scene.y + scene.height - units(0.45) - Ink.lineHeight(Ink.Weight.SEMIBOLD, nameSize);
            int drop = Math.max(1, units(0.06));
            Ink.text(SCENES[i], Ink.Weight.SEMIBOLD, nameSize, SCENE_SHADOW).drawAt(canvas, nameX, nameY + drop, 1f);
            Ink.text(SCENES[i], Ink.Weight.SEMIBOLD, nameSize, SCENE_LABEL).drawAt(canvas, nameX, nameY, 1f);
            y += height + units(0.6);
        }

        if (hits) {
            String text = "Test a hit";
            float size = textSize(0.92f);
            int lineHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, size);
            Rect button = new Rect(column.x, y, Ink.width(text, Ink.Weight.SEMIBOLD, size) + 2 * units(0.9),
                    lineHeight + 2 * units(0.55));
            Paint.roundRect(canvas, button.x, button.y, button.width, button.height, units(0.55),
                    button.contains(mouseX, mouseY) ? Palette.RAISED_STRONG : Palette.RAISED_HOVER, 1f);
            Ink.text(text, Ink.Weight.SEMIBOLD, size, Palette.TEXT).drawAt(canvas, button.x + units(0.9),
                    button.y + units(0.55), 1f);
            targets.add(new Target("testhit", button, (cx, cy) -> showHit(clock.getAsLong())));
        }
    }

    private void showHit(long now) {
        previewHit.confirmed();
        lastHit = now;
    }

    /**
     * A rounded rectangle filled top to bottom from one colour to another:
     * a row of pixels at a time, each row inset where the corners round it.
     */
    private static void scene(Canvas canvas, Rect at, int radius, int top, int bottom) {
        for (int row = 0; row < at.height; row++) {
            float t = at.height <= 1 ? 0 : row / (float) (at.height - 1);
            int inset = 0;
            int fromEdge = Math.min(row, at.height - 1 - row);
            if (fromEdge < radius) {
                double dy = radius - fromEdge - 0.5;
                inset = (int) Math.round(radius - Math.sqrt(Math.max(0, radius * (double) radius - dy * dy)));
            }
            canvas.fill(at.x + inset, at.y + row, at.width - 2 * inset, 1, mix(top, bottom, t));
        }
    }

    private static int mix(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /**
     * The preview, as a HUD: so the hit indicator draws itself there exactly
     * as it does in game, each of its pixels a {@code scale}-wide square.
     */
    private static final class PreviewSurface implements HudSurface {
        private final Canvas canvas;
        private final int originX;
        private final int originY;
        private final int scale;

        PreviewSurface(Canvas canvas, int originX, int originY, int scale) {
            this.canvas = canvas;
            this.originX = originX;
            this.originY = originY;
            this.scale = scale;
        }

        @Override
        public int width() {
            return canvas.width() / scale;
        }

        @Override
        public int height() {
            return canvas.height() / scale;
        }

        @Override
        public int textWidth(String text) {
            return 0;
        }

        @Override
        public int lineHeight() {
            return 9;
        }

        @Override
        public void drawText(String text, int x, int y, int colour) {
        }

        @Override
        public void fill(int x, int y, int width, int height, int colour) {
            canvas.fill(originX + x * scale, originY + y * scale, width * scale, height * scale, colour);
        }

        @Override
        public boolean debugScreenShown() {
            return false;
        }

        @Override
        public boolean hudHidden() {
            return false;
        }
    }

    static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }
}
