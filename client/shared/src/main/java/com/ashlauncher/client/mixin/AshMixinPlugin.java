package com.ashlauncher.client.mixin;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * Keeps track of which of ash's mixins actually landed, so a feature whose
 * mixin did not can be switched off and reported rather than crash the game or
 * silently not exist. Named as the {@code plugin} in both targets'
 * {@code ash.mixins.json}. See ADR-0017.
 *
 * <p>Both configs are {@code required: false} with {@code defaultRequire: 0},
 * so no failure of ash's can stop the game: a mixin Mixin rejects outright is
 * logged and left out, and an injector that matches nothing is merged and
 * never called. Neither says anything a feature could act on. This is what
 * does: {@link InjectorWiring} checks that every one of a mixin's injectors is
 * called from the class it was applied to.
 *
 * <p>Checked when a feature asks, not in {@link #postApply}. MixinExtras
 * applies its {@code @WrapOperation}s late - in an {@code IExtension}'s
 * {@code postApply}, which runs after every config plugin's - so at the moment
 * this plugin is told a mixin was applied, a wrap has not yet been written
 * into the class. Judging it then called every wrap unwired; the loader-level
 * test caught that on its first run. The class node Mixin hands over is the
 * same object the late extension then finishes, so it is kept, and judged
 * once the class has loaded and the node is final.
 *
 * <p>In the shared module because nothing in it is either game's: Mixin and
 * ASM are the same API on both targets.
 */
public final class AshMixinPlugin implements IMixinConfigPlugin {

    /** Per mixin class, the target and the mixin as they were applied. */
    private static final Map<String, Applied> APPLIED = new ConcurrentHashMap<>();

    /**
     * Whether every injector in the mixin landed.
     *
     * <p>False if any did not, and false if the mixin was never applied at all
     * - its target has not been loaded yet, or Mixin rejected it and logged
     * why. So a feature asks after loading the class its mixin targets.
     *
     * @param mixinClassName dotted, as in the config: a mixin class cannot be
     *     named by a class literal, because loading one directly is an error
     */
    public static boolean landed(String mixinClassName) {
        List<String> unwired = unwired(mixinClassName);
        return unwired != null && unwired.isEmpty();
    }

    /** For the log: which of the mixin's injectors did not land, or null if it was never applied. */
    public static List<String> unwired(String mixinClassName) {
        Applied applied = APPLIED.get(mixinClassName);
        return applied == null ? null : InjectorWiring.unwired(applied.target, applied.mixin);
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
        APPLIED.put(mixinClassName, new Applied(targetClass, mixinInfo.getClassNode(0)));
    }

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return true;
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /** A mixin and the class it went into, kept until someone asks how it went. */
    private static final class Applied {
        final ClassNode target;
        final ClassNode mixin;

        Applied(ClassNode target, ClassNode mixin) {
            this.target = target;
            this.mixin = mixin;
        }
    }
}
