package com.ashlauncher.client.ui;

import com.ashlauncher.client.report.Feature;
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
 * behaviour lives in {@link SettingsScreen}.
 *
 * <p>Laid out afresh from the screen size on every frame and every click, so
 * what is drawn and what a click hits can never disagree. Everything is in
 * GUI units and made of rectangles and text.
 *
 * <p>The version target's screen forwards the game's input - a click, a typed
 * character, Backspace - and draws through a {@link ScreenSurface}. It
 * closes itself on its own key and on Escape.
 */
public final class Panel {

    private static final int TOP = 22;
    private static final int FOOT = 14;
    private static final int SIDE = 72;
    private static final int PAD = 8;
    private static final int GAP = 6;
    private static final int CARD_HEIGHT = 46;
    private static final int CATEGORY_HEIGHT = 13;
    private static final int SWITCH_WIDTH = 18;
    private static final int SWITCH_HEIGHT = 10;
    private static final int MAX_QUERY = 32;

    private final SettingsScreen model;
    private final Supplier<String> closeKeyName;
    private final Runnable close;
    private int width = 427;
    private int height = 240;
    /** The chosen category, or {@code null} for all of them. */
    private Feature.Category category;
    private String query = "";

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
    }

    // ---- input ----

    /** A left click at a point in GUI units. Returns whether it landed on anything. */
    public boolean mouseClicked(int x, int y) {
        if (closeButton().contains(x, y)) {
            close.run();
            return true;
        }
        for (Feature.Category each : categories()) {
            if (categoryRect(each).contains(x, y)) {
                category = each;
                return true;
            }
        }
        if (categoryRect(null).contains(x, y)) {
            category = null;
            return true;
        }
        for (SettingsScreen.Row row : visibleRows()) {
            Rect card = cardOf(row.feature());
            if (card != null && switchIn(card).contains(x, y)) {
                row.press();
                return true;
            }
        }
        return searchBox().contains(x, y);
    }

    /**
     * A character typed while the panel is open. It always goes to the
     * search box: nothing else on the panel takes text.
     */
    public void charTyped(char character) {
        if (character >= ' ' && character != 127 && query.length() < MAX_QUERY) {
            query += character;
        }
    }

    public void backspace() {
        if (!query.isEmpty()) {
            query = query.substring(0, query.length() - 1);
        }
    }

    // ---- where things are, for clicks and for anything that has to point at them ----

    /**
     * Where a feature's switch is drawn, or {@code null} when its card is not
     * on screen - filtered out, or past the bottom. Public so that the
     * real-game tests can click it as a player would.
     */
    public Rect switchOf(Feature feature) {
        Rect card = cardOf(feature);
        return card == null ? null : switchIn(card);
    }

    Rect cardOf(Feature feature) {
        List<SettingsScreen.Row> rows = visibleRows();
        Rect grid = grid();
        int columns = grid.width >= 270 ? 3 : 2;
        int cardWidth = (grid.width - GAP * (columns - 1)) / columns;
        for (int i = 0; i < rows.size(); i++) {
            if (rows.get(i).feature() == feature) {
                int x = grid.x + (i % columns) * (cardWidth + GAP);
                int y = grid.y + (i / columns) * (CARD_HEIGHT + GAP);
                return y + CARD_HEIGHT <= grid.y + grid.height ? new Rect(x, y, cardWidth, CARD_HEIGHT) : null;
            }
        }
        return null;
    }

    /** The row for a category's name, "All" included. */
    Rect categoryAt(String name) {
        if (name.equals("All")) {
            return categoryRect(null);
        }
        for (Feature.Category each : categories()) {
            if (each.displayName().equals(name)) {
                return categoryRect(each);
            }
        }
        throw new IllegalArgumentException("no category " + name);
    }

    Rect closeButton() {
        Rect panel = panel();
        return new Rect(panel.x + panel.width - PAD - 12, panel.y + 5, 12, 12);
    }

    private Rect panel() {
        int w = Math.min(width - 16, Math.max(300, width * 4 / 5));
        int h = Math.min(height - 12, Math.max(170, height * 82 / 100));
        return new Rect((width - w) / 2, (height - h) / 2, w, h);
    }

    private Rect searchBox() {
        Rect panel = panel();
        int left = panel.x + PAD + 30;
        return new Rect(left, panel.y + 4, closeButton().x - 8 - left, 14);
    }

    private Rect categoryRect(Feature.Category which) {
        Rect panel = panel();
        int index = which == null ? 0 : categories().indexOf(which) + 1;
        return new Rect(panel.x + 4, panel.y + TOP + 6 + index * (CATEGORY_HEIGHT + 2), SIDE - 8, CATEGORY_HEIGHT);
    }

    /** Where the cards go: the body, less room for the notice when there is one. */
    private Rect grid() {
        Rect panel = panel();
        int top = panel.y + TOP + PAD;
        int bottom = panel.y + panel.height - FOOT - PAD - noticeHeight();
        return new Rect(panel.x + SIDE + PAD, top, panel.width - SIDE - 2 * PAD, bottom - top);
    }

    private int noticeHeight() {
        return model.footer().isEmpty() ? 0 : 2 * 9 + 4;
    }

    private static Rect switchIn(Rect card) {
        return new Rect(card.x + card.width - 6 - SWITCH_WIDTH, card.y + 5, SWITCH_WIDTH, SWITCH_HEIGHT);
    }

    // ---- what is shown ----

    /** The categories that have at least one switch, in their declared order. */
    private List<Feature.Category> categories() {
        List<Feature.Category> present = new ArrayList<>();
        for (Feature.Category each : Feature.Category.values()) {
            if (count(each) > 0) {
                present.add(each);
            }
        }
        return present;
    }

    private int count(Feature.Category which) {
        int count = 0;
        for (SettingsScreen.Row row : model.rows()) {
            if (which == null || row.feature().category() == which) {
                count++;
            }
        }
        return count;
    }

    private List<SettingsScreen.Row> visibleRows() {
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<SettingsScreen.Row> visible = new ArrayList<>();
        for (SettingsScreen.Row row : model.rows()) {
            Feature feature = row.feature();
            boolean inCategory = category == null || feature.category() == category;
            String haystack = (feature.displayName() + " " + feature.description() + " "
                    + feature.category().displayName()).toLowerCase(Locale.ROOT);
            if (inCategory && (needle.isEmpty() || haystack.contains(needle))) {
                visible.add(row);
            }
        }
        return visible;
    }

    // ---- drawing ----

    /** Draws the panel; the mouse position, in GUI units, decides what is highlighted. */
    public void render(ScreenSurface surface, int mouseX, int mouseY) {
        surface.fill(0, 0, width, height, Palette.DIM);
        Rect panel = panel();
        Shapes.rounded(surface, panel, 3, Palette.LINE);
        Shapes.rounded(surface, new Rect(panel.x + 1, panel.y + 1, panel.width - 2, panel.height - 2), 2, Palette.PANEL);

        drawTop(surface, panel, mouseX, mouseY);
        drawCategories(surface, panel, mouseX, mouseY);
        drawCards(surface, mouseX, mouseY);
        drawNotice(surface);
        drawFooter(surface, panel);
    }

    private void drawTop(ScreenSurface surface, Rect panel, int mouseX, int mouseY) {
        int textY = panel.y + (TOP - surface.lineHeight()) / 2 + 1;
        surface.drawText("ash", panel.x + PAD, textY, Palette.TEXT);

        Rect search = searchBox();
        Shapes.rounded(surface, search, 2, Palette.LINE);
        Shapes.rounded(surface, new Rect(search.x + 1, search.y + 1, search.width - 2, search.height - 2), 1, Palette.RAISED);
        int textX = search.x + 5;
        int room = search.width - 12;
        if (query.isEmpty()) {
            surface.drawText(Text.fit(surface, "Search features", room), textX, search.y + 3, Palette.MUTED);
        } else {
            String shown = Text.tail(surface, query, room);
            surface.drawText(shown, textX, search.y + 3, Palette.TEXT);
            surface.fill(textX + surface.textWidth(shown) + 1, search.y + 3, 1, surface.lineHeight() - 1, Palette.TEXT);
        }

        Rect close = closeButton();
        int closeColour = close.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
        surface.drawText("x", close.centreX() - surface.textWidth("x") / 2, close.y + 2, closeColour);

        surface.fill(panel.x + 1, panel.y + TOP, panel.width - 2, 1, Palette.LINE);
    }

    private void drawCategories(ScreenSurface surface, Rect panel, int mouseX, int mouseY) {
        List<Feature.Category> all = new ArrayList<>();
        all.add(null);
        all.addAll(categories());
        for (Feature.Category each : all) {
            Rect at = categoryRect(each);
            boolean chosen = each == category;
            if (chosen) {
                Shapes.rounded(surface, at, 2, Palette.RAISED);
            }
            int colour = chosen || at.contains(mouseX, mouseY) ? Palette.TEXT : Palette.MUTED;
            String name = each == null ? "All" : each.displayName();
            String count = String.valueOf(count(each));
            surface.drawText(name, at.x + 5, at.y + 3, colour);
            surface.drawText(count, at.x + at.width - 5 - surface.textWidth(count), at.y + 3, Palette.MUTED);
        }
        surface.fill(panel.x + SIDE, panel.y + TOP + 1, 1, panel.height - TOP - FOOT - 1, Palette.LINE);
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
            Shapes.rounded(surface, card, 2, border);
            Shapes.rounded(surface, new Rect(card.x + 1, card.y + 1, card.width - 2, card.height - 2), 1, Palette.RAISED);

            Rect toggle = switchIn(card);
            int nameRoom = toggle.x - card.x - 10;
            surface.drawText(Text.fit(surface, row.feature().displayName(), nameRoom), card.x + 6, card.y + 6, Palette.TEXT);
            Shapes.toggle(surface, toggle, row.on(), row.available());

            int textTop = card.y + 20;
            if (!row.available()) {
                surface.drawText("Did not load", card.x + 6, textTop, Palette.MUTED);
            } else {
                for (String line : Text.wrap(surface, row.feature().description(), card.width - 12, 2)) {
                    surface.drawText(line, card.x + 6, textTop, Palette.MUTED);
                    textTop += surface.lineHeight();
                }
            }
        }
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
            y += surface.lineHeight();
        }
    }

    private void drawFooter(ScreenSurface surface, Rect panel) {
        int top = panel.y + panel.height - FOOT;
        surface.fill(panel.x + 1, top, panel.width - 2, 1, Palette.LINE);
        int textY = top + (FOOT - surface.lineHeight()) / 2 + 1;
        String closes = closeKeyName.get() + " closes";
        int right = panel.x + panel.width - PAD - surface.textWidth(closes);
        surface.drawText(closes, right, textY, Palette.MUTED);
        String saves = "Changes save as you make them";
        surface.drawText(Text.fit(surface, saves, right - panel.x - PAD - 8), panel.x + PAD, textY, Palette.MUTED);
    }
}
