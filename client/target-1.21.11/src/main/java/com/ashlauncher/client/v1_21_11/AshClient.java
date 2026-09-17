package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.hud.Marker;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.minecraft.resources.Identifier;

/**
 * Where ash's client starts on 1.21.11.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 */
public final class AshClient implements ClientModInitializer {

    /**
     * Modern Fabric API names every HUD element, and ours is no exception -
     * a named element can be reordered or removed later without anyone having
     * to find it first. The 1.8.9 API has no such thing, which is why the
     * identifier stops here and never reaches {@link Marker}.
     */
    private static final Identifier MARKER = Identifier.fromNamespaceAndPath("ash", "marker");

    private final Marker marker = new Marker();

    @Override
    public void onInitializeClient() {
        // Last, so nothing vanilla draws over it. That is a decision about
        // this target's element registry rather than about the marker, so it
        // lives here.
        HudElementRegistry.addLast(MARKER, (graphics, tickCounter) ->
                marker.draw(new GuiGraphicsHudSurface(graphics)));
    }
}
