package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.mixin.AshMixinPlugin;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.FeatureState;
import com.ashlauncher.client.report.LoadReport;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.sprint.ToggleSprint;
import com.ashlauncher.client.sprint.ToggleSprintHook;
import com.mojang.blaze3d.platform.InputConstants;
import java.io.IOException;
import java.util.function.Supplier;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
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

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        settings.problems().forEach(LOG::warn);

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(
                () -> Minecraft.getInstance().getFps(), settings.fpsReadoutEnabled());

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

        FeatureState toggleSprint = !settings.toggleSprintEnabled() ? FeatureState.OFF
                : landed(Feature.TOGGLE_SPRINT, () -> KeyboardInput.class, TOGGLE_SPRINT_MIXIN)
                ? FeatureState.LOADED : FeatureState.DEGRADED;

        // R, under Movement beside the game's own Sprint. Free by default on
        // both targets - read from each game's options, not from a list - and
        // rebindable in Controls like any other key. Only registered when the
        // feature is wanted and its mixin landed: a binding that does nothing
        // should not be holding a key.
        if (toggleSprint == FeatureState.LOADED) {
            KeyMapping toggleSprintKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                    ToggleSprint.BINDING_NAME, InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_R,
                    KeyMapping.Category.MOVEMENT));
            ToggleSprintHook.install(new ToggleSprint(new KeyMappingToggleKey(toggleSprintKey), true));
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
     * <p>Loading the class a mixin targets is what applies it. Doing that here
     * means the feature knows before anything registers, and the load report
     * says so before the player has done anything at all.
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
