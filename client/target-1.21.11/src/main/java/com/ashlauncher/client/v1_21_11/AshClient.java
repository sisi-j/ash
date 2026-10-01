package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.crosshair.Crosshair;
import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.MixinFeature;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.player.KeyboardInput;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.glfw.GLFW;

/**
 * Where ash's client starts on 1.21.11.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 */
public final class AshClient implements ClientModInitializer {

    /** Log4j rather than SLF4J so that both targets log the same way. */
    private static final Logger LOG = LogManager.getLogger("ash");

    /**
     * Modern Fabric API names every HUD element, and ours are no exception -
     * a named element can be reordered or removed later without anyone having
     * to find it first. The 1.8.9 API has no such thing, which is why the
     * identifiers stop here and never reach the shared module.
     */
    private static final Identifier MARKER = Identifier.fromNamespaceAndPath("ash", "marker");

    private static final Identifier FPS_READOUT = Identifier.fromNamespaceAndPath("ash", "fps_readout");

    /** By name: a mixin class cannot be named by a class literal, because loading one directly is an error. */
    private static final String TOGGLE_SPRINT_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.KeyboardInputMixin";

    private static final String CROSSHAIR_MIXIN = "com.ashlauncher.client.v1_21_11.mixin.GuiCrosshairMixin";

    /**
     * The FPS readout this session draws, so the real-game test can ask it -
     * not the screenshot - whether it draws. Package-private and set once.
     */
    static FpsReadout fpsReadout;

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        settings.problems().forEach(LOG::warn);

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(
                () -> Minecraft.getInstance().getFps(), () -> settings.get(Settings.FPS_READOUT));
        AshClient.fpsReadout = fpsReadout;

        // Last, so nothing vanilla draws over them. That is a decision about
        // this target's element registry rather than about either feature, so
        // it lives here. It is also why both check `hudHidden()` themselves:
        // Fabric documents that `addLast` inherits no render condition, so
        // unlike every vanilla element but the sleep overlay, these would
        // otherwise draw through F1.
        HudElementRegistry.addLast(MARKER, (graphics, tickCounter) ->
                marker.draw(new GuiGraphicsHudSurface(graphics)));
        HudElementRegistry.addLast(FPS_READOUT, (graphics, tickCounter) ->
                fpsReadout.draw(new GuiGraphicsHudSurface(graphics)));

        // Every feature, less any whose mixin did not land. The settings
        // screen and the load report both read this one set.
        Set<Feature> landed = EnumSet.allOf(Feature.class);
        boolean toggleSprintLanded = MixinFeature.landed(() -> KeyboardInput.class, TOGGLE_SPRINT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.TOGGLE_SPRINT.displayName(), why)));
        if (!toggleSprintLanded) {
            landed.remove(Feature.TOGGLE_SPRINT);
        }

        // ash's crosshair, drawn from inside the game's own crosshair drawing
        // so that every rule the game has about when to show one still holds.
        boolean crosshairLanded = MixinFeature.landed(() -> Gui.class, CROSSHAIR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.CROSSHAIR.displayName(), why)));
        if (crosshairLanded) {
            CrosshairHook.install(new Crosshair(() -> settings.get(Settings.CROSSHAIR), Cross.DEFAULT));
        } else {
            landed.remove(Feature.CROSSHAIR);
        }

        // R, under Movement beside the game's own Sprint. Free by default on
        // both targets - read from each game's options, not from a list - and
        // rebindable in Controls. Registered whenever the mixin landed, on or
        // off: bindings can only be registered now, at startup, and the player
        // can switch the feature on from the settings screen mid-session.
        // While it is off, the key does nothing.
        if (toggleSprintLanded) {
            KeyMapping toggleSprintKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    ToggleSprint.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                    KeyMapping.Category.MOVEMENT));
            ToggleSprintHook.install(new ToggleSprint(new KeyMappingToggleKey(toggleSprintKey),
                    () -> settings.get(Settings.TOGGLE_SPRINT)));
        }

        Runnable writeReport = () -> writeLoadReport(settings, landed);
        SettingsScreen settingsScreen = new SettingsScreen(settings, landed::contains, writeReport);

        // Right Shift, which neither target binds by default. Polled on Fabric
        // API's client tick, which is inside the Fabric API ash already ships.
        // A binding gets no presses while a screen is open, so this only ever
        // opens the screen; the screen closes itself on the same key.
        KeyMapping settingsKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                SettingsScreen.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (settingsKey.consumeClick()) {
                if (client.screen == null) {
                    client.setScreen(new AshSettingsScreen(settingsScreen, settingsKey));
                }
            }
        });

        writeReport.run();
    }

    /**
     * What this session is running, for the launcher - written at startup and
     * again whenever a setting changes, so the report says what the session
     * ended with.
     */
    private static void writeLoadReport(Settings settings, Set<Feature> landed) {
        try {
            LoadReport.forSession(clientVersion(), landed::contains, settings::on)
                    .writeTo(FabricLoader.getInstance().getGameDir());
        } catch (IOException unwritable) {
            LOG.warn("ash: could not write the load report for the launcher: " + unwritable);
        }
    }

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer("ash")
                .map(ash -> ash.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
