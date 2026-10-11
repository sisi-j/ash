package com.ashlauncher.client.v1_8_9.cosmetics;

import com.ashlauncher.client.cosmetics.CapeSource;
import com.ashlauncher.client.cosmetics.Cosmetics;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.imageio.ImageIO;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * ash capes on 1.8.9 (spec 0004, research 0012): the game's own cape, drawn
 * from ash's texture. The cape renderer asks the player for its cape texture
 * - a method Legacy Yarn 604 calls {@code getSkinId()} - and that answer is
 * the one thing changed: the cape's model, its physics and the player's own
 * "Cape" option are the game's.
 *
 * <p>Asked on the render thread, which is also where a texture must be
 * registered; each texture is registered once, on first use.
 */
public final class CapeTextures {

    private static Cosmetics cosmetics;
    private static final Map<String, Identifier> REGISTERED = new HashMap<>();
    private static final Map<String, Boolean> UNREADABLE = new HashMap<>();

    private CapeTextures() {}

    public static void install(Cosmetics installed) {
        cosmetics = installed;
    }

    /** ash's cape texture for {@code player}, or {@code cape} - the game's own - as it was. */
    public static Identifier capeFor(UUID player, Identifier cape) {
        if (cosmetics == null) {
            return cape;
        }
        CapeSource.Cape ash = cosmetics.capeFor(player);
        if (ash == null) {
            return cape;
        }
        Identifier texture = textureFor(ash);
        return texture == null ? cape : texture;
    }

    static Identifier textureFor(CapeSource.Cape cape) {
        Identifier known = REGISTERED.get(cape.url());
        if (known != null || UNREADABLE.containsKey(cape.url())) {
            return known;
        }
        try {
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(cape.png()));
            if (image == null) {
                throw new IOException("not an image");
            }
            Identifier id = new Identifier("ash",
                    "cape/" + Integer.toHexString(cape.url().hashCode()) + "-" + REGISTERED.size());
            MinecraftClient.getInstance().getTextureManager().loadTexture(id, new NativeImageBackedTexture(image));
            REGISTERED.put(cape.url(), id);
            return id;
        } catch (IOException | RuntimeException notAnImage) {
            // Not a PNG the game can read: drawn as the game's own, and not tried again.
            UNREADABLE.put(cape.url(), Boolean.TRUE);
            return null;
        }
    }
}
