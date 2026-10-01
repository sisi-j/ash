package com.ashlauncher.client.settings;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SettingsTest {

    @TempDir
    Path configDir;

    /**
     * {@code file} with every declared setting it does not mention added at
     * the end, at its default - so a test about a complete file stays about
     * a complete file when the next feature declares a setting.
     */
    private static String complete(String file) {
        StringBuilder whole = new StringBuilder(file);
        for (OnOff setting : Settings.declared()) {
            if (!file.contains(setting.key())) {
                whole.append(setting.key()).append('=').append(setting.fallback()).append('\n');
            }
        }
        return whole.toString();
    }

    @Test
    void a_first_run_gets_the_defaults_and_a_file_to_edit() throws IOException {
        Settings settings = Settings.load(configDir);

        assertTrue(settings.get(Settings.FPS_READOUT));
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

        assertFalse(settings.get(Settings.FPS_READOUT), "the player's own choice was lost in the upgrade");
        assertTrue(settings.get(Settings.TOGGLE_SPRINT));
        String now = Files.readString(file);
        assertTrue(now.startsWith(older), "what the player had was changed:\n" + now);
        assertTrue(now.contains("\ntoggle-sprint.enabled=true\n"), "toggle sprint's setting was not added:\n" + now);
    }

    @Test
    void a_setting_the_player_changed_is_what_the_next_session_uses() throws IOException {
        Path file = configDir.resolve("ash.properties");
        // Complete - every setting present - so nothing needs adding and the
        // file has no reason to change.
        String mine = complete("# turned off for recording\nfps-readout.enabled=false\ntoggle-sprint.enabled=true\n");
        Files.writeString(file, mine);

        Settings settings = Settings.load(configDir);

        assertFalse(settings.get(Settings.FPS_READOUT));
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

        assertTrue(settings.get(Settings.FPS_READOUT));
        String now = Files.readString(file);
        assertTrue(now.startsWith(older + "\n"), "what the player had was changed:\n" + now);
        assertTrue(now.contains("\nfps-readout.enabled=true\n"), "the new setting was not added:\n" + now);
    }

    @Test
    void a_value_that_is_neither_true_nor_false_gets_the_default_and_is_reported() throws IOException {
        // `Boolean.parseBoolean("yes")` is false, so the obvious reading of
        // this file would switch the readout off without a word.
        Path file = configDir.resolve("ash.properties");
        String mine = complete("fps-readout.enabled=yes\ntoggle-sprint.enabled=true\n");
        Files.writeString(file, mine);

        Settings settings = Settings.load(configDir);

        assertTrue(settings.get(Settings.FPS_READOUT));
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

        assertTrue(settings.get(Settings.FPS_READOUT));
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
        assertTrue(settings.get(Settings.FPS_READOUT), "a setting read before the bad line survived it");
        assertEquals(1, settings.problems().size(), "problems: " + settings.problems());
        assertEquals(mine, Files.readString(file), "a file ash could not read was written to anyway");
    }

    // ---- one declaration ----

    @Test
    void every_setting_is_declared_once_in_the_order_a_first_run_writes_them() throws IOException {
        assertEquals(List.of(Settings.FPS_READOUT, Settings.TOGGLE_SPRINT, Settings.CROSSHAIR), Settings.declared());

        Settings.load(configDir);

        // Byte for byte what Phase 2's client wrote, then each setting since
        // in the order it arrived - so moving to a declaration changed nothing
        // a player can see, and a new setting only ever adds to the end.
        assertEquals("# Show the frame rate in the top-left corner. true or false.\n"
                        + "fps-readout.enabled=true\n"
                        + "# Sprint on a key press instead of a held key. The key is in Options, Controls, Movement."
                        + " true or false.\n"
                        + "toggle-sprint.enabled=true\n"
                        + "# Draw ash's crosshair in place of the game's. true or false.\n"
                        + "crosshair.enabled=true\n",
                Files.readString(configDir.resolve("ash.properties")));
    }

    @Test
    void every_declared_setting_has_a_value() throws IOException {
        // Walking the declaration is the only way in: a setting it lists that
        // `get` cannot answer is a setting the screen would show and break on.
        Settings settings = Settings.load(configDir);

        assertFalse(Settings.declared().isEmpty(), "the test proves nothing with no settings declared");
        for (OnOff setting : Settings.declared()) {
            assertEquals(setting.fallback(), settings.get(setting), setting.key() + " is not at its default on a first run");
        }
    }

    // ---- changing a value in place ----

    @Test
    void changing_a_setting_rewrites_its_value_and_not_one_other_byte() throws IOException {
        // Everything a hand-edited file acquires: both comment styles, blank
        // lines, Windows line endings, its own order, a key ash does not know,
        // and the separators Properties allows besides a bare `=`.
        Path file = configDir.resolve("ash.properties");
        String mine = "! kept from an old guide\r\n"
                + "\r\n"
                + "toggle-sprint.enabled = true\r\n"
                + "   # indented note\r\n"
                + "some-future.setting: 42\r\n"
                + "  fps-readout.enabled : true   \r\n"
                + "\r\n";
        mine = complete(mine);
        Files.writeString(file, mine);
        Settings settings = Settings.load(configDir);

        Saved saved = settings.set(Settings.FPS_READOUT, false);

        assertEquals(Saved.SAVED, saved);
        assertEquals(mine.replace("  fps-readout.enabled : true   \r\n", "  fps-readout.enabled : false\r\n"),
                Files.readString(file));
        assertFalse(settings.get(Settings.FPS_READOUT), "the change did not reach the running game");
    }

    @Test
    void every_way_properties_separates_a_key_from_its_value_is_found() throws IOException {
        Path file = configDir.resolve("ash.properties");
        for (String separated : List.of("toggle-sprint.enabled=true", "toggle-sprint.enabled:true",
                "toggle-sprint.enabled true", "toggle-sprint.enabled\t=\ttrue", "\ttoggle-sprint.enabled  true")) {
            String before = complete("fps-readout.enabled=true\n" + separated + "\n");
            Files.writeString(file, before);
            Settings settings = Settings.load(configDir);

            settings.set(Settings.TOGGLE_SPRINT, false);

            String after = Files.readString(file);
            assertEquals(before.replace("true\n" + separated + "\n", "true\n" + separated.replace("true", "false") + "\n"),
                    after, "for the line \"" + separated + "\"");
            assertFalse(Settings.load(configDir).get(Settings.TOGGLE_SPRINT), "for the line \"" + separated + "\"");
        }
    }

    @Test
    void a_key_with_no_value_gets_one_rather_than_a_longer_key() throws IOException {
        // A bare key reads as an empty value, which load reports as neither
        // true nor false - so this is exactly the line a player fixes from the
        // settings screen. Writing the value straight after the key would make
        // "fps-readout.enabledfalse", a different setting.
        Path file = configDir.resolve("ash.properties");
        Files.writeString(file, complete("fps-readout.enabled\ntoggle-sprint.enabled=true\n"));
        Settings settings = Settings.load(configDir);

        settings.set(Settings.FPS_READOUT, false);

        assertEquals(complete("fps-readout.enabled=false\ntoggle-sprint.enabled=true\n"), Files.readString(file));
        assertFalse(Settings.load(configDir).get(Settings.FPS_READOUT));
    }

    @Test
    void every_setting_changed_in_game_is_what_the_next_session_reads() throws IOException {
        // Each one alone, so a setting that saved into another's line would show.
        assertFalse(Settings.declared().isEmpty(), "the test proves nothing with no settings declared");
        for (OnOff changed : Settings.declared()) {
            Files.deleteIfExists(configDir.resolve("ash.properties"));
            assertEquals(Saved.SAVED, Settings.load(configDir).set(changed, !changed.fallback()), changed.key());

            Settings next = Settings.load(configDir);

            for (OnOff setting : Settings.declared()) {
                boolean expected = setting == changed ? !setting.fallback() : setting.fallback();
                assertEquals(expected, next.get(setting), "after changing " + changed.key() + ", " + setting.key());
            }
            assertEquals(List.of(), next.problems());
        }
    }

    @Test
    void a_value_continued_across_lines_is_replaced_whole() throws IOException {
        // A trailing backslash continues a Properties line, so this is one
        // setting - and replacing only its first line would leave "ue" behind
        // as the start of a key.
        Path file = configDir.resolve("ash.properties");
        Files.writeString(file, complete("fps-readout.enabled=tr\\\n    ue\ntoggle-sprint.enabled=true\n"));
        Settings settings = Settings.load(configDir);
        assertTrue(settings.get(Settings.FPS_READOUT), "the test file does not say what it means to");

        settings.set(Settings.FPS_READOUT, false);

        assertEquals(complete("fps-readout.enabled=false\ntoggle-sprint.enabled=true\n"), Files.readString(file));
    }

    @Test
    void of_two_lines_for_one_setting_the_one_that_counts_is_the_one_changed() throws IOException {
        // Properties keeps the last, so changing the first would change nothing.
        Path file = configDir.resolve("ash.properties");
        Files.writeString(file,
                complete("fps-readout.enabled=true\ntoggle-sprint.enabled=true\nfps-readout.enabled=true\n"));
        Settings settings = Settings.load(configDir);

        settings.set(Settings.FPS_READOUT, false);

        assertEquals(complete("fps-readout.enabled=true\ntoggle-sprint.enabled=true\nfps-readout.enabled=false\n"),
                Files.readString(file));
        assertFalse(Settings.load(configDir).get(Settings.FPS_READOUT));
    }

    @Test
    void a_comment_that_mentions_a_setting_is_not_mistaken_for_it() throws IOException {
        Path file = configDir.resolve("ash.properties");
        String mine = complete("# fps-readout.enabled=true was too distracting\nfps-readout.enabled=true\n"
                + "toggle-sprint.enabled=true\n");
        Files.writeString(file, mine);
        Settings settings = Settings.load(configDir);

        settings.set(Settings.FPS_READOUT, false);

        assertEquals(mine.replace("\nfps-readout.enabled=true\n", "\nfps-readout.enabled=false\n"),
                Files.readString(file));
    }

    @Test
    void a_hand_edit_made_while_the_game_was_running_survives_a_change_in_game() throws IOException {
        // The change is made to the file as it is now, not as it was at
        // startup - otherwise the player's edit is silently undone.
        Path file = configDir.resolve("ash.properties");
        Settings settings = Settings.load(configDir);
        String edited = Files.readString(file) + "# added by hand mid-session\n";
        Files.writeString(file, edited);

        settings.set(Settings.TOGGLE_SPRINT, false);

        assertEquals(edited.replace("toggle-sprint.enabled=true", "toggle-sprint.enabled=false"),
                Files.readString(file));
    }

    @Test
    void a_setting_removed_from_the_file_is_added_back_when_it_is_changed() throws IOException {
        Path file = configDir.resolve("ash.properties");
        Settings settings = Settings.load(configDir);
        Files.writeString(file, "# mine\nfps-readout.enabled=true");

        settings.set(Settings.TOGGLE_SPRINT, false);

        String now = Files.readString(file);
        assertTrue(now.startsWith("# mine\nfps-readout.enabled=true\n"), now);
        assertFalse(Settings.load(configDir).get(Settings.TOGGLE_SPRINT), now);
    }

    @Test
    void a_file_that_became_unreadable_is_not_written_by_a_change() throws IOException {
        Path file = configDir.resolve("ash.properties");
        Settings settings = Settings.load(configDir);
        String broken = Files.readString(file) + "note=C:\\users\\me\n";
        Files.writeString(file, broken);

        Saved saved = settings.set(Settings.FPS_READOUT, false);

        assertEquals(Saved.FILE_UNREADABLE, saved, "a file ash could not read was written to anyway");
        assertEquals(broken, Files.readString(file));
        assertFalse(settings.get(Settings.FPS_READOUT), "the running game ignored the player's choice");
    }

    @Test
    void a_file_a_windows_editor_saved_as_ansi_is_changed_without_touching_its_other_bytes() throws IOException {
        // Notepad's "ANSI" puts an \u00e9 in one byte that is not valid UTF-8. A
        // decode to UTF-8 and back would replace it; the player never asked
        // ash to touch that line.
        Path file = configDir.resolve("ash.properties");
        String mine = complete("# caf\u00e9\nfps-readout.enabled=true\ntoggle-sprint.enabled=true\n");
        Files.write(file, mine.getBytes(StandardCharsets.ISO_8859_1));
        Settings settings = Settings.load(configDir);

        Saved saved = settings.set(Settings.FPS_READOUT, false);

        assertEquals(Saved.SAVED, saved);
        assertArrayEquals(mine.replace("fps-readout.enabled=true", "fps-readout.enabled=false")
                .getBytes(StandardCharsets.ISO_8859_1), Files.readAllBytes(file));
    }

    @Test
    void a_change_that_cannot_be_saved_leaves_the_file_as_it_was_and_says_so() throws IOException {
        // This cannot prove the write is atomic - nothing here can stop a write
        // halfway - but it does prove the write goes through a temporary file
        // first: block that file, and a direct write would have succeeded.
        Path file = configDir.resolve("ash.properties");
        Settings settings = Settings.load(configDir);
        String before = Files.readString(file);
        Files.createDirectory(configDir.resolve("ash.properties.partial"));

        Saved saved = settings.set(Settings.FPS_READOUT, false);

        assertEquals(Saved.FILE_UNWRITABLE, saved);
        assertEquals(before, Files.readString(file));
        assertFalse(settings.get(Settings.FPS_READOUT), "the change did not last for the session");
    }

    @Test
    void what_the_player_is_told_names_no_path_and_quotes_no_exception() {
        for (Saved saved : Saved.values()) {
            assertFalse(saved.message().contains("\\") || saved.message().contains("/")
                    || saved.message().contains("Exception"), saved + ": " + saved.message());
        }
        assertEquals("", Saved.SAVED.message());
    }
}
