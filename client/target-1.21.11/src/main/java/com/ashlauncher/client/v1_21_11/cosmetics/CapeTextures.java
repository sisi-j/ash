package com.ashlauncher.client.v1_21_11.cosmetics;

import com.ashlauncher.client.cosmetics.CapeSource;
import com.ashlauncher.client.cosmetics.Cosmetics;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerSkin;

/**
 * ash capes on 1.21.11 (spec 0004, research 0012): the game's own cape, drawn
 * from ash's texture. {@code AbstractClientPlayer.getSkin()} is asked for each
 * player the game draws, and its {@code cape} is the one thing changed - the
 * cape's model, its physics and the player's own "Cape" option are the game's.
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

    /** {@code skin} with ash's cape for {@code player}, or {@code skin} as it was. */
    public static PlayerSkin withCape(UUID player, PlayerSkin skin) {
        if (cosmetics == null) {
            return skin;
        }
        CapeSource.Cape cape = cosmetics.capeFor(player);
        if (cape == null) {
            return skin;
        }
        Identifier texture = textureFor(cape);
        if (texture == null) {
            return skin;
        }
        return new PlayerSkin(skin.body(), new ClientAsset.ResourceTexture(texture, texture), skin.elytra(),
                skin.model(), skin.secure());
    }

    /** The cape's registered texture, registering it the first time it is seen. */
    static Identifier textureFor(CapeSource.Cape cape) {
        Identifier known = REGISTERED.get(cape.url());
        if (known != null || UNREADABLE.containsKey(cape.url())) {
            return known;
        }
        try {
            NativeImage image = NativeImage.read(cape.png());
            Identifier id = Identifier.fromNamespaceAndPath("ash",
                    "cape/" + Integer.toHexString(cape.url().hashCode()) + "-" + REGISTERED.size());
            Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "ash cape", image));
            REGISTERED.put(cape.url(), id);
            return id;
        } catch (IOException | RuntimeException notAnImage) {
            // Not a PNG the game can read: drawn as the game's own, and not tried again.
            UNREADABLE.put(cape.url(), Boolean.TRUE);
            return null;
        }
    }
}
