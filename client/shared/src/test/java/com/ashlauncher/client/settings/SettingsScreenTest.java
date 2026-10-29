package com.ashlauncher.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.hud.HudLayout;
import com.ashlauncher.client.hud.Placement;
import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.report.Target;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Everything ash's settings screen decides, without a game: which rows there
 * are, what each says, and what pressing one does. Each version target draws
 * these rows with its own buttons and decides nothing.
 */
class SettingsScreenTest {

    @TempDir
    Path configDir;

    private final List<String> changes = new ArrayList<>();

    private SettingsScreen screenWhereEverythingLanded(Settings settings) {
        return new SettingsScreen(settings, feature -> true, () -> changes.add("changed"));
    }

    /** The row for a setting, found by what it switches rather than by where it is. */
    private static SettingsScreen.Row row(SettingsScreen screen, OnOff setting) {
        for (SettingsScreen.Row row : screen.rows()) {
            if (row.feature() == setting.feature()) {
                return row;
            }
        }
        throw new AssertionError("no row for " + setting.key());
    }

    @Test
    void a_readout_s_place_is_set_in_edit_hud_so_it_gives_the_readout_no_options_page() {
        SettingsScreen screen = screenWhereEverythingLanded(Settings.load(configDir));

        assertFalse(row(screen, Settings.FPS_READOUT).hasOptions());
        assertFalse(row(screen, Settings.PING_READOUT).hasOptions());
    }

    @Test
    void resetting_the_readouts_puts_both_back_and_says_so() {
        Settings settings = Settings.load(configDir);
        settings.set(Settings.FPS_READOUT_POSITION, Placement.parse("bottom-right 4 4"));
        settings.set(Settings.PING_READOUT_POSITION, Placement.parse("top-right 4 4"));
        SettingsScreen screen = screenWhereEverythingLanded(settings);

        screen.resetReadouts();

        assertEquals(Settings.FPS_READOUT_POSITION.fallback(), Settings.load(configDir).get(Settings.FPS_READOUT_POSITION));
        assertEquals(Settings.PING_READOUT_POSITION.fallback(),
                Settings.load(configDir).get(Settings.PING_READOUT_POSITION));
        assertFalse(changes.isEmpty(), "the load report was not rewritten");
    }

    @Test
    void there_is_a_row_for_every_declared_setting_in_the_order_they_are_declared() {
        Settings settings = Settings.load(configDir);

        List<SettingsScreen.Row> rows = screenWhereEverythingLanded(settings).rows();

        // Walked, not listed: a setting declared tomorrow gets a row without
        // a line of screen code.
        assertFalse(Settings.switches().isEmpty(), "the test proves nothing with no settings declared");
        assertEquals(Settings.switches().size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            OnOff setting = Settings.switches().get(i);
            assertEquals(setting.feature(), rows.get(i).feature());
            assertTrue(rows.get(i).on(), setting.key() + " is not on by default");
            assertTrue(rows.get(i).available());
        }
    }

    @Test
    void pressing_a_row_switches_its_setting_now_and_for_the_next_session() throws IOException {
        Settings settings = Settings.load(configDir);
        SettingsScreen.Row fps = row(screenWhereEverythingLanded(settings), Settings.FPS_READOUT);

        fps.press();

        assertFalse(fps.on());
        assertFalse(settings.get(Settings.FPS_READOUT), "the running game did not see the change");
        assertFalse(Settings.load(configDir).get(Settings.FPS_READOUT), "the change was not saved");

        fps.press();

        assertTrue(fps.on());
        assertTrue(Settings.load(configDir).get(Settings.FPS_READOUT));
    }

    @Test
    void each_change_is_announced_after_it_has_been_made() {
        // The announcement rewrites the load report, which reads the settings -
        // so by the time it runs, the new value has to be there to read.
        Settings settings = Settings.load(configDir);
        List<Boolean> seen = new ArrayList<>();
        SettingsScreen screen = new SettingsScreen(settings, feature -> true,
                () -> seen.add(settings.get(Settings.TOGGLE_SPRINT)));

        row(screen, Settings.TOGGLE_SPRINT).press();

        assertEquals(List.of(false), seen);
    }

    @Test
    void a_feature_that_did_not_load_cannot_be_switched_and_says_why_in_the_launcher_s_words() throws IOException {
        Settings settings = Settings.load(configDir);
        Path file = configDir.resolve("ash.properties");
        String before = Files.readString(file);
        SettingsScreen screen = new SettingsScreen(settings, feature -> feature != Feature.TOGGLE_SPRINT,
                () -> changes.add("changed"));
        SettingsScreen.Row sprint = row(screen, Settings.TOGGLE_SPRINT);

        sprint.press();

        assertFalse(sprint.available());
        assertTrue(settings.get(Settings.TOGGLE_SPRINT), "a switch that does nothing was switched anyway");
        assertEquals(before, Files.readString(file));
        assertEquals(List.of(), changes);
        assertEquals("Toggle sprint did not load. That is a problem with ash, not with your game or your setup,"
                + " and an update to ash will fix it.", screen.footer());
    }

