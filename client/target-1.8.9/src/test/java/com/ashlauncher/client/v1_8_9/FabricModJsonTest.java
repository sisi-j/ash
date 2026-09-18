package com.ashlauncher.client.v1_8_9;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.api.ClientModInitializer;
import org.junit.jupiter.api.Test;

/**
 * What {@code fabric.mod.json} says, checked without the loader and without
 * the game.
 *
 * <p>The modern target asks Fabric Loader itself these questions, which is a
 * stronger check because it is the loader's own reading rather than a second
 * one. That tier does not run here — the loader cannot classify a 1.8.9 dev
 * jar — but the two failures most worth catching need neither it nor a game,
 * and "that tier does not work" is not a reason to check nothing.
 */
class FabricModJsonTest {

    /** The manifest as the build processed it, not as it is written in source. */
    private static String manifest() throws IOException {
        try (InputStream in = FabricModJsonTest.class.getResourceAsStream("/fabric.mod.json")) {
            assertNotNull(in, "fabric.mod.json is not on the class path at all");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void the_build_expanded_the_version_rather_than_shipping_the_placeholder() throws IOException {
        String manifest = manifest();

        // An unexpanded `${version}` is a valid-looking string that the loader
        // accepts and the mod list then shows to a player, so nothing downstream
        // would notice - it would just be the version named in every bug report.
        assertFalse(manifest.contains("${"), "a placeholder was never expanded:\n" + manifest);
        assertTrue(
                manifest.contains("\"version\": \"" + System.getProperty("ash.test.version") + "\""),
                "the manifest does not carry the version the build set:\n" + manifest);
    }

    @Test
    void the_client_entrypoint_it_names_is_a_class_that_exists() throws Exception {
        Matcher entry =
                Pattern.compile("\"client\"\\s*:\\s*\\[\\s*\"([^\"]+)\"").matcher(manifest());
        assertTrue(entry.find(), "fabric.mod.json names no client entrypoint");

        // Resolved but deliberately not initialised: loading this class for real
        // would run static initialisers that reach into the game, and what is
        // being asked is only whether the name still points at something.
        Class<?> named = Class.forName(entry.group(1), false, getClass().getClassLoader());
        assertTrue(
                ClientModInitializer.class.isAssignableFrom(named),
                entry.group(1) + " is not a client entrypoint");
    }
}
