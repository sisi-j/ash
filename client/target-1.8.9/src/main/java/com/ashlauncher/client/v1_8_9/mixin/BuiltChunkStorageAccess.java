package com.ashlauncher.client.v1_8_9.mixin;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** The section storage's own lookup, which is protected, for faster chunk search to fall back on. */
@Mixin(BuiltChunkStorage.class)
public interface BuiltChunkStorageAccess {

    @Invoker("getRenderedChunk")
    BuiltChunk ash$getRenderedChunk(BlockPos pos);
}
