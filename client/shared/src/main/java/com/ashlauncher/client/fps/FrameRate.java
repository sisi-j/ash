package com.ashlauncher.client.fps;

/**
 * The game's own frame counter, with the game held on the far side of it.
 *
 * <p>A port rather than a hook: the number already exists in the game - it is
 * what the debug screen shows - and the readout only has to ask for it. Each
 * target answers from its own API in one line.
 */
public interface FrameRate {

    /** Frames drawn in the last second, as the game counts them. */
    int perSecond();
}
