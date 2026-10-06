package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.SnaplookKey;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Snaplook's key on 1.8.9, read once a client tick, at the end of it, as the
 * settings key and freelook's are. Its own mixin so that snaplook fails alone.
 */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientSnaplookMixin {

    @Inject(method = "tick", at = @At("RETURN"))
    private void ash$readSnaplookKey(CallbackInfo info) {
        SnaplookKey.tick();
    }
}
