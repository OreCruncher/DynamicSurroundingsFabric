package org.orecruncher.dsurround.effects.clouds;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.Transforms;
import org.orecruncher.dsurround.lib.Library;

import java.nio.ByteBuffer;
import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the clouds from their {@link CloudSections}: the sections' vertices are uploaded to vertex buffers when they
 * change, and drawn each frame moved to where the clouds have drifted. The settled part twice: first only its depth
 * ({@link #DEPTH}), then its color where it is nearest ({@link #SETTLED}), so a cloud's faces behind its front don't
 * show through it. The changing part after ({@link #CHANGING}), fading in or out in the shader as the game's time goes
 * on, writing no depth: a part fading out would otherwise hide what is behind it once it is all but gone. With
 * Fabulous graphics to the clouds' own target, as vanilla's clouds are, which the transparency pass lays over the scene.
 * Render thread only.
 */
public final class CloudRenderer {

    // The shader's uniforms other than vanilla's, all vec4 (Std140Builder.putVec3 takes sixteen bytes all the same):
    // the offset from the sections' anchor to the camera and the ticks since their epoch; the light on the clouds and
    // how long a change fades; where they fade before the grid's edge; the lights' powers; and the lights
    private static final String CLOUD_INFO = "DSCloudInfo";
    private static final int CLOUD_INFO_SIZE;

    static {
        var size = new Std140SizeCalculator().putVec4().putVec4().putVec4().putVec4().putVec4();
        for (int i = 0; i < CloudLights.MAX; i++)
            size.putVec4();
        CLOUD_INFO_SIZE = size.get();
    }

    /**
     * The settled part's depth, and no color
     */
    public static final ModShader DEPTH = new ModShader(
            pipeline("pipeline/clouds_depth").withColorWrite(false).withDepthWrite(true).build(),
            "clouds", "vanilla's clouds will be drawn", Library.LOGGER);
    /**
     * The settled part's color, where it is nearest
     */
    public static final ModShader SETTLED = new ModShader(
            pipeline("pipeline/clouds").withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(true).build(),
            "clouds", "vanilla's clouds will be drawn", Library.LOGGER);
    /**
     * The changing part, over the settled part, fading
     */
    public static final ModShader CHANGING = new ModShader(
            pipeline("pipeline/clouds_changing").withBlend(BlendFunction.TRANSLUCENT)
                    .withDepthWrite(false).build(),
            "clouds", "vanilla's clouds will be drawn", Library.LOGGER);

    /**
     * All three, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(DEPTH, SETTLED, CHANGING);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Clouds");
    /**
     * How long working out the clouds again takes, on the CPU: every tick, a share of the cells at a time, on the tick
     * rather than while drawing, so it is timed apart (see CloudHandler)
     */
    public static final RenderTimer FIELD_TIMER = new RenderTimer("- Field");

    // How many cells before the grid's edge the clouds fade out over, wherever the fog is
    private static final int EDGE_FADE_CELLS = 3;

    @Nullable
    private static GpuBuffer settled;
    @Nullable
    private static GpuBuffer changing;
    @Nullable
    private static MappableRingBuffer cloudInfo;
    // The sections' version the buffers were uploaded from
    private static int uploadedVersion = -1;

    private CloudRenderer() {
    }

    private static RenderPipeline.Builder pipeline(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/clouds"))
                .withFragmentShader(Constants.asId("core/clouds"))
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withUniform(CLOUD_INFO, UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS);
    }

    /**
     * Whether the clouds can be drawn: their pipelines compiled, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return DEPTH.isUsable() && SETTLED.isUsable() && CHANGING.isUsable();
    }

    /**
     * Draws the clouds
     *
     * @param offsetX from the sections' anchor, at the cloud height, to the camera, in blocks; and {@code offsetY},
     *                {@code offsetZ}
     * @param red     the light on the clouds' tops; and {@code green}, {@code blue}
     * @param now     ticks since the sections' epoch, with the part of a tick: for the fading
     * @param edge    how far from the camera, across the ground, the grid's edge is at the nearest, in blocks
     * @param lights  the light inside the clouds this frame, from lightning
     * @return true if they were drawn; false if they couldn't be, and vanilla's should be
     */
    public static boolean render(CloudSections sections, float offsetX, float offsetY, float offsetZ, float red, float green,
                                 float blue, double now, float edge, CloudLights lights) {
        if (!isUsable())
            return false;
        if (sections.settledVertices() + sections.changingVertices() == 0)
            return true;
        RENDER_TIMER.begin();
        try {
            return SETTLED.run(() -> draw(sections, offsetX, offsetY, offsetZ, red, green, blue, now, edge, lights), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(CloudSections sections, float offsetX, float offsetY, float offsetZ, float red, float green,
                             float blue, double now, float edge, CloudLights lights) {
        if (uploadedVersion != sections.version()) {
            settled = upload(settled, sections.settled(), sections.settledVertices(), "settled");
            changing = upload(changing, sections.changing(), sections.changingVertices(), "changing");
            uploadedVersion = sections.version();
        }
        int settledQuads = sections.settledVertices() / 4;
        int changingQuads = sections.changingVertices() / 4;

        if (cloudInfo == null)
            cloudInfo = new MappableRingBuffer(() -> "Dynamic Surroundings clouds UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, CLOUD_INFO_SIZE);
        // Gone a cell inside the grid's edge, fading over the few cells before
        float gone = edge - CloudField.CELL;
        try (var view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(cloudInfo.currentBuffer(), false, true)) {
            var builder = Std140Builder.intoBuffer(view.data())
                    .putVec4(offsetX, offsetY, offsetZ, (float) now)
                    .putVec4(red, green, blue, (float) CloudField.FADE_TICKS)
                    .putVec4(gone - EDGE_FADE_CELLS * CloudField.CELL, gone, 0F, 0F)
                    .putVec4(lights.power(0), lights.power(1), lights.power(2), lights.power(3))
                    .putVec4(lights.power(4), lights.power(5), lights.power(6), lights.power(7));
            for (int i = 0; i < CloudLights.MAX; i++) {
                if (i < lights.count())
                    builder.putVec4(lights.x(i), lights.y(i), lights.z(i), lights.reach(i));
                else
                    builder.putVec4(0F, 0F, 0F, 1F);
            }
        }

        var minecraft = Minecraft.getInstance();
        var cloudsTarget = minecraft.levelRenderer.getCloudsTarget();
        var target = cloudsTarget != null ? cloudsTarget : minecraft.getMainRenderTarget();
        var transforms = Transforms.camera();
        var indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        var indexBuffer = indices.getBuffer(6 * Math.max(settledQuads, changingQuads));

        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings clouds", target.getColorTextureView(), OptionalInt.empty(),
                        target.getDepthTextureView(), OptionalDouble.empty())) {
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform(CLOUD_INFO, cloudInfo.currentBuffer());
            pass.setIndexBuffer(indexBuffer, indices.type());
            if (settledQuads > 0 && settled != null) {
                // Their depth, then their color where they are nearest
                pass.setVertexBuffer(0, settled);
                pass.setPipeline(DEPTH.pipeline());
                pass.drawIndexed(0, 0, 6 * settledQuads, 1);
                pass.setPipeline(SETTLED.pipeline());
                pass.drawIndexed(0, 0, 6 * settledQuads, 1);
            }
            if (changingQuads > 0 && changing != null) {
                // Over them, fading, writing no depth
                pass.setVertexBuffer(0, changing);
                pass.setPipeline(CHANGING.pipeline());
                pass.drawIndexed(0, 0, 6 * changingQuads, 1);
            }
        }
        cloudInfo.rotate();
        RENDER_TIMER.count(settledQuads + changingQuads);
    }

    /**
     * Uploads vertices laid out as POSITION_TEX_COLOR to a vertex buffer, made or grown as needed; none leaves the
     * buffer as it was, as it isn't drawn
     */
    @Nullable
    private static GpuBuffer upload(@Nullable GpuBuffer buffer, ByteBuffer vertices, int count, String part) {
        if (count == 0)
            return buffer;
        int bytes = count * CloudSections.VERTEX_BYTES;
        var device = RenderSystem.getDevice();
        if (buffer == null || buffer.size() < bytes) {
            if (buffer != null)
                buffer.close();
            // Room to grow, so clouds building up don't make a new buffer every few ticks
            buffer = device.createBuffer(() -> "Dynamic Surroundings clouds, " + part, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST,
                    bytes + bytes / 2);
        }
        device.createCommandEncoder().writeToBuffer(buffer.slice(), vertices.duplicate().limit(vertices.position() + bytes));
        return buffer;
    }

    /**
     * Lets go of the vertex buffers, as when leaving a world
     */
    public static void release() {
        if (settled != null) {
            settled.close();
            settled = null;
        }
        if (changing != null) {
            changing.close();
            changing = null;
        }
        if (cloudInfo != null) {
            cloudInfo.close();
            cloudInfo = null;
        }
        uploadedVersion = -1;
    }
}
