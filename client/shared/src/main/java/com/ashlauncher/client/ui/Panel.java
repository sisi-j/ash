package com.ashlauncher.client.ui;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Choice;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.settings.Whole;
import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Ink;
import com.ashlauncher.client.ui.draw.Paint;
import com.ashlauncher.client.ui.draw.Raster;
import java.util.List;
import java.util.function.LongSupplier;

/**
 * ash's settings panel in the final design, the same on both version targets
 * (`docs/specs/0003`, *The final design*; the approved mockup is on branch
 * {@code prototype/final-design}).
 *
 * <p>An 85% panel over the blurred game: a strip down its left with SETTINGS,
 * Edit HUD and the gear for ash's own settings, and a tile per feature with
 * its ENABLED or DISABLED button. A tile with options opens its
 * {@link OptionsPage} in place of the tiles. Still to come: icons, search and
 * tabs (#65), motion (#66), the final options pages (#67), ash's own settings
 * (#69) and FPS marks (#70).
 *
 * <p>Everything is laid out in the screen's real pixels, from one unit - a
 * hundredth of the screen's width - so the panel keeps its proportions at any
 * resolution, and afresh from the canvas's size on every frame. The behaviour
 * lives in {@link SettingsScreen}; the look lives here; the drawing goes
 * through a {@link Canvas}.
 */
public final class Panel {

    private static final String LETTERS = "SETTINGS";
    private static final long TOAST_NANOS = 2_200_000_000L;

    private final SettingsScreen model;
    private final Runnable close;
    private final LongSupplier clock;
    private int width = 1920;
    private int height = 1080;
    /** The first row of tiles on view. */
    private int scroll;
    /** The feature whose options are open in place of the tiles, or {@code null}. */
    private OptionsPage page;
    /** Where the open page was last drawn, so a click and a hook can be turned into its units. */
    private CanvasScreenSurface pageSurface;
    /** Whether the gear's page - ash's own settings - is open in place of the tiles. */
    private boolean ashSettings;
    private String toast;
    private long toastAt;

    /** @param close closes the screen the panel is on */
    public Panel(SettingsScreen model, Runnable close) {
        this(model, close, System::nanoTime);
    }

    /** @param clock nanoseconds, only ever going forward: for how long a message stays up */
    Panel(SettingsScreen model, Runnable close, LongSupplier clock) {
        this.model = model;
        this.close = close;
        this.clock = clock;
    }

