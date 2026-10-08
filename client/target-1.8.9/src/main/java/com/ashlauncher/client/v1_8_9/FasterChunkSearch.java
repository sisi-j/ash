package com.ashlauncher.client.v1_8_9;

import java.util.function.BooleanSupplier;
import net.minecraft.util.math.Direction;

/**
 * Faster chunk search (#104): 1.8.9's search for the chunks in view, with
 * the same answer for less work.
 *
 * <p>Every frame the camera moves or turns, the game's {@code setupTerrain}
 * walks out from the camera's chunk section to its neighbours, and theirs, as
 * far as the frustum and the render distance allow: about a seventh of a frame
 * in the profile. For each section it reaches it looks up all six neighbours
 * and tests each new one against the frustum. Three things there cost more
 * than they need to, and each is replaced by something that gives the same
 * answer:
 * <ul>
 * <li><b>The neighbour lookup.</b> The game works each neighbour out from its
 *     coordinates, five integer divisions every time, though a section's
 *     neighbours only change when the section moves. Each section keeps its
 *     six, checked against the very position object the game asks with, which
 *     is a new one whenever the section moves (see
 *     {@link ChunkNeighbours}).</li>
 * <li><b>The frustum test.</b> One corner per plane decides it, not eight:
 *     see {@code FrustumPlanes}, which shows the answer is the game's bit for
 *     bit.</li>
 * <li><b>The six directions.</b> The game copies the list of directions for
 *     every section it reaches; one copy is kept.</li>
 * </ul>
 * The search itself - its order, its rules, what it reaches - is the game's.
 */
public final class FasterChunkSearch {

    private static final Direction[] DIRECTIONS = Direction.values();

    private static BooleanSupplier wanted = () -> false;

    /**
     * The setting as it was at the start of this frame's search. Read once a
     * frame, not once a lookup: the lookups run thousands of times a frame,
     * and a settings lookup each time would cost as much as it saves.
     */
    private static boolean on;

    /** Neighbours handed back without a lookup since startup, for the smoke test. */
    static int neighboursKept;

    private FasterChunkSearch() {
    }

    /** Hands over the player's setting; until then, the game searches as it always has. */
    static void install(BooleanSupplier setting) {
        wanted = setting;
    }

    /** Reads the setting for this frame's search. */
    public static void startFrame() {
        on = wanted.getAsBoolean();
    }

    public static boolean on() {
        return on;
    }

    /** The six directions, in the game's order: the one kept copy when on, a fresh one as the game makes when off. */
    public static Direction[] directions() {
        return on ? DIRECTIONS : Direction.values();
    }

    /** Counts a neighbour handed back without a lookup. */
    public static void keptNeighbour() {
        neighboursKept++;
    }
}
