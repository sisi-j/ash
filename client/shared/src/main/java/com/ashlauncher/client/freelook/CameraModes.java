package com.ashlauncher.client.freelook;

/**
 * The game's own camera modes - first person, behind, in front - with the
 * game on the far side. A {@code CameraType} on 1.21.11 and an {@code int}
 * on 1.8.9, so nothing here names either.
 *
 * @param <M> the target's own camera mode
 */
public interface CameraModes<M> {

    M current();

    void set(M mode);

    boolean isFirstPerson(M mode);

    /** The game's own third-person view from in front: what F5 shows second. */
    M front();

    /** The game's own third-person view from behind. */
    M behind();
}
