package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.settings.Settings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.loader.api.FabricLoader;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
    }
}