    /** The screen's size in real pixels: whenever the screen opens or the window changes. */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        scroll = Math.min(scroll, maxScroll());
    }

    // ---- input, in real pixels ----

    /** A left click. Returns whether it landed on anything. */
    public boolean mouseClicked(int x, int y) {
        if (gearButton().contains(x, y)) {
            ashSettings = !ashSettings;
            page = null;
            return true;
        }
        if (editHudButton().contains(x, y)) {
            say("Edit HUD is coming soon.");
            return true;
        }
        if (page != null) {
            return pageSurface != null && page.click(pageSurface.toUnitsX(x), pageSurface.toUnitsY(y));
        }
        if (ashSettings) {
            return panel().contains(x, y);
        }
        for (SettingsScreen.Row row : model.rows()) {
            Rect tile = tileOf(row.feature());
            if (tile == null || !tile.contains(x, y)) {
                continue;
            }
            if (!row.available()) {
                say(row.name() + " did not load. An update to ash will fix it.");
            } else if (toggleIn(tile, hasGear(row)).contains(x, y)) {
                row.press();
            } else if (row.hasOptions()) {
                open(row);
            }
            return true;
        }
        return panel().contains(x, y);
    }

    private void open(SettingsScreen.Row row) {
        page = new OptionsPage(model, row, () -> page = null);
    }

    /** The mouse moved with the left button held, as when dragging a slider. */
    public void mouseDragged(int x, int y) {
        if (page != null && pageSurface != null) {
            page.drag(pageSurface.toUnitsX(x), pageSurface.toUnitsY(y));
        }
    }

    public void mouseReleased() {
        if (page != null) {
            page.release();
        }
    }

    /** The mouse wheel: positive is away from the player, which scrolls up. */
    public void mouseScrolled(double amount) {
        if (page != null) {
            page.scroll(amount);
        } else if (amount > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (amount < 0) {
            scroll = Math.min(maxScroll(), scroll + 1);
        }
    }

    /** Escape goes back from a page, and closes the panel from the tiles. */
    public void keyPressed(Key key) {
        if (page != null && page.keyPressed(key)) {
            return;
        }
        boolean onAPage = page != null || ashSettings;
        if (onAPage && (key == Key.ESCAPE || key == Key.BACKSPACE)) {
            page = null;
            ashSettings = false;
        } else if (key == Key.ESCAPE) {
            close.run();
        }
    }

    /** A character typed while the panel is open: only a colour's box takes typing until search arrives (#65). */
    public void charTyped(int codePoint) {
        if (page != null && page.typing()) {
            page.charTyped(codePoint);
        }
    }

    private void say(String message) {
        toast = message;
        toastAt = clock.getAsLong();
    }

    // ---- where things are, in real pixels ----

    private float unit() {
        return width / 100f;
    }

    private int u(double units) {
        return Math.round((float) (units * unit()));
    }

    Rect panel() {
        int x = Math.round(width * 0.075f);
        int y = Math.round(height * 0.075f);
        return new Rect(x, y, width - 2 * x, height - 2 * y);
    }

    private int stripWidth() {
        return u(5.2);
    }

    /** The gear at the bottom of the strip: ash's own settings. Public so that the real-game tests can press it. */
    public Rect gearButton() {
        Rect panel = panel();
        int pad = u(0.75);
        int side = stripWidth() - 2 * pad;
        return new Rect(panel.x + pad, panel.y + panel.height - pad - side, side, side);
    }

    /** Edit HUD, above the gear. */
    public Rect editHudButton() {
        Rect gear = gearButton();
        return new Rect(gear.x, gear.y - u(0.6) - gear.height, gear.width, gear.height);
    }

    /** Where the tiles, or a page, go. */
    Rect main() {
        Rect panel = panel();
        int x = panel.x + stripWidth() + u(1.5);
        int y = panel.y + u(1.3);
        return new Rect(x, y, panel.x + panel.width - u(1.5) - x, panel.y + panel.height - u(1.1) - noticeHeight() - y);
    }

    private int gap() {
        return u(1);
    }

    private int columns() {
        return Math.max(1, Math.round(main().width / (15.36f * unit())));
    }

    private int tileWidth() {
        return (main().width - gap() * (columns() - 1)) / columns();
    }

    private int tileHeight() {
        return u(13.5);
    }

    private int rowsOnView() {
        return Math.max(1, (main().height + gap()) / (tileHeight() + gap()));
    }

    int maxScroll() {
        int rows = (model.rows().size() + columns() - 1) / columns();
        return Math.max(0, rows - rowsOnView());
    }

    /** Where a feature's tile is, or {@code null} when it is scrolled out of view or a page is open. */
    Rect tileOf(Feature feature) {
        if (page != null || ashSettings) {
            return null;
        }
        List<SettingsScreen.Row> rows = model.rows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).feature() != feature) {
                continue;
            }
            int row = i / columns();
            if (row < scroll || row >= scroll + rowsOnView()) {
                return null;
            }
            Rect main = main();
            return new Rect(main.x + (i % columns()) * (tileWidth() + gap()),
                    main.y + (row - scroll) * (tileHeight() + gap()), tileWidth(), tileHeight());
        }
        return null;
    }

    /** The ENABLED / DISABLED button: beside the gear, or the tile's whole width when there is no gear (yet: #67). */
    private Rect toggleIn(Rect tile, boolean besideGear) {
        int pad = u(0.9);
        int side = u(2.3);
        int x = besideGear ? tile.x + pad + side + u(0.45) : tile.x + pad;
        return new Rect(x, tile.y + tile.height - u(0.85) - side, tile.x + tile.width - pad - x, side);
    }

    private Rect gearIn(Rect tile) {
        int side = u(2.3);
        return new Rect(tile.x + u(0.9), tile.y + tile.height - u(0.85) - side, side, side);
    }

    private int noticeHeight() {
        return model.footer().isEmpty() ? 0 : Ink.lineHeight(Ink.Weight.REGULAR, textSize(0.85f)) + u(0.6);
    }

    private float textSize(float units) {
        return units * unit();
    }

    // ---- hooks: where things are drawn, for the real-game tests to click as a player would ----

    /** A feature's ENABLED / DISABLED button, or the switch on its open page; {@code null} when not on view. */
    public Rect switchOf(Feature feature) {
        if (page != null) {
            return page.feature() == feature ? pagePixels("switch:" + feature.id()) : null;
        }
        Rect tile = tileOf(feature);
        for (SettingsScreen.Row row : model.rows()) {
            if (row.feature() == feature && tile != null) {
                return toggleIn(tile, hasGear(row));
            }
        }
        return null;
    }

    private static boolean hasGear(SettingsScreen.Row row) {
        return row.hasOptions() && row.available();
    }

    /** The gear on a feature's tile, which opens its options; {@code null} when it has none, did not load, or is not on view. */
    public Rect optionsLinkOf(Feature feature) {
        for (SettingsScreen.Row row : model.rows()) {
            if (row.feature() == feature && row.hasOptions() && row.available()) {
                Rect tile = tileOf(feature);
                return tile == null ? null : gearIn(tile);
            }
        }
        return null;
    }

    public Rect switchOf(OnOff option) {
        return pagePixels("flag:" + option.key());
    }

    public Rect choiceOf(Choice choice, String id) {
        return pagePixels("choice:" + choice.key() + ":" + id);
    }

    /** The point along a whole number's slider that stands for {@code value}. */
    public Rect sliderAt(Whole whole, int value) {
        return page == null || pageSurface == null ? null
                : nonEmpty(pageSurface.toPixels(page.pointOn("slider:" + whole.key(), value, whole.min(), whole.max())));
    }

    public Rect swatchOf(Colour colour, int rgb) {
        return pagePixels("swatch:" + colour.key() + ":" + OptionsPage.hex(rgb));
    }

    public Rect hexBoxOf(Colour colour) {
        return pagePixels("hex:" + colour.key());
    }

    public Rect opacitySlider(Colour colour) {
        return pagePixels("opacity:" + colour.key());
    }

    /** The point along a colour's opacity slider that stands for {@code percent}. */
    public Rect opacityAt(Colour colour, int percent) {
        int min = (int) Math.round(Colour.MIN_ALPHA * 100 / 255.0);
        return page == null || pageSurface == null ? null
                : nonEmpty(pageSurface.toPixels(page.pointOn("opacity:" + colour.key(), percent, min, 100)));
    }

    public Rect resetToDefaults() {
        return pagePixels("reset");
    }

    public Rect backLink() {
        return pagePixels("back");
    }

    private Rect pagePixels(String id) {
        if (page == null || pageSurface == null) {
            return null;
        }
        Rect units = page.target(id);
        return units == null ? null : nonEmpty(pageSurface.toPixels(units));
    }

    /** At least a pixel each way: a point on a slider is one unit wide, and a unit can round to nothing. */
    private static Rect nonEmpty(Rect rect) {
        return rect == null ? null : new Rect(rect.x, rect.y, Math.max(1, rect.width), Math.max(1, rect.height));
    }

    // ---- drawing ----

    /** Draws the panel at the canvas's size; the mouse, in real pixels, decides what is highlighted. */
    public void render(Canvas canvas, int mouseX, int mouseY) {
        if (canvas.width() != width || canvas.height() != height) {
            resize(canvas.width(), canvas.height());
        }
        scroll = Math.min(scroll, maxScroll());
        canvas.fill(0, 0, width, height, Palette.OVERLAY);

        Rect panel = panel();
        int radius = u(1.1);
        Paint.shadow(canvas, panel.x, panel.y, panel.width, panel.height, radius, u(2.4), Palette.SHADOW_ALPHA);
        int strip = stripWidth();
        Paint.roundRect(canvas, panel.x, panel.y, strip, panel.height, radius, Palette.STRIP, 1f, true, false);
        Paint.roundRect(canvas, panel.x + strip, panel.y, panel.width - strip, panel.height, radius, Palette.PANEL, 1f,
                false, true);
        canvas.fill(panel.x + strip, panel.y, 1, panel.height, Palette.LINE);

        drawLetters(canvas, panel);
        drawToolButton(canvas, editHudButton(), "layout", 0.42f, mouseX, mouseY);
        drawToolButton(canvas, gearButton(), "gear", 1f, mouseX, mouseY);

        if (page != null) {
            drawPage(canvas, mouseX, mouseY);
        } else if (ashSettings) {
            drawAshSettings(canvas);
        } else {
            drawTiles(canvas, mouseX, mouseY);
        }
        drawNotice(canvas);
        drawToast(canvas, panel);
    }

    private void drawLetters(Canvas canvas, Rect panel) {
        float size = textSize(2.2f);
        int step = Ink.capHeight(Ink.Weight.EXTRABOLD, size) + u(0.75);
        int capTop = Ink.lineHeight(Ink.Weight.EXTRABOLD, size) - Ink.capHeight(Ink.Weight.EXTRABOLD, size);
        int y = panel.y + u(1.3) - capTop / 2;
        for (int i = 0; i < LETTERS.length(); i++) {
            String letter = LETTERS.substring(i, i + 1);
            int x = panel.x + (stripWidth() - Ink.width(letter, Ink.Weight.EXTRABOLD, size)) / 2;
            Ink.text(letter, Ink.Weight.EXTRABOLD, size, Palette.TEXT).drawAt(canvas, x, y, 1f);
            y += step;
        }
    }

    private void drawToolButton(Canvas canvas, Rect button, String icon, float opacity, int mouseX, int mouseY) {
        int fill = button.contains(mouseX, mouseY) && opacity >= 1f ? Palette.RAISED_HOVER : Palette.RAISED;
        Paint.roundRect(canvas, button.x, button.y, button.width, button.height, u(0.65), fill, opacity);
        int size = Math.round(button.width * 0.52f);
        canvas.draw(Ink.icon(icon, size, Palette.ICON), button.x + (button.width - size) / 2,
                button.y + (button.height - size) / 2, opacity);
    }

    private void drawTiles(Canvas canvas, int mouseX, int mouseY) {
        Rect main = main();
        canvas.clip(main.x, main.y, main.width, main.height);
        for (SettingsScreen.Row row : model.rows()) {
            Rect tile = tileOf(row.feature());
            if (tile != null) {
                drawTile(canvas, row, tile, mouseX, mouseY);
            }
        }
        canvas.unclip();
        drawScrollBar(canvas, main);
    }

    private void drawTile(Canvas canvas, SettingsScreen.Row row, Rect tile, int mouseX, int mouseY) {
        boolean available = row.available();
        float opacity = available ? 1f : 0.45f;
        int fill = available && tile.contains(mouseX, mouseY) ? Palette.RAISED_HOVER : Palette.RAISED;
        Paint.roundRect(canvas, tile.x, tile.y, tile.width, tile.height, u(0.9), fill, opacity);

        float nameSize = textSize(0.95f);
        String name = fit(row.name(), Ink.Weight.SEMIBOLD, nameSize, tile.width - 2 * u(0.9));
        int nameWidth = Ink.width(name, Ink.Weight.SEMIBOLD, nameSize);
        Ink.text(name, Ink.Weight.SEMIBOLD, nameSize, Palette.TEXT)
                .drawAt(canvas, tile.x + (tile.width - nameWidth) / 2, tile.y + u(0.95), opacity);

        if (hasGear(row)) {
            Rect gear = gearIn(tile);
            Paint.roundRect(canvas, gear.x, gear.y, gear.width, gear.height, u(0.55),
                    gear.contains(mouseX, mouseY) ? Palette.RAISED_HOVER : Palette.RAISED, 1f);
            int size = Math.round(gear.width * 0.55f);
            canvas.draw(Ink.icon("gear", size, Palette.ICON), gear.x + (gear.width - size) / 2,
                    gear.y + (gear.height - size) / 2, 1f);
        }

        Rect toggle = toggleIn(tile, hasGear(row));
        int colour = !available ? Palette.UNAVAILABLE : row.on() ? Palette.GREEN : Palette.RED;
        Paint.roundRect(canvas, toggle.x, toggle.y, toggle.width, toggle.height, u(0.55), colour, opacity);
        String label = !available ? "UNAVAILABLE" : row.on() ? "ENABLED" : "DISABLED";
        float labelSize = textSize(0.74f);
        Raster text = Ink.text(label, Ink.Weight.BOLD, labelSize, Palette.TEXT);
        int labelWidth = Ink.width(label, Ink.Weight.BOLD, labelSize);
        int lineHeight = Ink.lineHeight(Ink.Weight.BOLD, labelSize);
        text.drawAt(canvas, toggle.x + (toggle.width - labelWidth) / 2, toggle.y + (toggle.height - lineHeight) / 2,
                opacity);
    }

    /** A thin bar beside the tiles when there are more rows than fit, its thumb where the view is. */
    private void drawScrollBar(Canvas canvas, Rect main) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        int x = main.x + main.width + u(0.5);
        int rows = max + rowsOnView();
        int thumb = Math.max(u(2), main.height * rowsOnView() / rows);
        int thumbY = main.y + (main.height - thumb) * scroll / max;
        Paint.roundRect(canvas, x, main.y, u(0.3), main.height, u(0.15), Palette.RAISED, 1f);
        Paint.roundRect(canvas, x, thumbY, u(0.3), thumb, u(0.15), Palette.MUTED, 1f);
    }

    private void drawPage(Canvas canvas, int mouseX, int mouseY) {
        Rect main = main();
        float scale = 0.105f * unit();
        pageSurface = new CanvasScreenSurface(canvas, scale, main.x, main.y);
        Rect area = new Rect(0, 0, (int) Math.floor(main.width / scale), (int) Math.floor(main.height / scale));
        page.render(pageSurface, area, pageSurface.toUnitsX(mouseX), pageSurface.toUnitsY(mouseY));
    }

    /** A placeholder until ash's own settings arrive with #69. */
    private void drawAshSettings(Canvas canvas) {
        Rect main = main();
        float title = textSize(1.35f);
        Ink.text("ash settings", Ink.Weight.BOLD, title, Palette.TEXT).drawAt(canvas, main.x, main.y, 1f);
        float body = textSize(0.92f);
        Ink.text("Coming soon: the open key, interface size, animations and blur.", Ink.Weight.REGULAR, body, Palette.MUTED)
                .drawAt(canvas, main.x, main.y + Ink.lineHeight(Ink.Weight.BOLD, title) + u(0.8), 1f);
    }

    /** Why a change was not saved, or which features did not load: the model's words, under the tiles. */
    private void drawNotice(Canvas canvas) {
        String notice = model.footer();
        if (notice.isEmpty()) {
            return;
        }
        Rect main = main();
        float size = textSize(0.85f);
        Ink.text(fit(notice, Ink.Weight.REGULAR, size, main.width), Ink.Weight.REGULAR, size, Palette.MUTED)
                .drawAt(canvas, main.x, main.y + main.height + u(0.4), 1f);
    }

    private void drawToast(Canvas canvas, Rect panel) {
        if (toast == null || clock.getAsLong() - toastAt > TOAST_NANOS) {
            toast = null;
            return;
        }
        float size = textSize(0.92f);
        int textWidth = Ink.width(toast, Ink.Weight.REGULAR, size);
        int lineHeight = Ink.lineHeight(Ink.Weight.REGULAR, size);
        int w = textWidth + 2 * u(1.1);
        int h = lineHeight + 2 * u(0.6);
        int x = panel.x + (panel.width - w) / 2;
        int y = panel.y + panel.height - u(1.2) - h;
        Paint.roundRect(canvas, x, y, w, h, u(0.8), 0xEB141416, 1f);
        Ink.text(toast, Ink.Weight.REGULAR, size, Palette.TEXT).drawAt(canvas, x + u(1.1), y + u(0.6), 1f);
    }

    /** {@code text}, cut with an ellipsis if it is wider than {@code room}. */
    private static String fit(String text, Ink.Weight weight, float size, int room) {
        if (Ink.width(text, weight, size) <= room) {
            return text;
        }
        String cut = text;
        while (!cut.isEmpty() && Ink.width(cut + "...", weight, size) > room) {
            cut = cut.substring(0, cut.offsetByCodePoints(cut.length(), -1));
        }
        return cut + "...";
    }
}
