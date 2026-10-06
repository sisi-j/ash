package com.ashlauncher.client.freelook;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BlockListTest {

    private static String matched(String address) {
        BlockList.Server server = BlockList.match(address);
        return server == null ? null : server.name();
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
        for (BlockList.Server server : BlockList.SERVERS) {
            assertTrue(server.rules().startsWith("https://"), server.name());
            assertTrue(server.whyOff().contains(server.name()), server.whyOff());
        }
    }
}
