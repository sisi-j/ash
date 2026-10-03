package com.ashlauncher.client.ui;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Category;
import com.ashlauncher.client.settings.Choice;
import com.ashlauncher.client.settings.Colour;
import com.ashlauncher.client.settings.OnOff;
import com.ashlauncher.client.settings.Whole;
import com.ashlauncher.client.settings.SettingsScreen;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;

/**
 * ash's settings panel: drawn by ash, the same on both version targets.
 *
 * <p>The layout is the product owner's pick from the prototype on branch
 * {@code prototype/ash-ui}, design A: a centred window with the wordmark,
 * search and close along the top; categories down the left with counts; a
 * card per feature with its switch; and a footer. It is a stand-in until the
 * final design brief, so the look lives here, in one class, and the
 * behaviour lives in {@link SettingsScreen}. A feature with options has an
 * "Options" link on its card, which opens its {@link OptionsPage} in place of
 * the cards. Still to come: "Edit HUD", with #41.
 *
 * <p>The cards are laid out afresh from the screen size and the font on every
 * frame and every click; an options page records where it drew each control
 * and matches a click against that record. Either way, what a click hits is
 * what is on screen. Everything is in GUI units and made of rectangles and
 * text.
 *
 * <p>The version target's screen passes the game's input on - a click, a
 * scroll, a typed character, a {@link Key} - and draws through a
 * {@link ScreenSurface}. Escape closes the panel; the key that opened it is a
 * key binding, the target's to recognise.
 */
public final class Panel {

    private static final int TOP_BAR_HEIGHT = 22;
    private static final int PAD = 8;
    private static final int GAP = 6;
    private static final int SWITCH_WIDTH = 18;
    private static final int SWITCH_HEIGHT = 10;
    /** From this wide, in GUI units, three columns of cards fit; below it, two. */
    private static final int THREE_COLUMNS_FROM = 270;
    private static final int MAX_QUERY = 32;

    private final SettingsScreen model;
    private final Supplier<String> closeKeyName;
    private final Runnable close;
    private int width = 427;
    private int height = 240;
    /** The font's line height, as last drawn: every height below is built from it. */
    private int lineHeight = 9;
    /** As wide as the widest category's name and count need, in the font as last drawn. */
    private int categoryColumnWidth = 78;
    /** The chosen category, or {@code null} for all of them. */
    private Category category;
    private String query = "";
    /** The first row of cards on view. */
    private int scroll;
    /** The feature whose options are open in place of the cards, or {@code null}. */
    private OptionsPage page;
    /** How wide "Options >" draws, as last measured. */
    private int optionsLinkWidth = 50;

    /**
     * @param closeKeyName what the key that opens and closes the panel is
     *     called right now - a player may have moved it in Controls
     * @param close closes the screen the panel is on
     */
    public Panel(SettingsScreen model, Supplier<String> closeKeyName, Runnable close) {
        this.model = model;
        this.closeKeyName = closeKeyName;
        this.close = close;
    }

    /** The screen's size in GUI units: whenever the screen opens or the window changes. */
    public void resize(int width, int height) {
        this.width = width;
        this.height = height;
        scroll = Math.min(scroll, maxScroll());
    }

    // ---- input ----

    /** A left click at a point in GUI units. Returns whether it landed on anything. */
    public boolean mouseClicked(int x, int y) {
        if (closeButton().contains(x, y)) {
            close.run();
            return true;
        }
        if (categoryRect(null).contains(x, y)) {
            choose(null);
            return true;
        }
        for (Category each : categories()) {
            if (categoryRect(each).contains(x, y)) {
                choose(each);
                return true;
            }
        }
        if (page != null) {
            return page.click(x, y) || searchBox().contains(x, y);
        }
        for (SettingsScreen.Row row : visibleRows()) {
            Rect card = cardOf(row.feature());
            if (card == null) {
                continue;
            }
            if (switchIn(card).contains(x, y)) {
                row.press();
                return true;
            }
            if (row.hasOptions() && row.available() && optionsLinkIn(card).contains(x, y)) {
                page = new OptionsPage(model, row, () -> page = null);
                return true;
            }
        }
        return searchBox().contains(x, y);
    }

