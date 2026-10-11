package com.ashlauncher.client.cosmetics;

import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * Whose cosmetics this client draws (spec 0004): every ash player's, unless
 * cosmetics are switched off - then nobody's - or "Hide others' cosmetics" is
 * on, when only the player's own.
 *
 * <p>The per-target hooks ask this for each player the game is about to
 * draw, and change only the texture the game was going to use anyway.
 */
public final class Cosmetics {

    private final CapeSource source;
    private final BooleanSupplier enabled;
    private final BooleanSupplier hideOthers;
    private final Supplier<UUID> self;

    public Cosmetics(CapeSource source, BooleanSupplier enabled, BooleanSupplier hideOthers, Supplier<UUID> self) {
        this.source = source;
        this.enabled = enabled;
        this.hideOthers = hideOthers;
        this.self = self;
    }

    /** The cape to draw on {@code player} in place of the game's, or null for the game's own. */
    public CapeSource.Cape capeFor(UUID player) {
        if (player == null || !enabled.getAsBoolean()) {
            return null;
        }
        if (hideOthers.getAsBoolean() && !player.equals(self.get())) {
            return null;
        }
        return source.capeFor(player);
    }
}
