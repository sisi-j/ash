package com.ashlauncher.client.mixin;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Whether a mixin's injectors actually landed, judged from the class files the
 * way Mixin leaves them - built here by hand rather than by running a game.
 *
 * <p>Mixin merges each handler into the target under a new name that ends in
 * its own - {@code handler$zza000$ash$readToggleKey},
 * {@code wrapOperation$zzb000$ash$sprintKeyDown} - and, where the injection
 * point matched, calls it from the target method. An injection point that
 * matched nothing leaves the handler merged and called from nowhere, and with
 * {@code require = 0} it says nothing at all. That silence is what this finds.
 */
class InjectorWiringTest {

    private static final String WRAP_OPERATION = "Lcom/llamalad7/mixinextras/injector/wrapoperation/WrapOperation;";
    private static final String INJECT = "Lorg/spongepowered/asm/mixin/injection/Inject;";
    private static final String SHADOW = "Lorg/spongepowered/asm/mixin/Shadow;";

    /** Any class will do. The shared module may not name the game, even here. */
    private static final String TARGET = "example/Target";

    /** A mixin class declaring these methods, each carrying the annotation beside its name. */
    private static ClassNode mixin(String... nameThenAnnotation) {
        ClassNode mixin = new ClassNode();
        mixin.name = "com/ashlauncher/client/v1_21_11/mixin/KeyboardInputMixin";
        for (int i = 0; i < nameThenAnnotation.length; i += 2) {
            MethodNode method = new MethodNode(Opcodes.ACC_PRIVATE, nameThenAnnotation[i], "()Z", null, null);
            String annotation = nameThenAnnotation[i + 1];
            if (annotation != null) {
                method.visibleAnnotations = new ArrayList<>();
                method.visibleAnnotations.add(new AnnotationNode(annotation));
            }
            mixin.methods.add(method);
        }
        return mixin;
    }

    /** A target after Mixin: these handlers merged, and a tick that calls some of them. */
    private static ClassNode target(List<String> merged, List<String> called) {
        ClassNode target = new ClassNode();
        target.name = TARGET;
        MethodNode tick = new MethodNode(Opcodes.ACC_PUBLIC, "method_3129", "()V", null, null);
        for (String name : called) {
            tick.instructions.add(new MethodInsnNode(Opcodes.INVOKEVIRTUAL, TARGET, name, "()Z", false));
        }
        target.methods.add(tick);
        for (String name : merged) {
            target.methods.add(new MethodNode(Opcodes.ACC_PRIVATE, name, "()Z", null, null));
        }
        return target;
    }

    @Test
    void injectors_whose_handlers_the_target_calls_are_wired() {
        ClassNode mixin = mixin("ash$readToggleKey", INJECT, "ash$sprintKeyDown", WRAP_OPERATION);
        List<String> merged = List.of("handler$zza000$ash$readToggleKey", "wrapOperation$zzb000$ash$sprintKeyDown");

        assertEquals(List.of(), InjectorWiring.unwired(target(merged, merged), mixin));
    }

    @Test
    void an_injector_that_matched_nothing_is_found_out() {
        // The silent failure: the handler is merged, nothing calls it, and
        // Mixin said nothing because the injector did not require a match.
        ClassNode mixin = mixin("ash$readToggleKey", INJECT, "ash$sprintKeyDown", WRAP_OPERATION);
        List<String> merged = List.of("handler$zza000$ash$readToggleKey", "wrapOperation$zzb000$ash$sprintKeyDown");

        List<String> unwired = InjectorWiring.unwired(
                target(merged, List.of("handler$zza000$ash$readToggleKey")), mixin);

        assertEquals(List.of("ash$sprintKeyDown"), unwired);
    }

    @Test
    void methods_that_are_not_injectors_are_not_expected_to_be_called() {
        // A shadow of a field, and a plain helper: neither is an injection,
        // so neither having no caller means anything.
        ClassNode mixin = mixin("ash$sprintKeyDown", WRAP_OPERATION, "options", SHADOW, "ash$helper", null);
        List<String> merged = List.of("wrapOperation$zzb000$ash$sprintKeyDown");

        assertEquals(List.of(), InjectorWiring.unwired(target(merged, merged), mixin));
    }

    @Test
    void a_handler_is_not_mistaken_for_another_whose_name_starts_with_its_own() {
        ClassNode mixin = mixin("ash$read", INJECT, "ash$readMore", INJECT);
        List<String> merged = List.of("handler$zza000$ash$read", "handler$zzb000$ash$readMore");

        List<String> unwired = InjectorWiring.unwired(
                target(merged, List.of("handler$zzb000$ash$readMore")), mixin);

        assertEquals(List.of("ash$read"), unwired, "a call to ash$readMore was taken for a call to ash$read");
    }

    @Test
    void an_injector_annotation_kept_only_in_the_class_file_still_counts() {
        ClassNode mixin = mixin("ash$sprintKeyDown", null);
        MethodNode handler = mixin.methods.get(0);
        handler.invisibleAnnotations = new ArrayList<>();
        handler.invisibleAnnotations.add(new AnnotationNode(WRAP_OPERATION));

        assertEquals(List.of("ash$sprintKeyDown"),
                InjectorWiring.unwired(target(List.of(), List.of()), mixin));
    }
}