    @Test
    void a_change_that_could_not_be_saved_says_so_and_still_takes_effect_for_the_session() throws IOException {
        Settings settings = Settings.load(configDir);
        Files.createDirectory(configDir.resolve("ash.properties.partial"));
        SettingsScreen screen = screenWhereEverythingLanded(settings);

        row(screen, Settings.FPS_READOUT).press();

        assertEquals(Saved.FILE_UNWRITABLE.message(), screen.footer());
        assertFalse(settings.get(Settings.FPS_READOUT));
        assertEquals(List.of("changed"), changes);
    }

    @Test
    void with_nothing_to_report_the_footer_is_empty() {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = screenWhereEverythingLanded(settings);

        row(screen, Settings.FPS_READOUT).press();

        assertFalse(settings.get(Settings.FPS_READOUT), "the test proves nothing if the press did nothing");
        assertEquals("", screen.footer());
    }

    @Test
    void every_feature_that_did_not_load_is_named_as_the_launcher_names_them() {
        // The launcher's notice lists every one; a screen that named only the
        // first would tell a different story about the same session.
        SettingsScreen screen = new SettingsScreen(Settings.load(configDir), feature -> false, () -> { });

        assertEquals("FPS readout, Toggle sprint, Crosshair, Hit indicator, Freelook, Snaplook, Ping readout, Hit"
                + " colour, Faster clouds, Faster view scan and Cosmetics did not load. That is a problem with ash, not"
                + " with your game"
                + " or your setup, and an update to ash will fix it.", screen.footer());
    }

    @Test
    void a_feature_this_target_does_not_have_yet_has_no_row_at_all() {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = new SettingsScreen(settings, feature -> feature != Feature.FREELOOK,
                feature -> true, feature -> "", () -> changes.add("changed"));

        for (SettingsScreen.Row row : screen.rows()) {
            assertTrue(row.feature() != Feature.FREELOOK, "a feature this target lacks has a row");
        }
        assertEquals("", screen.footer(), "a feature this target lacks was reported as one that did not load");
    }

    @Test
    void a_feature_off_on_this_server_cannot_be_switched_and_says_why_without_blaming_ash() {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = new SettingsScreen(settings, feature -> true, feature -> true,
                feature -> feature == Feature.FREELOOK ? "Freelook is off on Hypixel: its rules ban it." : "",
                () -> changes.add("changed"));
        SettingsScreen.Row freelook = row(screen, Settings.FREELOOK);

        freelook.press();

        assertFalse(freelook.available());
        assertTrue(freelook.loaded());
        assertTrue(settings.get(Settings.FREELOOK), "it was switched off as if the player had chosen to");
        assertEquals("Freelook is off on Hypixel: its rules ban it.", freelook.whyUnavailable());
        assertEquals("", screen.footer(), "a server's ban was told as ash's fault");
        assertEquals(List.of(), changes);
    }

    // ---- beta phase 1 (spec 0007, The settings screen; behind the beta switch, ADR-0029) ----

    private SettingsScreen beta(Settings settings, Target target) {
        return new SettingsScreen(settings, target, true, feature -> true, feature -> "",
                () -> changes.add("changed"), new HudLayout(settings));
    }

    private SettingsScreen release(Settings settings, Target target) {
        return new SettingsScreen(settings, target, false, feature -> true, feature -> "",
                () -> changes.add("changed"), new HudLayout(settings));
    }

    /** What a search shows, tile by tile: the feature's name, and the option found in it, if one was. */
    private static List<String> found(SettingsScreen screen, String query, Category tab) {
        List<String> found = new ArrayList<>();
        for (SettingsScreen.Found each : screen.find(query, tab)) {
            found.add(each.option() == null ? each.row().name() : each.row().name() + ": " + each.option().label());
        }
        return found;
    }

    @Test
    void the_beta_files_tiles_under_seven_tabs_in_order_leaving_out_the_empty_ones() {
        SettingsScreen screen = beta(Settings.load(configDir), Target.V1_21_11);

        // Nothing is filed under Chat yet, so it has no tab.
        assertEquals(List.of(Category.HUD, Category.PVP, Category.VISUAL, Category.MOVEMENT, Category.PERFORMANCE,
                Category.COSMETICS), screen.tabs());
    }

    @Test
    void a_release_build_keeps_the_alpha_s_tabs() {
        SettingsScreen screen = release(Settings.load(configDir), Target.V1_21_11);

        assertEquals(List.of(Category.PVP, Category.HUD, Category.MOVEMENT, Category.COSMETICS), screen.tabs());
        assertEquals(Category.PVP, row(screen, Settings.HIT_COLOUR).category());
    }

    @Test
    void hit_colour_moves_to_visual() {
        assertEquals(Category.VISUAL, row(beta(Settings.load(configDir), Target.V1_21_11), Settings.HIT_COLOUR)
                .category());
    }

