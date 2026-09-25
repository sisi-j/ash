package com.ashlauncher.client.mixin;

import java.util.ArrayList;
import java.util.List;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;

/**
 * Whether a mixin's injectors actually landed in the class it was applied to.
 *
 * <p>ash's mixins run with {@code require = 0}, because the alternative is a
 * crash: an injection point that matches nothing throws an
 * {@code InjectionError}, which escapes Mixin's error handling whatever the
 * config's {@code required} says. With nothing required, a match of nothing is
 * silent - the handler is merged into the target and nothing calls it - and a
 * feature would quietly not exist. So this looks, after the fact, for a call
 * to every injector's handler. One with no caller is an injector that did not
 * land, and its feature is degraded.
 *
 * <p>Mixin renames a handler as it merges it, keeping the original name as the
 * end of the new one: {@code ash$readToggleKey} becomes
 * {@code handler$zza000$ash$readToggleKey}. So a call counts if the called
 * name is the handler's, or ends in {@code $} and the handler's.
 */
public final class InjectorWiring {

    /** Where the annotations that make a method an injector live. */
    private static final String[] INJECTOR_PACKAGES = {
        "Lorg/spongepowered/asm/mixin/injection/",
        "Lcom/llamalad7/mixinextras/injector/",
    };

    private InjectorWiring() {
    }

    /**
     * The mixin's injector handlers that nothing in {@code target} calls.
     *
     * @param target the class as it stands after the mixin was applied
     * @param mixin the mixin class itself, as Mixin read it
     */
    public static List<String> unwired(ClassNode target, ClassNode mixin) {
        List<String> unwired = new ArrayList<>();
        for (MethodNode method : mixin.methods) {
            if (isInjector(method) && !called(target, method.name)) {
                unwired.add(method.name);
            }
        }
        return unwired;
    }

    private static boolean isInjector(MethodNode method) {
        return hasInjectorAnnotation(method.visibleAnnotations) || hasInjectorAnnotation(method.invisibleAnnotations);
    }

    private static boolean hasInjectorAnnotation(List<AnnotationNode> annotations) {
        if (annotations == null) {
            return false;
        }
        for (AnnotationNode annotation : annotations) {
            for (String prefix : INJECTOR_PACKAGES) {
                if (annotation.desc.startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean called(ClassNode target, String handler) {
        String renamed = "$" + handler;
        for (MethodNode method : target.methods) {
            for (AbstractInsnNode instruction : method.instructions) {
                if (instruction instanceof MethodInsnNode) {
                    String name = ((MethodInsnNode) instruction).name;
                    if (name.equals(handler) || name.endsWith(renamed)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
}
