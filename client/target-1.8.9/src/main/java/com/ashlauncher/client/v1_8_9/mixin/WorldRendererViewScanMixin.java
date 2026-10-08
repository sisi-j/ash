package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.FasterViewScan;
import java.util.Set;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faster view scan on 1.8.9, in the world renderer: the scan of the camera's
 * chunk section answered from {@link FasterViewScan} when it can be, and the
 * kept answer forgotten whenever the renderer hears of a change.
 *
 * <p>{@code updateBlock} is where all three of the renderer's change calls
 * meet - a block, its light, a region - and a chunk arriving or unloading is
 * a region. {@code setWorld} and {@code reload} start the renderer afresh,
 * and {@code reload} is where leaves change between solid and see-through.
 *
 * <p>If any of this does not land, the feature is off and the game scans
 * every time, as it always has. See {@code WorldViewScanMixin} for the other
 * half.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererViewScanMixin {

    @Shadow
    private ClientWorld world;

    @Inject(method = "getOpenChunkFaces", at = @At("HEAD"), cancellable = true)
    private void ash$reuseScan(BlockPos camera, CallbackInfoReturnable<Set<Direction>> info) {
        Set<Direction> kept = FasterViewScan.kept(camera, world.getChunk(camera));
        if (kept != null) {
            info.setReturnValue(kept);
        }
    }

    @Inject(method = "getOpenChunkFaces", at = @At("RETURN"))
    private void ash$keepScan(BlockPos camera, CallbackInfoReturnable<Set<Direction>> info) {
        FasterViewScan.keep(camera, world.getChunk(camera), info.getReturnValue());
    }

    @Inject(method = "updateBlock", at = @At("HEAD"))
    private void ash$forgetOnChange(int minX, int minY, int minZ, int maxX, int maxY, int maxZ, CallbackInfo info) {
        FasterViewScan.forget();
    }

    @Inject(method = "setWorld", at = @At("HEAD"))
    private void ash$forgetOnWorld(ClientWorld world, CallbackInfo info) {
        FasterViewScan.forget();
    }

    @Inject(method = "reload()V", at = @At("HEAD"))
    private void ash$forgetOnReload(CallbackInfo info) {
        FasterViewScan.forget();
    }
}
