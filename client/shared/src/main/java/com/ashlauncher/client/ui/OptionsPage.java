package com.ashlauncher.client.ui;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Choice;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.Setting;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.settings.Whole;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * A feature's page of options in ash's panel, in place of the cards: the
 * feature and its switch at the top, then one row per option - a choice, a
 * slider, a colour - and "Reset to defaults", with a live preview beside them
 * where one helps.
 *
 * <p>Every frame records where it drew each thing that can be clicked, and
 * a click is matched against that record, so what can be clicked is exactly
 * what is on screen. Rows scroll when there are more than fit.
 */
final class OptionsPage {

    /** The colours a swatch offers: ash's grayscale, then a few that stand out against most worlds. */
    static final int[] SWATCHES = {0xFAFAFA, 0x0E0E0F, 0x8B8C90, 0xFF4D4D, 0x4DFF88, 0x4DC3FF, 0xFFE14D, 0xFF4DE1};

    private static final int[] PREVIEW_GROUNDS = {0xFF8DB3F6, 0xFFF1F3F6, 0xFF16192A};
    private static final String[] PREVIEW_NAMES = {"Sky", "Snow", "Night"};
    private static final int SWITCH_WIDTH = 18;
    private static final int SWITCH_HEIGHT = 10;

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
    private enum Kind { CHOICE, WHOLE, SWATCHES, HEX, OPACITY, FLAG, RESET }

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
    private final List<Row> rows = new ArrayList<>();
    private final List<Target> targets = new ArrayList<>();
    private Target dragging;
    private Colour editing;
    private String hexText = "";
    private int scroll;
    private int maxScroll;

    OptionsPage(SettingsScreen model, SettingsScreen.Row feature, Runnable back) {
        this.model = model;
        this.feature = feature;
        this.back = back;
        for (Setting<?> option : model.optionsOf(feature.feature())) {
            if (option instanceof Choice) {
                rows.add(new Row(Kind.CHOICE, option, option.label()));
            } else if (option instanceof Whole) {
                rows.add(new Row(Kind.WHOLE, option, option.label()));
            } else if (option instanceof Colour) {
                rows.add(new Row(Kind.SWATCHES, option, option.label()));
                rows.add(new Row(Kind.HEX, option, ""));
                rows.add(new Row(Kind.OPACITY, option, "Opacity"));
            } else if (option instanceof OnOff) {
                rows.add(new Row(Kind.FLAG, option, option.label()));
            }
        }
        rows.add(new Row(Kind.RESET, null, ""));
    }

    Feature feature() {
        return feature.feature();
    }

    // ---- input ----

