package com.ashlauncher.client.v1_8_9.mixin;

import com.ashlauncher.client.v1_8_9.ChunkNeighbours;
import com.ashlauncher.client.v1_8_9.FasterChunkSearch;
import java.util.Arrays;
import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

/** Faster chunk search on 1.8.9: each section keeps its six neighbours, as {@link ChunkNeighbours} says. */
@Mixin(BuiltChunk.class)
abstract class BuiltChunkNeighboursMixin implements ChunkNeighbours {

    @Unique
    private final BuiltChunk[] ash$neighbours = new BuiltChunk[6];

    /** The position object each neighbour was found at; null where none is kept. */
    @Unique
    private final BlockPos[] ash$neighboursAt = new BlockPos[6];

    @Unique
    private BuiltChunkStorage ash$neighboursIn;

    @Override
    public BuiltChunk ash$neighbour(Direction direction, BlockPos at, BuiltChunkStorage storage) {
        int index = direction.ordinal();
        if (ash$neighboursIn == storage && ash$neighboursAt[index] == at) {
            FasterChunkSearch.keptNeighbour();
            return ash$neighbours[index];
        }
        if (ash$neighboursIn != storage) {
            Arrays.fill(ash$neighboursAt, null);
            Arrays.fill(ash$neighbours, null);
            ash$neighboursIn = storage;
        }
        BuiltChunk found = ((BuiltChunkStorageAccess) storage).ash$getRenderedChunk(at);
        ash$neighbours[index] = found;
        ash$neighboursAt[index] = at;
        return found;
    }
}
