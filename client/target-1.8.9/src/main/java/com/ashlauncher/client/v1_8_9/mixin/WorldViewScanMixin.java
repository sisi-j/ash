package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.FasterViewScan;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Faster view scan on 1.8.9, in the world: any block set in the client's
 * world forgets the kept scan, by every route there is to setting one. The
 * renderer is not told of every change - the game skips telling it about a
 * chunk it does not count as populated - so this does not rely on it.
 */
@Mixin(World.class)
abstract class WorldViewScanMixin {

    @Shadow
    @Final
    public boolean isClient;

    @Inject(method = "setBlockState(Lnet/minecraft/util/math/BlockPos;Lnet/minecraft/block/BlockState;I)Z",
            at = @At("HEAD"))
    private void ash$forgetOnBlock(BlockPos pos, BlockState state, int flags, CallbackInfoReturnable<Boolean> info) {
        if (isClient) {
            FasterViewScan.forget();
        }
    }
}
