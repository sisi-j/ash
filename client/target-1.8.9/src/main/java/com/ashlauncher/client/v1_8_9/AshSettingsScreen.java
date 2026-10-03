package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Panel;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.KeyBinding;
import org.lwjgl.input.Keyboard;

/**
 * ash's settings on 1.8.9: a screen that holds the shared {@link Panel} and
 * decides nothing. It passes the game's input through, gives the panel a
 * surface to draw on, and closes on its own key and on Escape.
 *
 * <p>{@link #keyPressed} and {@link #mouseClicked} are overridden here, in
 * this package, also so that the smoke test - in this package - can deliver
 * a key and a click the way the game's input loop does. 1.8.9 has no input
 * framework to do it, and the test runs on production names, so reflection
 * is no way in. Protected is enough: protected reaches the declaring
 * class's package.
 */
public final class AshSettingsScreen extends Screen {

    private final KeyBinding key;
    private final Panel panel;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyBinding key) {
        this.key = key;
        this.panel = new Panel(settingsScreen, () -> GameOptions.getFormattedNameForKeyCode(key.getCode()),
                () -> client.setScreen(null));
    }

    /** The panel this screen shows, so the smoke test can find a switch and click it. */
    Panel panel() {
        return panel;
    }

    @Override
    public void init() {
        panel.resize(width, height);
    }

    /** The panel draws its own light dim, so the game's dark gradient is not drawn. */
    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        panel.render(new LegacyScreenSurface(textRenderer), mouseX, mouseY);
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            panel.mouseClicked(mouseX, mouseY);
        }
    }

    /**
     * The key that opened it closes it. A key binding gets no presses while a
     * screen is open - the screen drains the keyboard first - so the screen
     * has to know its own key. Escape is the game's own handling; any other
     * printable character goes to the panel's search.
     */
    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == key.getCode()) {
            client.setScreen(null);
        } else if (keyCode == Keyboard.KEY_BACK) {
            panel.backspace();
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            super.keyPressed(character, keyCode);
        } else {
            panel.charTyped(character);
        }
    }
}
