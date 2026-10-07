package com.ashlauncher.client.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.ashlauncher.client.report.Feature;
import com.ashlauncher.client.settings.Settings;
import com.ashlauncher.client.settings.SettingsScreen;
import com.ashlauncher.client.ui.draw.FakeCanvas;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ash's own settings, behind the gear (#69): each takes effect at once and is saved. */
class AshSettingsPageTest {

    @TempDir
    Path configDir;

    private final AtomicLong now = new AtomicLong(1_000_000_000L);
    private final List<String> closed = new ArrayList<>();
    private final List<String> sentToControls = new ArrayList<>();
    private Settings settings;
    private Panel panel;
    private int width = 1920;
    private int height = 1080;

    /** A panel as the game has it: moving or not as the setting says, with no override. */
    private void open(int width, int height) {
        this.width = width;
        this.height = height;
        settings = Settings.load(configDir);
        panel = new Panel(new SettingsScreen(settings, f -> true, () -> { }), () -> closed.add("closed"), now::get);
        panel.setOpenKey(() -> "Right Shift", () -> sentToControls.add("controls"));
        panel.resize(width, height);
        settle();
        click(panel.gearButton());
    }

    private void open() {
        open(1920, 1080);
    }

    /** Lets time pass until everything has landed, as a player waits. */
    private FakeCanvas settle() {
        FakeCanvas canvas = render();
        for (int i = 0; i < 20 && panel.animating(); i++) {
            now.addAndGet(100_000_000L);
            canvas = render();
        }
        return canvas;
    }

    private FakeCanvas render() {
        FakeCanvas canvas = new FakeCanvas(width, height);
        panel.render(canvas, -1, -1);
        return canvas;
    }

    private void click(Rect at) {
        assertNotNull(at, "nothing on screen to click");
        panel.mouseClicked(at.centreX(), at.centreY());
        panel.mouseReleased();
        settle();
    }

    private String file() throws Exception {
        return Files.readString(configDir.resolve("ash.properties"));
    }

    @Test
    void the_gear_opens_ash_s_own_settings() throws Exception {
        open();
        FakeCanvas page = settle();
        page.save(new File("build/ui/ash-settings.png"));

        for (String line : new String[] {"ash settings", "Open ash settings", "Right Shift", "Interface size", "100%",
                "Animations", "Background blur"}) {
            assertTrue(page.drew(line), "\"" + line + "\" is not on the page: " + page.texts());
        }
        assertFalse(page.drew("ENABLED"), "ash itself was given an on and off button");
        assertNull(panel.resetToDefaults(), "the mockup's page has no reset");
    }

    @Test
    void the_key_sends_the_player_to_the_games_controls() {
        open();

        click(panel.openKeyChip());

        assertEquals(List.of("controls"), sentToControls);
    }

    @Test
    void interface_size_scales_the_panel_at_once_and_is_saved() throws Exception {
        open();
        Rect gearAt100 = panel.gearButton();

        click(panel.sliderAt(Settings.PANEL_SIZE, 130));

        assertEquals(130, (int) settings.get(Settings.PANEL_SIZE));
        assertTrue(file().contains("\npanel.size=130\n"), file());
        assertTrue(panel.gearButton().width > gearAt100.width, "the panel did not grow");

        click(panel.sliderAt(Settings.PANEL_SIZE, 80));
        assertTrue(panel.gearButton().width < gearAt100.width, "the panel did not shrink");
    }

    @Test
    void with_animations_off_everything_is_in_its_final_place_at_once() throws Exception {
        open();
        click(panel.switchOf(Settings.PANEL_ANIMATIONS));
        assertFalse(settings.get(Settings.PANEL_ANIMATIONS));
        assertTrue(file().contains("\npanel.animations=false\n"), file());

        // Closing no longer waits for a motion.
        panel.requestClose();
        assertEquals(List.of("closed"), closed);

        // And a panel opened now is in place on its first frame.
        Panel fresh = new Panel(new SettingsScreen(settings, f -> true, () -> { }), () -> { }, now::get);
        fresh.resize(width, height);
        FakeCanvas first = new FakeCanvas(width, height);
        fresh.render(first, -1, -1);
        assertEquals(1f, first.textDrawn("S").opacity(), 0.001f);
        assertFalse(fresh.animating());
    }

    @Test
    void with_blur_off_the_panel_darkens_what_is_behind_it_more() throws Exception {
        open();
        int blurredOverlay = settle().fills.get(0).argb();

        click(panel.switchOf(Settings.PANEL_BLUR));

        assertFalse(panel.blurWanted(), "the screens are still told to blur");
        assertTrue(file().contains("\npanel.blur=false\n"), file());
        int overlay = settle().fills.get(0).argb();
        assertTrue((overlay >>> 24) > (blurredOverlay >>> 24), "no darker without blur: "
                + Integer.toHexString(overlay) + " vs " + Integer.toHexString(blurredOverlay));
    }

    @Test
    void at_the_largest_size_in_the_smallest_window_everything_can_still_be_reached() {
        open(854, 480);
        click(panel.sliderAt(Settings.PANEL_SIZE, 130));
        assertEquals(130, (int) settings.get(Settings.PANEL_SIZE));
        assertNotNull(panel.switchOf(Settings.PANEL_BLUR), "the last of ash's settings is out of reach");

        // Back on the tiles, scrolling reaches the last one.
        click(panel.backLink());
        for (int i = 0; i < 20 && panel.tileOf(Feature.HIT_COLOUR) == null; i++) {
            panel.mouseScrolled(-1);
            settle();
        }
        assertNotNull(panel.tileOf(Feature.HIT_COLOUR), "the last tile cannot be reached at 130%, even by scrolling");
    }
}
