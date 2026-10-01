package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.MixinFeature;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.FeatureStatus;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsMenu;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import java.io.IOException;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.entity.player.ClientPlayerEntity;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;

/**
 * Where ash's client starts on 1.8.9.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 *
 * <p>What it draws is {@link Marker} and {@link FpsReadout}, unchanged and
 * shared with the modern target. Everything that differs between the two is in
 * this package, and the whole of the difference is worth seeing: modern Fabric
 * API takes named elements into an ordered registry, and this takes a callback
 * that fires once after the vanilla HUD and can only draw on top. There is no
 * identifier here, no ordering and no removal - which is why the shared
 * surface is "draw this here" and never "replace element X".
 */
public final class AshClient implements ClientModInitializer {

    private static final Logger LOG = LogManager.getLogger("ash");

    /** By name: a mixin class cannot be named by a class literal, because loading one directly is an error. */
    private static final String TOGGLE_SPRINT_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.ClientPlayerEntityMixin";

    private static final String SETTINGS_KEY_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.MinecraftClientMixin";

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        for (String problem : settings.problems()) {
            LOG.warn(problem);
        }

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(MinecraftClient::getCurrentFps, () -> settings.get(Settings.FPS_READOUT));

        HudRenderCallback.EVENT.register((minecraft, tickDelta) -> {
            // One surface for both: constructing it reads the window size and
            // GUI scale, which is once a frame's work rather than once a feature's.
            LegacyHudSurface surface = new LegacyHudSurface(minecraft);
            marker.draw(surface);
            fpsReadout.draw(surface);
        });

        boolean toggleSprintLanded = MixinFeature.landed(() -> ClientPlayerEntity.class, TOGGLE_SPRINT_MIXIN,
                why -> LOG.warn("ash: " + Feature.TOGGLE_SPRINT.displayName() + " did not load - " + why
                        + ". The game runs without it, and the launcher will say so before the next play."));

        // R, in the game's own Movement category beside Sprint - free by
        // default on both targets, and rebindable in Controls. Registered
        // whenever the mixin landed, on or off: bindings can only be
        // registered now, at startup, and the player can switch the feature
        // on from the settings screen mid-session. While it is off, the key
        // does nothing.
        if (toggleSprintLanded) {
            KeyBinding toggleSprintKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(ToggleSprint.BINDING_NAME, Keyboard.KEY_R, "key.categories.movement"));
            ToggleSprintHook.install(new ToggleSprint(new KeyBindingToggleKey(toggleSprintKey),
                    () -> settings.get(Settings.TOGGLE_SPRINT)));
        }

        // Right Shift, which neither target binds by default, read by ash's
        // own tick mixin. MinecraftClient is loaded long before this runs, so
        // asking whether that mixin landed loads nothing new.
        boolean settingsKeyLanded = MixinFeature.landed(() -> MinecraftClient.class, SETTINGS_KEY_MIXIN,
                why -> LOG.warn("ash: the key for ash's settings did not load - " + why
                        + ". The game runs without it, and the launcher will say so before the next play."));

        Runnable writeReport = () -> writeLoadReport(settings, toggleSprintLanded, settingsKeyLanded);
        if (settingsKeyLanded) {
            SettingsMenu menu = new SettingsMenu(settings,
                    feature -> feature != Feature.TOGGLE_SPRINT || toggleSprintLanded, writeReport);
            KeyBinding settingsKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(SettingsMenu.BINDING_NAME, Keyboard.KEY_RSHIFT, "key.categories.misc"));
            SettingsKey.install(settingsKey, menu);
        }

        writeReport.run();
    }

    /**
     * What this session is running, for the launcher - written at startup and
     * again whenever a setting changes, so the report says what the session
     * ended with.
     */
    private static void writeLoadReport(Settings settings, boolean toggleSprintLanded, boolean settingsKeyLanded) {
        LoadReport report = new LoadReport(clientVersion())
                .with(Feature.FPS_READOUT, FeatureStatus.of(settings.get(Settings.FPS_READOUT), true))
                .with(Feature.TOGGLE_SPRINT, FeatureStatus.of(settings.get(Settings.TOGGLE_SPRINT), toggleSprintLanded))
                .with(Feature.SETTINGS_SCREEN, FeatureStatus.of(true, settingsKeyLanded));
        try {
            report.writeTo(FabricLoader.getInstance().getGameDir());
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
