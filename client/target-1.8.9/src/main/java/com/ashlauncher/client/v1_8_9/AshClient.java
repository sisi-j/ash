package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.crosshair.Cross;
import com.ashlauncher.client.crosshair.Crosshair;
import com.ashlauncher.client.crosshair.CrosshairHook;
import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hit.HitHook;
import com.ashlauncher.client.hit.HitIndicator;
import com.ashlauncher.client.hit.RecentAttacks;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.MixinFeature;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import java.io.IOException;
import java.util.EnumSet;
import java.util.Set;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.Window;
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

    private static final String CROSSHAIR_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.InGameHudMixin";

    private static final String HIT_ATTACK_MIXIN =
            "com.ashlauncher.client.v1_8_9.mixin.ClientPlayerInteractionManagerMixin";

    private static final String HIT_HURT_MIXIN = "com.ashlauncher.client.v1_8_9.mixin.ClientPlayNetworkHandlerMixin";

    /**
     * The FPS readout this session draws, so the real-game test can ask it -
     * not the screenshot - whether it draws. Package-private and set once.
     */
    static FpsReadout fpsReadout;

    /** The hit indicator this session draws, if both its mixins landed, for the smoke test to ask. */
    static HitIndicator hitIndicator;

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        for (String problem : settings.problems()) {
            LOG.warn(problem);
        }

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(MinecraftClient::getCurrentFps, () -> settings.get(Settings.FPS_READOUT));
        AshClient.fpsReadout = fpsReadout;

        HudRenderCallback.EVENT.register((minecraft, tickDelta) -> {
            // One surface for both: constructing it reads the window size and
            // GUI scale, which is once a frame's work rather than once a feature's.
            LegacyHudSurface surface = new LegacyHudSurface(minecraft);
            marker.draw(surface);
            fpsReadout.draw(surface);
            // Around the middle pixel of the game's crosshair, which it draws
            // at (width / 2 - 7, height / 2 - 7) with its centre on pixel 7.
            Window window = new Window(minecraft);
            HitHook.draw(surface, window.getWidth() / 2, window.getHeight() / 2);
        });

        // Every feature, less any whose mixin did not land. The settings
        // screen and the load report both read this one set.
        Set<Feature> landed = EnumSet.allOf(Feature.class);
        boolean toggleSprintLanded = MixinFeature.landed(() -> ClientPlayerEntity.class, TOGGLE_SPRINT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.TOGGLE_SPRINT.displayName(), why)));
        if (!toggleSprintLanded) {
            landed.remove(Feature.TOGGLE_SPRINT);
        }

        // ash's crosshair, drawn from inside the game's own crosshair drawing
        // so that every rule the game has about when to show one still holds.
        boolean crosshairLanded = MixinFeature.landed(() -> InGameHud.class, CROSSHAIR_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.CROSSHAIR.displayName(), why)));
        if (crosshairLanded) {
            // Built from the settings every frame, so a change on the settings
            // screen shows on the next one.
            CrosshairHook.install(new Crosshair(() -> settings.get(Settings.CROSSHAIR), () -> Cross.of(settings)));
        } else {
            landed.remove(Feature.CROSSHAIR);
        }

        // The hit indicator. 1.8.9's "this entity was hurt" names no attacker,
        // so it takes both halves: the player's attack, and the server's
        // hurt matched to it. Either missing, and it degrades alone.
        boolean hitAttackLanded = MixinFeature.landed(() -> ClientPlayerInteractionManager.class, HIT_ATTACK_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_INDICATOR.displayName(), why)));
        boolean hitHurtLanded = MixinFeature.landed(() -> ClientPlayNetworkHandler.class, HIT_HURT_MIXIN,
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.HIT_INDICATOR.displayName(), why)));
        if (hitAttackLanded && hitHurtLanded) {
            hitIndicator = new HitIndicator(() -> settings.get(Settings.HIT_INDICATOR),
                    () -> settings.get(Settings.HIT_INDICATOR_COLOUR), () -> settings.get(Settings.HIT_INDICATOR_DURATION),
                    AshClient::clockMillis);
            HitHook.install(hitIndicator, new RecentAttacks(AshClient::clockMillis));
        } else {
            landed.remove(Feature.HIT_INDICATOR);
        }

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
                why -> LOG.warn(MixinFeature.didNotLoad(Feature.SETTINGS_SCREEN.displayName(), why)));
        if (!settingsKeyLanded) {
            landed.remove(Feature.SETTINGS_SCREEN);
        }

        Runnable writeReport = () -> writeLoadReport(settings, landed);
        if (settingsKeyLanded) {
            SettingsScreen settingsScreen = new SettingsScreen(settings, landed::contains, writeReport);
            KeyBinding settingsKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(SettingsScreen.BINDING_NAME, Keyboard.KEY_RSHIFT, "key.categories.misc"));
            SettingsKey.install(settingsKey, settingsScreen);
        }

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

    /** A clock that only goes forward, unlike the wall clock a player can change mid-session. */
    private static long clockMillis() {
        return System.nanoTime() / 1_000_000L;
    }

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer("ash")
                .map(ash -> ash.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
