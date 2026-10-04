package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Key;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.v1_8_9.mixin.GameRendererInvoker;
import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.gl.ShaderEffect;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
import net.minecraft.util.Identifier;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

/**
 * ash's settings on 1.8.9: a screen that holds the shared {@link Panel} and
 * decides nothing. It blurs the world behind it with the game's own blur
 * shader, has the panel draw in real pixels - the matrix scaled by one over
 * the GUI scale - maps the game's input to ash's own names and to real
 * pixels, and closes on its own key.
 *
 * <p>The blur is the game's {@code shaders/post/blur.json}, loaded into the
 * post-shader slot the game runs after the world and before the HUD and any
 * screen (`docs/research/0007`). Whatever was in that slot before - a
 * spectator's view - is put back on closing. Where the machine cannot run
 * post shaders, the panel shows over the unblurred world.
 *
 * <p>{@link #keyPressed} and the {@code ...At} methods are reachable from this
 * package so that the smoke test can deliver a key and a click the way the
 * game's input loop does: 1.8.9 has no input framework to do it.
 */
public final class AshSettingsScreen extends Screen {

    private static final Identifier BLUR = new Identifier("shaders/post/blur.json");

    private final KeyBinding key;
    private final Panel panel;
    private boolean blurring;
    /** The post shader that was loaded before the panel's blur, or {@code null} for none. */
    private String shaderBefore;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyBinding key) {
        this.key = key;
        this.panel = new Panel(settingsScreen, () -> client.setScreen(null));
    }

    /** The panel this screen shows, so the smoke test can find a switch and click it. */
    Panel panel() {
        return panel;
    }

    /** Called on opening and again on every window resize. */
    @Override
    public void init() {
        panel.resize(client.width, client.height);
        if (!blurring && client.gameRenderer.areShadersSupported()) {
            ShaderEffect before = client.gameRenderer.getShader();
            shaderBefore = before == null ? null : before.getName();
            ((GameRendererInvoker) client.gameRenderer).ash$loadShader(BLUR);
            blurring = true;
        }
    }

    @Override
    public void removed() {
        if (blurring) {
            if (shaderBefore == null) {
                client.gameRenderer.disableShader();
            } else {
                ((GameRendererInvoker) client.gameRenderer).ash$loadShader(new Identifier(shaderBefore));
            }
            blurring = false;
        }
    }

    /**
     * Drawn in real pixels, with the game's alpha test off for the moment:
     * it would throw away the faint pixels of every anti-aliased edge and of
     * the panel's shadow.
     */
    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        int scale = new Window(client).getScaleFactor();
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F / scale, 1.0F / scale, 1.0F);
        GlStateManager.disableAlphaTest();
        panel.render(new LegacyCanvas(client, client.width, client.height), Mouse.getX(), client.height - Mouse.getY() - 1);
        GlStateManager.enableAlphaTest();
        GlStateManager.popMatrix();
    }

    // The game hands these GUI units; the panel works in real pixels, which
    // the mouse's own event position gives exactly.

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int button) {
        if (button == 0) {
            clickAt(Mouse.getEventX(), client.height - Mouse.getEventY() - 1);
        }
    }

    @Override
    protected void mouseDragged(int mouseX, int mouseY, int button, long msSinceClick) {
        if (button == 0) {
            dragAt(Mouse.getEventX(), client.height - Mouse.getEventY() - 1);
        }
    }

    @Override
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        panel.mouseReleased();
    }

    /** A left click at a point in real pixels, from the top-left. */
    void clickAt(int x, int y) {
        panel.mouseClicked(x, y);
    }

    void dragAt(int x, int y) {
        panel.mouseDragged(x, y);
    }

    /** The game's own mouse handling, then the wheel, which 1.8.9's screens leave to each screen. */
    @Override
    public void handleMouse() {
        super.handleMouse();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) {
            panel.mouseScrolled(wheel);
        }
    }

    /**
     * The key that opened it closes it. A key binding gets no presses while a
     * screen is open - the screen drains the keyboard first - so the screen
     * has to know its own key. Backspace, Escape and Enter go to the panel by
     * ash's names, and every other key's character to the panel - which
     * ignores the control characters keys such as Shift type here.
     */
    @Override
    protected void keyPressed(char character, int keyCode) {
        if (keyCode == key.getCode()) {
            client.setScreen(null);
        } else if (keyCode == Keyboard.KEY_BACK) {
            panel.keyPressed(Key.BACKSPACE);
        } else if (keyCode == Keyboard.KEY_ESCAPE) {
            panel.keyPressed(Key.ESCAPE);
        } else if (keyCode == Keyboard.KEY_RETURN || keyCode == Keyboard.KEY_NUMPADENTER) {
            panel.keyPressed(Key.ENTER);
        } else if (character >= ' ') {
            panel.charTyped(character);
        }
    }
}
