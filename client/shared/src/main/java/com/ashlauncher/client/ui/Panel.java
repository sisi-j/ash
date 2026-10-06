package com.ashlauncher.client.ui;

import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.Placement;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Category;
import com.ashlauncher.client.settings.Choice;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.settings.Whole;
import com.ashlauncher.client.ui.draw.Canvas;
import com.ashlauncher.client.ui.draw.Ink;
import com.ashlauncher.client.ui.draw.Paint;
import com.ashlauncher.client.ui.draw.Raster;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.LongSupplier;

/**
 * ash's settings panel in the final design, the same on both version targets
 * (`docs/specs/0003`, *The final design*; the approved mockup is on branch
 * {@code prototype/final-design}).
 *
 * <p>An 85% panel over the blurred game: a strip down its left with SETTINGS,
 * Edit HUD and the gear for ash's own settings, and a tile per feature with
 * its ENABLED or DISABLED button. A tile with options opens its
 * {@link OptionsPage} in place of the tiles. Edit HUD puts the panel away and
 * outlines each readout over the game, to be dragged where the player wants
 * it (#41). Still to come: icons, search and
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
    private static final float LABEL_TRACKING = 0.06f;

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
    /** What has been typed to search the tiles; on the tiles, every key typed goes here. */
    private String query = "";
    /** The selected tab's category, or {@code null} for All. */
    private Category tab;
    private String toast;
    private long toastAt;
    /** Whether the game is blurring what is behind the panel; where it cannot, the panel darkens it more. */
    private boolean blurred = true;
    /** Real pixels to one of the game's GUI units, which the readouts are placed in. */
    private int guiScale = 2;
    /** Whether Edit HUD is open in place of the panel. */
    private boolean editingHud;
    /** The readout being dragged in Edit HUD, or {@code null}. */
    private HudLayout.Readout dragging;
    /** Where in the dragged readout the mouse took hold of it, in GUI units, so it does not jump to the cursor. */
    private float grabX;
    private float grabY;

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

    /** Tells the panel whether the game behind it is blurred: 1.8.9 cannot blur on every machine. */
    public void setBlurred(boolean blurred) {
        this.blurred = blurred;
    }

    /** The game's GUI scale, which the readouts are laid out in: whenever the screen draws. */
    public void setGuiScale(int guiScale) {
        this.guiScale = Math.max(1, guiScale);
    }

    /**
     * Whether Edit HUD is open: the panel is put away, and the screen shows
     * the game as it is, unblurred, so the readouts read as they will.
     */
    public boolean editingHud() {
        return editingHud;
    }

    /** The screen the panel is on has closed - by its key, perhaps mid-drag - and Edit HUD with it. */
    public void closed() {
        stopEditingHud();
    }

    private void startEditingHud() {
        editingHud = true;
        page = null;
        ashSettings = false;
        model.hudLayout().setEditing(true);
    }

    private void stopEditingHud() {
        editingHud = false;
        dragging = null;
        model.hudLayout().setEditing(false);
    }

    // ---- input, in real pixels ----

    /** A left click. Returns whether it landed on anything. */
    public boolean mouseClicked(int x, int y) {
        if (editingHud) {
            return hudEditClicked(x, y);
        }
        if (gearButton().contains(x, y)) {
            ashSettings = !ashSettings;
            page = null;
            return true;
        }
        if (editHudButton().contains(x, y)) {
            startEditingHud();
            return true;
        }
        if (page != null) {
            return pageSurface != null && page.click(pageSurface.toUnitsX(x), pageSurface.toUnitsY(y));
        }
        if (ashSettings) {
            return panel().contains(x, y);
        }
        for (Tab tabHere : tabs()) {
            if (tabHere.at.contains(x, y)) {
                tab = tabHere.category;
                filtered();
                return true;
            }
        }
        if (searchBox().contains(x, y)) {
            // Typing on the tiles always searches; the box is where it shows.
            return true;
        }
        List<SettingsScreen.Row> rows = visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            SettingsScreen.Row row = rows.get(i);
            Rect tile = tileAt(i);
            if (tile == null || !tile.contains(x, y)) {
                continue;
            }
            if (!row.available()) {
                say(row.whyUnavailable());
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

    /**
     * In Edit HUD: Done, Reset, or taking hold of a readout. Every click is
     * Edit HUD's, so one on the empty game does nothing rather than reach the
     * panel behind it.
     */
    private boolean hudEditClicked(int x, int y) {
        if (doneButton().contains(x, y)) {
            stopEditingHud();
            return true;
        }
        if (resetReadoutsButton().contains(x, y)) {
            model.resetReadouts();
            return true;
        }
        HudLayout layout = model.hudLayout();
        for (HudLayout.Readout readout : HudLayout.Readout.values()) {
            Rect box = readoutBox(readout);
            if (box != null && box.contains(x, y)) {
                Placement placement = layout.placement(readout);
                grabX = x / (float) guiScale - placement.x(layout.width(readout), guiWidth());
                grabY = y / (float) guiScale - placement.y(layout.height(readout), guiHeight());
                dragging = readout;
                layout.drag(readout, placement);
                return true;
            }
        }
        return true;
    }

    /** The mouse moved with the left button held, as when dragging a slider or a readout. */
    public void mouseDragged(int x, int y) {
        if (editingHud) {
            if (dragging != null) {
                HudLayout layout = model.hudLayout();
                int left = Math.round(x / (float) guiScale - grabX);
                int top = Math.round(y / (float) guiScale - grabY);
                layout.drag(dragging, Placement.nearest(left, top, layout.width(dragging), layout.height(dragging),
                        guiWidth(), guiHeight()));
            }
        } else if (page != null && pageSurface != null) {
            page.drag(pageSurface.toUnitsX(x), pageSurface.toUnitsY(y));
        }
    }

    /** A dragged readout stays where it was let go, and is saved there at once. */
    public void mouseReleased() {
        if (dragging != null) {
            HudLayout layout = model.hudLayout();
            Placement dropped = layout.placement(dragging);
            HudLayout.Readout readout = dragging;
            dragging = null;
            layout.endDrag();
            model.change(readout.position(), dropped);
        } else if (page != null) {
            page.release();
        }
    }

    /** The mouse wheel: positive is away from the player, which scrolls up. */
    public void mouseScrolled(double amount) {
        if (editingHud) {
            return;
        }
        if (page != null) {
            page.scroll(amount);
        } else if (amount > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (amount < 0) {
            scroll = Math.min(maxScroll(), scroll + 1);
        }
    }

    /** Escape goes back from a page or Edit HUD, and closes the panel from the tiles. */
    public void keyPressed(Key key) {
        if (editingHud) {
            if (key == Key.ESCAPE || key == Key.BACKSPACE || key == Key.ENTER) {
                stopEditingHud();
            }
            return;
        }
        if (page != null && page.keyPressed(key)) {
            return;
        }
        boolean onAPage = page != null || ashSettings;
        if (onAPage && (key == Key.ESCAPE || key == Key.BACKSPACE)) {
            page = null;
            ashSettings = false;
        } else if (!onAPage && key == Key.BACKSPACE && !query.isEmpty()) {
            query = query.substring(0, query.offsetByCodePoints(query.length(), -1));
            filtered();
        } else if (!onAPage && key == Key.ESCAPE && !query.isEmpty()) {
            // A search is cleared first, so Escape never closes on a player
            // who was only looking for something.
            query = "";
            filtered();
        } else if (key == Key.ESCAPE) {
            close.run();
        }
    }

    /** The longest search, in characters: longer than any feature's name. */
    private static final int MAX_QUERY = 40;

    /**
     * A character typed while the panel is open. On the tiles it searches
     * them, with no box to click first; on a page, a colour's box takes it.
     */
    public void charTyped(int codePoint) {
        if (editingHud) {
            return;
        }
        if (page != null) {
            if (page.typing()) {
                page.charTyped(codePoint);
            }
        } else if (!ashSettings && !Character.isISOControl(codePoint) && query.length() < MAX_QUERY) {
            query += new String(Character.toChars(codePoint));
            filtered();
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

    /** {@code amount} of the panel's unit - a hundredth of the screen's width - in whole pixels. */
    private int units(double amount) {
        return Math.round((float) (amount * unit()));
    }

    Rect panel() {
        int x = Math.round(width * 0.075f);
        int y = Math.round(height * 0.075f);
        return new Rect(x, y, width - 2 * x, height - 2 * y);
    }

    private int stripWidth() {
        return units(5.2);
    }

    /** The gear at the bottom of the strip: ash's own settings. Public so that the real-game tests can press it. */
    public Rect gearButton() {
        Rect panel = panel();
        int pad = units(0.75);
        int side = stripWidth() - 2 * pad;
        return new Rect(panel.x + pad, panel.y + panel.height - pad - side, side, side);
    }

    /** The strip above its buttons, where SETTINGS is drawn: for the real-game tests to compare across GUI scales. */
    public Rect lettersArea() {
        Rect panel = panel();
        return new Rect(panel.x, panel.y, stripWidth(), editHudButton().y - panel.y);
    }

    /** Edit HUD, above the gear. */
    public Rect editHudButton() {
        Rect gear = gearButton();
        return new Rect(gear.x, gear.y - units(0.6) - gear.height, gear.width, gear.height);
    }

    /** The screen's width in GUI units, as the game works it out: a part unit counts as a unit. */
    private int guiWidth() {
        return (width + guiScale - 1) / guiScale;
    }

    private int guiHeight() {
        return (height + guiScale - 1) / guiScale;
    }

    /**
     * Where a readout is outlined in Edit HUD, a GUI unit round its text;
     * {@code null} when it is switched off, or has not drawn yet to say how
     * big it is.
     */
    public Rect readoutBox(HudLayout.Readout readout) {
        HudLayout layout = model.hudLayout();
        int w = layout.width(readout);
        int h = layout.height(readout);
        if (!layout.shown(readout) || w == 0 || h == 0) {
            return null;
        }
        Placement placement = layout.placement(readout);
        int x = placement.x(w, guiWidth());
        int y = placement.y(h, guiHeight());
        return new Rect((x - 1) * guiScale, (y - 1) * guiScale, (w + 1) * guiScale, (h + 1) * guiScale);
    }

    /** Edit HUD's Done, top-right, back to the panel. */
    public Rect doneButton() {
        int w = units(5.5);
        int h = units(2.3);
        return new Rect(width - units(1.2) - w, units(1.2), w, h);
    }

    /** Edit HUD's Reset, beside Done: both readouts back where they started. */
    public Rect resetReadoutsButton() {
        Rect done = doneButton();
        int w = units(5.5);
        return new Rect(done.x - units(0.6) - w, done.y, w, done.height);
    }

    /** Where the tiles, or a page, go. */
    Rect main() {
        Rect panel = panel();
        int x = panel.x + stripWidth() + units(1.5);
        int y = panel.y + units(1.3);
        return new Rect(x, y, panel.x + panel.width - units(1.5) - x, panel.y + panel.height - units(1.1) - noticeHeight() - y);
    }

    private int gap() {
        return units(1);
    }

    /** The search box, at the start of the bar above the tiles. Public so that the real-game tests can find it. */
    public Rect searchBox() {
        Rect main = main();
        return new Rect(main.x, main.y, Math.min(units(19), main.width / 2), units(2.5));
    }

    /** The tabs above the tiles - All, then each category a feature here is filed under - right to left from the bar's end. */
    private List<Tab> tabs() {
        List<Tab> tabs = new ArrayList<>();
        tabs.add(new Tab(null));
        for (Category category : Category.values()) {
            for (SettingsScreen.Row row : model.rows()) {
                if (row.category() == category) {
                    tabs.add(new Tab(category));
                    break;
                }
            }
        }
        Rect main = main();
        Rect search = searchBox();
        float size = textSize(0.88f);
        int padX = units(0.95);
        int x = main.x + main.width;
        for (int i = tabs.size() - 1; i >= 0; i--) {
            Tab tab = tabs.get(i);
            int width = Ink.width(tab.label(), Ink.Weight.SEMIBOLD, size) + 2 * padX;
            x -= width;
            tab.at = new Rect(x, search.y, width, search.height);
            x -= units(0.35);
        }
        return tabs;
    }

    /** One tab: a category, or {@code null} for All, and where it is drawn. */
    private static final class Tab {
        final Category category;
        Rect at;

        Tab(Category category) {
            this.category = category;
        }

        String label() {
            return category == null ? "All" : category.displayName();
        }
    }

    /** A category's tab, or All's for {@code null}; {@code null} when there is no such tab or a page is open. */
    public Rect tabOf(Category category) {
        if (page != null || ashSettings) {
            return null;
        }
        for (Tab tab : tabs()) {
            if (tab.category == category) {
                return tab.at;
            }
        }
        return null;
    }

    /** Where the tiles go: the main area, below the bar with the search box and tabs. */
    Rect grid() {
        Rect main = main();
        int top = searchBox().height + units(1.2);
        return new Rect(main.x, main.y + top, main.width, main.height - top);
    }

    /**
     * The tiles on view: those in the selected tab whose name has what has
     * been typed in it, in the order the file lists them.
     */
    List<SettingsScreen.Row> visibleRows() {
        String wanted = query.trim().toLowerCase(Locale.ROOT);
        List<SettingsScreen.Row> rows = new ArrayList<>();
        for (SettingsScreen.Row row : model.rows()) {
            boolean inTab = tab == null || row.category() == tab;
            if (inTab && (wanted.isEmpty() || row.name().toLowerCase(Locale.ROOT).contains(wanted))) {
                rows.add(row);
            }
        }
        return rows;
    }

    /** What has been typed into the search box. */
    public String query() {
        return query;
    }

    private void filtered() {
        scroll = 0;
    }

    private int columns() {
        return Math.max(1, Math.round(main().width / (15.36f * unit())));
    }

    private int tileWidth() {
        return (main().width - gap() * (columns() - 1)) / columns();
    }

    private int tileHeight() {
        return units(13.5);
    }

    private int rowsOnView() {
        return Math.max(1, (grid().height + gap()) / (tileHeight() + gap()));
    }

    int maxScroll() {
        int rows = (visibleRows().size() + columns() - 1) / columns();
        return Math.max(0, rows - rowsOnView());
    }

    /** Where a feature's tile is, or {@code null} when it is filtered out, scrolled out of view or a page is open. */
    Rect tileOf(Feature feature) {
        List<SettingsScreen.Row> rows = visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).feature() == feature) {
                return tileAt(i);
            }
        }
        return null;
    }

    /** Where the tile at this place in the order is, or {@code null} when it is scrolled out of view or a page is open. */
    private Rect tileAt(int index) {
        if (page != null || ashSettings) {
            return null;
        }
        int row = index / columns();
        if (row < scroll || row >= scroll + rowsOnView()) {
            return null;
        }
        Rect grid = grid();
        return new Rect(grid.x + (index % columns()) * (tileWidth() + gap()),
                grid.y + (row - scroll) * (tileHeight() + gap()), tileWidth(), tileHeight());
    }

    /** The ENABLED / DISABLED button: beside the gear, or the tile's whole width when there is no gear (yet: #67). */
    private Rect toggleIn(Rect tile, boolean besideGear) {
        int pad = units(0.9);
        int side = units(2.3);
        int x = besideGear ? tile.x + pad + side + units(0.45) : tile.x + pad;
        return new Rect(x, tile.y + tile.height - units(0.85) - side, tile.x + tile.width - pad - x, side);
    }

    /** A feature's icon on its tile, or {@code null} when the tile is not on view: for the real-game tests to compare across GUI scales. */
    public Rect tileIconOf(Feature feature) {
        Rect tile = tileOf(feature);
        return tile == null ? null : glyphIn(tile);
    }

    /**
     * Where a tile's icon goes: 3.2 units square, centred, below the name as
     * the mockup spaces them. Package-private for the tests.
     */
    Rect glyphIn(Rect tile) {
        int side = units(3.2);
        int nameBottom = tile.y + units(0.95) + Ink.lineHeight(Ink.Weight.SEMIBOLD, textSize(0.95f));
        return new Rect(tile.x + (tile.width - side) / 2, nameBottom + units(0.55 + 0.35), side, side);
    }

    private Rect gearIn(Rect tile) {
        int side = units(2.3);
        return new Rect(tile.x + units(0.9), tile.y + tile.height - units(0.85) - side, side, side);
    }

    private int noticeHeight() {
        return model.footer().isEmpty() ? 0 : Ink.lineHeight(Ink.Weight.REGULAR, textSize(0.85f)) + units(0.6);
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
            if (row.feature() == feature && hasGear(row)) {
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
        if (editingHud) {
            drawHudEditing(canvas, mouseX, mouseY);
            return;
        }
        scroll = Math.min(scroll, maxScroll());
        canvas.fill(0, 0, width, height, blurred ? Palette.OVERLAY : Palette.OVERLAY_UNBLURRED);

        Rect panel = panel();
        int radius = units(1.1);
        Paint.shadow(canvas, panel.x, panel.y, panel.width, panel.height, radius, units(2.4), units(0.8),
                Palette.PANEL_SHADOW_ALPHA);
        int strip = stripWidth();
        Paint.roundRect(canvas, panel.x, panel.y, strip, panel.height, radius, Palette.STRIP, 1f, true, false);
        Paint.roundRect(canvas, panel.x + strip, panel.y, panel.width - strip, panel.height, radius, Palette.PANEL, 1f,
                false, true);
        canvas.fill(panel.x + strip, panel.y, 1, panel.height, Palette.LINE);
        Paint.outline(canvas, panel.x, panel.y, panel.width, panel.height, radius, Palette.PANEL_EDGE);

        drawLetters(canvas, panel);
        drawIconButton(canvas, editHudButton(), Ink.Icon.LAYOUT, units(0.65), 0.52f, 1f, mouseX, mouseY);
        drawIconButton(canvas, gearButton(), Ink.Icon.GEAR, units(0.65), 0.52f, 1f, mouseX, mouseY);

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
        // As the mockup stacks them: a line one size high, and half a unit between.
        int step = Math.round(size) + units(0.5);
        int capTop = Ink.lineHeight(Ink.Weight.EXTRABOLD, size) - Ink.capHeight(Ink.Weight.EXTRABOLD, size);
        int y = panel.y + units(1.3) - capTop / 2;
        for (int i = 0; i < LETTERS.length(); i++) {
            String letter = LETTERS.substring(i, i + 1);
            int x = panel.x + (stripWidth() - Ink.width(letter, Ink.Weight.EXTRABOLD, size)) / 2;
            Ink.text(letter, Ink.Weight.EXTRABOLD, size, Palette.TEXT).drawAt(canvas, x, y, 1f);
            y += step;
        }
    }

    /** A square button with an icon in its middle, {@code iconShare} of its width; it lights up under the mouse unless faded. */
    private void drawIconButton(Canvas canvas, Rect button, Ink.Icon icon, int radius, float iconShare, float opacity,
            int mouseX, int mouseY) {
        int fill = button.contains(mouseX, mouseY) && opacity >= 1f ? Palette.RAISED_HOVER : Palette.RAISED;
        Paint.roundRect(canvas, button.x, button.y, button.width, button.height, radius, fill, opacity);
        int size = Math.round(button.width * iconShare);
        canvas.draw(Ink.icon(icon, size, Palette.ICON), button.x + (button.width - size) / 2,
                button.y + (button.height - size) / 2, opacity);
    }

    private void drawTiles(Canvas canvas, int mouseX, int mouseY) {
        drawSearch(canvas);
        drawTabs(canvas, mouseX, mouseY);

        Rect grid = grid();
        List<SettingsScreen.Row> rows = visibleRows();
        if (rows.isEmpty()) {
            float size = textSize(0.92f);
            String none = fit("No feature matches “" + query.trim() + "”.", Ink.Weight.REGULAR, size,
                    grid.width);
            int width = Ink.width(none, Ink.Weight.REGULAR, size);
            Ink.text(none, Ink.Weight.REGULAR, size, Palette.MUTED)
                    .drawAt(canvas, grid.x + (grid.width - width) / 2, grid.y + units(4), 1f);
            return;
        }
        canvas.clip(grid.x, grid.y, grid.width, grid.height);
        for (int i = 0; i < rows.size(); i++) {
            Rect tile = tileAt(i);
            if (tile != null) {
                drawTile(canvas, rows.get(i), tile, mouseX, mouseY);
            }
        }
        canvas.unclip();
        drawScrollBar(canvas, grid);
    }

    /**
     * The search box: the search icon, then what has been typed and a caret,
     * or the placeholder. Outlined while it holds a search, as a focused
     * field is in the mockup - on the tiles, typing always goes to it.
     */
    private void drawSearch(Canvas canvas) {
        Rect box = searchBox();
        int radius = units(0.7);
        boolean searching = !query.isEmpty();
        Paint.roundRect(canvas, box.x, box.y, box.width, box.height, radius,
                searching ? Palette.RAISED_HOVER : Palette.RAISED, 1f);
        if (searching) {
            Paint.outline(canvas, box.x, box.y, box.width, box.height, radius, Palette.FOCUS);
        }
        int pad = units(0.85);
        int iconSize = units(1.05);
        canvas.draw(Ink.icon(Ink.Icon.SEARCH, iconSize, Palette.ICON), box.x + pad,
                box.y + (box.height - iconSize) / 2, 1f);

        float size = textSize(0.92f);
        int textX = box.x + pad + iconSize + units(0.55);
        int room = box.x + box.width - pad - textX;
        int lineHeight = Ink.lineHeight(Ink.Weight.REGULAR, size);
        int textY = box.y + (box.height - lineHeight) / 2;
        if (!searching) {
            Ink.text(fit("Search features", Ink.Weight.REGULAR, size, room), Ink.Weight.REGULAR, size,
                    Palette.PLACEHOLDER).drawAt(canvas, textX, textY, 1f);
            return;
        }
        // The end of a long search, so what is being typed is what shows.
        String shown = query;
        while (shown.length() > 1 && Ink.width(shown, Ink.Weight.REGULAR, size) > room - units(0.3)) {
            shown = shown.substring(shown.offsetByCodePoints(0, 1));
        }
        Ink.text(shown, Ink.Weight.REGULAR, size, Palette.TEXT).drawAt(canvas, textX, textY, 1f);
        int caretX = textX + Ink.width(shown, Ink.Weight.REGULAR, size) + Math.max(1, units(0.08));
        canvas.fill(caretX, textY + lineHeight / 8, Math.max(1, units(0.08)), lineHeight * 3 / 4, Palette.TEXT);
    }

    /** All, then each category in use: the selected one raised and white, the rest quieter until the mouse is on them. */
    private void drawTabs(Canvas canvas, int mouseX, int mouseY) {
        float size = textSize(0.88f);
        int lineHeight = Ink.lineHeight(Ink.Weight.SEMIBOLD, size);
        for (Tab each : tabs()) {
            Rect at = each.at;
            boolean selected = each.category == tab;
            if (selected) {
                Paint.roundRect(canvas, at.x, at.y, at.width, at.height, units(0.6), Palette.RAISED_HOVER, 1f);
            }
            int colour = selected || at.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
            int width = Ink.width(each.label(), Ink.Weight.SEMIBOLD, size);
            Ink.text(each.label(), Ink.Weight.SEMIBOLD, size, colour)
                    .drawAt(canvas, at.x + (at.width - width) / 2, at.y + (at.height - lineHeight) / 2, 1f);
        }
    }

    /** Each feature's icon, from Lucide, as the approved mockup chose them; {@code null} for one with none yet. */
    static Ink.Icon iconOf(Feature feature) {
        switch (feature) {
            case FPS_READOUT:
                return Ink.Icon.GAUGE;
            case TOGGLE_SPRINT:
                return Ink.Icon.SPRINT;
            case CROSSHAIR:
                return Ink.Icon.CROSSHAIR;
            case HIT_INDICATOR:
                return Ink.Icon.HIT;
            case FREELOOK:
                return Ink.Icon.EYE;
            case SNAPLOOK:
                return Ink.Icon.ROTATE;
            case PING_READOUT:
                return Ink.Icon.SIGNAL;
            case HIT_COLOUR:
                return Ink.Icon.DROPLET;
            default:
                return null;
        }
    }

    private void drawTile(Canvas canvas, SettingsScreen.Row row, Rect tile, int mouseX, int mouseY) {
        boolean available = row.available();
        float opacity = available ? 1f : 0.45f;
        int fill = available && tile.contains(mouseX, mouseY) ? Palette.RAISED_HOVER : Palette.RAISED;
        Paint.shadow(canvas, tile.x, tile.y, tile.width, tile.height, units(0.9), units(0.9), units(0.3),
                Palette.TILE_SHADOW_ALPHA);
        Paint.roundRect(canvas, tile.x, tile.y, tile.width, tile.height, units(0.9), fill, opacity);

        float nameSize = textSize(0.95f);
        String name = fit(row.name(), Ink.Weight.SEMIBOLD, nameSize, tile.width - 2 * units(0.9));
        int nameWidth = Ink.width(name, Ink.Weight.SEMIBOLD, nameSize);
        int nameTop = tile.y + units(0.95);
        Ink.text(name, Ink.Weight.SEMIBOLD, nameSize, Palette.TEXT)
                .drawAt(canvas, tile.x + (tile.width - nameWidth) / 2, nameTop, opacity);

        Ink.Icon icon = iconOf(row.feature());
        if (icon != null) {
            Rect glyph = glyphIn(tile);
            canvas.draw(Ink.icon(icon, glyph.width, Palette.ICON), glyph.x, glyph.y, opacity);
        }

        if (hasGear(row)) {
            drawIconButton(canvas, gearIn(tile), Ink.Icon.GEAR, units(0.55), 0.55f, 1f, mouseX, mouseY);
        }

        Rect toggle = toggleIn(tile, hasGear(row));
        int colour = !available ? Palette.UNAVAILABLE : row.on() ? Palette.GREEN : Palette.RED;
        Paint.roundRect(canvas, toggle.x, toggle.y, toggle.width, toggle.height, units(0.55), colour, opacity);
        String label = !available ? "UNAVAILABLE" : row.on() ? "ENABLED" : "DISABLED";
        float labelSize = textSize(0.74f);
        // Spaced out a little, as the mockup's capitals are: 0.06 of the size.
        Raster text = Ink.text(label, Ink.Weight.BOLD, labelSize, Palette.TEXT, LABEL_TRACKING);
        int labelWidth = Ink.width(label, Ink.Weight.BOLD, labelSize, LABEL_TRACKING);
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
        int x = main.x + main.width + units(0.5);
        int rows = max + rowsOnView();
        int thumb = Math.max(units(2), main.height * rowsOnView() / rows);
        int thumbY = main.y + (main.height - thumb) * scroll / max;
        Paint.roundRect(canvas, x, main.y, units(0.3), main.height, units(0.15), Palette.RAISED, 1f);
        Paint.roundRect(canvas, x, thumbY, units(0.3), thumb, units(0.15), Palette.MUTED, 1f);
    }

    private void drawPage(Canvas canvas, int mouseX, int mouseY) {
        Rect main = main();
        float scale = 0.105f * unit();
        pageSurface = new CanvasScreenSurface(canvas, scale, main.x, main.y);
        Rect area = new Rect(0, 0, (int) Math.floor(main.width / scale), (int) Math.floor(main.height / scale));
        page.render(pageSurface, area, pageSurface.toUnitsX(mouseX), pageSurface.toUnitsY(mouseY));
    }

    /**
     * Edit HUD, as the prototype on {@code prototype/ash-ui} has it: the game
     * lightly dimmed, each readout that is on outlined and tagged with the
     * anchor it will keep, a hint along the top, and Reset and Done. The
     * readouts themselves are the game's, drawn beneath. While one is
     * dragged, everything but the boxes steps out of the way.
     */
    private void drawHudEditing(Canvas canvas, int mouseX, int mouseY) {
        canvas.fill(0, 0, width, height, Palette.HUD_EDIT_SCRIM);
        boolean any = false;
        for (HudLayout.Readout readout : HudLayout.Readout.values()) {
            Rect box = readoutBox(readout);
            if (box == null) {
                continue;
            }
            any = true;
            boolean held = readout == dragging || (dragging == null && box.contains(mouseX, mouseY));
            canvas.fill(box.x, box.y, box.width, box.height, held ? Palette.HUD_BOX_HOVER : Palette.HUD_BOX);
            drawDashed(canvas, box, Math.max(1, units(0.1)), Palette.TEXT);
            drawTag(canvas, readout, box);
        }
        if (dragging != null) {
            return;
        }

        String hint = any ? "Drag the readouts anywhere. They keep their place when the window changes size."
                : "Switch the FPS or ping readout on to move it here.";
        float size = textSize(0.85f);
        Rect done = doneButton();
        int w = Ink.width(hint, Ink.Weight.REGULAR, size) + 2 * units(0.8);
        int x = (width - w) / 2;
        Paint.roundRect(canvas, x, done.y, w, done.height, units(0.35), Palette.SCRIM, 1f);
        Paint.outline(canvas, x, done.y, w, done.height, units(0.35), Palette.LINE);
        Ink.text(hint, Ink.Weight.REGULAR, size, Palette.TEXT).drawAt(canvas, x + units(0.8),
                done.y + (done.height - Ink.lineHeight(Ink.Weight.REGULAR, size)) / 2, 1f);

        drawTextButton(canvas, resetReadoutsButton(), "Reset", false, mouseX, mouseY);
        drawTextButton(canvas, done, "Done", true, mouseX, mouseY);
    }

    /** A rectangle's edge in dashes, {@code thickness} thick. */
    private void drawDashed(Canvas canvas, Rect box, int thickness, int colour) {
        int dash = Math.max(2, units(0.45));
        int step = dash + Math.max(2, units(0.3));
        for (int x = box.x; x < box.x + box.width; x += step) {
            int w = Math.min(dash, box.x + box.width - x);
            canvas.fill(x, box.y, w, thickness, colour);
            canvas.fill(x, box.y + box.height - thickness, w, thickness, colour);
        }
        for (int y = box.y; y < box.y + box.height; y += step) {
            int h = Math.min(dash, box.y + box.height - y);
            canvas.fill(box.x, y, thickness, h, colour);
            canvas.fill(box.x + box.width - thickness, y, thickness, h, colour);
        }
    }

    /**
     * "FPS readout · Top left" beside a readout's box - to its right, or its
     * left where there is no room - so the tags of readouts stacked one above
     * the other, as they start, never cover the next box.
     */
    private void drawTag(Canvas canvas, HudLayout.Readout readout, Rect box) {
        String tag = readout.feature().displayName() + " · "
                + model.hudLayout().placement(readout).anchor().label();
        float size = textSize(0.72f);
        int padX = units(0.45);
        int padY = units(0.15);
        int w = Ink.width(tag, Ink.Weight.REGULAR, size) + 2 * padX;
        int h = Ink.lineHeight(Ink.Weight.REGULAR, size) + 2 * padY;
        int x = box.x + box.width + units(0.4);
        if (x + w > width) {
            x = Math.max(0, box.x - units(0.4) - w);
        }
        int y = Math.max(0, Math.min(box.y + (box.height - h) / 2, height - h));
        Paint.roundRect(canvas, x, y, w, h, units(0.2), Palette.SCRIM, 1f);
        Ink.text(tag, Ink.Weight.REGULAR, size, Palette.MUTED).drawAt(canvas, x + padX, y + padY, 1f);
    }

    /** A button with a word on it: white with dark text when it is the one to press, quiet otherwise. */
    private void drawTextButton(Canvas canvas, Rect button, String label, boolean primary, int mouseX, int mouseY) {
        int radius = units(0.35);
        int fill = primary ? Palette.TEXT : button.contains(mouseX, mouseY) ? Palette.TOAST : Palette.SCRIM;
        Paint.roundRect(canvas, button.x, button.y, button.width, button.height, radius, fill, 1f);
        if (!primary) {
            Paint.outline(canvas, button.x, button.y, button.width, button.height, radius, Palette.LINE);
        }
        Ink.Weight weight = primary ? Ink.Weight.SEMIBOLD : Ink.Weight.REGULAR;
        float size = textSize(0.85f);
        int textWidth = Ink.width(label, weight, size);
        int lineHeight = Ink.lineHeight(weight, size);
        Ink.text(label, weight, size, primary ? Palette.PRIMARY_TEXT : Palette.TEXT)
                .drawAt(canvas, button.x + (button.width - textWidth) / 2, button.y + (button.height - lineHeight) / 2, 1f);
    }

    /** A placeholder until ash's own settings arrive with #69. */
    private void drawAshSettings(Canvas canvas) {
        Rect main = main();
        float title = textSize(1.35f);
        Ink.text("ash settings", Ink.Weight.BOLD, title, Palette.TEXT).drawAt(canvas, main.x, main.y, 1f);
        float body = textSize(0.92f);
        Ink.text("Coming soon: the open key, interface size, animations and blur.", Ink.Weight.REGULAR, body, Palette.MUTED)
                .drawAt(canvas, main.x, main.y + Ink.lineHeight(Ink.Weight.BOLD, title) + units(0.8), 1f);
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
                .drawAt(canvas, main.x, main.y + main.height + units(0.4), 1f);
    }

    private void drawToast(Canvas canvas, Rect panel) {
        if (toast == null || clock.getAsLong() - toastAt > TOAST_NANOS) {
            toast = null;
            return;
        }
        float size = textSize(0.92f);
        int textWidth = Ink.width(toast, Ink.Weight.REGULAR, size);
        int lineHeight = Ink.lineHeight(Ink.Weight.REGULAR, size);
        int w = textWidth + 2 * units(1.1);
        int h = lineHeight + 2 * units(0.6);
        int x = panel.x + (panel.width - w) / 2;
        int y = panel.y + panel.height - units(1.2) - h;
        Paint.roundRect(canvas, x, y, w, h, units(0.8), Palette.TOAST, 1f);
        Ink.text(toast, Ink.Weight.REGULAR, size, Palette.TEXT).drawAt(canvas, x + units(1.1), y + units(0.6), 1f);
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
