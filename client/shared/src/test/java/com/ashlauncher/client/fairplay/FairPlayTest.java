package com.ashlauncher.client.fairplay;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.game.FakeGameView;
import com.ashlauncher.client.report.Feature;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The fair-play list (spec 0007, Fair play). Freelook's block list moved in
 * whole: its servers, and every one of its tests, case for case.
 */
class FairPlayTest {

    /** The server whose rule switches freelook off at this address, as the shipped list has it. */
    private static String matched(String address) {
        FairPlay.Rule rule = FairPlay.shipped().ruleFor(Feature.FREELOOK, address);
        return rule == null ? null : rule.server();
    }

    @Test
    void each_listed_server_matches_by_its_domain_and_its_subdomains() {
        assertEquals("Hypixel", matched("hypixel.net"));
        assertEquals("Hypixel", matched("mc.hypixel.net"));
        assertEquals("Hypixel", matched("eu.mc.hypixel.net"));
        assertEquals("MCC Island", matched("play.mccisland.net"));
        assertEquals("MCC Island", matched("mccisland.net"));
        assertEquals("Hoplite", matched("hoplite.gg"));
        assertEquals("Hoplite", matched("java.hoplite.gg"));
    }

    @Test
    void an_address_matches_however_the_player_wrote_it() {
        assertEquals("Hypixel", matched("  MC.Hypixel.NET  "));
        assertEquals("Hypixel", matched("mc.hypixel.net:25565"));
        assertEquals("Hypixel", matched("mc.hypixel.net."));
    }

    @Test
    void a_look_alike_never_matches() {
        assertNull(matched("nothypixel.net"));
        assertNull(matched("hypixel.net.example.com"));
        assertNull(matched("hypixel.network"));
        assertNull(matched("myhoplite.gg"));
        assertNull(matched("mccisland.net-fan.org"));
    }

    @Test
    void singleplayer_and_addresses_with_no_domain_never_match() {
        assertNull(matched(null));
        assertNull(matched(""));
        assertNull(matched("localhost:25565"));
        assertNull(matched("[::1]:25565"));
        assertNull(matched("172.65.197.160"));
    }

    @Test
    void every_entry_says_where_its_ban_is_written() {
        assertTrue(FairPlay.shipped().rules().size() >= 3, "freelook's servers did not all move in");
        for (FairPlay.Rule rule : FairPlay.shipped().rules()) {
            assertTrue(rule.source().startsWith("https://"), rule.server());
            assertTrue(rule.whyOff().contains(rule.server()), rule.whyOff());
        }
    }

    @Test
    void the_server_is_the_one_the_game_says_the_player_is_on() {
        assertEquals("Freelook is off on Hypixel: its rules ban it.",
                FairPlay.shipped().whyOff(Feature.FREELOOK, new FakeGameView().onServer("mc.hypixel.net")));
        assertEquals("", FairPlay.shipped().whyOff(Feature.FREELOOK, new FakeGameView().onServer("play.example.org")));
        // Singleplayer, or no world at all: nothing is listed.
        assertEquals("", FairPlay.shipped().whyOff(Feature.FREELOOK, new FakeGameView()));
    }

    // ---- the list itself: one entry a rule, for any feature ----

    private static final String LIST = String.join("\n",
            "# A comment, and a blank line, are nothing.",
            "",
            "crosshair | Example | play.example.net | it says so | https://example.net/rules",
            "freelook | Example | *.example.net, example.org | it says so too | https://example.net/rules");

    @Test
    void a_rule_is_for_its_own_feature_alone() {
        FairPlay list = FairPlay.parse(LIST);

        assertEquals("Crosshair is off on Example: it says so.",
                list.whyOff(Feature.CROSSHAIR, new FakeGameView().onServer("play.example.net")));
        assertEquals("", list.whyOff(Feature.SNAPLOOK, new FakeGameView().onServer("play.example.net")));
        assertNull(FairPlay.shipped().ruleFor(Feature.CROSSHAIR, "mc.hypixel.net"), "only freelook is listed there");
    }

