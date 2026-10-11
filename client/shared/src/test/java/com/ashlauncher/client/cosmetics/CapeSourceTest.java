package com.ashlauncher.client.cosmetics;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;

class CapeSourceTest {

    private static final String CDN = "https://cdn.test";
    private static final UUID STEVE = UUID.fromString("986dec87-b7ec-47ff-89ff-033fdb95c4b5");
    private static final UUID ALEX = UUID.fromString("853c80ef-3c37-49fd-aa49-938b674adae6");
    private static final String TEXTURE = CDN + "/cosmetics/c0e8d87203e4-cape-ember.png";
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 1, 2, 3};

    /** A CDN that serves what the test puts on it, and remembers what was asked. */
    private static final class FakeCdn implements CapeSource.Fetch {
        final Map<String, byte[]> files = new HashMap<>();
        final List<String> asked = new ArrayList<>();
        boolean down;

        @Override
        public byte[] get(String url, int max) throws IOException {
            asked.add(url);
            if (down) {
                throw new IOException("down");
            }
            byte[] body = files.get(url);
            if (body != null && body.length > max) {
                throw new IOException("too large");
            }
            return body;
        }

        void profile(UUID player, String json) {
            files.put(CDN + "/profiles/" + player.toString().replace("-", "") + ".json",
                    json.getBytes(StandardCharsets.UTF_8));
        }
    }

    private final FakeCdn cdn = new FakeCdn();
    private final AtomicLong now = new AtomicLong(1_000_000);
    // Runs the background work at once, so each test reads in order.
    private final CapeSource source = new CapeSource(CDN, cdn, Runnable::run, now::get);

    private static String wearing(String url) {
        return "{\"v\":1,\"equipped\":{\"cape\":{\"id\":\"cape.ember\",\"texture_url\":\"" + url + "\"}}}";
    }

    @Test
    void a_published_cape_is_fetched_once_and_then_answered_from_memory() {
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.files.put(TEXTURE, PNG);

        CapeSource.Cape first = source.capeFor(STEVE);
        CapeSource.Cape again = source.capeFor(STEVE);

        assertNotNull(again);
        assertEquals(TEXTURE, again.url());
        assertArrayEquals(PNG, again.png());
        assertEquals(2, cdn.asked.size(), "the profile and the texture, once each: " + cdn.asked);
        assertNull(first, "the first sight only starts the fetch: the frame never waits for it");
    }

    @Test
    void the_profile_is_asked_again_once_stale_but_the_texture_never() {
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.files.put(TEXTURE, PNG);
        source.capeFor(STEVE);

        now.addAndGet(CapeSource.FRESH_MS + 1);
        source.capeFor(STEVE);

        assertEquals(3, cdn.asked.size(), "a second profile request, no second texture: " + cdn.asked);
    }

    @Test
    void a_player_with_no_profile_has_no_cape_and_isnt_asked_about_every_frame() {
        assertNull(source.capeFor(ALEX));
        assertNull(source.capeFor(ALEX));
        assertEquals(1, cdn.asked.size());
    }

    @Test
    void a_changed_cape_shows_once_its_profile_is_fresh_again() {
        String other = CDN + "/cosmetics/5d654b442f29-cape-midnight.png";
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.files.put(TEXTURE, PNG);
        cdn.files.put(other, new byte[] {9});
        source.capeFor(STEVE);

        cdn.profile(STEVE, wearing(other));
        now.addAndGet(CapeSource.FRESH_MS + 1);
        source.capeFor(STEVE);

        assertEquals(other, source.capeFor(STEVE).url());
    }

    @Test
    void a_texture_from_anywhere_but_ash_s_own_cdn_is_never_fetched() {
        for (String elsewhere : new String[] {
            "https://evil.example/cosmetics/x.png",
            CDN + "/profiles/x.png",
            CDN + "/cosmetics/../x.png",
            "http://cdn.test/cosmetics/x.png",
            CDN + "/cosmetics/x.png?y=1",
        }) {
            assertNull(CapeSource.capeUrl(wearing(elsewhere), CDN), elsewhere);
        }
        assertEquals(TEXTURE, CapeSource.capeUrl(wearing(TEXTURE), CDN));
    }

    @Test
    void a_profile_that_wears_no_cape_is_no_cape() {
        assertNull(CapeSource.capeUrl("{\"v\":1,\"equipped\":{}}", CDN));
        assertNull(CapeSource.capeUrl("not json", CDN));
    }

    @Test
    void ash_s_cdn_being_down_is_no_cape_and_tried_again_once_stale() {
        cdn.down = true;
        assertNull(source.capeFor(STEVE));
        assertNull(source.capeFor(STEVE));
        assertEquals(1, cdn.asked.size());

        cdn.down = false;
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.files.put(TEXTURE, PNG);
        now.addAndGet(CapeSource.FRESH_MS + 1);
        source.capeFor(STEVE);

        assertNotNull(source.capeFor(STEVE));
    }

    @Test
    void an_oversized_texture_is_refused() {
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.files.put(TEXTURE, new byte[CapeSource.TEXTURE_MAX_BYTES + 1]);
        assertNull(source.capeFor(STEVE));
        assertNull(source.capeFor(STEVE));
    }

    @Test
    void cosmetics_off_draws_nobody_s_and_hiding_others_draws_only_mine() {
        cdn.profile(STEVE, wearing(TEXTURE));
        cdn.profile(ALEX, wearing(TEXTURE));
        cdn.files.put(TEXTURE, PNG);
        boolean[] on = {true};
        boolean[] hideOthers = {false};
        Cosmetics cosmetics = new Cosmetics(source, () -> on[0], () -> hideOthers[0], () -> STEVE);
        cosmetics.capeFor(STEVE);
        cosmetics.capeFor(ALEX);

        assertNotNull(cosmetics.capeFor(ALEX));
        hideOthers[0] = true;
        assertNull(cosmetics.capeFor(ALEX), "someone else's, hidden");
        assertNotNull(cosmetics.capeFor(STEVE), "mine still shows");
        on[0] = false;
        assertNull(cosmetics.capeFor(STEVE), "off is nobody's");
    }
}
