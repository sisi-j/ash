package com.ashlauncher.client.fairplay;

import com.ashlauncher.client.game.GameView;
import com.ashlauncher.client.report.Feature;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * The note in game on joining a server where a feature the player has on is
 * off by the fair-play list: one line a feature, naming it and why, once per
 * join (spec 0007, Fair play). Without it, a feature that does nothing would
 * look broken.
 *
 * <p>Ticked by each target. It waits until the player is in the world, so the
 * note isn't lost while the world loads; a change of dimension isn't a join,
 * and leaving and coming back is.
 */
public final class FairPlayNotes {

    private final FairPlay list;
    private final GameView game;
    private final Predicate<Feature> on;
    private final Consumer<String> say;
    /** The address this join was noted under; null while not on a server. */
    private String noted;

    /**
     * @param on whether the player has a feature on, and it loaded: there is
     *     nothing to say about one they don't
     * @param say shows the player one line in game
     */
    public FairPlayNotes(FairPlay list, GameView game, Predicate<Feature> on, Consumer<String> say) {
        this.list = list;
        this.game = game;
        this.on = on;
        this.say = say;
    }

    /** Once a tick. */
    public void tick() {
        String address = game.serverAddress();
        if (address == null) {
            noted = null;
            return;
        }
        if (!game.inWorld() || address.equals(noted)) {
            return;
        }
        noted = address;
        for (FairPlay.Rule rule : list.rulesAt(address)) {
            if (on.test(rule.feature())) {
                say.accept(rule.whyOff());
            }
        }
    }
}
