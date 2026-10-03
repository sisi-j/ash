package com.ashlauncher.client.spike;

import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

/** THROWAWAY: renders the spike panel at common sizes, saves PNGs and prints the cost. */
class SpikePanelTest {

    @Test
    void render_and_time() throws Exception {
        File out = new File("build/spike");
        out.mkdirs();
        for (int[] size : new int[][] {{1920, 1080}, {2560, 1440}, {3840, 2160}}) {
            SpikePanel.render(size[0], size[1]);
            long best = Long.MAX_VALUE;
            BufferedImage image = null;
            for (int i = 0; i < 10; i++) {
                image = SpikePanel.render(size[0], size[1]);
                best = Math.min(best, SpikePanel.lastRenderNanos);
            }
            ImageIO.write(image, "png", new File(out, "panel-" + size[0] + "x" + size[1] + ".png"));
            System.out.println("spike: " + size[0] + "x" + size[1] + " rendered in " + best / 1_000_000.0 + " ms (best of 10)");
        }
    }
}
