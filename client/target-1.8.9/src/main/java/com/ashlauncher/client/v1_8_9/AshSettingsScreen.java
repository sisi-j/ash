package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.Key;
import com.ashlauncher.client.ui.Panel;
import com.ashlauncher.client.v1_8_9.mixin.GameRendererAccess;
import com.mojang.blaze3d.platform.GLX;
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
 * spectator's view - is put back on closing, switched on or off as it was.
 * Where the machine cannot run post shaders, the panel shows over the
 * unblurred world, darker to make up for it.
 *
 * <p>{@link #keyPressed} and the {@code ...At} methods are reachable from this
 * package so that the smoke test can deliver a key, and a click in real
 * pixels: 1.8.9 has no input framework to do it. So the one thing the smoke
 * test does not cover is {@link #mouseClicked}'s reading of the mouse's own
 * event position, which no test can feed.
 */
public final class AshSettingsScreen extends Screen {

    private static final Identifier BLUR = new Identifier("shaders/post/blur.json");

    private final KeyBinding key;
    private final Panel panel;
    private boolean blurring;
    /** The post shader that was loaded before the panel's blur, or {@code null} for none. */
    private String shaderBefore;
    /** Whether post shaders were switched on before the panel's blur. */
    private boolean shadersWereOn;

    AshSettingsScreen(SettingsScreen settingsScreen, KeyBinding key) {
        this.key = key;
        this.panel = new Panel(settingsScreen, () -> client.setScreen(null));
    }

    /** The panel this screen shows, so the smoke test can find a switch and click it. */
    Panel panel() {
        return panel;
    }

    /**
     * Whether the game's HUD is hidden under this screen: always under the
     * panel, so 1.8.9 looks as 1.21.11 does, and never in Edit HUD, which is
     * there to show the HUD as it will be.
     */
    public boolean hidesHud() {
        return !panel.editingHud();
    }

    /** Called on opening and again on every window resize. */
    @Override
    public void init() {
        panel.resize(client.width, client.height);
        // GLX's flag, not GameRenderer.areShadersSupported(): despite its
        // name, that is only true when a shader is already loaded.
        if (!blurring && GLX.shadersSupported) {
            GameRendererAccess renderer = (GameRendererAccess) client.gameRenderer;
            ShaderEffect before = client.gameRenderer.getShader();
            shaderBefore = before == null ? null : before.getName();
            shadersWereOn = renderer.ash$shadersEnabled();
            renderer.ash$loadShader(BLUR);
            blurring = true;
        }
        panel.setBlurred(blurring);
    }

    @Override
    public void removed() {
        panel.closed();
        if (blurring) {
            GameRendererAccess renderer = (GameRendererAccess) client.gameRenderer;
            if (shaderBefore == null) {
                client.gameRenderer.disableShader();
            } else {
                renderer.ash$loadShader(new Identifier(shaderBefore));
            }
            renderer.ash$setShadersEnabled(shadersWereOn);
            blurring = false;
        }
    }

    /**
     * Drawn in real pixels, with the game's alpha test off for the moment:
     * it would throw away the faint pixels of every anti-aliased edge and of
     * the panel's shadow. Blending is left off afterwards, as the game's own
     * fills leave it.
     */
    @Override
    public void render(int mouseX, int mouseY, float tickDelta) {
        int scale = new Window(client).getScaleFactor();
        panel.setGuiScale(scale);
        if (blurring) {
            // No blur in Edit HUD, where the readouts have to be seen as they
            // will be: the shader is switched off, not unloaded, so leaving
            // Edit HUD has it back on the next frame.
            ((GameRendererAccess) client.gameRenderer).ash$setShadersEnabled(!panel.editingHud());
        }
        GlStateManager.pushMatrix();
        GlStateManager.scale(1.0F / scale, 1.0F / scale, 1.0F);
        GlStateManager.disableAlphaTest();
        panel.render(new LegacyCanvas(client, client.width, client.height), Mouse.getX(), client.height - Mouse.getY() - 1);
        GlStateManager.disableBlend();
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
            panel.requestClose();
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
