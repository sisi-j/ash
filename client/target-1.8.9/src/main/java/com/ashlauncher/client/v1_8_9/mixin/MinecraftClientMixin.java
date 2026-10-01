package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.SettingsKey;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The settings key on 1.8.9: read once a client tick, at the end of it.
 *
 * <p>ash's own hook rather than Legacy Fabric's client tick event, which
 * would mean two more jars pinned and mirrored, and whose mixins are required
 * - so if one failed to apply, the game would not start. This one fails
 * alone, costs only the settings key, and is reported in the load report.
 * See {@code docs/research/0005}, B.3.
 *
 * <p>At the end of {@code tick()} because the keyboard is read inside it, so
 * this tick's presses have been counted by then. Not toggle sprint's player
 * tick: that would make the settings key depend on another feature's mixin,
 * and each feature fails alone (ADR-0017).
 */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void ash$readSettingsKey(CallbackInfo info) {
        SettingsKey.tick();
    }
}
