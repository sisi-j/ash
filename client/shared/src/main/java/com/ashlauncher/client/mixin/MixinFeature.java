package com.ashlauncher.client.mixin;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Whether a feature's mixin landed, decided once at startup.
 *
 * <p>The same decision on both targets, so it is made here; each target
 * supplies only the one thing it cannot share, the class its mixin targets.
 * Loading that class is what applies the mixin - now, rather than at the first
 * world - so the feature knows before anything registers, and the load report
 * can say so before the player has done anything at all.
 *
 * <p>Asked whether or not the player has the feature on. A feature can be
 * switched on in game, mid-session, and what it needs - a key binding, above
 * all - can only be registered at startup; so whether the mixin landed has to
 * be known then, not only for the features that started switched on. What the
 * load report says comes from both: see {@code FeatureStatus.of}.
 */
public final class MixinFeature {

    private MixinFeature() {
    }

    /**
     * @param target loads the class the mixin targets
     * @param mixin the mixin's dotted class name, as in the config
     * @param log where to say why, when it did not land
     * @return whether every one of the mixin's injectors is wired into its target
     */
    public static boolean landed(Supplier<Class<?>> target, String mixin, Consumer<String> log) {
        try {
            target.get();
        } catch (LinkageError broken) {
            log.accept("the class " + mixin + " targets would not load (" + broken + ")");
            return false;
        }
        List<String> unwired = AshMixinPlugin.unwired(mixin);
        if (unwired == null) {
            log.accept(mixin + " was never applied - Mixin rejected it, and its own log says why");
            return false;
        }
        if (!unwired.isEmpty()) {
            log.accept(mixin + " applied, but these injectors matched nothing on this game version: " + unwired);
            return false;
        }
        return true;
    }
}