    @Test
    void a_host_alone_matches_only_itself_and_a_star_matches_it_and_everything_under_it() {
        FairPlay list = FairPlay.parse(LIST);

        assertEquals("Example", list.ruleFor(Feature.CROSSHAIR, "Play.Example.NET:25565.").server());
        assertNull(list.ruleFor(Feature.CROSSHAIR, "example.net"));
        assertNull(list.ruleFor(Feature.CROSSHAIR, "eu.play.example.net"));
        assertEquals("Example", list.ruleFor(Feature.FREELOOK, "example.net").server());
        assertEquals("Example", list.ruleFor(Feature.FREELOOK, "eu.play.example.net").server());
        assertEquals("Example", list.ruleFor(Feature.FREELOOK, "example.org").server());
        assertNull(list.ruleFor(Feature.FREELOOK, "mc.example.org"));
    }

    @Test
    void every_rule_in_force_at_an_address_is_found() {
        FairPlay list = FairPlay.parse(LIST);

        assertEquals(2, list.rulesAt("play.example.net").size());
        assertEquals(0, list.rulesAt(null).size());
    }

    @Test
    void a_broken_line_is_refused_rather_than_read_as_something_else() {
        assertThrows(IllegalArgumentException.class, () -> FairPlay.parse("freelook | Example | example.net"));
        assertThrows(IllegalArgumentException.class,
                () -> FairPlay.parse("no-such-feature | Example | example.net | why | https://example.net"));
    }

    @Test
    void a_real_game_test_s_own_list_is_read_afresh_beside_the_shipped_one(@TempDir Path dir) throws IOException {
        Path list = dir.resolve("fair-play-test.txt");
        System.setProperty(FairPlay.TEST_LIST_PROPERTY, list.toString());
        try {
            assertEquals(FairPlay.shipped().rules().size(), FairPlay.inForce().rules().size(),
                    "no file yet should be no rules of the test's");

            Files.writeString(list, "snaplook | the test server | localhost | the test bans it | https://example.invalid");

            assertEquals("Snaplook is off on the test server: the test bans it.",
                    FairPlay.inForce().whyOff(Feature.SNAPLOOK, new FakeGameView().onServer("localhost:25565")));
            assertEquals("Hypixel", FairPlay.inForce().ruleFor(Feature.FREELOOK, "mc.hypixel.net").server());
        } finally {
            System.clearProperty(FairPlay.TEST_LIST_PROPERTY);
        }
        assertEquals(FairPlay.shipped().rules(), FairPlay.inForce().rules(), "outside a test, only the shipped list");
    }

    // ---- the note on joining ----

    private final List<String> said = new ArrayList<>();

    private FairPlayNotes notes(FakeGameView game) {
        return new FairPlayNotes(FairPlay.parse(LIST), game, feature -> feature != Feature.SNAPLOOK, said::add);
    }

    @Test
    void joining_a_listed_server_names_each_feature_off_there_once() {
        FakeGameView game = new FakeGameView().onServer("play.example.net");
        FairPlayNotes notes = notes(game);

        notes.tick();
        notes.tick();

        assertEquals(List.of("Crosshair is off on Example: it says so.", "Freelook is off on Example: it says so too."),
                said);
    }

    @Test
    void the_note_waits_for_the_world_and_comes_again_on_the_next_join() {
        FakeGameView game = new FakeGameView().onServer("play.example.net").outOfWorld();
        FairPlayNotes notes = notes(game);

        notes.tick();
        assertEquals(List.of(), said, "a note came before the player was in the world to read it");

        game.backInWorld();
        notes.tick();
        game.onServer(null);
        notes.tick();
        game.onServer("example.org");
        notes.tick();

        assertEquals(List.of("Crosshair is off on Example: it says so.", "Freelook is off on Example: it says so too.",
                "Freelook is off on Example: it says so too."), said);
    }

    @Test
    void a_feature_the_player_has_off_is_not_named() {
        FairPlayNotes notes = new FairPlayNotes(FairPlay.parse(LIST), new FakeGameView().onServer("example.org"),
                feature -> false, said::add);

        notes.tick();

        assertEquals(List.of(), said);
    }
}
