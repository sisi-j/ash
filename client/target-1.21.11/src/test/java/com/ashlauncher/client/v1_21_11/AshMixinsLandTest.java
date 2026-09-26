package com.ashlauncher.client.v1_21_11;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.mixin.AshMixinPlugin;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassReader;
import org.objectweb.asm.Type;
import org.objectweb.asm.tree.AnnotationNode;
import org.objectweb.asm.tree.ClassNode;

/**
 * Every mixin ash declares lands on this version target - on every
 * {@code ./gradlew build}, in seconds, with the real loader and the real game
 * classes, and no game running.
 *
 * <p>The client game test catches the same thing, but only in CI and only
 * after a minute of starting a game. This is the same safety net ADR-0017
 * relies on, moved to where a developer trips over it first: bump the game
 * version, and a mixin that stopped matching fails here, by name, before
 * anyone launches anything.
 *
 * <p>It asks {@code ash.mixins.json} which mixins there are and each mixin's
 * own {@code @Mixin} which class it targets, so a mixin added later is covered
 * without anyone remembering to add a line here. Loading the target is what
 * applies the mixin; {@link AshMixinPlugin} then says whether it landed.
 */
class AshMixinsLandTest {

    private static final ClassLoader LOADER = AshMixinsLandTest.class.getClassLoader();

    @Test
    void every_mixin_ash_declares_lands_on_1_21_11() throws Exception {
        JsonObject config = JsonParser.parseReader(reader("ash.mixins.json")).getAsJsonObject();
        String pkg = config.get("package").getAsString();
        List<String> mixins = new ArrayList<>();
        for (JsonElement name : config.getAsJsonArray("client")) {
            mixins.add(pkg + "." + name.getAsString());
        }
        assertFalse(mixins.isEmpty(), "ash.mixins.json declares no mixins, so this proved nothing");

        List<String> failures = new ArrayList<>();
        for (String mixin : mixins) {
            for (String target : targetsOf(mixin)) {
                Class.forName(target, false, LOADER);
            }
            if (!AshMixinPlugin.landed(mixin)) {
                List<String> unwired = AshMixinPlugin.unwired(mixin);
                failures.add(mixin + (unwired == null ? " was never applied" : " did not land: " + unwired));
            }
        }
        assertTrue(failures.isEmpty(), String.join("\n", failures));
    }

    /** The classes a mixin names in its own {@code @Mixin}, read from its class file rather than loaded. */
    private static List<String> targetsOf(String mixin) throws IOException {
        ClassNode node = new ClassNode();
        try (InputStream in = LOADER.getResourceAsStream(mixin.replace('.', '/') + ".class")) {
            new ClassReader(in).accept(node, ClassReader.SKIP_CODE);
        }
        List<String> targets = new ArrayList<>();
        List<AnnotationNode> annotations = new ArrayList<>();
        if (node.visibleAnnotations != null) {
            annotations.addAll(node.visibleAnnotations);
        }
        if (node.invisibleAnnotations != null) {
            annotations.addAll(node.invisibleAnnotations);
        }
        for (AnnotationNode annotation : annotations) {
            if (!annotation.desc.equals("Lorg/spongepowered/asm/mixin/Mixin;") || annotation.values == null) {
                continue;
            }
            for (int i = 0; i < annotation.values.size(); i += 2) {
                if ("value".equals(annotation.values.get(i))) {
                    for (Object type : (List<?>) annotation.values.get(i + 1)) {
                        targets.add(((Type) type).getClassName());
                    }
                }
            }
        }
        assertFalse(targets.isEmpty(), mixin + " names no target class");
        return targets;
    }

    private static InputStreamReader reader(String resource) {
        InputStream in = LOADER.getResourceAsStream(resource);
        assertTrue(in != null, resource + " is not on the class path");
        return new InputStreamReader(in, StandardCharsets.UTF_8);
    }
}
