package org.orecruncher.dsurround.effects.frost;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * Draws the frost on windows: each frosted face as a grid of small quads just off the glass, with the frost texture
 * laid across it in the world's coordinates, so a window of many blocks shows one pattern. Each corner of the grid
 * carries how far the frost has grown there, in its alpha: more near the window's edges, where frost starts, and its
 * bottom, where it gathers, than in its middle. The shader ({@code window_frost}) shows the texels of the
 * {@link FrostTexture} that have frosted by then. Positions are relative to the camera. Render thread only.
 * <p>
 * Two pipelines: as vanilla's weather, depth is written only with Fabulous graphics, where the frost is drawn to the
 * weather's own target, which the transparency pass lays over the scene by depth.
 */
public final class FrostRenderer {

    /**
     * Drawn after the translucent blocks (without Fabulous graphics all frost, with it the frost on stained glass), its
     * depth tested in the shader against the scene's from before them (DEPTH_COPY, bound as DepthSampler), not by the
     * depth buffer, which has the water and stained glass in it by then. Writes no depth.
     */
    public static final ModShader SHADER = new ModShader(
            pipeline("pipeline/window_frost")
                    .withShaderDefine("DEPTH_COPY")
                    .withSampler("DepthSampler")
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false)
                    .build(),
            "window frost", "windows will not frost", Library.LOGGER);
    public static final ModShader SHADER_DEPTH = new ModShader(
            pipeline("pipeline/window_frost_depth_write").withDepthWrite(true).build(),
            "window frost (Fabulous)", "windows will not frost", Library.LOGGER);

    /**
     * Both, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, SHADER_DEPTH);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Window Frost");

    /**
     * The sampler the copy of the scene's depth is bound as, without Fabulous graphics
     */
    public static final String DEPTH_SAMPLER = "DepthSampler";

    // The size of a cell of the grid a face is drawn as, in blocks
    static final float CELL = 0.25F;
    // How many blocks the texture covers each way
    static final float TEXTURE_BLOCKS = 4F;
    // How far frost grows past what the face has, at a window's edge, and how far short of it at its middle
    static final float EDGE_AHEAD = 0.6F;
    static final float MIDDLE_BEHIND = 0.6F;
    // The bottom edge counts as nearer than it is: frost gathers at the bottom of a window
    static final float BOTTOM_NEARER = 0.6F;
    // Faces with less frost than this aren't drawn
    static final float MIN_SHOWN = 0.01F;
    // The frost's colour, before light: a little blue
    static final float RED = 0.88F, GREEN = 0.94F, BLUE = 1F;

    private FrostRenderer() {
    }

    /**
     * Whether the frost can be drawn: both pipelines compiled, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isAvailable() {
        return SHADER.isUsable() && SHADER_DEPTH.isUsable();
    }

    // Premultiplied, "over" (see the shader); seen from both sides
    private static RenderPipeline.Builder pipeline(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/window_frost"))
                .withFragmentShader(Constants.asId("core/window_frost"))
                .withShaderDefine("FADE_DISTANCE", (float) FrostField.RANGE)
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA)
                .withCull(false);
    }

    /**
     * Builds the frost into a buffer and hands it to {@code drawer}, which draws it with the frost's pipeline and
     * texture
     *
     * @return how many faces were drawn
     */
    public static int draw(FrostField field, ByteBufferBuilder bytes, Consumer<MeshData> drawer, Camera camera, Predicate<FrostFace> which) {
        var cam = camera.position();
        float range = FrostField.RANGE;
        float rangeSq = range * range;
        BufferBuilder builder = new BufferBuilder(bytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.PARTICLE);
        int[] drawn = {0};
        field.forEachFace(face -> {
            if (face.shown < MIN_SHOWN || !which.test(face))
                return;
            double dx = face.x + 0.5D - cam.x, dy = face.y + 0.5D - cam.y, dz = face.z + 0.5D - cam.z;
            if (dx * dx + dy * dy + dz * dz > rangeSq)
                return;
            drawFace(builder, face, cam.x, cam.y, cam.z);
            drawn[0]++;
        });
        var mesh = builder.build();
        if (mesh != null)
            drawer.accept(mesh);
        return drawn[0];
    }

    private static void drawFace(BufferBuilder builder, FrostFace face, double camX, double camY, double camZ) {
        // Fully frosted, the frost is the same all over, so one quad does: a window frosts fully on cold nights, and
        // the grid's quads were most of drawing it
        boolean uniform = isUniform(face);
        int cellsU = uniform ? 1 : Math.max(1, Mth.ceil((float) (face.u1 - face.u0) / CELL));
        int cellsV = uniform ? 1 : Math.max(1, Mth.ceil((face.v1 - face.v0) / CELL));
        double stepU = (face.u1 - face.u0) / cellsU;
        float stepV = (face.v1 - face.v0) / cellsV;
        // The texture's origin: on a whole number of its repeats, and moved along by an amount of the window's own, so
        // windows side by side don't show the same frost
        boolean alongX = face.side.getAxis() == Direction.Axis.Z;
        int blockU = alongX ? face.x : face.z;
        float originU = TEXTURE_BLOCKS * Mth.floor(blockU / TEXTURE_BLOCKS);
        float originV = TEXTURE_BLOCKS * Mth.floor(face.y / TEXTURE_BLOCKS);
        int planeBlock = alongX ? face.z : face.x;
        float offsetU = shift(planeBlock, face.side.get2DDataValue());
        float offsetV = shift(planeBlock, face.side.get2DDataValue() + 4);

        for (int j = 0; j < cellsV; j++) {
            float va = face.v0 + j * stepV, vb = va + stepV;
            for (int i = 0; i < cellsU; i++) {
                double ua = face.u0 + i * stepU, ub = ua + stepU;
                corner(builder, face, alongX, ua, va, originU, originV, offsetU, offsetV, camX, camY, camZ);
                corner(builder, face, alongX, ub, va, originU, originV, offsetU, offsetV, camX, camY, camZ);
                corner(builder, face, alongX, ub, vb, originU, originV, offsetU, offsetV, camX, camY, camZ);
                corner(builder, face, alongX, ua, vb, originU, originV, offsetU, offsetV, camX, camY, camZ);
            }
        }
    }

    private static void corner(BufferBuilder builder, FrostFace face, boolean alongX, double u, float v, float originU,
                               float originV, float offsetU, float offsetV, double camX, double camY, double camZ) {
        float x = (float) ((alongX ? u : face.plane) - camX);
        float y = (float) (v - camY);
        float z = (float) ((alongX ? face.plane : u) - camZ);
        float texU = (float) (u - originU) / TEXTURE_BLOCKS + offsetU;
        float texV = (originV - v) / TEXTURE_BLOCKS + offsetV;
        builder.addVertex(x, y, z).setUv(texU, texV).setColor(RED, GREEN, BLUE, grown(face, u, v)).setLight(face.light);
    }

    /**
     * Whether the frost has grown as far as it goes everywhere on a face (see {@link #grown})
     */
    static boolean isUniform(FrostFace face) {
        return face.shown >= 1F;
    }

    /**
     * How far the frost has grown at a point on a face, 0 to 1: the frost the face has, ahead of that at the window's
     * edges and behind it in its middle
     */
    static float grown(FrostFace face, double u, float v) {
        float across = (float) Math.min(u - face.edgeU0, face.edgeU1 - u);
        float edge = Math.min(across, Math.min((v - face.edgeV0) * BOTTOM_NEARER, face.edgeV1 - v));
        float edgeness = 1F - Mth.clamp(edge / FrostFaces.EDGE_REACH, 0F, 1F);
        return Mth.clamp(face.shown * (1F + EDGE_AHEAD * edgeness) - MIDDLE_BEHIND * (1F - edgeness) * (1F - face.shown), 0F, 1F);
    }

    /**
     * An amount, 0 to 1, of a window's own: the same for every face in the plane of the glass
     */
    private static float shift(int plane, int salt) {
        long h = (plane * 0x9E3779B97F4A7C15L) ^ (salt * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 29;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 32;
        return (h & 0xFFFF) / 65536F;
    }
}
