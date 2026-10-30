package com.ashlauncher.client.v1_20_4;

import com.ashlauncher.client.perf.FpsMeasurements;
import com.ashlauncher.client.report.Target;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Key;
import com.ashlauncher.client.ui.Panel;
import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.controls.KeyBindsScreen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

/**
 * ash's settings on 1.20.4: a screen that holds the shared {@link Panel}
 * and decides nothing. 1.20.4 has no menu blur - it came in 1.20.5 - so the
 * panel is told it isn't blurred and draws darker to make up for it, as on
 * 1.8.9 without shaders. It has the panel draw in real pixels - the pose
 * scaled by one over the GUI scale - maps the game's input to ash's own names
 * and to real pixels, and closes on its own key, a key binding only the game
 * knows how to match.
 */
final class AshSettingsScreen extends Screen {

    private final KeyMapping key;
    private final Panel panel;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyMapping key) {
        super(Component.literal(SettingsScreen.TITLE));
        this.key = key;
        this.panel = new Panel(settingsScreen, this::onClose);
        // Shift while dragging in Edit HUD places an item freely, unsnapped.
        panel.setShiftHeld(Screen::hasShiftDown);
        // ash's own page shows the key as the game names it, and sends the
        // player to the game's own key bindings to change it.
        panel.setOpenKey(() -> key.getTranslatedKeyMessage().getString(),
                () -> minecraft.setScreen(new KeyBindsScreen(this, minecraft.options)));
        // Each tile's FPS mark, as it was measured on this version (#70).
        panel.setMarks(feature -> FpsMeasurements.markOf(feature, Target.V1_20_4));
        panel.setBlurred(false);
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

    /** None of the game's darkening: the panel is the dark part, and in Edit HUD the readouts must show as they are. */
    @Override
    public void renderBackground(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
    }

    /** Closed by any means - its key, Escape, another screen - Edit HUD closes with it. */
    @Override
    public void removed() {
        panel.closed();
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Window window = minecraft.getWindow();
        float scale = (float) window.getGuiScale();
        // A whole number on 1.20.4 too; the window keeps it as a double.
        panel.setGuiScale((int) window.getGuiScale());
        graphics.pose().pushPose();
        graphics.pose().scale(1 / scale, 1 / scale, 1);
        // The cursor from the mouse handler, not the GUI-unit position the
        // game passes in, which has already lost the pixels between units.
        int x = (int) (minecraft.mouseHandler.xpos() * window.getWidth() / window.getScreenWidth());
        int y = (int) (minecraft.mouseHandler.ypos() * window.getHeight() / window.getScreenHeight());
        panel.render(new GuiCanvas(graphics, window.getWidth(), window.getHeight()), x, y);
        graphics.pose().popPose();
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
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        return button == GLFW.GLFW_MOUSE_BUTTON_LEFT && panel.mouseClicked(pixelsX(mouseX), pixelsY(mouseY));
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        panel.mouseDragged(pixelsX(mouseX), pixelsY(mouseY));
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        panel.mouseReleased();
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        panel.mouseScrolled(vertical);
        return true;
    }

    @Override
    public boolean charTyped(char character, int modifiers) {
        panel.charTyped(character);
        return true;
    }

    /**
     * The key that opened it closes it, through the panel's closing motion. A
     * key binding gets no presses while a screen is open - the screen is
     * asked first - so the screen has to know its own key. Backspace, Escape
     * and Enter go to the panel by ash's names.
     */
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (key.matches(keyCode, scanCode)) {
            panel.requestClose();
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_BACKSPACE) {
            panel.keyPressed(Key.BACKSPACE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            panel.keyPressed(Key.ESCAPE);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) {
            panel.keyPressed(Key.ENTER);
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
