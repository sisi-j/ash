package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.FreelookKey;
import com.ashlauncher.client.v1_8_9.FreelookView;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Freelook's tick on 1.8.9: its key read once a client tick, at the end of it,
 * as the settings key is. Its own mixin so that freelook fails alone.
 *
 * <p>And, at the start of every tick, the player's own angles made certain:
 * the view's swap ends with the world's drawing, but should a frame ever end
 * without reaching that, nothing in the tick - movement packets above all -
 * may see the camera's angles.
 */
@Mixin(MinecraftClient.class)
abstract class MinecraftClientFreelookMixin {

    @Inject(method = "tick", at = @At("HEAD"))
    private void ash$playersOwnAngles(CallbackInfo info) {
        FreelookView.turnBack();
    }

    @Inject(method = "tick", at = @At("RETURN"))
    private void ash$readFreelookKey(CallbackInfo info) {
        FreelookKey.tick();
    }
}
