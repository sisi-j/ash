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
import com.ashlauncher.client.ui.draw.Hsv;
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
import java.util.function.Supplier;

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
    /** The faint edge round a colour chip or swatch, so a dark one shows on the dark panel: white at 20%. */
    private static final int CHIP_EDGE = 0x33FFFFFF;
    /** The dark edge round a picker's markers, so they show on white as on black. */
    private static final int MARKER_EDGE = 0x66000000;

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
    private enum Kind { CHOICE, WHOLE, COLOUR, FLAG, KEY }

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
    /** The feature the page is for, by its switch's row; {@code null} on ash's own page, which has no switch. */
    private final SettingsScreen.Row feature;
    /** Whose options these are, what the header calls it, and its icon. */
    private final Feature subject;
    private final String title;
    private final Ink.Icon icon;
    /** On ash's own page: the key that opens the panel, by name, and the way to change it. */
    private final Supplier<String> keyName;
    private final Runnable changeKey;
    private final Runnable back;
    private final LongSupplier clock;
    private final Consumer<String> say;
    private final BooleanSupplier animated;
    /** When each switch on the page last changed, by its target, so it eases across (#66). */
    private final Map<String, Long> switchedAt = new HashMap<>();
    /** When the colour box last refused what was typed in it, so it shakes. */
    private long hexRefusedAt = Long.MIN_VALUE;
    private final RecentColours recent;
    /** The colour whose picker is open, by its key, or {@code null}: one at a time. */
    private String openPicker;
    /** Each colour's place in the picker, by key, kept so grey and black do not lose the hue they had. */
    private final Map<String, float[]> hsv = new HashMap<>();
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
     * @param recent the colours the player has picked lately, for the picker's Recent row
     */
    OptionsPage(SettingsScreen model, SettingsScreen.Row feature, Runnable back, LongSupplier clock,
            Consumer<String> say, BooleanSupplier animated, RecentColours recent) {
        this(model, feature, feature.feature(), feature.name(), Panel.iconOf(feature.feature()), back, clock, say,
                animated, recent, null, null);
    }

    /**
     * ash's own page, behind the gear (#69): the key that opens the panel,
     * then ash's own settings - interface size, animations, blur.
     *
     * @param keyName the key that opens the panel, as the game names it
     * @param changeKey takes the player to the game's Controls, where it is changed
     */
    static OptionsPage ash(SettingsScreen model, Runnable back, LongSupplier clock, Consumer<String> say,
            BooleanSupplier animated, RecentColours recent, Supplier<String> keyName, Runnable changeKey) {
        return new OptionsPage(model, null, Feature.SETTINGS_SCREEN, "ash settings", Ink.Icon.GEAR, back, clock,
                say, animated, recent, keyName, changeKey);
    }

    private OptionsPage(SettingsScreen model, SettingsScreen.Row feature, Feature subject, String title,
            Ink.Icon icon, Runnable back, LongSupplier clock, Consumer<String> say, BooleanSupplier animated,
            RecentColours recent, Supplier<String> keyName, Runnable changeKey) {
        this.model = model;
        this.feature = feature;
        this.subject = subject;
        this.title = title;
        this.icon = icon;
        this.keyName = keyName;
        this.changeKey = changeKey;
        if (keyName != null) {
            rows.add(new Row(Kind.KEY, null, "Open ash settings"));
        }
        this.back = back;
        this.clock = clock;
        this.say = say;
        this.animated = animated;
        this.recent = recent;
        for (Setting<?> option : model.optionsOf(subject)) {
            if (option instanceof Choice) {
                rows.add(new Row(Kind.CHOICE, option, option.label()));
            } else if (option instanceof Whole) {
                rows.add(new Row(Kind.WHOLE, option, option.label()));
            } else if (option instanceof Colour) {
                rows.add(new Row(Kind.COLOUR, option, option.label()));
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
        return subject;
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
        if (hit.id.startsWith("slider:") || isPickerPart(hit.id)) {
            dragging = hit;
        }
        return true;
    }

    void drag(int x, int y) {
        if (dragging != null) {
            dragging.action.at(x, y);
        }
    }

    /** A drag's end: a colour picked on the square or a bar is remembered among the recent ones. */
    void release() {
        if (dragging != null && isPickerPart(dragging.id)) {
            rememberColourOf(dragging.id);
        }
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
        int listWidth = area.width - previewWidth - (preview ? units(1.6) : 0);
        if (feature == null) {
            // ash's own page is a short list, kept narrow as the mockup keeps it.
            listWidth = Math.min(listWidth, units(52));
        }
        Rect list = new Rect(area.x, headerBottom, listWidth, area.y + area.height - headerBottom);
        if (rows.isEmpty()) {
            float size = textSize(0.92f);
            Ink.text(title + " has no options yet.", Ink.Weight.REGULAR, size, Palette.MUTED)
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
        return subject == Feature.CROSSHAIR || subject == Feature.HIT_INDICATOR;
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
        if (icon != null) {
            int glyph = units(1.8);
            canvas.draw(Ink.icon(icon, glyph, Palette.ICON), x, area.y + (height - glyph) / 2, 1f);
            x += glyph + units(0.8);
        }

        Rect toggle = new Rect(area.x + area.width - units(8.5), area.y + (height - units(2.3)) / 2, units(8.5),
                units(2.3));
        float nameSize = textSize(1.25f);
        int room = (feature == null ? area.x + area.width : toggle.x - units(0.8)) - x;
        String name = Panel.fit(title, Ink.Weight.BOLD, nameSize, room);
        Ink.text(name, Ink.Weight.BOLD, nameSize, Palette.TEXT)
                .drawAt(canvas, x, area.y + (height - Ink.lineHeight(Ink.Weight.BOLD, nameSize)) / 2, 1f);

        if (feature == null) {
            // ash itself has no switch: it cannot be turned off from its own panel.
            return area.y + height + units(1.2);
        }
        String switchId = "switch:" + feature.feature().id();
        Panel.drawToggle(canvas, toggle, feature.available(), feature.on(), eased(switchId, feature.on(), true),
                units(0.55), textSize(0.74f), 1f);
        targets.add(new Target(switchId, toggle, (cx, cy) -> {
            feature.press();
            switchedAt.put(switchId, clock.getAsLong());
        }));
        return area.y + height + units(1.2);
    }

    /** A row's height: one line, or for a colour with its picker open, the picker under it too. */
    private int rowHeight(Row row) {
        boolean picking = row.kind == Kind.COLOUR && row.setting.key().equals(openPicker);
        return units(3) + (picking ? units(PICKER_HEIGHT) + units(0.9) : 0);
    }

    private void drawRows(Canvas canvas, Rect list, int mouseX, int mouseY) {
        List<Row> shown = new ArrayList<>();
        for (Row row : rows) {
            if (model.settings().applies(row.setting)) {
                shown.add(row);
            }
        }
        int gap = units(0.35);
        int resetHeight = units(2.3);
        int bottom = list.y + list.height - resetHeight - units(0.6);

        // As many rows from the first on view as fit; scrolling moves the first.
        maxScroll = 0;
        for (int first = 0; first < shown.size(); first++) {
            int y = list.y;
            int i = first;
            while (i < shown.size() && y + rowHeight(shown.get(i)) <= bottom) {
                y += rowHeight(shown.get(i)) + gap;
                i++;
            }
            if (i == shown.size()) {
                maxScroll = first;
                break;
            }
        }
        scroll = Math.min(scroll, maxScroll);
        int width = list.width - (maxScroll > 0 ? units(1) : 0);

        int y = list.y;
        int drawn = 0;
        for (int i = scroll; i < shown.size(); i++) {
            int height = rowHeight(shown.get(i));
            if (y + height > bottom && drawn > 0) {
                break;
            }
            drawRow(canvas, shown.get(i), new Rect(list.x, y, width, height), mouseX, mouseY);
            y += height + gap;
            drawn++;
        }
        if (maxScroll > 0) {
            int height = Math.max(units(2), y - gap - list.y);
            int thumb = Math.max(units(2), height * drawn / shown.size());
            int barX = list.x + list.width - units(0.3);
            Paint.roundRect(canvas, barX, list.y, units(0.3), height, units(0.15), Palette.RAISED, 1f);
            Paint.roundRect(canvas, barX, list.y + (height - thumb) * scroll / maxScroll, units(0.3), thumb,
                    units(0.15), Palette.MUTED, 1f);
        }

        // Reset to defaults: a quiet link under the rows - on a feature's
        // page; ash's own has none, as the mockup has it.
        if (feature == null) {
            return;
        }
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
        targets.add(new Target("reset", link, (cx, cy) -> model.resetToDefaults(subject)));
    }

    /** One option: a rounded row, its name on the left in a column of its own, its control after it. */
    private void drawRow(Canvas canvas, Row row, Rect at, int mouseX, int mouseY) {
        Paint.roundRect(canvas, at.x, at.y, at.width, at.height, units(0.7), Palette.RAISED, 1f);
        float size = textSize(0.92f);
        int padX = units(0.9);
        Ink.text(Panel.fit(row.label, Ink.Weight.REGULAR, size, units(9.5)), Ink.Weight.REGULAR, size, Palette.MUTED)
                .drawAt(canvas, at.x + padX, at.y + (units(3) - Ink.lineHeight(Ink.Weight.REGULAR, size)) / 2, 1f);
        int controlX = at.x + padX + units(9.5) + units(1);
        // The control sits on the first line; an open picker folds out under it.
        Rect control = new Rect(controlX, at.y, at.x + at.width - padX - controlX, units(3));

        switch (row.kind) {
            case CHOICE:
                drawChips(canvas, (Choice) row.setting, control, mouseX, mouseY);
                break;
            case WHOLE: {
                Whole whole = (Whole) row.setting;
                // A per cent sign sits against its number, as in "100%"; other units are a word apart.
                String unitText = whole.unit().isEmpty() ? "" : whole.unit().equals("%") ? "%" : " " + whole.unit();
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
                drawColour(canvas, (Colour) row.setting, at, control, mouseX, mouseY);
                break;
            case KEY: {
                // The key as the game names it; pressed, the game's Controls, where keys are changed.
                String name = keyName.get();
                float keySize = textSize(0.92f);
                int lineHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, keySize);
                Rect chip = new Rect(control.x, control.y + (control.height - lineHeight - 2 * units(0.45)) / 2,
                        Ink.width(name, Ink.Weight.SEMIBOLD, keySize) + 2 * units(0.9), lineHeight + 2 * units(0.45));
                Paint.roundRect(canvas, chip.x, chip.y, chip.width, chip.height, units(0.55),
                        chip.contains(mouseX, mouseY) ? Palette.RAISED_STRONG : Palette.RAISED_HOVER, 1f);
                Ink.text(name, Ink.Weight.SEMIBOLD, keySize, Palette.TEXT)
                        .drawAt(canvas, chip.x + units(0.9), chip.y + units(0.45), 1f);
                targets.add(new Target("key", chip, (x, y) -> changeKey.run()));
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

    /** The picker's height, in units, under its colour's line. */
    private static final float PICKER_HEIGHT = 10f;

    /**
     * A colour (#68): on its line, a chip of it over a checkerboard and its
     * code and opacity; pressed, the picker folds out under them - the
     * saturation and brightness square, the hue bar, the opacity bar for a
     * colour that has one, and beside them the code box, the presets and the
     * colours picked lately.
     */
    private void drawColour(Canvas canvas, Colour colour, Rect row, Rect line, int mouseX, int mouseY) {
        int current = model.value(colour);
        int chipSide = units(2.1);
        Rect chip = new Rect(line.x, line.y + (line.height - chipSide) / 2, chipSide, chipSide);
        canvas.draw(Ink.colourChip(current, chipSide, units(0.55), units(0.35)), chip.x, chip.y, 1f);
        Paint.outline(canvas, chip.x, chip.y, chip.width, chip.height, units(0.55), CHIP_EDGE);
        float size = textSize(0.92f);
        String code = "#" + hex(current & 0xFFFFFF)
                + (colour.withOpacity() ? " · " + Math.round((current >>> 24) * 100 / 255f) + "%" : "");
        int codeX = chip.x + chip.width + units(0.7);
        Ink.text(code, Ink.Weight.SEMIBOLD, size, Palette.MUTED)
                .drawAt(canvas, codeX, line.y + (line.height - Ink.lineHeight(Ink.Weight.SEMIBOLD, size)) / 2, 1f);
        Rect toggle = new Rect(chip.x, chip.y, codeX - chip.x + Ink.width(code, Ink.Weight.SEMIBOLD, size), chipSide);
        targets.add(new Target("chip:" + colour.key(), toggle, (cx, cy) -> {
            openPicker = colour.key().equals(openPicker) ? null : colour.key();
            editing = null;
        }));
        if (!colour.key().equals(openPicker)) {
            return;
        }

        float[] place = hsvOf(colour);
        int padX = units(0.9);
        int top = row.y + units(3) + units(0.3);
        int height = units(PICKER_HEIGHT);
        int radius = units(0.6);

        // Saturation across, brightness down, for the hue the bar is at.
        Rect square = new Rect(row.x + padX, top, units(13), height);
        canvas.draw(Ink.colourSquare(Math.round(place[0]) % 360, square.width, square.height, radius), square.x,
                square.y, 1f);
        ring(canvas, square.x + Math.round(place[1] * (square.width - 1)),
                square.y + Math.round((1 - place[2]) * (square.height - 1)), units(1), current | 0xFF000000);
        targets.add(new Target("square:" + colour.key(), square, (x, y) -> {
            float[] at = hsvOf(colour);
            at[1] = fraction(x - square.x, square.width);
            at[2] = 1 - fraction(y - square.y, square.height);
            setRgb(colour, Hsv.toRgb(at[0], at[1], at[2]));
        }));

        Rect hue = new Rect(square.x + square.width + units(0.8), top, units(1.4), height);
        canvas.draw(Ink.hueBar(hue.width, hue.height, radius), hue.x, hue.y, 1f);
        barMarker(canvas, hue, hue.y + Math.round(place[0] / 360f * (hue.height - 1)));
        targets.add(new Target("hue:" + colour.key(), hue, (x, y) -> {
            float[] at = hsvOf(colour);
            at[0] = Math.min(359.9f, fraction(y - hue.y, hue.height) * 360f);
            setRgb(colour, Hsv.toRgb(at[0], at[1], at[2]));
        }));

        int sideX = hue.x + hue.width + units(0.8);
        if (colour.withOpacity()) {
            Rect alpha = new Rect(sideX, top, units(1.4), height);
            canvas.draw(Ink.opacityBar(current, alpha.width, alpha.height, radius, units(0.35)), alpha.x, alpha.y, 1f);
            barMarker(canvas, alpha, alpha.y + Math.round((1 - (current >>> 24) / 255f) * (alpha.height - 1)));
            targets.add(new Target("alpha:" + colour.key(), alpha, (x, y) -> {
                int percent = Math.round((1 - fraction(y - alpha.y, alpha.height)) * 100);
                // Never fainter than both targets draw alike.
                int a = Math.max(Colour.MIN_ALPHA, Math.round(percent * 255 / 100f));
                int wanted = (a << 24) | (model.value(colour) & 0xFFFFFF);
                if (model.value(colour) != wanted) {
                    model.change(colour, wanted);
                }
            }));
            sideX = alpha.x + alpha.width + units(0.8);
        }

        // The code box, then the presets, then the colours picked lately.
        Rect side = new Rect(sideX, top, row.x + row.width - padX - sideX, height);
        drawCodeBox(canvas, colour, side.x, side.y);
        float labelSize = textSize(0.68f);
        int labelHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, labelSize);
        int y = side.y + units(2.2) + units(0.6);
        Ink.text("PRESETS", Ink.Weight.SEMIBOLD, labelSize, Palette.PLACEHOLDER, 0.06f).drawAt(canvas, side.x, y, 1f);
        y += labelHeight + units(0.3);
        y = drawSwatches(canvas, colour, "swatch", toList(SWATCHES), side, y) + units(0.6);
        Ink.text("RECENT", Ink.Weight.SEMIBOLD, labelSize, Palette.PLACEHOLDER, 0.06f).drawAt(canvas, side.x, y, 1f);
        y += labelHeight + units(0.3);
        if (recent.colours().isEmpty()) {
            Ink.text("Colours you use show here", Ink.Weight.REGULAR, textSize(0.74f), Palette.PLACEHOLDER)
                    .drawAt(canvas, side.x, y, 1f);
        } else {
            drawSwatches(canvas, colour, "recent", recent.colours(), side, y);
        }
    }

    /** A row of swatches, wrapping inside {@code side}, the current colour's ringed. Returns where the next thing goes. */
    private int drawSwatches(Canvas canvas, Colour colour, String kind, List<Integer> colours, Rect side, int top) {
        int size = units(1.6);
        int gap = units(0.4);
        int current = model.value(colour) & 0xFFFFFF;
        int x = side.x;
        int y = top;
        for (int rgb : colours) {
            if (x + size > side.x + side.width && x > side.x) {
                x = side.x;
                y += size + gap;
            }
            if (current == rgb) {
                int ring = Math.max(2, units(0.18));
                Paint.outline(canvas, x - ring, y - ring, size + 2 * ring, size + 2 * ring, units(0.45) + ring,
                        Palette.TEXT);
            }
            Paint.roundRect(canvas, x, y, size, size, units(0.45), 0xFF000000 | rgb, 1f);
            Paint.outline(canvas, x, y, size, size, units(0.45), CHIP_EDGE);
            targets.add(new Target(kind + ":" + colour.key() + ":" + hex(rgb), new Rect(x, y, size, size), (cx, cy) -> {
                setRgb(colour, rgb);
                recent.remember(rgb);
            }));
            x += size + gap;
        }
        return y + size;
    }

    /** The box a colour's code is typed into: it applies as soon as it is whole, and shakes if it never is. */
    private void drawCodeBox(Canvas canvas, Colour colour, int x, int y) {
        boolean typing = editing == colour;
        float size = textSize(0.92f);
        // Refused, it shakes; the place it is clicked is where it settles.
        long sinceRefused = hexRefusedAt == Long.MIN_VALUE ? Long.MAX_VALUE : clock.getAsLong() - hexRefusedAt;
        int shake = animated.getAsBoolean() ? units(Motion.shake(sinceRefused)) : 0;
        Rect box = new Rect(x + shake, y, units(8.5), units(2.2));
        Paint.roundRect(canvas, box.x, box.y, box.width, box.height, units(0.55), Palette.RAISED_HOVER, 1f);
        if (typing) {
            Paint.outline(canvas, box.x, box.y, box.width, box.height, units(0.55), Palette.FOCUS);
        }
        String text = typing ? hexText : "#" + hex(model.value(colour) & 0xFFFFFF);
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

    /** A marker on the square: a white ring with a dark edge, round the colour it points at. */
    private void ring(Canvas canvas, int x, int y, int diameter, int argb) {
        int r = diameter / 2;
        Paint.roundRect(canvas, x - r - 1, y - r - 1, diameter + 2, diameter + 2, r + 1, MARKER_EDGE, 1f);
        Paint.roundRect(canvas, x - r, y - r, diameter, diameter, r, Palette.TEXT, 1f);
        int inner = Math.max(2, diameter - 2 * Math.max(2, units(0.18)));
        Paint.roundRect(canvas, x - inner / 2, y - inner / 2, inner, inner, inner / 2, argb, 1f);
    }

    /** A marker on a bar: a white pill across it, a little wider, at {@code y}. */
    private void barMarker(Canvas canvas, Rect bar, int y) {
        int height = units(0.5);
        int over = units(0.2);
        Paint.roundRect(canvas, bar.x - over - 1, y - height / 2 - 1, bar.width + 2 * over + 2, height + 2,
                height / 2 + 1, MARKER_EDGE, 1f);
        Paint.roundRect(canvas, bar.x - over, y - height / 2, bar.width + 2 * over, height, height / 2, Palette.TEXT, 1f);
    }

    /** {@code along} pixels into something {@code size} long, as 0 to 1. */
    private static float fraction(int along, int size) {
        return Math.max(0f, Math.min(1f, along / (float) Math.max(1, size - 1)));
    }

    /** A colour's RGB set, its opacity kept, and saved if it changed. */
    private void setRgb(Colour colour, int rgb) {
        int wanted = (model.value(colour) & 0xFF000000) | (rgb & 0xFFFFFF);
        if (model.value(colour) != wanted) {
            model.change(colour, wanted);
        }
    }

    /**
     * Where a colour is in the picker: its hue, saturation and brightness,
     * from its RGB - except that grey and black keep the hue and saturation
     * they were dragged through, so the bar does not jump back to red.
     */
    private float[] hsvOf(Colour colour) {
        int rgb = model.value(colour) & 0xFFFFFF;
        float[] kept = hsv.get(colour.key());
        if (kept == null || Hsv.toRgb(kept[0], kept[1], kept[2]) != rgb) {
            kept = Hsv.fromRgb(rgb, kept);
            hsv.put(colour.key(), kept);
        }
        return kept;
    }

    private static boolean isPickerPart(String id) {
        return id.startsWith("square:") || id.startsWith("hue:") || id.startsWith("alpha:");
    }

    /** The colour a picker part's target is for, remembered among the recent ones. */
    private void rememberColourOf(String id) {
        String key = id.substring(id.indexOf(':') + 1);
        for (Row row : rows) {
            if (row.kind == Kind.COLOUR && row.setting.key().equals(key)) {
                recent.remember(model.value((Colour) row.setting));
            }
        }
    }

    /** Where on a colour's opacity bar {@code percent} is, as a one-pixel-high rectangle; null when closed. */
    Rect opacityPoint(String key, int percent) {
        Rect bar = target("alpha:" + key);
        if (bar == null) {
            return null;
        }
        int y = bar.y + Math.round((100 - percent) / 100f * (bar.height - 1));
        return new Rect(bar.x, y, bar.width, 1);
    }

    private static List<Integer> toList(int[] values) {
        List<Integer> list = new ArrayList<>();
        for (int value : values) {
            list.add(value);
        }
        return list;
    }

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
        boolean hits = subject == Feature.HIT_INDICATOR;
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
