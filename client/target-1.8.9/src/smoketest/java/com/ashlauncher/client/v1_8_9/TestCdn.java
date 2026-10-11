package com.ashlauncher.client.v1_8_9;

import com.sun.net.httpserver.HttpServer;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import javax.imageio.ImageIO;

/**
 * ash's CDN, as far as one player's cape needs it, on 127.0.0.1: the game
 * test points the client here (build.gradle's {@code ash.cosmetics.cdn}) so
 * nothing reaches the network. Serves one cape, solid magenta so a screenshot
 * shows it plainly, as worn by every player it is asked about: in a
 * development world the player's UUID need not be the session's. Each
 * request is printed, for a failure to show what was asked.
 */
final class TestCdn implements AutoCloseable {

    static final int PORT = 25613;
    static final String BASE = "http://127.0.0.1:" + PORT;
    static final String CAPE = BASE + "/cosmetics/test-cape.png";
    static final int MAGENTA = 0xFFFF00FF;

    private final HttpServer server;

    TestCdn() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", PORT), 0);
        byte[] profile = ("{\"v\":1,\"equipped\":{\"cape\":{\"id\":\"cape.test\",\"texture_url\":\"" + CAPE + "\"}}}")
                .getBytes(StandardCharsets.UTF_8);
        byte[] cape = magentaCape();
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            System.out.println("ash test CDN: " + path);
            byte[] body = path.matches("/profiles/[0-9a-f]{32}\\.json") ? profile
                    : path.equals("/cosmetics/test-cape.png") ? cape
                    : null;
            if (body == null) {
                exchange.sendResponseHeaders(404, -1);
            } else {
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            }
            exchange.close();
        });
        server.start();
    }

    private static byte[] magentaCape() throws IOException {
        BufferedImage image = new BufferedImage(64, 32, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 22; x++) {
            for (int y = 0; y < 17; y++) {
                image.setRGB(x, y, MAGENTA);
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    @Override
    public void close() {
        server.stop(0);
    }
}