    @Test
    void a_feature_this_version_cannot_have_says_so_on_its_tile_and_is_not_blamed_on_ash() {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = beta(settings, Target.V1_21_11);
        SettingsScreen.Row clouds = row(screen, Settings.FASTER_CLOUDS);

        clouds.press();

        assertEquals("Not on 1.21.11", clouds.notHere());
        assertFalse(clouds.available());
        assertEquals("Faster clouds is only on 1.8.9.", clouds.whyUnavailable());
        assertTrue(settings.get(Settings.FASTER_CLOUDS), "a switch that does nothing here was switched");
        assertEquals(List.of(), changes);
        assertEquals("", screen.footer(), "a feature this version can't have was told as one that did not load");
        assertEquals("", row(screen, Settings.CROSSHAIR).notHere());
    }

    @Test
    void a_release_build_leaves_out_a_feature_this_version_cannot_have() {
        SettingsScreen screen = release(Settings.load(configDir), Target.V1_21_11);

        for (SettingsScreen.Row row : screen.rows()) {
            assertTrue(row.feature() != Feature.FASTER_CLOUDS, "a release build showed a feature 1.21.11 lacks");
        }
    }

    @Test
    void search_finds_a_feature_by_its_name_and_an_option_by_its_own() {
        SettingsScreen screen = beta(Settings.load(configDir), Target.V1_21_11);

        assertEquals(List.of("Crosshair: Gap"), found(screen, "gap", null));
        // Two options called Colour, and a feature named for it.
        assertEquals(List.of("Crosshair: Colour", "Hit indicator: Colour", "Hit colour"),
                found(screen, "COLOUR", null));
        // An option by its feature's name and its own together.
        assertEquals(List.of("Crosshair: Colour"), found(screen, "crosshair col", null));
        // Within a tab, only that tab's.
        assertEquals(List.of("Hit colour"), found(screen, "colour", Category.VISUAL));
        assertEquals(List.of(), found(screen, "nothing like this", null));
    }

    @Test
    void a_release_build_searches_feature_names_only() {
        SettingsScreen screen = release(Settings.load(configDir), Target.V1_21_11);

        assertEquals(List.of(), found(screen, "gap", null));
        assertEquals(List.of("Hit colour"), found(screen, "colour", null));
    }

    @Test
    void starred_tiles_come_first_on_all_in_the_order_they_were_starred() {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = beta(settings, Target.V1_21_11);

        screen.star(Feature.FREELOOK);
        screen.star(Feature.CROSSHAIR);

        List<SettingsScreen.Found> all = screen.find("", null);
        assertEquals(Feature.FREELOOK, all.get(0).row().feature());
        assertEquals(Feature.CROSSHAIR, all.get(1).row().feature());
        assertTrue(all.get(0).favourite() && all.get(1).favourite());
        assertFalse(all.get(2).favourite());
        assertEquals(Feature.FPS_READOUT, all.get(2).row().feature(), "the rest are not in the file's order");
        assertEquals(screen.rows().size(), all.size(), "a starred tile shows twice");
        assertTrue(screen.starred(Feature.FREELOOK));
        assertFalse(screen.starred(Feature.SNAPLOOK));
    }

    @Test
    void favourites_are_a_client_setting_so_they_are_saved_and_sync() throws IOException {
        Settings settings = Settings.load(configDir);
        SettingsScreen screen = beta(settings, Target.V1_21_11);

        screen.star(Feature.FREELOOK);
        screen.star(Feature.CROSSHAIR);
        screen.star(Feature.FREELOOK);

        // Sync carries every line of the client's settings file but panel.size (ADR-0022).
        assertTrue(Files.readString(configDir.resolve("ash.properties")).contains("panel.favourites=crosshair\n"));
        assertEquals(List.of(Feature.CROSSHAIR), Settings.load(configDir).get(Settings.FAVOURITES));
        assertEquals(List.of("changed", "changed", "changed"), changes);
    }

    @Test
    void favourites_keep_the_file_s_order_on_a_tab_and_in_a_search() {
        SettingsScreen screen = beta(Settings.load(configDir), Target.V1_21_11);
        screen.star(Feature.HIT_INDICATOR);

        List<SettingsScreen.Found> pvp = screen.find("", Category.PVP);
        assertEquals(Feature.CROSSHAIR, pvp.get(0).row().feature());
        assertFalse(pvp.get(1).favourite());
        assertEquals(List.of("Crosshair", "Hit indicator"), found(screen, "i", Category.PVP).subList(0, 2));
    }

    @Test
    void a_release_build_has_no_favourites_row() {
        Settings settings = Settings.load(configDir);
        beta(settings, Target.V1_21_11).star(Feature.COSMETICS);

        List<SettingsScreen.Found> all = release(settings, Target.V1_21_11).find("", null);

        assertEquals(Feature.FPS_READOUT, all.get(0).row().feature());
        assertFalse(all.get(0).favourite());
    }
}
