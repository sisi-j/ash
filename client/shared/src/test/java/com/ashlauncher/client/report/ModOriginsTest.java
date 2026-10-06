package com.ashlauncher.client.report;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ModOriginsTest {

    private static final Path GAME = Paths.get("instances", "pvp", "minecraft").toAbsolutePath();
    private static final Path MODS = GAME.resolve("mods");
    /** Where ash points the loader while the player's own mods are off. */
    private static final Path NO_MODS = Paths.get("data", "no-mods").toAbsolutePath();
    private static final Path DEPOT = Paths.get("depot", "libraries").toAbsolutePath();

    private static Map<String, List<Path>> loaded(Object... idThenPath) {
        Map<String, List<Path>> mods = new LinkedHashMap<>();
        for (int i = 0; i < idThenPath.length; i += 2) {
            mods.put((String) idThenPath[i], Collections.singletonList((Path) idThenPath[i + 1]));
        }
        return mods;
    }

    @Test
    void with_the_players_mods_off_nothing_of_theirs_loaded() {
        ModOrigins origins = ModOrigins.of(NO_MODS, loaded(
                "ash", Paths.get("client", "ash-client-1.21.11.jar").toAbsolutePath(),
                "fabric-api", DEPOT.resolve("fabric-api-0.141.6+1.21.11.jar")));

        assertFalse(origins.thirdPartyModsLoaded());
        assertEquals(Collections.singletonMap("fabric-api", BundledCopy.ASH),
                origins.bundled(Collections.singletonList("fabric-api")));
    }

    @Test
    void a_mod_from_the_players_folder_is_one_of_theirs() {
        ModOrigins origins = ModOrigins.of(MODS, loaded(
                "fabric-api", DEPOT.resolve("fabric-api-0.141.6+1.21.11.jar"),
                "sodium", MODS.resolve("sodium-fabric-0.6.13+mc1.21.11.jar")));

        assertTrue(origins.thirdPartyModsLoaded());
    }

    @Test
    void ash_s_own_client_in_the_players_folder_is_not_a_third_party_mod() {
        ModOrigins origins = ModOrigins.of(MODS, loaded("ash", MODS.resolve("ash-client-1.21.11.jar")));

        assertFalse(origins.thirdPartyModsLoaded());
    }

    @Test
    void the_players_newer_copy_of_a_bundled_mod_is_reported_as_theirs() {
        // What the loader does with two copies: keeps the newest, whichever
        // folder it came from. Only the one it kept is in the list.
        ModOrigins origins = ModOrigins.of(MODS, loaded(
                "fabric-api", MODS.resolve("fabric-api-0.142.0+1.21.11.jar")));

        assertEquals(Collections.singletonMap("fabric-api", BundledCopy.PLAYER),
                origins.bundled(Collections.singletonList("fabric-api")));
        assertTrue(origins.thirdPartyModsLoaded());
    }

    @Test
    void a_bundled_mod_that_did_not_load_is_left_out_and_the_order_is_kept() {
        ModOrigins origins = ModOrigins.of(NO_MODS, loaded(
                "legacy-fabric-rendering-api-v1", DEPOT.resolve("rendering.jar"),
                "legacy-fabric-api", DEPOT.resolve("api.jar")));

        Map<String, BundledCopy> copies = origins.bundled(Arrays.asList(
                "legacy-fabric-api", "legacy-fabric-keybinding-api-v1-common", "legacy-fabric-rendering-api-v1"));

        assertEquals(Arrays.asList("legacy-fabric-api", "legacy-fabric-rendering-api-v1"),
                Arrays.asList(copies.keySet().toArray()));
    }

    @Test
    void a_path_that_only_looks_like_it_is_in_the_folder_is_not() {
        // `mods-old` starts with the same letters as `mods` and is a different folder.
        ModOrigins origins = ModOrigins.of(MODS, loaded("sodium", GAME.resolve("mods-old").resolve("sodium.jar")));

        assertFalse(origins.thirdPartyModsLoaded());
    }
}
