package com.ashlauncher.client.v1_8_9;

import com.ashlauncher.client.v1_8_9.mixin.BufferBuilderOffsetsAccess;
import com.mojang.blaze3d.platform.GLX;
import com.mojang.blaze3d.platform.GlStateManager;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
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
 * twice.
 *
 * <p>The game draws fancy clouds in two passes - depth only, then colour -
 * and in each pass builds the geometry again, one 8 by 8 tile at a time,
 * handing each tile to the driver as a draw of its own. Both passes build the
 * same vertices: nothing they are made from changes between them. So this
 * builds every tile once, into one buffer, and draws the tiles from it for
 * each pass. The 1.8.9 profile put clouds at about a sixth of a frame.
 *
 * <p>Nothing a player sees changes, pixel for pixel. The vertices are the
 * game's own: {@link #render} is the game's {@code renderFancyClouds}, its
 * arithmetic unchanged, with the pass loop taken out of the building and put
 * round the drawing. The same draws, of the same vertices, reach the same
 * state in the same order. The smoke test proves it on a frozen frame: ash's
 * clouds against the game's.
 */
public final class FasterClouds {

    /** The 8 by 8 tiles, in the game's order. */
    private static final int TILES = 64;

    /** Where each tile's vertices end in this frame's buffer, so each can be drawn on its own as the game does. */
    private static final int[] TILE_ENDS = new int[TILES];

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
        // Written by ash, not the shared builder, but with the builder's
        // offset, which it would have added to every position.
        BufferBuilderOffsetsAccess offsets = (BufferBuilderOffsetsAccess) bufferBuilder;
        double[] offset = {offsets.ash$offsetX(), offsets.ash$offsetY(), offsets.ash$offsetZ()};
        VERTICES.reset(offset);
        tiles(VERTICES, j, z, aa, ab, ac, m, n, o, p, q, r, s, t, u, v, w, x);
        lastFrame = new float[] {j, z, aa, ab, ac, m, n, o, p, q, r, s, t, u, v, w, x};
        lastOffset = offset;
        drawBothPasses(VERTICES, anaglyphFilter);
        drawn++;

        GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
        GlStateManager.disableBlend();
        GlStateManager.enableCull();
    }

    /**
     * The game's 64 tiles, its arithmetic unchanged, each vertex handed to
     * {@code sink} with exactly the values the game hands its buffer builder.
     * The colours and offsets are the game's locals from {@link #render}, by
     * their names there.
     */
    static void tiles(CloudVertices sink, float j, float z, float aa, float ab, float ac, float m, float n,
            float o, float p, float q, float r, float s, float t, float u, float v, float w, float x) {
        int tile = 0;
        for (int ah = -3; ah <= 4; ah++) {
            for (int ai = -3; ai <= 4; ai++) {
                float aj = ah * 8;
                float ak = ai * 8;
                float al = aj - ab;
                float am = ak - ac;
                if (j > -5.0F) {
                    sink.vertex(al + 0.0F, j + 0.0F, am + 8.0F,
                            (aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                            s, t, u, 0.8F,
                            0.0F, -1.0F, 0.0F);
                    sink.vertex(al + 8.0F, j + 0.0F, am + 8.0F,
                            (aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                            s, t, u, 0.8F,
                            0.0F, -1.0F, 0.0F);
                    sink.vertex(al + 8.0F, j + 0.0F, am + 0.0F,
                            (aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                            s, t, u, 0.8F,
                            0.0F, -1.0F, 0.0F);
                    sink.vertex(al + 0.0F, j + 0.0F, am + 0.0F,
                            (aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                            s, t, u, 0.8F,
                            0.0F, -1.0F, 0.0F);
                }

                if (j <= 5.0F) {
                    sink.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 8.0F,
                            (aj + 0.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                            m, n, o, 0.8F,
                            0.0F, 1.0F, 0.0F);
                    sink.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 8.0F,
                            (aj + 8.0F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                            m, n, o, 0.8F,
                            0.0F, 1.0F, 0.0F);
                    sink.vertex(al + 8.0F, j + 4.0F - 9.765625E-4F, am + 0.0F,
                            (aj + 8.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                            m, n, o, 0.8F,
                            0.0F, 1.0F, 0.0F);
                    sink.vertex(al + 0.0F, j + 4.0F - 9.765625E-4F, am + 0.0F,
                            (aj + 0.0F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                            m, n, o, 0.8F,
                            0.0F, 1.0F, 0.0F);
                }

                if (ah > -1) {
                    for (int an = 0; an < 8; an++) {
                        sink.vertex(al + an + 0.0F, j + 0.0F, am + 8.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                -1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 0.0F, j + 4.0F, am + 8.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                -1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 0.0F, j + 4.0F, am + 0.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                -1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 0.0F, j + 0.0F, am + 0.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                -1.0F, 0.0F, 0.0F);
                    }
                }

                if (ah <= 1) {
                    for (int an = 0; an < 8; an++) {
                        sink.vertex(al + an + 1.0F - 9.765625E-4F, j + 0.0F, am + 8.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 1.0F - 9.765625E-4F, j + 4.0F, am + 8.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 8.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 1.0F - 9.765625E-4F, j + 4.0F, am + 0.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                1.0F, 0.0F, 0.0F);
                        sink.vertex(al + an + 1.0F - 9.765625E-4F, j + 0.0F, am + 0.0F,
                                (aj + an + 0.5F) * 0.00390625F + z, (ak + 0.0F) * 0.00390625F + aa,
                                p, q, r, 0.8F,
                                1.0F, 0.0F, 0.0F);
                    }
                }

                if (ai > -1) {
                    for (int an = 0; an < 8; an++) {
                        sink.vertex(al + 0.0F, j + 4.0F, am + an + 0.0F,
                                (aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, -1.0F);
                        sink.vertex(al + 8.0F, j + 4.0F, am + an + 0.0F,
                                (aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, -1.0F);
                        sink.vertex(al + 8.0F, j + 0.0F, am + an + 0.0F,
                                (aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, -1.0F);
                        sink.vertex(al + 0.0F, j + 0.0F, am + an + 0.0F,
                                (aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, -1.0F);
                    }
                }

                if (ai <= 1) {
                    for (int an = 0; an < 8; an++) {
                        sink.vertex(al + 0.0F, j + 4.0F, am + an + 1.0F - 9.765625E-4F,
                                (aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, 1.0F);
                        sink.vertex(al + 8.0F, j + 4.0F, am + an + 1.0F - 9.765625E-4F,
                                (aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, 1.0F);
                        sink.vertex(al + 8.0F, j + 0.0F, am + an + 1.0F - 9.765625E-4F,
                                (aj + 8.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, 1.0F);
                        sink.vertex(al + 0.0F, j + 0.0F, am + an + 1.0F - 9.765625E-4F,
                                (aj + 0.0F) * 0.00390625F + z, (ak + an + 0.5F) * 0.00390625F + aa,
                                v, w, x, 0.8F,
                                0.0F, 0.0F, 1.0F);
                    }
                }

                TILE_ENDS[tile++] = sink.count();
            }
        }
    }

    /** Where {@link #tiles} hands each vertex: the values the game hands its buffer builder, in its order. */
    interface CloudVertices {

        void vertex(double x, double y, double z, double u, double v, float red, float green, float blue, float alpha,
                float normalX, float normalY, float normalZ);

        int count();
    }

    /** The most vertices the 64 tiles can make: four for each top and bottom, 32 for each of four sides. */
    private static final int MOST_VERTICES = TILES * (4 + 4 + 4 * 32);

    /**
     * The clouds' vertices, written by ash straight into a buffer of its own
     * in the game's format for them, position, texture, colour and normal:
     * byte for byte what the game's buffer builder writes, without the
     * builder's bookkeeping for every element of every vertex, which the
     * flight recording put at most of the clouds' cost.
     *
     * <p>Each element converts as {@code BufferBuilder} does for its type in
     * this format: positions as floats of the value plus the builder's offset,
     * texture coordinates as floats, colour as a byte per channel of the
     * channel times 255 in the order the machine keeps ints, and the normal as
     * a byte per axis of the axis times 127. The one padding byte after the
     * normal the builder never writes, and GL never reads. The smoke test
     * holds a frame of these against the game's builder, byte for byte.
     */
    static final class CloudBuffer implements CloudVertices {

        static final int STRIDE = 28;

        final ByteBuffer data = ByteBuffer.allocateDirect(MOST_VERTICES * STRIDE).order(ByteOrder.nativeOrder());
        private int count;
        private double offsetX;
        private double offsetY;
        private double offsetZ;

        void reset(double[] offset) {
            count = 0;
            offsetX = offset[0];
            offsetY = offset[1];
            offsetZ = offset[2];
        }

        @Override
        public void vertex(double x, double y, double z, double u, double v, float red, float green, float blue,
                float alpha, float normalX, float normalY, float normalZ) {
            int at = count * STRIDE;
            data.putFloat(at, (float) (x + offsetX));
            data.putFloat(at + 4, (float) (y + offsetY));
            data.putFloat(at + 8, (float) (z + offsetZ));
            data.putFloat(at + 12, (float) u);
            data.putFloat(at + 16, (float) v);
            int r = (int) (red * 255.0F);
            int g = (int) (green * 255.0F);
            int b = (int) (blue * 255.0F);
            int a = (int) (alpha * 255.0F);
            if (ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN) {
                data.put(at + 20, (byte) r);
                data.put(at + 21, (byte) g);
                data.put(at + 22, (byte) b);
                data.put(at + 23, (byte) a);
            } else {
                data.put(at + 20, (byte) a);
                data.put(at + 21, (byte) b);
                data.put(at + 22, (byte) g);
                data.put(at + 23, (byte) r);
            }
            data.put(at + 24, (byte) ((int) normalX * 127 & 0xFF));
            data.put(at + 25, (byte) ((int) normalY * 127 & 0xFF));
            data.put(at + 26, (byte) ((int) normalZ * 127 & 0xFF));
            count++;
        }

        @Override
        public int count() {
            return count;
        }
    }

    /** The same vertices handed to a game buffer builder, as the game's own clouds hand them: for the smoke test. */
    static final class GameBuilder implements CloudVertices {

        final BufferBuilder builder;

        GameBuilder(BufferBuilder builder) {
            this.builder = builder;
        }

        @Override
        public void vertex(double x, double y, double z, double u, double v, float red, float green, float blue,
                float alpha, float normalX, float normalY, float normalZ) {
            builder.vertex(x, y, z).texture(u, v).color(red, green, blue, alpha).normal(normalX, normalY, normalZ).next();
        }

        @Override
        public int count() {
            return builder.getVertexCount();
        }
    }

    private static final CloudBuffer VERTICES = new CloudBuffer();

    /** The last frame's values that the tiles are made from, and the builder's offset then: for the smoke test. */
    private static float[] lastFrame;
    private static double[] lastOffset;

    /**
     * The last frame's clouds built both ways - ash's writer and the game's
     * buffer builder, with the same offset - and compared byte for byte but
     * for padding. Null if they match, or what differs. For the smoke test, on
     * the client's thread.
     */
    static String lastFrameAgainstTheGame() {
        float[] f = lastFrame;
        if (f == null) {
            return "ash has drawn no clouds yet";
        }
        CloudBuffer ash = new CloudBuffer();
        ash.reset(lastOffset);
        tiles(ash, f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11], f[12], f[13], f[14],
                f[15], f[16]);
        BufferBuilder builder = new BufferBuilder(MOST_VERTICES * CloudBuffer.STRIDE / 4 + 64);
        builder.begin(GL11.GL_QUADS, VertexFormats.POSITION_TEXTURE_COLOR_NORMAL);
        builder.offset(lastOffset[0], lastOffset[1], lastOffset[2]);
        tiles(new GameBuilder(builder), f[0], f[1], f[2], f[3], f[4], f[5], f[6], f[7], f[8], f[9], f[10], f[11],
                f[12], f[13], f[14], f[15], f[16]);
        builder.end();
        if (builder.getVertexCount() != ash.count()) {
            return "the game's builder made " + builder.getVertexCount() + " vertices and ash " + ash.count();
        }
        ByteBuffer game = builder.getByteBuffer();
        for (int vertex = 0; vertex < ash.count(); vertex++) {
            for (int b = 0; b < CloudBuffer.STRIDE - 1; b++) {
                int at = vertex * CloudBuffer.STRIDE + b;
                if (game.get(at) != ash.data.get(at)) {
                    return "vertex " + vertex + ", byte " + b + ": the game wrote " + game.get(at) + ", ash "
                            + ash.data.get(at);
                }
            }
        }
        return null;
    }

    /**
     * Each tile as a draw of its own, in order, exactly as the game hands
     * them over. Drawing them as one call instead is no faster to the eye but
     * not the same to every renderer: Mesa's software renderer, on CI, then
     * settled a few dozen ties between two cloud faces at equal depth the
     * other way.
     */
    private static void drawTiles(int mode) {
        int start = 0;
        for (int tile = 0; tile < TILES; tile++) {
            int end = TILE_ENDS[tile];
            if (end > start) {
                GL11.glDrawArrays(mode, start, end - start);
            }
            start = end;
        }
    }

    /**
     * The game's buffer renderer, {@code BufferRenderer.draw}, over ash's
     * buffer in the game's format, drawing every tile twice: depth only, then
     * colour through the mask the game's own pass loop sets for the anaglyph
     * filter.
     */
    private static void drawBothPasses(CloudBuffer vertices, int anaglyphFilter) {
        VertexFormat format = VertexFormats.POSITION_TEXTURE_COLOR_NORMAL;
        int stride = format.getVertexSize();
        ByteBuffer data = vertices.data;
        List<VertexFormatElement> elements = format.getElements();
        boolean any = vertices.count() > 0;
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
            drawTiles(GL11.GL_QUADS);
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
            drawTiles(GL11.GL_QUADS);
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
    }
}
