package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class TextTest {

    private final FakeScreenSurface surface = new FakeScreenSurface();

    @Test
    void no_wrapped_line_is_wider_than_its_room_even_a_single_long_word() {
        for (String text : new String[] {"Incomprehensibilities first, then short words", "a b c",
                "short then incomprehensibilities"}) {
            for (String line : Text.wrap(surface, text, 60, 3)) {
                assertTrue(surface.textWidth(line) <= 60, "\"" + line + "\" from \"" + text + "\" is wider than 60");
            }
        }
    }

    @Test
    void text_that_needs_more_lines_than_it_has_ends_in_an_ellipsis() {
        List<String> lines = Text.wrap(surface, "one two three four five six seven", 30, 2);

        assertEquals(2, lines.size());
        assertTrue(lines.get(1).endsWith("..."), lines.toString());
    }

    @Test
    void the_end_of_long_typing_stays_in_view_and_no_character_is_split() {
        String typed = "abcdefghij" + new String(Character.toChars(0x1F600));

        String shown = Text.tail(surface, typed, 30);

        assertTrue(shown.endsWith(new String(Character.toChars(0x1F600))), shown);
        assertTrue(!Character.isLowSurrogate(shown.charAt(0)), "the view starts in the middle of a character");
    }
}
