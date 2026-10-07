package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Key;
import com.ashlauncher.client.ui.Panel;
import com.mojang.blaze3d.platform.Window;
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
 * and decides nothing. It blurs the game behind it with the game's own menu
 * blur, has the panel draw in real pixels - the pose scaled by one over the
 * GUI scale - maps the game's input to ash's own names and to real pixels,
 * and closes on its own key, a key binding only the game knows how to match.
 */
final class AshSettingsScreen extends Screen {

    private final KeyMapping key;
    private final Panel panel;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyMapping key) {
        super(Component.literal(SettingsScreen.TITLE));
        this.key = key;
        this.panel = new Panel(settingsScreen, this::onClose);
    }

    /** The panel this screen shows, so the real-game test can find a switch and click it as a player would. */
    Panel panel() {
        return panel;
    }

    @Override
    protected void init() {
        Window window = minecraft.getWindow();
        panel.resize(window.getWidth(), window.getHeight());
    }

    /**
     * The game's own blur, and none of its darkening: the panel is the dark
     * part. None at all in Edit HUD, where the readouts have to be seen as
     * they will be.
     */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        if (!panel.editingHud()) {
            renderBlurredBackground(graphics);
        }
    }

    /** Closed by any means - its key, Escape, another screen - Edit HUD closes with it. */
    @Override
    public void removed() {
        panel.closed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Window window = minecraft.getWindow();
        float scale = window.getGuiScale();
        panel.setGuiScale(window.getGuiScale());
        graphics.pose().pushMatrix();
        graphics.pose().scale(1 / scale, 1 / scale);
        // The cursor from the mouse handler, not the GUI-unit position the
        // game passes in, which has already lost the pixels between units.
        int x = (int) (minecraft.mouseHandler.xpos() * window.getWidth() / window.getScreenWidth());
        int y = (int) (minecraft.mouseHandler.ypos() * window.getHeight() / window.getScreenHeight());
        panel.render(new GuiCanvas(graphics, window.getWidth(), window.getHeight()), x, y);
        graphics.pose().popMatrix();
    }

    private int pixelsX(double guiX) {
        Window window = minecraft.getWindow();
        return (int) (guiX * window.getWidth() / window.getGuiScaledWidth());
    }

    private int pixelsY(double guiY) {
        Window window = minecraft.getWindow();
        return (int) (guiY * window.getHeight() / window.getGuiScaledHeight());
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return event.button() == GLFW.GLFW_MOUSE_BUTTON_LEFT && panel.mouseClicked(pixelsX(event.x()), pixelsY(event.y()));
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        panel.mouseDragged(pixelsX(event.x()), pixelsY(event.y()));
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
     * The key that opened it closes it, through the panel's closing motion. A
     * key binding gets no presses while a screen is open - the screen is
     * asked first - so the screen has to know its own key. Backspace, Escape
     * and Enter go to the panel by ash's names.
     */
    @Override
    public boolean keyPressed(KeyEvent event) {
        if (key.matches(event)) {
            panel.requestClose();
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
