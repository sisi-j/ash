package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Key;
import com.ashlauncher.client.ui.Panel;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * ash's settings on 1.21.11: a screen that holds the shared {@link Panel}
 * and decides nothing. It maps the game's input to ash's own names and passes
 * it on, gives the panel a surface to draw on, and closes on its own key -
 * a key binding, which only the game knows how to match.
 */
final class AshSettingsScreen extends Screen {

    private final KeyMapping key;
    private final Panel panel;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyMapping key) {
        super(Component.literal(SettingsScreen.TITLE));
        this.key = key;
        this.panel = new Panel(settingsScreen, () -> key.getTranslatedKeyMessage().getString(), this::onClose);
    }

    /** The panel this screen shows, so the real-game test can find a switch and click it as a player would. */
    Panel panel() {
        return panel;
    }

    @Override
    protected void init() {
        panel.resize(width, height);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        panel.render(new GuiScreenSurface(graphics, font), mouseX, mouseY);
    }

    /** Nothing: the panel draws its own light dim, so the HUD stays readable behind it. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && panel.mouseClicked((int) event.x(), (int) event.y());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        panel.mouseDragged((int) event.x(), (int) event.y());
        return true;
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        panel.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        panel.mouseScrolled(vertical);
        return true;
    }

    @Override
    public boolean charTyped(CharacterEvent event) {
        panel.charTyped(event.codepoint());
        return true;
    }

    /**
     * The key that opened it closes it. A key binding gets no presses while a
     * screen is open - the screen is asked first - so the screen has to know
     * its own key. Backspace, Escape and Enter go to the panel by ash's names.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (key.matches(event)) {
            onClose();
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_BACKSPACE) {
            panel.keyPressed(Key.BACKSPACE);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ESCAPE) {
            panel.keyPressed(Key.ESCAPE);
            return true;
        }
        if (event.key() == GLFW.GLFW_KEY_ENTER || event.key() == GLFW.GLFW_KEY_KP_ENTER) {
            panel.keyPressed(Key.ENTER);
            return true;
        }
        return super.keyPressed(event);
    }
}
