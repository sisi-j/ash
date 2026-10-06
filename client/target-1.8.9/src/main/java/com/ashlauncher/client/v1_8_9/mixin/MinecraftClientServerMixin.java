package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.CurrentServer;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Notes which server is being played on, once a client tick. Its own mixin
 * rather than another injector in the settings key's, so that it failing
 * costs only that line in the game's log.
 */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientServerMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void ash$noteServer(CallbackInfo info) {
        CurrentServer.tick();
    }
}
