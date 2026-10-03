package com.ashlauncher.client.hit;

import java.util.ArrayDeque;
import java.util.Deque;
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
 * entity inside the window, the mark lights for a hit that was not theirs.
 *
 * <p>Each attack lights at most one mark, and every attack is kept, not just
 * an entity's latest: on a slow connection two hits on one entity can both be
 * in flight, and the first hurt to come back must not use up the second. See
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 2.3.
 */
public final class RecentAttacks {

    /**
     * How long after an attack its entity's hurt still counts: the server's
     * answer takes a round trip and up to a tick, so this is long enough for
     * a ping of most of a second. On a worse connection than that, the
     * player's own hits show no mark.
     */
    public static final long WINDOW_MILLIS = 1000;

    private final LongSupplier clockMillis;
    /** Per entity, the times it was attacked, oldest first. */
    private final Map<Integer, Deque<Long>> attackedAt = new HashMap<>();

    /** @param clockMillis a clock that only goes forward */
    public RecentAttacks(LongSupplier clockMillis) {
        this.clockMillis = clockMillis;
    }

    /** The player attacked this entity - a click, not yet a hit. */
    public void attacked(int entityId) {
        long now = clockMillis.getAsLong();
        forgetOlderThan(now);
        Deque<Long> times = attackedAt.get(entityId);
        if (times == null) {
            times = new ArrayDeque<>();
            attackedAt.put(entityId, times);
        }
        times.addLast(now);
    }

    /**
     * The server says this entity was hurt.
     *
     * @return whether it was one the player attacked within the window; if
     *     so, the oldest such attack is used up
     */
    public boolean hurt(int entityId) {
        forgetOlderThan(clockMillis.getAsLong());
        Deque<Long> times = attackedAt.get(entityId);
        if (times == null) {
            return false;
        }
        times.removeFirst();
        if (times.isEmpty()) {
            attackedAt.remove(entityId);
        }
        return true;
    }

    /** How many attacks are being kept, across every entity. */
    int size() {
        int size = 0;
        for (Deque<Long> times : attackedAt.values()) {
            size += times.size();
        }
        return size;
    }

    private void forgetOlderThan(long now) {
        for (Iterator<Deque<Long>> entities = attackedAt.values().iterator(); entities.hasNext(); ) {
            Deque<Long> times = entities.next();
            while (!times.isEmpty() && now - times.peekFirst() > WINDOW_MILLIS) {
                times.removeFirst();
            }
            if (times.isEmpty()) {
                entities.remove();
            }
        }
    }
}