    /** A click. Typing in a colour's box ends when anything else is clicked, applying what was typed if it is a colour. */
    boolean click(int x, int y) {
        Target hit = targetAt(x, y);
        if (editing != null && (hit == null || !hit.id.equals("hex:" + editing.key()))) {
            commitHex();
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
        if (key == Key.ENTER || key == Key.ESCAPE) {
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

    /** Ends typing in a colour's box. What was typed is already applied, if it was ever a whole colour. */
    private void commitHex() {
        editing = null;
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

    // ---- where things are ----

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

    /** The point along a slider's track that stands for {@code value}, as a one-unit-wide rectangle. */
    Rect pointOn(String id, int value, int min, int max) {
        Rect track = target(id);
        if (track == null) {
            return null;
        }
        int x = track.x + (int) Math.round((value - min) * (track.width - 3) / (double) (max - min)) + 1;
        return new Rect(x, track.y, 1, track.height);
    }

    // ---- drawing ----

    void render(ScreenSurface surface, Rect area, int mouseX, int mouseY) {
        targets.clear();
        int lineHeight = surface.lineHeight();
        int rowHeight = lineHeight + 5;
        int y = area.y;

        boolean preview = feature.feature() == Feature.CROSSHAIR;
        int previewWidth = preview ? Math.max(40, lineHeight * 4) : 0;
        Rect column = new Rect(area.x, y, area.width - (preview ? previewWidth + 10 : 0), area.height);

        // One line: the way back, the feature, and its switch.
        String backText = "< All features";
        Rect backLink = new Rect(area.x, y, surface.textWidth(backText) + 2, lineHeight + 2);
        surface.drawText(backText, area.x, y + 1, backLink.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED);
        targets.add(new Target("back", backLink, (x, yy) -> back.run()));
        Rect toggle = new Rect(column.x + column.width - SWITCH_WIDTH, y, SWITCH_WIDTH, SWITCH_HEIGHT);
        int nameX = backLink.x + backLink.width + 10;
        surface.drawText(Text.fit(surface, feature.name(), toggle.x - nameX - 6), nameX, y + 1, Palette.TEXT);
        Shapes.onOffSwitch(surface, toggle, feature.on(), feature.available());
        targets.add(new Target("switch:" + feature.feature().id(), toggle, (x, yy) -> feature.press()));
        y += lineHeight + 7;

        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            if (row.setting == null || model.settings().applies(row.setting)) {
                shown.add(row);
            }
        }
        int labelWidth = 0;
        for (Row row : shown) {
            labelWidth = Math.max(labelWidth, surface.textWidth(row.label));
        }
        labelWidth += 8;

        int capacity = Math.max(1, (area.y + area.height - y) / rowHeight);
        maxScroll = Math.max(0, shown.size() - capacity);
        scroll = Math.min(scroll, maxScroll);
        for (int i = scroll; i < shown.size() && i < scroll + capacity; i++) {
            Row row = shown.get(i);
            int top = y + (i - scroll) * rowHeight;
            surface.drawText(row.label, column.x, top + 2, Palette.MUTED);
            Rect controls = new Rect(column.x + labelWidth, top, column.width - labelWidth, lineHeight + 3);
            drawRow(surface, row, column, controls, mouseX, mouseY);
        }
        if (maxScroll > 0) {
            int height = capacity * rowHeight;
            int thumb = Math.max(6, height * capacity / shown.size());
            int x = column.x + column.width + 3;
            surface.fill(x, y, 2, height, Palette.LINE);
            surface.fill(x, y + (height - thumb) * scroll / maxScroll, 2, thumb, Palette.MUTED);
        }

        if (preview) {
            drawPreview(surface, new Rect(area.x + area.width - previewWidth, area.y + lineHeight + 7, previewWidth,
                    area.height - lineHeight - 7));
        }
    }

    private void drawRow(ScreenSurface surface, Row row, Rect column, Rect area, int mouseX, int mouseY) {
        switch (row.kind) {
            case CHOICE:
                drawChoice(surface, (Choice) row.setting, area);
                break;
            case WHOLE: {
                Whole whole = (Whole) row.setting;
                drawSlider(surface, "slider:" + whole.key(), area, model.value(whole), whole.min(), whole.max(), "",
                        value -> {
                            // Only when it moves: a drag along one value is not a save per frame.
                            if (model.value(whole) != value) {
                                model.change(whole, value);
                            }
                        });
                break;
            }
            case SWATCHES:
                drawSwatches(surface, (Colour) row.setting, area);
                break;
            case HEX:
                drawHexBox(surface, (Colour) row.setting, area);
                break;
            case OPACITY: {
                Colour colour = (Colour) row.setting;
                // Rounded both ways, so the faintest colour reads as the
                // slider's lowest step and that step sets exactly it.
                int minPercent = (int) Math.round(Colour.MIN_ALPHA * 100 / 255.0);
                int percent = Math.max(minPercent, (int) Math.round((model.value(colour) >>> 24) * 100 / 255.0));
                drawSlider(surface, "opacity:" + colour.key(), area, percent, minPercent, 100, "%", value -> {
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
                Rect toggle = new Rect(area.x, area.y + 2, SWITCH_WIDTH, SWITCH_HEIGHT);
                Shapes.onOffSwitch(surface, toggle, model.value(flag), true);
                targets.add(new Target("flag:" + flag.key(), toggle, (x, y) -> model.change(flag, !model.value(flag))));
                break;
            }
            case RESET: {
                String text = "Reset to defaults";
                Rect link = new Rect(column.x, area.y, surface.textWidth(text) + 2, area.height);
                surface.drawText(text, column.x, area.y + 3, link.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED);
                targets.add(new Target("reset", link, (x, y) -> model.resetToDefaults(feature.feature())));
                break;
            }
            default:
                break;
        }
    }

    private void drawChoice(ScreenSurface surface, Choice choice, Rect area) {
        int x = area.x;
        String current = model.value(choice);
        for (Choice.Option option : choice.options()) {
            Rect segment = new Rect(x, area.y, surface.textWidth(option.label()) + 10, area.height);
            boolean chosen = option.id().equals(current);
            Shapes.bordered(surface, segment, 2, chosen ? Palette.TEXT : Palette.LINE, Palette.RAISED);
            surface.drawText(option.label(), segment.x + 5, segment.y + 3, chosen ? Palette.TEXT : Palette.MUTED);
            targets.add(new Target("choice:" + choice.key() + ":" + option.id(), segment,
                    (cx, cy) -> model.change(choice, option.id())));
            x += segment.width + 3;
        }
    }

    /** What a slider does with the value under the mouse. */
    private interface Slide {
        void to(int value);
    }

    private void drawSlider(ScreenSurface surface, String id, Rect area, int value, int min, int max, String unit,
            Slide slide) {
        String widest = max + unit;
        int valueWidth = surface.textWidth(widest) + 6;
        Rect track = new Rect(area.x, area.y, Math.max(12, area.width - valueWidth), area.height);
        int line = track.centreY();
        int thumbX = track.x + (int) Math.round((value - min) * (track.width - 3) / (double) (max - min));
        surface.fill(track.x, line, track.width, 2, Palette.LINE);
        surface.fill(track.x, line, thumbX - track.x, 2, Palette.TEXT);
        surface.fill(thumbX, track.y + 1, 3, track.height - 2, Palette.TEXT);
        String shown = value + unit;
        surface.drawText(shown, area.x + area.width - surface.textWidth(shown), area.y + 3, Palette.TEXT);
        targets.add(new Target(id, track, (x, y) -> {
            // From the handle's middle, one unit in from where it is drawn,
            // so pressing the handle where it stands leaves it there.
            int under = min + (int) Math.round((x - track.x - 1) * (max - min) / (double) (track.width - 3));
            slide.to(Math.max(min, Math.min(max, under)));
        }));
    }

    private void drawSwatches(ScreenSurface surface, Colour colour, Rect area) {
        int size = Math.max(4, Math.min(area.height - 4, (area.width - 3 * (SWATCHES.length - 1)) / SWATCHES.length));
        int current = model.value(colour);
        int x = area.x;
        for (int rgb : SWATCHES) {
            Rect swatch = new Rect(x, area.y + (area.height - size) / 2, size, size);
            if ((current & 0xFFFFFF) == rgb) {
                surface.fill(swatch.x - 1, swatch.y - 1, swatch.width + 2, swatch.height + 2, Palette.TEXT);
            }
            surface.fill(swatch.x, swatch.y, swatch.width, swatch.height, 0xFF000000 | rgb);
            targets.add(new Target("swatch:" + colour.key() + ":" + hex(rgb), swatch,
                    (cx, cy) -> model.change(colour, (model.value(colour) & 0xFF000000) | rgb)));
            x += size + 3;
        }
    }

    private void drawHexBox(ScreenSurface surface, Colour colour, Rect area) {
        boolean typing = editing == colour;
        Rect box = new Rect(area.x, area.y, Math.min(area.width, surface.textWidth("#DDDDDD") + 12), area.height);
        Shapes.bordered(surface, box, 2, typing ? Palette.TEXT : Palette.LINE, Palette.RAISED);
        String text = typing ? hexText : "#" + hex(model.value(colour) & 0xFFFFFF);
        surface.drawText(Text.tail(surface, text, box.width - 10), box.x + 5, box.y + 3, Palette.TEXT);
        if (typing) {
            int caretX = box.x + 5 + surface.textWidth(Text.tail(surface, text, box.width - 10)) + 1;
            surface.fill(caretX, box.y + 3, 1, box.height - 6, Palette.TEXT);
        }
        targets.add(new Target("hex:" + colour.key(), box, (x, y) -> {
            if (editing != colour) {
                editing = colour;
                hexText = "#" + hex(model.value(colour) & 0xFFFFFF);
            }
        }));
    }

    /** The crosshair as set, over sky, snow and night, each as large as fits. */
    private void drawPreview(ScreenSurface surface, Rect column) {
        int lineHeight = surface.lineHeight();
        surface.drawText("Preview", column.x, column.y + 1, Palette.MUTED);
        int top = column.y + lineHeight + 4;
        int box = Math.max(12, Math.min(column.width, (column.y + column.height - top - 3 * (lineHeight + 4)) / 3));
        Cross cross = Cross.of(model.settings());
        int scale = Math.max(1, (box - 4) / cross.extent());
        for (int i = 0; i < PREVIEW_GROUNDS.length; i++) {
            Rect ground = new Rect(column.x, top, box, box);
            Shapes.rounded(surface, ground, 2, PREVIEW_GROUNDS[i]);
            cross.drawOnto(surface::fill, ground.centreX() - scale / 2, ground.centreY() - scale / 2, scale);
            surface.drawText(PREVIEW_NAMES[i], column.x, top + box + 2, Palette.MUTED);
            top += box + lineHeight + 4;
        }
    }

    static String hex(int rgb) {
        return String.format(Locale.ROOT, "%06X", rgb & 0xFFFFFF);
    }
}
