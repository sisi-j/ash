package com.ashlauncher.client.ui;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/**
 * The colours the player has picked lately, newest first, for the colour
 * picker's Recent row (#68) - kept across sessions in a file of their own
 * beside ash's settings, one {@code #RRGGBB} a line.
 *
 * <p>Not a setting: nothing reads it but the picker, and losing it costs
 * nothing, so a file that cannot be read is simply an empty list and one
 * that cannot be written keeps the list for the session.
 */
final class RecentColours {

    /** As many as the picker shows. */
    static final int KEPT = 6;

    static final String FILE_NAME = "ash-recent-colours.txt";

    private final Path file;
    private final List<Integer> colours;

    private RecentColours(Path file, List<Integer> colours) {
        this.file = file;
        this.colours = colours;
    }

    /** The list kept in {@code folder}, or an empty one when there is none to read. */
    static RecentColours load(Path folder) {
        Path file = folder.resolve(FILE_NAME);
        List<Integer> colours = new ArrayList<>();
        try {
            if (Files.exists(file)) {
                for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                    int rgb = OptionsPage.wholeColour(line.trim());
                    if (rgb >= 0 && !colours.contains(rgb) && colours.size() < KEPT) {
                        colours.add(rgb);
                    }
                }
            }
        } catch (IOException | RuntimeException unreadable) {
            colours.clear();
        }
        return new RecentColours(file, colours);
    }

    /** Newest first, at most {@link #KEPT}. */
    List<Integer> colours() {
        return Collections.unmodifiableList(colours);
    }

    /** {@code rgb} was just picked: it goes to the front, once, and the list is saved. */
    void remember(int rgb) {
        Integer colour = rgb & 0xFFFFFF;
        if (!colours.isEmpty() && colours.get(0).equals(colour)) {
            return;
        }
        colours.remove(colour);
        colours.add(0, colour);
        while (colours.size() > KEPT) {
            colours.remove(colours.size() - 1);
        }
        StringBuilder text = new StringBuilder();
        for (int each : colours) {
            text.append('#').append(String.format(Locale.ROOT, "%06X", each)).append('\n');
        }
        try {
            Files.createDirectories(file.getParent());
            Files.write(file, text.toString().getBytes(StandardCharsets.UTF_8));
        } catch (IOException unwritable) {
            // Kept for the session; nothing else depends on it.
        }
    }
}
