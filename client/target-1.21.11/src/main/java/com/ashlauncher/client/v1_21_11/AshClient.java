package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.fps.FpsReadout;
import com.ashlauncher.client.hud.Marker;
import com.ashlauncher.client.settings.Settings;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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

    @Override
    public void onInitializeClient() {
        Settings settings = Settings.load(FabricLoader.getInstance().getConfigDir());
        settings.problems().forEach(LOG::warn);

        Marker marker = new Marker();
        FpsReadout fpsReadout = new FpsReadout(
                () -> Minecraft.getInstance().getFps(), settings.fpsReadoutEnabled());

        // Last, so nothing vanilla draws over them. That is a decision about
        // this target's element registry rather than about either feature, so
        // it lives here. It is also why both check `hidden()` themselves:
        // Fabric documents that `addLast` inherits no render condition, so
        // unlike every vanilla element these would otherwise draw through F1.
        HudElementRegistry.addLast(MARKER, (graphics, tickCounter) ->
                marker.draw(new GuiGraphicsHudSurface(graphics)));
        HudElementRegistry.addLast(FPS_READOUT, (graphics, tickCounter) ->
                fpsReadout.draw(new GuiGraphicsHudSurface(graphics)));
    }
}
