package com.ashlauncher.client.servers;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RecentServersTest {

    /** The contract: the launcher's tests read this same file. */
    private static final Path CONTRACT = Paths.get("../../launcher/core/tests/fixtures/recent-servers.json");

    @TempDir
    Path gameDir;

    private List<String> addresses() {
        List<String> addresses = new ArrayList<>();
        for (RecentServers.Join join : RecentServers.read(gameDir.resolve(RecentServers.RELATIVE_PATH))) {
            addresses.add(join.address);
        }
        return addresses;
    }

    @Test
    void it_writes_exactly_what_the_launcher_reads() throws IOException {
        RecentServers.record(gameDir, "localhost:25570", 1791200000000L);
        RecentServers.record(gameDir, "mc.hypixel.net", 1791300000000L);

        String written = new String(Files.readAllBytes(gameDir.resolve(RecentServers.RELATIVE_PATH)), "UTF-8");
        String contract = new String(Files.readAllBytes(CONTRACT), "UTF-8").replace("\r\n", "\n");
        assertEquals(contract, written);
    }

    @Test
    void joining_again_moves_a_server_to_the_front_once_however_it_was_written() throws IOException {
        RecentServers.record(gameDir, "mc.hypixel.net", 1);
        RecentServers.record(gameDir, "play.mccisland.net", 2);
        RecentServers.record(gameDir, " MC.Hypixel.NET. ", 3);

        assertEquals(List.of("MC.Hypixel.NET.", "play.mccisland.net"), addresses());
    }

    @Test
    void only_the_most_recent_are_kept() throws IOException {
        for (int i = 0; i < RecentServers.KEPT + 5; i++) {
            RecentServers.record(gameDir, "server" + i + ".example", i);
        }

        List<String> kept = addresses();
        assertEquals(RecentServers.KEPT, kept.size());
        assertEquals("server" + (RecentServers.KEPT + 4) + ".example", kept.get(0));
    }

    @Test
    void the_record_holds_addresses_and_times_and_nothing_else() throws IOException {
        RecentServers.record(gameDir, "mc.hypixel.net", 1);

        String written = new String(Files.readAllBytes(gameDir.resolve(RecentServers.RELATIVE_PATH)), "UTF-8");
        assertEquals("{\n  \"servers\": [\n    { \"address\": \"mc.hypixel.net\", \"joined_ms\": 1 }\n  ]\n}\n", written);
    }

    @Test
    void a_file_it_cannot_read_starts_the_record_again_rather_than_stopping_the_game() throws IOException {
        Path file = gameDir.resolve(RecentServers.RELATIVE_PATH);
        Files.createDirectories(file.getParent());
        Files.write(file, "not json".getBytes("UTF-8"));

        RecentServers.record(gameDir, "mc.hypixel.net", 1);

        assertEquals(List.of("mc.hypixel.net"), addresses());
    }

    @Test
    void an_address_with_a_quote_in_it_is_still_json() throws IOException {
        RecentServers.record(gameDir, "odd\"name", 1);

        assertTrue(addresses().contains("odd\"name"), addresses().toString());
    }
}