    /** The mouse moved with the left button held, as when dragging a slider. */
    public void mouseDragged(int x, int y) {
        if (page != null) {
            page.drag(x, y);
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
            return;
        }
        if (amount > 0) {
            scroll = Math.max(0, scroll - 1);
        } else if (amount < 0) {
            scroll = Math.min(maxScroll(), scroll + 1);
        }
    }

    public void keyPressed(Key key) {
        if (page != null && page.keyPressed(key)) {
            return;
        }
        if (page != null && key == Key.BACKSPACE) {
            // As typing does: the search is the cards', so editing it goes back to them.
            page = null;
        }
        if (key == Key.ESCAPE) {
            close.run();
        } else if (key == Key.BACKSPACE && !query.isEmpty()) {
            int cut = query.offsetByCodePoints(query.length(), -1);
            setQuery(query.substring(0, cut));
        }
    }

    /**
     * A character typed while the panel is open, as a whole code point, so
     * nothing here can split one in half. It goes to a colour's box while one
     * is being typed in, and otherwise to the search - which, typed into from
     * a feature's options, goes back to the cards to show what it finds.
     * Control characters - what Shift or an arrow key types on 1.8.9 - are
     * ignored.
     */
    public void charTyped(int codePoint) {
        if (page != null && page.typing()) {
            page.charTyped(codePoint);
            return;
        }
        if (codePoint < ' ' || codePoint == 127 || !Character.isValidCodePoint(codePoint)
                || query.codePointCount(0, query.length()) >= MAX_QUERY) {
            return;
        }
        page = null;
        setQuery(new StringBuilder(query).appendCodePoint(codePoint).toString());
    }

    private void choose(Category which) {
        category = which;
        scroll = 0;
        page = null;
    }

    private void setQuery(String text) {
        query = text;
        scroll = 0;
    }

    // ---- where things are, for clicks and for anything that has to point at them ----

    /**
     * Where a feature's switch is drawn, or {@code null} when its card is not
     * on view - filtered out, or scrolled away. Public so that the real-game
     * tests can click it as a player would.
     */
    public Rect switchOf(Feature feature) {
        if (page != null) {
            return page.feature() == feature ? page.target("switch:" + feature.id()) : null;
        }
        Rect card = cardOf(feature);
        return card == null ? null : switchIn(card);
    }

    /** Where a feature's "Options" link is on its card, or {@code null} when it has none or its card is not on view. */
    public Rect optionsLinkOf(Feature feature) {
        if (page != null) {
            return null;
        }
        for (SettingsScreen.Row row : visibleRows()) {
            if (row.feature() == feature && row.hasOptions() && row.available()) {
                Rect card = cardOf(feature);
                return card == null ? null : optionsLinkIn(card);
            }
        }
        return null;
    }

    // Where the open options page drew each control, for the real-game tests
    // to click as a player would; null when it is not on view.

    /** An on/off option's switch on its feature's page. */
    public Rect switchOf(OnOff option) {
        return page == null ? null : page.target("flag:" + option.key());
    }

    public Rect choiceOf(Choice choice, String id) {
        return page == null ? null : page.target("choice:" + choice.key() + ":" + id);
    }

    /** The point along a whole number's slider that stands for {@code value}. */
    public Rect sliderAt(Whole whole, int value) {
        return page == null ? null : page.pointOn("slider:" + whole.key(), value, whole.min(), whole.max());
    }

    public Rect swatchOf(Colour colour, int rgb) {
        return page == null ? null : page.target("swatch:" + colour.key() + ":" + OptionsPage.hex(rgb));
    }

    public Rect hexBoxOf(Colour colour) {
        return page == null ? null : page.target("hex:" + colour.key());
    }

    public Rect opacitySlider(Colour colour) {
        return page == null ? null : page.target("opacity:" + colour.key());
    }

