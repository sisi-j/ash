package com.ashlauncher.client.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
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
    void a_mixin_that_was_never_applied_did_not_land_and_says_so_plainly() {
        boolean landed = MixinFeature.landed(() -> Object.class, "example.NeverAppliedMixin", log::add);

        assertFalse(landed);
        assertEquals(1, log.size(), "log: " + log);
        assertTrue(log.get(0).contains("was never applied"), log.get(0));
        assertFalse(log.get(0).contains("null"), "said null rather than what happened: " + log.get(0));
    }

    @Test
    void a_target_class_that_will_not_load_costs_the_feature_rather_than_the_game() {
        boolean landed = MixinFeature.landed(() -> {
            throw new NoClassDefFoundError("example/Gone");
        }, "example.SomeMixin", log::add);

        assertFalse(landed);
        assertTrue(log.get(0).contains("example/Gone"), "the reason was lost: " + log);
    }

    @Test
    void a_mixin_whose_injectors_all_landed_landed_quietly() {
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

        boolean landed = MixinFeature.landed(() -> Object.class, mixinName, log::add);

        assertTrue(landed);
        assertTrue(log.isEmpty(), "logged about a feature that loaded: " + log);
    }
}
