package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.ChunkNeighbours;
import com.ashlauncher.client.v1_8_9.FasterChunkSearch;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.render.CameraView;
import net.minecraft.client.render.WorldRenderer;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Faster chunk search on 1.8.9, in the world renderer: the setting read once
 * at the start of each frame's search, the search's list of directions kept,
 * and each neighbour found through the section's kept six.
 *
 * <p>{@code getAdjacentChunk} is the game's own, line for line, but for its
 * last step: the lookup goes through {@link ChunkNeighbours} when the feature
 * is on. Its checks on render distance and height are unchanged and still come
 * first, as they depend on where the camera is.
 */
@Mixin(WorldRenderer.class)
abstract class WorldRendererChunkSearchMixin {

    @Shadow
    private BuiltChunkStorage chunks;

    @Shadow
    private int renderDistance;

    @Inject(method = "setupTerrain", at = @At("HEAD"))
    private void ash$startSearch(Entity entity, double tickDelta, CameraView cameraView, int frame,
            boolean spectator, CallbackInfo info) {
        FasterChunkSearch.startFrame();
    }

    @Redirect(method = "setupTerrain",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Direction;values()[Lnet/minecraft/util/math/Direction;"))
    private Direction[] ash$directions() {
        return FasterChunkSearch.directions();
    }

    /**
     * @author ash
     * @reason faster chunk search (#104): the neighbour lookup through the
     *     section's kept six, with the game's own checks unchanged before it
     */
    @Overwrite
    private BuiltChunk getAdjacentChunk(BlockPos pos, BuiltChunk chunk, Direction direction) {
        BlockPos blockPos = chunk.method_10161(direction);
        if (MathHelper.abs(pos.getX() - blockPos.getX()) > this.renderDistance * 16) {
            return null;
        } else if (blockPos.getY() < 0 || blockPos.getY() >= 256) {
            return null;
        } else if (MathHelper.abs(pos.getZ() - blockPos.getZ()) > this.renderDistance * 16) {
            return null;
        } else if (FasterChunkSearch.on()) {
            return ((ChunkNeighbours) chunk).ash$neighbour(direction, blockPos, chunks);
        } else {
            return ((BuiltChunkStorageAccess) chunks).ash$getRenderedChunk(blockPos);
        }
    }
}
