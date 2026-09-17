package com.ashlauncher.client.v1_21_11;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import org.junit.jupiter.api.Test;

/**
 * What the game's own mod list will say, asserted without starting the game.
 *
 * <p>These run under Fabric Loader JUnit, which stands the real loader up
 * inside the test JVM - so what they assert is what the loader itself made of
 * {@code fabric.mod.json}, not what a second reading of the file would make of
 * it. That is the point: a broken manifest, a mod id that changed, or a
 * version that failed to expand at build time all show up here instead of in
 * a game that comes up without ash in it.
 */
class AshModMetadataTest {

    @Test
    void the_loader_finds_ash_by_the_id_the_mod_list_will_show() {
        ModContainer ash = FabricLoader.getInstance()
                .getModContainer("ash")
                .orElseThrow(() -> new AssertionError(
                        "Fabric Loader did not find ash; it will not be in the mod list either"));

        assertEquals("ash", ash.getMetadata().getName());
        assertTrue(ash.getMetadata().getDescription().length() > 0,
                "the mod list shows this, so an empty one is a blank row");
    }

    @Test
    void the_version_reached_the_manifest_from_the_build() {
        String version = FabricLoader.getInstance()
                .getModContainer("ash")
                .orElseThrow()
                .getMetadata()
                .getVersion()
                .getFriendlyString();

        // `processResources` expands this from `gradle.properties`. An
        // unexpanded one reads as the literal placeholder, which the loader
        // would happily accept and a bug report would then name.
        assertEquals(System.getProperty("ash.test.version"), version);
    }

    @Test
    void the_fabric_api_ash_depends_on_is_there() {
        // ash's client hard-depends on Fabric API: the HUD element it draws
        // the marker through is an API type, not a mixin. The loader refuses
        // to start a game where a declared dependency is absent, so a build
        // that lost the dependency would fail on a player's machine and
        // nowhere earlier.
        assertTrue(FabricLoader.getInstance().isModLoaded("fabric-api"),
                "Fabric API is not on the class path");
    }
}
