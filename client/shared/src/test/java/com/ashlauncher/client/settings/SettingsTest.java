package com.ashlauncher.client.settings;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path configDir;

    @Test
    void a_first_run_gets_the_defaults_and_a_file_to_edit() throws IOException {
        Settings settings = Settings.load(configDir);

        assertTrue(settings.fpsReadoutEnabled());
        String written = Files.readString(configDir.resolve("ash.properties"));
        assertTrue(written.contains("fps-readout.enabled=true"), written);
    }

    @Test
    void a_file_from_before_toggle_sprint_gains_its_setting_and_keeps_the_player_s() throws IOException {
        // Exactly the file every player who ran the FPS readout's release has.
        Path file = configDir.resolve("ash.properties");
        String older = "# Show the frame rate in the top-left corner. true or false.\nfps-readout.enabled=false\n";
        Files.writeString(file, older);

        Settings settings = Settings.load(configDir);

        assertFalse(settings.fpsReadoutEnabled(), "the player's own choice was lost in the upgrade");
        assertTrue(settings.toggleSprintEnabled());
        String now = Files.readString(file);
        assertTrue(now.startsWith(older), "what the player had was changed:\n" + now);
        assertTrue(now.contains("\ntoggle-sprint.enabled=true\n"), "toggle sprint's setting was not added:\n" + now);
    }

    @Test
    void a_setting_the_player_changed_is_what_the_next_session_uses() throws IOException {
        Path file = configDir.resolve("ash.properties");
        // Complete - every setting present - so nothing needs adding and the
        // file has no reason to change.
        String mine = "# turned off for recording\nfps-readout.enabled=false\ntoggle-sprint.enabled=true\n";
        Files.writeString(file, mine);

        Settings settings = Settings.load(configDir);

        assertFalse(settings.fpsReadoutEnabled());
        assertEquals(mine, Files.readString(file), "the player's own file was rewritten");
    }

    @Test
    void a_setting_an_older_file_lacks_is_added_without_disturbing_the_rest() throws IOException {
        // The file toggle sprint's settings will arrive into: written by an
        // earlier ash, edited by hand since, and with no newline at the end -
        // so a careless append would glue the new line onto the last one.
        Path file = configDir.resolve("ash.properties");
        String older = "# my notes\nsome-future.setting=42";
        Files.writeString(file, older);

        Settings settings = Settings.load(configDir);

        assertTrue(settings.fpsReadoutEnabled());
        String now = Files.readString(file);
        assertTrue(now.startsWith(older + "\n"), "what the player had was changed:\n" + now);
        assertTrue(now.contains("\nfps-readout.enabled=true\n"), "the new setting was not added:\n" + now);
    }

    @Test
    void a_value_that_is_neither_true_nor_false_gets_the_default_and_is_reported() throws IOException {
        // `Boolean.parseBoolean("yes")` is false, so the obvious reading of
        // this file would switch the readout off without a word.
        Path file = configDir.resolve("ash.properties");
        String mine = "fps-readout.enabled=yes\ntoggle-sprint.enabled=true\n";
        Files.writeString(file, mine);

        Settings settings = Settings.load(configDir);

        assertTrue(settings.fpsReadoutEnabled());
        assertEquals(1, settings.problems().size(), "problems: " + settings.problems());
        String problem = settings.problems().get(0);
        assertTrue(problem.contains("fps-readout.enabled") && problem.contains("yes"), problem);
        assertEquals(mine, Files.readString(file), "the player's own value was overwritten");
    }

    @Test
    void a_config_directory_it_cannot_use_costs_the_settings_and_not_the_session() throws IOException {
        // A file squatting where the directory should be: nothing can be read
        // and nothing can be written. This runs while the game is starting, so
        // throwing here is a client that will not start.
        Path notADirectory = configDir.resolve("config");
        Files.writeString(notADirectory, "a file where the directory should be");

        Settings settings = Settings.load(notADirectory);

        assertTrue(settings.fpsReadoutEnabled());
        assertEquals(1, settings.problems().size(), "problems: " + settings.problems());
    }

    @Test
    void a_windows_path_in_the_file_does_not_stop_the_game_starting() throws IOException {
        // `Properties` reads a backslash then a u as the start of a unicode
        // escape and throws on a malformed one, and a path into the players'
        // own users folder is the likeliest way anyone ever writes one. (Java
        // does the same to source, which is why that path is not spelled out
        // in this comment.)
        Path file = configDir.resolve("ash.properties");
        String mine = "fps-readout.enabled=false\nnote=C:\\users\\me\n";
        Files.writeString(file, mine);

        Settings settings = Settings.load(configDir);

        // Every setting at its default, as the reported problem says - not
        // `false` from the line that happened to come before the bad one.
        assertTrue(settings.fpsReadoutEnabled(), "a setting read before the bad line survived it");
        assertEquals(1, settings.problems().size(), "problems: " + settings.problems());
        assertEquals(mine, Files.readString(file), "a file ash could not read was written to anyway");
    }
}
