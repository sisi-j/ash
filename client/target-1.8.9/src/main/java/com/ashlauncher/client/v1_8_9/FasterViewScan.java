package com.ashlauncher.client.v1_8_9;

import java.util.EnumSet;
import java.util.Set;
import java.util.function.BooleanSupplier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.chunk.Chunk;

/**
 * Faster view scan (#105): the scan of the blocks round the camera that
 * 1.8.9's chunk culling makes every frame, reused until its answer could
 * differ.
 *
 * <p>Each time the culling looks for visible chunks - every frame the camera
 * moves or turns - the game's {@code getOpenChunkFaces} reads all 4,096
 * blocks of the camera's chunk section and flood-fills them, to find which of
 * the section's faces the camera can see out through. The answer depends on
 * nothing but the block the camera is in and the blocks of that section. The
 * profile put it at 6.5% of a frame.
 *
 * <p>So the answer is kept, and handed back while the camera is in the same
 * block of the same chunk and nothing has been told to change since. It is
 * forgotten whenever:
 * <ul>
 * <li>a block changes in the client's world, by any route;</li>
 * <li>the renderer is told a block, its light or a region changed - which is
 *     also how a chunk arriving, being resent or unloading reaches it;</li>
 * <li>the world changes, or the renderer reloads - which is also when leaves
 *     switch between solid and see-through with the graphics setting.</li>
 * </ul>
 * Kept a moment too long, it would let the culling see through a wall just
 * built, so it errs towards forgetting: any change anywhere drops it, not
 * only one inside the section.
 */
public final class FasterViewScan {

    private static BooleanSupplier wanted = () -> false;

    private static Set<Direction> kept;
    private static int keptX;
    private static int keptY;
    private static int keptZ;
    private static Chunk keptChunk;

    /** Answers handed back without a scan since startup, for the smoke test. */
    static int reused;

    private FasterViewScan() {
    }

    /** Hands over the player's setting; until then, the game scans every time. */
    static void install(BooleanSupplier on) {
        wanted = on;
    }

    /**
     * The kept answer for the camera in this block of this chunk, as a set of
     * its own, which the caller may change; or null to scan.
     */
    public static Set<Direction> kept(BlockPos camera, Chunk chunk) {
        if (!wanted.getAsBoolean()) {
            kept = null;
            return null;
        }
        if (kept == null || chunk != keptChunk || camera.getX() != keptX || camera.getY() != keptY
                || camera.getZ() != keptZ) {
            return null;
        }
        reused++;
        return copy(kept);
    }

    /** Keeps the game's answer for the camera in this block of this chunk. */
    public static void keep(BlockPos camera, Chunk chunk, Set<Direction> answer) {
        if (!wanted.getAsBoolean()) {
            return;
        }
        kept = copy(answer);
        keptX = camera.getX();
        keptY = camera.getY();
        keptZ = camera.getZ();
        keptChunk = chunk;
    }

    /** Something may have changed: the next scan is the game's own. */
    public static void forget() {
        kept = null;
        keptChunk = null;
    }

    private static Set<Direction> copy(Set<Direction> answer) {
        Set<Direction> copy = EnumSet.noneOf(Direction.class);
        copy.addAll(answer);
        return copy;
    }
}
