package com.ashlauncher.client.v1_8_9;

import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormatElement;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.lwjgl.opengl.GL11;

/**
 * Faster clouds (#45): 1.8.9's fancy clouds, built once a frame instead of
 * twice, and drawn in two calls instead of 128.
 *
 * <p>The game draws fancy clouds in two passes - depth only, then colour -
 * and in each pass builds the geometry again, one 8 by 8 tile at a time,
 * handing each tile to the driver as a draw of its own. Both passes build the
 * same vertices: nothing they are made from changes between them. So this
 * builds every tile once, into one buffer, and draws that buffer for each
 * pass. The 1.8.9 frame-time baseline put clouds at about a sixth of a frame.
 *
 * <p>Nothing a player sees changes, pixel for pixel. The vertices are the
 * game's own: {@link #render} is the game's {@code renderFancyClouds}, its
 * arithmetic unchanged, with the pass loop taken out of the building and put
 * round the drawing. The same primitives reach the same state in the same
 * order, and only the boundaries between draws move, which change no pixel.
 * The smoke test proves it on a frozen frame: ash's clouds against the game's.
 */
public final class FasterClouds {

    private static BooleanSupplier wanted = () -> false;

    /** Draws done by ash's path since startup, so the smoke test can tell which path drew a frame. */
    static int drawn;

    private FasterClouds() {
    }

    /** Hands over the player's setting; until then, the game draws its own clouds. */
    static void install(BooleanSupplier on) {
        wanted = on;
    }

    /** Whether ash draws the clouds this frame, asked by the world renderer's mixin. */
    public static boolean on() {
        return wanted.getAsBoolean();
    }

