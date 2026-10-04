package com.ashlauncher.client.ui.draw;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/** Which rasters stay uploaded, judged by what was uploaded and freed. */
class PieceCacheTest {

    private final List<String> uploads = new ArrayList<>();
    private final List<String> releases = new ArrayList<>();
    private final PieceCache<String> cache = new PieceCache<>(200, new PieceCache.Uploader<String>() {
        @Override
        public String upload(Raster raster) {
            uploads.add(raster.text());
            return "texture of " + raster.text();
        }

        @Override
        public void release(String handle) {
            releases.add(handle);
        }
    });

    /** A 10 by 10 raster: 100 pixels, so two fit under the cap. */
    private static Raster piece(String name) {
        return new Raster(10, 10, new int[100], 0, 0, name);
    }

    @Test
    void a_raster_is_uploaded_once_however_often_it_is_drawn() {
        Raster a = piece("a");

        assertSame(cache.get(a), cache.get(a));
        assertEquals(List.of("a"), uploads);
    }

    @Test
    void past_the_cap_the_least_recently_drawn_is_freed_first() {
        Raster a = piece("a");
        Raster b = piece("b");
        cache.get(a);
        cache.get(b);
        cache.nextFrame();
        cache.get(a);
        cache.nextFrame();

        cache.get(piece("c"));

        assertEquals(List.of("texture of b"), releases);
        assertEquals(2, cache.size());
    }

    @Test
    void nothing_drawn_this_frame_is_ever_freed_even_past_the_cap() {
        cache.get(piece("a"));
        cache.get(piece("b"));
        cache.get(piece("c"));

        assertEquals(List.of(), releases);
        assertEquals(3, cache.size());

        cache.nextFrame();
        cache.get(piece("d"));
        assertEquals(2, releases.size(), "the frame after, the oldest go until it fits");
    }

    @Test
    void a_freed_raster_drawn_again_is_uploaded_again() {
        Raster a = piece("a");
        cache.get(a);
        cache.nextFrame();
        cache.get(piece("b"));
        cache.get(piece("c"));
        cache.nextFrame();

        cache.get(a);

        assertEquals(List.of("a", "b", "c", "a"), uploads);
    }
}
