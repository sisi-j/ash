package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsMenu;
import net.minecraft.client.gui.DrawableHelper;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.resource.language.I18n;

/**
 * ash's settings screen on 1.8.9: the shared {@link SettingsMenu}'s rows,
 * drawn as the game's own buttons. Every decision - what each row says,
 * whether it can be pressed, what pressing does, what the footer says - is
 * the menu's.
 *
 * <p>Laid out the same as 1.21.11's: a column of 200-wide buttons a quarter of
 * the way down, the title above, Done below, the footer beneath that. A plain
 * button whose label changes is the switch, as the game's own option screens
 * do it - 1.8.9 has no checkbox and no cycling button.
 */
public final class AshSettingsScreen extends Screen {

    private static final int BUTTON_WIDTH = 200;
    private static final int ROW_HEIGHT = 24;

    /** Past every row's id, which is its index in the menu. */
    private static final int DONE = 1000;

    private final SettingsMenu menu;
    private final KeyBinding key;

    AshSettingsScreen(SettingsMenu menu, KeyBinding key) {
        this.menu = menu;
        this.key = key;
    }

    @Override
    public void init() {
        buttons.clear();
        int x = width / 2 - BUTTON_WIDTH / 2;
        int y = height / 4;
        for (int i = 0; i < menu.rows().size(); i++) {
            SettingsMenu.Row row = menu.rows().get(i);
            ButtonWidget button = new ButtonWidget(i, x, y, BUTTON_WIDTH, 20, row.label());
            button.active = row.available();
            buttons.add(button);
            y += ROW_HEIGHT;
        }
        buttons.add(new ButtonWidget(DONE, x, y + ROW_HEIGHT / 2, BUTTON_WIDTH, 20, I18n.translate("gui.done")));
    }

    @Override
    protected void buttonClicked(ButtonWidget button) {
        if (button.id == DONE) {
            client.setScreen(null);
            return;
        }
        SettingsMenu.Row row = menu.rows().get(button.id);
        row.press();
        for (ButtonWidget each : buttons) {
            if (each.id != DONE) {
                each.message = menu.rows().get(each.id).label();
            }
        }
    }

    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        // A light dim rather than the game's dark gradient, so the HUD stays
        // readable behind the screen and a switch can be seen taking effect -
        // the same dim 1.21.11's screen draws.
        DrawableHelper.fill(0, 0, width, height, 0x60000000);
        drawCenteredString(textRenderer, SettingsMenu.TITLE, width / 2, height / 4 - 20, 0xFFFFFFFF);
        super.render(mouseX, mouseY, tickDelta);
        String footer = menu.footer();
        if (!footer.isEmpty()) {
            int top = height / 4 + (menu.rows().size() + 2) * ROW_HEIGHT;
            textRenderer.drawTrimmed(footer, width / 2 - BUTTON_WIDTH, top, BUTTON_WIDTH * 2, 0xFFFFFFFF);
        }
    }

    /**
     * The key that opened it closes it. A key binding gets no presses while a
     * screen is open - the screen drains the keyboard first - so the screen
     * has to know its own key. Escape is the game's own handling.
     *
     * <p>Public, as is {@link #mouseClicked}, only so that the smoke test can
     * deliver a key and a click the way the game's input loop does: 1.8.9 has
     * no input framework to do it, and the test runs on production names, so
     * reflection is no way in either.
     */
    @Override
    public void keyPressed(char character, int keyCode) {
        if (keyCode == key.getCode()) {
            client.setScreen(null);
            return;
        }
        super.keyPressed(character, keyCode);
    }

    @Override
    public void mouseClicked(int mouseX, int mouseY, int button) {
        super.mouseClicked(mouseX, mouseY, button);
    }

    /** The button with this label, for the smoke test to click; {@code null} if there is none. */
    ButtonWidget buttonLabelled(String label) {
        for (ButtonWidget button : buttons) {
            if (label.equals(button.message)) {
                return button;
            }
        }
        return null;
    }
}
