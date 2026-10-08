package com.ashlauncher.client.v1_8_9.benchmark;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * A section of its own in the game's profile for the scan of the camera's
 * chunk section that terrain setup makes every frame it searches for visible
 * chunks, so the profile can tell that scan from the search around it. Part
 * of the frame-time measurement only, so it never reaches a player.
 *
 * <p>Round the call in {@code setupTerrain}, not inside the method: faster
 * view scan (#105) can answer the call before the method starts, and the
 * section should time whatever answers it.
 */
@Mixin(WorldRenderer.class)
abstract class AshBenchmarkOpenFacesMixin {

    private static final String SCAN =
            "Lnet/minecraft/client/render/WorldRenderer;getOpenChunkFaces(Lnet/minecraft/util/math/BlockPos;)Ljava/util/Set;";

    @Inject(method = "setupTerrain", at = @At(value = "INVOKE", target = SCAN))
    private void ash$startOpenFaces(Entity entity, double tickDelta, CameraView cameraView, int frame,
            boolean spectator, CallbackInfo info) {
        MinecraftClient.getInstance().profiler.push("open_faces");
    }

    @Inject(method = "setupTerrain", at = @At(value = "INVOKE", target = SCAN, shift = At.Shift.AFTER))
    private void ash$endOpenFaces(Entity entity, double tickDelta, CameraView cameraView, int frame,
            boolean spectator, CallbackInfo info) {
        MinecraftClient.getInstance().profiler.pop();
    }
}
