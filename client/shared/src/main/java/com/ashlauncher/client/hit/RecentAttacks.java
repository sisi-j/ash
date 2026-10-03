package com.ashlauncher.client.hit;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * The player's own recent attacks, for 1.8.9, where the server's "this
 * entity was hurt" names no attacker.
 *
 * <p>A hurt counts as the player's when it is of an entity they attacked in
 * the last {@link #WINDOW_MILLIS}. The limit is that it cannot tell whose hit
 * it was: if the player's own swing did not land - the entity was still
 * invulnerable from an earlier hit, say - and someone else's lands on the same
 * entity inside the window, the mark lights for a hit that was not theirs. Each
 * attack lights at most one mark, so one swing is never confirmed twice. See
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 2.3.
 */
public final class RecentAttacks {

    /**
     * How long after an attack its entity's hurt still counts: the server's
     * answer takes a round trip and up to a tick, so this is long enough for
     * a ping of most of a second. A player on a worse connection than that
     * sees no mark at all, never a wrong one of their own.
     */
    public static final long WINDOW_MILLIS = 1000;

    private final LongSupplier clockMillis;
    private final Map<Integer, Long> attackedAt = new HashMap<>();

    /** @param clockMillis a clock that only goes forward */
    public RecentAttacks(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** The player attacked this entity - a click, not yet a hit. */
    public void attacked(int entityId) {
        long now = clockMillis.getAsLong();
        forgetOlderThan(now);
        attackedAt.put(entityId, now);
    }

    /**
     * The server says this entity was hurt.
     *
     * @return whether it was one the player attacked within the window; if
     *     so, that attack is used up
     */
    public boolean hurt(int entityId) {
        long now = clockMillis.getAsLong();
        forgetOlderThan(now);
        return attackedAt.remove(entityId) != null;
    }

    int size() {
        return attackedAt.size();
    }

    private void forgetOlderThan(long now) {
        for (Iterator<Long> at = attackedAt.values().iterator(); at.hasNext(); ) {
            if (now - at.next() > WINDOW_MILLIS) {
                at.remove();
            }
        }
    }
}
