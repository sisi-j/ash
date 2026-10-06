package com.ashlauncher.client.v1_21_11;

import com.ashlauncher.client.hit.HitColour;
import com.ashlauncher.client.v1_21_11.mixin.OverlayTextureAccess;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;

/**
 * Hit colour on 1.21.11: the red rows of the game's overlay texture,
 * rewritten and uploaded again whenever the colour changes. Every hurt
 * entity's flash samples those rows, so the change is global and shows at
 * once ({@code docs/research/0004}, section 4.1).
 */
final class HitColourTexture {

    /** The texel the game itself writes: red, keeping 178 of 255 of the entity's colour. */
    static final int GAME_TEXEL = 0xB2FF0000;

    private final HitColour hitColour;
    /** What the red rows hold now. The game starts them at its own texel. */
    private int applied = GAME_TEXEL;

    HitColourTexture(HitColour hitColour) {
        this.hitColour = hitColour;
    }

    /** Once a client tick, on the render thread, where the texture may be written. */
    void tick() {
        int texel = hitColour.overlayTexel();
        if (texel == applied) {
            return;
        }
        DynamicTexture texture =
                ((OverlayTextureAccess) Minecraft.getInstance().gameRenderer.overlayTexture()).ash$texture();
        NativeImage pixels = texture.getPixels();
        if (pixels == null) {
            return;
        }
        // Rows 0 to 7 are the red rows; 8 to 15 are the white flash, left as they are.
        for (int y = 0; y < 8; y++) {
            for (int x = 0; x < 16; x++) {
                pixels.setPixel(x, y, texel);
            }
        }
        texture.upload();
        applied = texel;
    }

    /** What the red rows of the texture hold, for the real-game test to read. */
    static int redRowTexel() {
        DynamicTexture texture =
                ((OverlayTextureAccess) Minecraft.getInstance().gameRenderer.overlayTexture()).ash$texture();
        return texture.getPixels().getPixel(0, 0);
    }
}
