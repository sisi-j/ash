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
class SettingsMenuTest {

    @TempDir
    Path configDir;

    private final List<String> changes = new ArrayList<>();

    private SettingsMenu menuWhereEverythingLanded(Settings settings) {
        return new SettingsMenu(settings, feature -> true, () -> changes.add("changed"));
    }

    @Test
    void there_is_a_row_for_every_declared_setting_in_the_order_they_are_declared() {
        Settings settings = Settings.load(configDir);

        List<SettingsMenu.Row> rows = menuWhereEverythingLanded(settings).rows();

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
        SettingsMenu.Row fps = menuWhereEverythingLanded(settings).rows().get(0);

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
        SettingsMenu menu = new SettingsMenu(settings, feature -> true,
                () -> seen.add(settings.get(Settings.TOGGLE_SPRINT)));

        menu.rows().get(1).press();

        assertEquals(List.of(false), seen);
    }

    @Test
    void a_feature_that_did_not_load_cannot_be_switched_and_says_why_in_the_launcher_s_words() throws IOException {
        Settings settings = Settings.load(configDir);
        Path file = configDir.resolve("ash.properties");
        String before = Files.readString(file);
        SettingsMenu menu = new SettingsMenu(settings, feature -> feature != Feature.TOGGLE_SPRINT,
                () -> changes.add("changed"));
        SettingsMenu.Row sprint = menu.rows().get(1);

        sprint.press();

        assertFalse(sprint.available());
        assertEquals("Toggle sprint: Did not load", sprint.label());
        assertTrue(settings.get(Settings.TOGGLE_SPRINT), "a switch that does nothing was switched anyway");
        assertEquals(before, Files.readString(file));
        assertEquals(List.of(), changes);
        assertEquals("Toggle sprint did not load. That is a problem with ash, not with your game or your setup,"
                + " and an update to ash will fix it.", menu.footer());
    }

    @Test
    void a_change_that_could_not_be_saved_says_so_and_still_takes_effect_for_the_session() throws IOException {
        Settings settings = Settings.load(configDir);
        Files.createDirectory(configDir.resolve("ash.properties.partial"));
        SettingsMenu menu = menuWhereEverythingLanded(settings);

        menu.rows().get(0).press();

        assertEquals(Saved.FILE_UNWRITABLE.message(), menu.footer());
        assertFalse(settings.get(Settings.FPS_READOUT));
        assertEquals(List.of("changed"), changes);
    }

    @Test
    void with_nothing_to_report_the_footer_is_empty() {
        SettingsMenu menu = menuWhereEverythingLanded(Settings.load(configDir));

        menu.rows().get(0).press();

        assertEquals("", menu.footer());
    }
}
