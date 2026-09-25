package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.AshMixinPlugin;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.FeatureState;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import java.io.IOException;
import java.util.function.Supplier;
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

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        for (String problem : settings.problems()) {
            LOG.warn(problem);
        }

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(MinecraftClient::getCurrentFps, settings.fpsReadoutEnabled());

        HudRenderCallback.EVENT.register((minecraft, tickDelta) -> {
            // One surface for both: constructing it reads the window size and
            // GUI scale, which is once a frame's work rather than once a feature's.
            LegacyHudSurface surface = new LegacyHudSurface(minecraft);
            marker.draw(surface);
            fpsReadout.draw(surface);
        });

        FeatureState toggleSprint = !settings.toggleSprintEnabled() ? FeatureState.OFF
                : landed(Feature.TOGGLE_SPRINT, () -> ClientPlayerEntity.class, TOGGLE_SPRINT_MIXIN)
                ? FeatureState.LOADED : FeatureState.DEGRADED;

        // R, in the game's own Movement category beside Sprint - free by
        // default on both targets, and rebindable in Controls. Only when the
        // feature is wanted and its mixin landed, so a binding that does
        // nothing is not holding a key.
        if (toggleSprint == FeatureState.LOADED) {
            KeyBinding toggleSprintKey = KeyBindingHelper.registerKeyBinding(
                    new KeyBinding(ToggleSprint.BINDING_NAME, Keyboard.KEY_R, "key.categories.movement"));
            ToggleSprintHook.install(new ToggleSprint(new KeyBindingToggleKey(toggleSprintKey), true));
        }

        LoadReport report = new LoadReport(clientVersion())
                .with(Feature.FPS_READOUT, settings.fpsReadoutEnabled() ? FeatureState.LOADED : FeatureState.OFF)
                .with(Feature.TOGGLE_SPRINT, toggleSprint);
        try {
            report.writeTo(FabricLoader.getInstance().getGameDir());
        } catch (IOException unwritable) {
            LOG.warn("ash: could not write the load report for the launcher: " + unwritable);
        }
    }

    /**
     * Whether a feature's mixin landed, found out now rather than at the first
     * world - and said in the game's log when it did not.
     *
     * <p>Loading the class a mixin targets is what applies it: here, the local
     * player's class, which the game would otherwise not load until a world
     * began.
     */
    private static boolean landed(Feature feature, Supplier<Class<?>> target, String mixin) {
        try {
            target.get();
        } catch (LinkageError broken) {
            LOG.warn("ash: " + feature.displayName() + " did not load - the class its mixin targets would not: "
                    + broken);
            return false;
        }
        if (AshMixinPlugin.landed(mixin)) {
            return true;
        }
        LOG.warn("ash: " + feature.displayName() + " did not load on this game version - " + mixin
                + " did not land " + AshMixinPlugin.unwired(mixin)
                + ". The game runs without it, and the launcher will say so before the next play.");
        return false;
    }

    private static String clientVersion() {
        return FabricLoader.getInstance().getModContainer("ash")
                .map(ash -> ash.getMetadata().getVersion().getFriendlyString())
                .orElse("unknown");
    }
}