    /**
     * The game's fancy clouds, from {@code WorldRenderer.renderFancyClouds},
     * with the renderer's fields passed in. The locals keep the game's names
     * and its unused constants, so it reads line for line against the game.
     */
    public static void render(MinecraftClient client, ClientWorld world, int ticks, TextureManager textures,
            Identifier texture, float tickDelta, int anaglyphFilter) {
        GlStateManager.disableCull();
        float f = (float)(client.getCameraEntity().prevTickY + (client.getCameraEntity().y - client.getCameraEntity().prevTickY) * tickDelta);
        Tessellator tessellator = Tessellator.getInstance();
        BufferBuilder bufferBuilder = tessellator.getBuffer();
        float g = 12.0F;
        float h = 4.0F;
        double d = ticks + tickDelta;
        double e = (client.getCameraEntity().prevX + (client.getCameraEntity().x - client.getCameraEntity().prevX) * tickDelta + d * 0.03F) / 12.0;
        double i = (client.getCameraEntity().prevZ + (client.getCameraEntity().z - client.getCameraEntity().prevZ) * tickDelta) / 12.0 + 0.33F;
        float j = world.dimension.getCloudHeight() - f + 0.33F;
        int k = MathHelper.floor(e / 2048.0);
        int l = MathHelper.floor(i / 2048.0);
        e -= k * 2048;
        i -= l * 2048;
        textures.bindTexture(texture);
        GlStateManager.enableBlend();
        GlStateManager.blendFuncSeparate(770, 771, 1, 0);
        Vec3d vec3d = world.getCloudColor(tickDelta);
        float m = (float)vec3d.x;
        float n = (float)vec3d.y;
        float o = (float)vec3d.z;
        if (anaglyphFilter != 2) {
            float p = (m * 30.0F + n * 59.0F + o * 11.0F) / 100.0F;
            float q = (m * 30.0F + n * 70.0F) / 100.0F;
            float r = (m * 30.0F + o * 70.0F) / 100.0F;
            m = p;
            n = q;
            o = r;
        }

        float p = m * 0.9F;
        float q = n * 0.9F;
        float r = o * 0.9F;
        float s = m * 0.7F;
        float t = n * 0.7F;
        float u = o * 0.7F;
        float v = m * 0.8F;
        float w = n * 0.8F;
        float x = o * 0.8F;
        float y = 0.00390625F;
        float z = MathHelper.floor(e) * 0.00390625F;
        float aa = MathHelper.floor(i) * 0.00390625F;
        float ab = (float)(e - MathHelper.floor(e));
        float ac = (float)(i - MathHelper.floor(i));
        int ad = 8;
        int ae = 4;
        float af = 9.765625E-4F;
        GlStateManager.scale(12.0F, 1.0F, 12.0F);

        // The game's two passes would each build all of this again; it is
        // built once, then drawn for each.
        bufferBuilder.begin(7, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        for (int ah = -3; ah <= 4; ah++) {
            for (int ai = -3; ai <= 4; ai++) {
                float aj = ah * 8;
                float ak = ai * 8;
                float al = aj - ab;
                float am = ak - ac;
                if (j > -5.0F) {
                    bufferBuilder.vertex(al + 0.0F, j + 0.0F, am + 8.0F)
                        .texture((aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                        .color(s, t, u, 0.8F)
                        .normal(0.0F, -1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 8.0F, j + 0.0F, am + 8.0F)
                        .texture((aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                        .color(s, t, u, 0.8F)
                        .normal(0.0F, -1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 8.0F, j + 0.0F, am + 0.0F)
                        .texture((aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                        .color(s, t, u, 0.8F)
                        .normal(0.0F, -1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 0.0F, j + 0.0F, am + 0.0F)
                        .texture((aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                        .color(s, t, u, 0.8F)
                        .normal(0.0F, -1.0F, 0.0F)
                        .next();
                }

                if (j <= 5.0F) {
                    bufferBuilder.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 8.0F)
                        .texture((aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                        .color(m, n, o, 0.8F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 8.0F)
                        .texture((aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                        .color(m, n, o, 0.8F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 0.0F)
                        .texture((aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                        .color(m, n, o, 0.8F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .next();
                    bufferBuilder.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 0.0F)
                        .texture((aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                        .color(m, n, o, 0.8F)
                        .normal(0.0F, 1.0F, 0.0F)
                        .next();
                }

                if (ah > -1) {
                    for (int an = 0; an < 8; an++) {
                        bufferBuilder.vertex(al + an + 0.0F, j + 0.0F, am + 8.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(-1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 0.0F, j + 4.0F, am + 8.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(-1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 0.0F, j + 4.0F, am + 0.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(-1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 0.0F, j + 0.0F, am + 0.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(-1.0F, 0.0F, 0.0F)
                            .next();
                    }
                }

                if (ah <= 1) {
                    for (int an = 0; an < 8; an++) {
                        bufferBuilder.vertex(al + an + 1.0F - 9.765625E-4F, j + 0.0F, am + 8.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 1.0F - 9.765625E-4F, j + 4.0F, am + 8.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 1.0F - 9.765625E-4F, j + 4.0F, am + 0.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(1.0F, 0.0F, 0.0F)
                            .next();
                        bufferBuilder.vertex(al + an + 1.0F - 9.765625E-4F, j + 0.0F, am + 0.0F)
                            .texture((aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa)
                            .color(p, q, r, 0.8F)
                            .normal(1.0F, 0.0F, 0.0F)
                            .next();
                    }
                }

                if (ai > -1) {
                    for (int an = 0; an < 8; an++) {
                        bufferBuilder.vertex(al + 0.0F, j + 4.0F, am + an + 0.0F)
                            .texture((aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, -1.0F)
                            .next();
                        bufferBuilder.vertex(al + 8.0F, j + 4.0F, am + an + 0.0F)
                            .texture((aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, -1.0F)
                            .next();
                        bufferBuilder.vertex(al + 8.0F, j + 0.0F, am + an + 0.0F)
                            .texture((aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, -1.0F)
                            .next();
                        bufferBuilder.vertex(al + 0.0F, j + 0.0F, am + an + 0.0F)
                            .texture((aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, -1.0F)
                            .next();
                    }
                }

                if (ai <= 1) {
                    for (int an = 0; an < 8; an++) {
                        bufferBuilder.vertex(al + 0.0F, j + 4.0F, am + an + 1.0F - 9.765625E-4F)
                            .texture((aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, 1.0F)
                            .next();
                        bufferBuilder.vertex(al + 8.0F, j + 4.0F, am + an + 1.0F - 9.765625E-4F)
                            .texture((aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, 1.0F)
                            .next();
                        bufferBuilder.vertex(al + 8.0F, j + 0.0F, am + an + 1.0F - 9.765625E-4F)
                            .texture((aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, 1.0F)
                            .next();
                        bufferBuilder.vertex(al + 0.0F, j + 0.0F, am + an + 1.0F - 9.765625E-4F)
                            .texture((aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa)
                            .color(v, w, x, 0.8F)
                            .normal(0.0F, 0.0F, 1.0F)
                            .next();
                    }
                }

            }
        }
        bufferBuilder.end();
        drawBothPasses(bufferBuilder, anaglyphFilter);
        drawn++;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
    }

    /**
     * The game's buffer renderer, {@code BufferRenderer.draw}, drawing twice
     * before it resets the buffer: depth only, then colour through the mask
     * the game's own pass loop sets for the anaglyph filter.
     */
    private static void drawBothPasses(BufferBuilder builder, int anaglyphFilter) {
        VertexFormat format = builder.getFormat();
        int stride = format.getVertexSize();
        ByteBuffer data = builder.getByteBuffer();
        List<VertexFormatElement> elements = format.getElements();
        boolean any = builder.getVertexCount() > 0;
        if (any) {
            for (int index = 0; index < elements.size(); index++) {
                VertexFormatElement element = elements.get(index);
                int glType = element.getFormat().getGlId();
                ((Buffer) data).position(format.getIndex(index));
                switch (element.getType()) {
                    case POSITION:
                        GL11.glVertexPointer(element.getCount(), glType, stride, data);
                        GL11.glEnableClientState(GL11.GL_VERTEX_ARRAY);
                        break;
                    case UV:
                        GLX.gl13ClientActiveTexture(GLX.textureUnit + element.getIndex());
                        GL11.glTexCoordPointer(element.getCount(), glType, stride, data);
                        GL11.glEnableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        GLX.gl13ClientActiveTexture(GLX.textureUnit);
                        break;
                    case COLOR:
                        GL11.glColorPointer(element.getCount(), glType, stride, data);
                        GL11.glEnableClientState(GL11.GL_COLOR_ARRAY);
                        break;
                    case NORMAL:
                        GL11.glNormalPointer(glType, stride, data);
                        GL11.glEnableClientState(GL11.GL_NORMAL_ARRAY);
                        break;
                    default:
                        break;
                }
            }
        }

        GlStateManager.colorMask(false, false, false, false);
        if (any) {
            GL11.glDrawArrays(builder.getDrawMode(), 0, builder.getVertexCount());
        }
        switch (anaglyphFilter) {
            case 0:
                GlStateManager.colorMask(false, true, true, true);
                break;
            case 1:
                GlStateManager.colorMask(true, false, false, true);
                break;
            case 2:
                GlStateManager.colorMask(true, true, true, true);
                break;
            default:
                break;
        }
        if (any) {
            GL11.glDrawArrays(builder.getDrawMode(), 0, builder.getVertexCount());
            for (VertexFormatElement element : elements) {
                switch (element.getType()) {
                    case POSITION:
                        GL11.glDisableClientState(GL11.GL_VERTEX_ARRAY);
                        break;
                    case UV:
                        GLX.gl13ClientActiveTexture(GLX.textureUnit + element.getIndex());
                        GL11.glDisableClientState(GL11.GL_TEXTURE_COORD_ARRAY);
                        GLX.gl13ClientActiveTexture(GLX.textureUnit);
                        break;
                    case COLOR:
                        GL11.glDisableClientState(GL11.GL_COLOR_ARRAY);
                        GlStateManager.clearColor();
                        break;
                    case NORMAL:
                        GL11.glDisableClientState(GL11.GL_NORMAL_ARRAY);
                        break;
                    default:
                        break;
                }
            }
        }
        builder.reset();
    }
}
