package com.ashlauncher.client.v1_8_9;

import net.minecraft.client.render.BuiltChunkStorage;
import net.minecraft.client.world.BuiltChunk;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

/**
 * A chunk section's six neighbours, kept on the section itself by
 * {@code BuiltChunkNeighboursMixin}, for faster chunk search (#104).
 *
 * <p>The game finds the neighbour in a direction by looking up the position
 * the section keeps for that direction in the section storage. That answer
 * depends on nothing but the position and the storage: the storage's array of
 * sections is filled once, when it is made, and never changed. A section that
 * moves gets new position objects for its neighbours. So a neighbour kept for
 * the same position object, in the same storage, is the one the lookup would
 * find.
 */
public interface ChunkNeighbours {

    /** The section the game's lookup would find at {@code at}, the neighbour in {@code direction}. */
    BuiltChunk ash$neighbour(Direction direction, BlockPos at, BuiltChunkStorage storage);
}
