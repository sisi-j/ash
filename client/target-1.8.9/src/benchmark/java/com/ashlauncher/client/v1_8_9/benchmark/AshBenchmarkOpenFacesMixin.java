package com.ashlauncher.client.v1_8_9.benchmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.WorldRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * A section of its own in the game's profile for the scan of the camera's
 * chunk section that terrain setup makes every frame it searches for visible
 * chunks, so the profile can tell that scan from the search around it. Part
 * of the frame-time measurement only, so it never reaches a player.
 */
@Mixin(WorldRenderer.class)
abstract class AshBenchmarkOpenFacesMixin {

    @Inject(method = "getOpenChunkFaces", at = @At("HEAD"))
    private void ash$startOpenFaces(CallbackInfoReturnable<?> info) {
        MinecraftClient.getInstance().profiler.push("open_faces");
    }

    @Inject(method = "getOpenChunkFaces", at = @At("RETURN"))
    private void ash$endOpenFaces(CallbackInfoReturnable<?> info) {
        MinecraftClient.getInstance().profiler.pop();
    }
}
