package com.ashlauncher.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
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
            if (row.label().startsWith(setting.feature().displayName() + ": ")) {
                return row;
            }
        }
        throw new AssertionError("no row for " + setting.key());
    }

    @Test
    void there_is_a_row_for_every_declared_setting_in_the_order_they_are_declared() {
        Settings settings = Settings.load(configDir);

        List<SettingsScreen.Row> rows = screenWhereEverythingLanded(settings).rows();

        // Walked, not listed: a setting declared tomorrow gets a row without
        // a line of screen code.
        assertFalse(Settings.declared().isEmpty(), "the test proves nothing with no settings declared");
        assertEquals(Settings.declared().size(), rows.size());
        for (int i = 0; i < rows.size(); i++) {
            OnOff setting = Settings.declared().get(i);
            assertEquals(setting.feature().displayName() + ": On", rows.get(i).label());
            assertTrue(rows.get(i).available());
        }
    }

    @Test
    void pressing_a_row_switches_its_setting_now_and_for_the_next_session() throws IOException {
        Settings settings = Settings.load(configDir);
        SettingsScreen.Row fps = row(screenWhereEverythingLanded(settings), Settings.FPS_READOUT);

        fps.press();

        assertEquals("FPS readout: Off", fps.label());
        assertFalse(settings.get(Settings.FPS_READOUT), "the running game did not see the change");
        assertFalse(Settings.load(configDir).get(Settings.FPS_READOUT), "the change was not saved");

        fps.press();

        assertEquals("FPS readout: On", fps.label());
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
        assertEquals("Toggle sprint: Did not load", sprint.label());
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

        assertEquals("FPS readout and Toggle sprint did not load. That is a problem with ash, not with your game or"
                + " your setup, and an update to ash will fix it.", screen.footer());
    }
}