    /** The point along a colour's opacity slider that stands for {@code percent}. */
    public Rect opacityAt(Colour colour, int percent) {
        int min = (int) Math.round(Colour.MIN_ALPHA * 100 / 255.0);
        return page == null ? null : page.pointOn("opacity:" + colour.key(), percent, min, 100);
    }

    public Rect resetToDefaults() {
        return page == null ? null : page.target("reset");
    }

    public Rect backLink() {
        return page == null ? null : page.target("back");
    }

    Rect cardOf(Feature feature) {
        List<SettingsScreen.Row> rows = visibleRows();
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).feature() == feature) {
                int row = i / columns();
                if (row < scroll || row >= scroll + rowsOnView()) {
                    return null;
                }
                Rect grid = grid();
                int x = grid.x + (i % columns()) * (cardWidth() + GAP);
                int y = grid.y + (row - scroll) * (cardHeight() + GAP);
                return new Rect(x, y, cardWidth(), cardHeight());
            }
        }
        return null;
    }

    /** The row for a category's name, "All" included. */
    Rect categoryAt(String name) {
        if (name.equals("All")) {
            return categoryRect(null);
        }
        for (Category each : categories()) {
            if (each.displayName().equals(name)) {
                return categoryRect(each);
            }
        }
        throw new IllegalArgumentException("no category " + name);
    }

    Rect closeButton() {
        Rect panel = panel();
        return new Rect(panel.x + panel.width - PAD - 12, panel.y + (TOP_BAR_HEIGHT - 12) / 2, 12, 12);
    }

    int maxScroll() {
        int rows = (visibleRows().size() + columns() - 1) / columns();
        return Math.max(0, rows - rowsOnView());
    }

    private Rect panel() {
        int w = Math.min(width - 16, Math.max(300, width * 4 / 5));
        int h = Math.min(height - 12, Math.max(170, height * 82 / 100));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    /**
     * The category column, measured: the widest name and the widest count,
     * five units inside each end of the row, six between them, and four
     * either side of the row - so no name ever runs into its count.
     */
    private int measureCategoryColumn(ScreenSurface surface) {
        int widestName = surface.textWidth("All");
        for (Category each : categories()) {
            widestName = Math.max(widestName, surface.textWidth(each.displayName()));
        }
        int widestCount = surface.textWidth(String.valueOf(count(null)));
        return widestName + widestCount + 5 + 6 + 5 + 8;
    }

    private int footerHeight() {
        return lineHeight + 5;
    }

    /** The name, two lines of description, and a line for the "Options" link. */
    private int cardHeight() {
        return 6 + lineHeight + 5 + 2 * lineHeight + 4 + lineHeight + 5;
    }

    private Rect optionsLinkIn(Rect card) {
        return new Rect(card.x + 4, card.y + card.height - lineHeight - 6, optionsLinkWidth + 4, lineHeight + 3);
    }

    private Rect searchBox() {
        Rect panel = panel();
        int left = panel.x + PAD + 30;
        int h = lineHeight + 5;
        return new Rect(left, panel.y + (TOP_BAR_HEIGHT - h) / 2, closeButton().x - 8 - left, h);
    }

    private Rect categoryRect(Category which) {
        Rect panel = panel();
        int index = which == null ? 0 : categories().indexOf(which) + 1;
        int h = lineHeight + 4;
        return new Rect(panel.x + 4, panel.y + TOP_BAR_HEIGHT + 6 + index * (h + 2), categoryColumnWidth - 8, h);
    }

    /** Where the cards go: the body, less room for the notice when there is one. */
    private Rect grid() {
        Rect panel = panel();
        int top = panel.y + TOP_BAR_HEIGHT + PAD;
        int bottom = panel.y + panel.height - footerHeight() - PAD - noticeHeight();
        return new Rect(panel.x + categoryColumnWidth + PAD, top, panel.width - categoryColumnWidth - 2 * PAD,
                bottom - top);
    }

    private int noticeHeight() {
        return model.footer().isEmpty() ? 0 : 2 * lineHeight + 4;
    }

    private int columns() {
        return grid().width >= THREE_COLUMNS_FROM ? 3 : 2;
    }

    private int cardWidth() {
        return (grid().width - GAP * (columns() - 1)) / columns();
    }

    private int rowsOnView() {
        return Math.max(1, (grid().height + GAP) / (cardHeight() + GAP));
    }

    private static Rect switchIn(Rect card) {
        return new Rect(card.x + card.width - 6 - SWITCH_WIDTH, card.y + 5, SWITCH_WIDTH, SWITCH_HEIGHT);
    }

    // ---- what is shown ----

    /** The categories that have at least one switch, in their declared order. */
    private List<Category> categories() {
        List<Category> present = new ArrayList<>();
        for (Category each : Category.values()) {
            if (count(each) > 0) {
                present.add(each);
            }
        }
        return present;
    }

    private int count(Category which) {
        int count = 0;
        for (SettingsScreen.Row row : model.rows()) {
            if (which == null || row.category() == which) {
                count++;
            }
        }
        return count;
    }

    private List<SettingsScreen.Row> visibleRows() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SettingsScreen.Row> visible = new ArrayList<>();
        for (SettingsScreen.Row row : model.rows()) {
            boolean inCategory = category == null || row.category() == category;
            String haystack = (row.name() + " " + row.description() + " " + row.category().displayName())
                    .toLowerCase(Locale.ROOT);
            if (inCategory && (needle.isEmpty() || haystack.contains(needle))) {
                visible.add(row);
            }
        }
        return visible;
    }

    // ---- drawing ----

    /** Draws the panel; the mouse position, in GUI units, decides what is highlighted. */
    public void render(ScreenSurface surface, int mouseX, int mouseY) {
        lineHeight = surface.lineHeight();
        categoryColumnWidth = measureCategoryColumn(surface);
        scroll = Math.min(scroll, maxScroll());
        surface.fill(0, 0, width, height, Palette.DIM);
        Rect panel = panel();
        Shapes.bordered(surface, panel, 3, Palette.LINE, Palette.PANEL);

        drawTopBar(surface, panel, mouseX, mouseY);
        drawCategories(surface, panel, mouseX, mouseY);
        if (page != null) {
            page.render(surface, grid(), mouseX, mouseY);
        } else {
            drawCards(surface, mouseX, mouseY);
            drawScrollBar(surface);
        }
        drawNotice(surface);
        drawFooter(surface, panel);
    }

    private void drawTopBar(ScreenSurface surface, Rect panel, int mouseX, int mouseY) {
        int textY = panel.y + (TOP_BAR_HEIGHT - lineHeight) / 2 + 1;
        surface.drawText("ash", panel.x + PAD, textY, Palette.TEXT);

        Rect search = searchBox();
        Shapes.bordered(surface, search, 2, Palette.LINE, Palette.RAISED);
        int textX = search.x + 5;
        int room = search.width - 12;
        int searchTextY = search.y + 3;
        if (query.isEmpty()) {
            surface.drawText(Text.fit(surface, "Search features", room), textX, searchTextY, Palette.MUTED);
        } else {
            String shown = Text.tail(surface, query, room);
            surface.drawText(shown, textX, searchTextY, Palette.TEXT);
            surface.fill(textX + surface.textWidth(shown) + 1, searchTextY, 1, lineHeight - 1, Palette.TEXT);
        }

        Rect close = closeButton();
        int closeColour = close.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
        surface.drawText("x", close.centreX() - surface.textWidth("x") / 2, close.y + (close.height - lineHeight) / 2 + 1,
                closeColour);

        surface.fill(panel.x + 1, panel.y + TOP_BAR_HEIGHT, panel.width - 2, 1, Palette.LINE);
    }

    private void drawCategories(ScreenSurface surface, Rect panel, int mouseX, int mouseY) {
        List<Category> all = new ArrayList<>();
        all.add(null);
        all.addAll(categories());
        for (Category each : all) {
            Rect at = categoryRect(each);
            boolean chosen = each == category;
            if (chosen) {
                Shapes.rounded(surface, at, 2, Palette.RAISED);
            }
            int colour = chosen || at.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
            String name = each == null ? "All" : each.displayName();
            String count = String.valueOf(count(each));
            surface.drawText(name, at.x + 5, at.y + 2, colour);
            surface.drawText(count, at.x + at.width - 5 - surface.textWidth(count), at.y + 2, Palette.MUTED);
        }
        int top = panel.y + TOP_BAR_HEIGHT + 1;
        surface.fill(panel.x + categoryColumnWidth, top, 1, panel.height - TOP_BAR_HEIGHT - footerHeight() - 1,
                Palette.LINE);
    }

    private void drawCards(ScreenSurface surface, int mouseX, int mouseY) {
        List<SettingsScreen.Row> rows = visibleRows();
        if (rows.isEmpty()) {
            Rect grid = grid();
            surface.drawText(Text.fit(surface, "Nothing matches \"" + query.trim() + "\".", grid.width), grid.x, grid.y + 2,
                    Palette.MUTED);
            return;
        }
        for (SettingsScreen.Row row : rows) {
            Rect card = cardOf(row.feature());
            if (card == null) {
                continue;
            }
            int border = card.contains(mouseX, mouseY) ? Palette.EMPHASIS : Palette.LINE;
            Shapes.bordered(surface, card, 2, border, Palette.RAISED);

            Rect toggle = switchIn(card);
            int nameRoom = toggle.x - card.x - 10;
            surface.drawText(Text.fit(surface, row.name(), nameRoom), card.x + 6, card.y + 6, Palette.TEXT);
            Shapes.onOffSwitch(surface, toggle, row.on(), row.available());

            // A feature that did not load offers no options: they would change nothing.
            if (row.hasOptions() && row.available()) {
                String options = "Options >";
                optionsLinkWidth = surface.textWidth(options);
                Rect link = optionsLinkIn(card);
                surface.drawText(options, link.x + 2, link.y + 2, link.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED);
            }

            int textTop = card.y + 6 + lineHeight + 5;
            if (!row.available()) {
                surface.drawText("Did not load", card.x + 6, textTop, Palette.MUTED);
            } else {
                for (String line : Text.wrap(surface, row.description(), card.width - 12, 2)) {
                    surface.drawText(line, card.x + 6, textTop, Palette.MUTED);
                    textTop += lineHeight;
                }
            }
        }
    }

    /** A thin bar beside the cards when there are more rows than fit, its thumb where the view is. */
    private void drawScrollBar(ScreenSurface surface) {
        int max = maxScroll();
        if (max == 0) {
            return;
        }
        Rect grid = grid();
        int x = grid.x + grid.width + 3;
        int rows = max + rowsOnView();
        int thumb = Math.max(6, grid.height * rowsOnView() / rows);
        int thumbY = grid.y + (grid.height - thumb) * scroll / max;
        surface.fill(x, grid.y, 2, grid.height, Palette.LINE);
        surface.fill(x, thumbY, 2, thumb, Palette.MUTED);
    }

    /** Why a change was not saved, or why a feature is unavailable: the model's words, above the footer. */
    private void drawNotice(ScreenSurface surface) {
        String notice = model.footer();
        if (notice.isEmpty()) {
            return;
        }
        Rect grid = grid();
        int y = grid.y + grid.height + 4;
        for (String line : Text.wrap(surface, notice, grid.width, 2)) {
            surface.drawText(line, grid.x, y, Palette.TEXT);
            y += lineHeight;
        }
    }

    private void drawFooter(ScreenSurface surface, Rect panel) {
        int top = panel.y + panel.height - footerHeight();
        surface.fill(panel.x + 1, top, panel.width - 2, 1, Palette.LINE);
        int textY = top + 3;
        String closes = closeKeyName.get() + " closes";
        int right = panel.x + panel.width - PAD - surface.textWidth(closes);
        surface.drawText(closes, right, textY, Palette.MUTED);
        String saves = "Changes save as you make them";
        surface.drawText(Text.fit(surface, saves, right - panel.x - PAD - 8), panel.x + PAD, textY, Palette.MUTED);
    }
}
