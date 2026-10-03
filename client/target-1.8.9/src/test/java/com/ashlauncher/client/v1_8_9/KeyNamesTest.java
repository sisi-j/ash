package com.ashlauncher.client.v1_8_9;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class KeyNamesTest {

    @Test
    void the_settings_key_reads_as_it_does_on_1_21_11() {
        // The first real-game screenshot of the panel said "RSHIFT closes".
        assertEquals("Right Shift", KeyNames.readable("RSHIFT"));
    }

    @Test
    void other_keys_read_as_words_and_single_letters_stay_letters() {
        assertEquals("Left Alt", KeyNames.readable("LMENU"));
        assertEquals("Grave", KeyNames.readable("GRAVE"));
        assertEquals("R", KeyNames.readable("R"));
    }
}
