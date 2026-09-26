package com.ashlauncher.client.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.FeatureStatus;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Whether a feature that needs a mixin gets to run - the one decision both
 * targets' entrypoints make the same way, made once here.
 */
class MixinFeatureTest {

    private final List<String> log = new ArrayList<>();

    @Test
    void a_feature_the_player_switched_off_is_off_and_its_target_is_left_alone() {
        AtomicBoolean loaded = new AtomicBoolean();

        FeatureStatus status = MixinFeature.status(false, () -> {
            loaded.set(true);
            return Object.class;
        }, "example.NeverAppliedMixin", log::add);

        assertEquals(FeatureStatus.OFF, status);
        assertFalse(loaded.get(), "the target was loaded for a feature nobody wants");
        assertTrue(log.isEmpty(), "logged about a feature the player switched off: " + log);
    }

    @Test
    void a_mixin_that_was_never_applied_degrades_its_feature_and_says_so_plainly() {
        FeatureStatus status = MixinFeature.status(true, () -> Object.class, "example.NeverAppliedMixin", log::add);

        assertEquals(FeatureStatus.DEGRADED, status);
        assertEquals(1, log.size(), "log: " + log);
        assertTrue(log.get(0).contains("was never applied"), log.get(0));
        assertFalse(log.get(0).contains("null"), "said null rather than what happened: " + log.get(0));
    }

    @Test
    void a_target_class_that_will_not_load_degrades_its_feature_rather_than_the_game() {
        FeatureStatus status = MixinFeature.status(true, () -> {
            throw new NoClassDefFoundError("example/Gone");
        }, "example.SomeMixin", log::add);

        assertEquals(FeatureStatus.DEGRADED, status);
        assertTrue(log.get(0).contains("example/Gone"), "the reason was lost: " + log);
    }

    @Test
    void a_mixin_whose_injectors_all_landed_loads_its_feature_quietly() {
        String mixinName = "example.LandedMixin";
        ClassNode mixin = new ClassNode();
        MethodNode handler = new MethodNode(Opcodes.ACC_PRIVATE, "ash$go", "()V", null, null);
        handler.visibleAnnotations = new ArrayList<>();
        handler.visibleAnnotations.add(new AnnotationNode("Lorg/spongepowered/asm/mixin/injection/Inject;"));
        mixin.methods.add(handler);
        ClassNode target = new ClassNode();
        target.name = "example/Target";
        MethodNode method = new MethodNode(Opcodes.ACC_PUBLIC, "run", "()V", null, null);
        method.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, "example/Target",
                "handler$zza000$ash$go", "()V", false));
        target.methods.add(method);
        AshMixinPlugin.record(mixinName, target, mixin);

        FeatureStatus status = MixinFeature.status(true, () -> Object.class, mixinName, log::add);

        assertEquals(FeatureStatus.LOADED, status);
        assertTrue(log.isEmpty(), "logged about a feature that loaded: " + log);
    }
}
