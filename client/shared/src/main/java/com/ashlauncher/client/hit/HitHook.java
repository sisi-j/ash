package com.ashlauncher.client.hit;

import com.ashlauncher.client.hud.HudSurface;

/**
 * Where each target's mixins reach ash's {@link HitIndicator}.
 *
 * <p>Static because a mixin can only reach the rest of ash through something
 * static. Installed by the target's entrypoint once its mixins are known to
 * have landed; until then, and if they never are, every report is ignored.
 *
 * <p>Every report here is the server's word, never the click's. On both
 * targets the client's own attack runs the success path on the click when the
 * target is another player, so nothing on that path is a confirmation. See
 * {@code docs/research/0004-vanilla-hooks-for-phase-3-features.md}, 2.
 */
public final class HitHook {

    private static HitIndicator indicator;
    private static RecentAttacks attacks;

    private HitHook() {
    }

    /**
     * @param attacks the player's recent attacks, on a target whose hurt
     *     message names no attacker; {@code null} on one whose does
     */
    public static void install(HitIndicator indicator, RecentAttacks attacks) {
        HitHook.indicator = indicator;
        HitHook.attacks = attacks;
    }

    /**
     * 1.21.11: the server's damage event, which names its cause. The player's
     * when they caused it to something other than themselves.
     *
     * @param causeId the entity behind the damage - the shooter of an arrow,
     *     not the arrow - or -1 for none
     */
    public static void damageEvent(int hurtId, int causeId, int playerId) {
        if (indicator != null && causeId == playerId && hurtId != playerId) {
            indicator.confirmed();
        }
    }

    /** 1.8.9: the player attacked this entity. Only a click; it marks nothing until the server agrees. */
    public static void attacked(int entityId) {
        if (attacks != null) {
            attacks.attacked(entityId);
        }
    }

    /** 1.8.9: the server says this entity was hurt, by someone. */
    public static void hurt(int entityId) {
        if (indicator != null && attacks != null && attacks.hurt(entityId)) {
            indicator.confirmed();
        }
    }

    /** Draws the mark around the crosshair's centre pixel, if there is one to show. */
    public static void draw(HudSurface surface, int centreX, int centreY) {
        if (indicator != null) {
            indicator.draw(surface, centreX, centreY);
        }
    }
}
