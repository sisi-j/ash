package com.ashlauncher.client.mixin;

import com.ashlauncher.client.report.FeatureStatus;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Whether a feature that needs a mixin gets to run, decided once at startup.
 *
 * <p>The same decision on both targets, so it is made here; each target
 * supplies only the one thing it cannot share, the class its mixin targets.
 * Loading that class is what applies the mixin - now, rather than at the first
 * world - so the feature knows before anything registers, and the load report
 * can say so before the player has done anything at all.
 */
public final class MixinFeature {

    private MixinFeature() {
    }

    /**
     * @param wanted whether the player wants it, from their settings
     * @param target loads the class the mixin targets; not called for a
     *     feature the player switched off
     * @param mixin the mixin's dotted class name, as in the config
     * @param log where to say why, when a wanted feature did not load
     */
    public static FeatureStatus status(boolean wanted, Supplier<Class<?>> target, String mixin,
            Consumer<String> log) {
        if (!wanted) {
            return FeatureStatus.OFF;
        }
        try {
            target.get();
        } catch (LinkageError broken) {
            log.accept("the class " + mixin + " targets would not load (" + broken + ")");
            return FeatureStatus.DEGRADED;
        }
        List<String> unwired = AshMixinPlugin.unwired(mixin);
        if (unwired == null) {
            log.accept(mixin + " was never applied - Mixin rejected it, and its own log says why");
            return FeatureStatus.DEGRADED;
        }
        if (!unwired.isEmpty()) {
            log.accept(mixin + " applied, but these injectors matched nothing on this game version: " + unwired);
            return FeatureStatus.DEGRADED;
        }
        return FeatureStatus.LOADED;
    }
}
