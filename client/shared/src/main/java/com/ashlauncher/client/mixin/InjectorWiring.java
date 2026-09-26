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
 * <p>Mixin renames a handler as it merges it, and the rule is Fabric's
 * {@code MethodMapper.getHandlerName}: strip a leading {@code ash$} or
 * {@code ash_} from the handler's name, then write
 * {@code <prefix>$<unique id>$ash$<what is left>}. So {@code ash$readToggleKey}
 * and {@code ash_readToggleKey} both become
 * {@code handler$zza000$ash$readToggleKey}, and a call counts if the called
 * name ends in {@code $ash$} and the handler's name with its prefix gone.
 *
 * <p>Necessary, not sufficient. A handler called from somewhere in the class
 * proves the injection matched something, not that it matched the call the
 * feature needs - if the sprint read moved, a wrap on every {@code isDown()}
 * would still land on the other six. The real-game tests are what catch that;
 * this catches the silence.
 */
public final class InjectorWiring {

    /** The mod id Fabric's Mixin writes into every handler it merges for ash. */
    private static final String MOD = "ash";

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
        String renamed = "$" + MOD + "$" + withoutModPrefix(handler);
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

    /** What {@code MethodMapper.getHandlerName} keeps of a handler's own name. */
    private static String withoutModPrefix(String handler) {
        boolean prefixed = handler.startsWith(MOD) && handler.length() > MOD.length() + 1
                && (handler.charAt(MOD.length()) == '$' || handler.charAt(MOD.length()) == '_');
        return prefixed ? handler.substring(MOD.length() + 1) : handler;
    }
}
