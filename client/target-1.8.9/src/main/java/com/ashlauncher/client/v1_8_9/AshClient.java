package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.hud.Marker;
import net.fabricmc.api.ClientModInitializer;
import net.legacyfabric.fabric.api.client.rendering.v1.HudRenderCallback;

/**
 * Where ash's client starts on 1.8.9.
 *
 * <p>Named in {@code fabric.mod.json} as the client entrypoint, which is also
 * what puts ash in the game's own mod list.
 *
 * <p>The marker it draws is {@link Marker}, unchanged and shared with the
 * modern target. Everything that differs between the two is in this package,
 * and the whole of the difference is worth seeing: modern Fabric API takes a
 * named element into an ordered registry, and this takes a callback that fires
 * once after the vanilla HUD and can only draw on top. There is no identifier
 * here, no ordering and no removal - which is why the shared surface is
 * "draw this here" and never "replace element X".
 */
public final class AshClient implements ClientModInitializer {

    private final Marker marker = new Marker();

    @Override
    public void onInitializeClient() {
        HudRenderCallback.EVENT.register(
                (minecraft, tickDelta) -> marker.draw(new LegacyHudSurface(minecraft)));
    }
}
