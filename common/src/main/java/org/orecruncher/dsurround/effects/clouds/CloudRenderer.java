package org.orecruncher.dsurround.effects.clouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.system.MemoryUtil;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.nio.ByteBuffer;

/**
 * Draws the clouds from their {@link CloudSections}: the sections' vertices are uploaded to vertex buffers when they
 * change, and drawn each frame moved to where the clouds have drifted. The settled part twice, as vanilla's clouds are:
 * first only its depth, then its color where it is nearest, so a cloud's faces behind its front don't show through it.
 * With Fabulous graphics, to the clouds' own target, which vanilla's transparency pass lays over the scene by its depth.
 * <p>
 * The parts of clouds that are changing are a second buffer, drawn after, fading in or out in the shader as the game's
 * time goes on, so the buffers are only uploaded again when the clouds change. They write no depth: a part fading out
 * would otherwise hide what is behind it even once it is all but gone.
 * <p>
 * They fade with distance by a fog of their own: the weather's and the biome's fog, as the terrain has it, stretched from
 * the render distance out to the clouds' distance, so rain and dust close in on them as they do on the land; and toward
 * the clouds' distance, as vanilla's clouds fade toward theirs on later versions. Render thread only.
 */
public final class CloudRenderer {

    /**
     * Draws the clouds. Without it, vanilla's are drawn.
     */
    public static final ModShader SHADER = new ModShader("clouds", DefaultVertexFormat.POSITION_TEX_COLOR,
            "clouds", "vanilla's clouds will be drawn", Library.LOGGER);
    public static final RenderTimer RENDER_TIMER = new RenderTimer("Clouds");
    /**
     * How long working out the clouds again takes, on the CPU: every tick, a share of the cells at a time, on the tick
     * rather than while drawing, so it is timed apart (see CloudHandler)
     */
    public static final RenderTimer FIELD_TIMER = new RenderTimer("- Field");

    // The light uniforms' names, made once
    private static final String[] LIGHTS = {"Light0", "Light1", "Light2", "Light3", "Light4", "Light5", "Light6", "Light7"};
    // How many cells before the grid's edge the clouds fade out over, wherever the fog is
    private static final int EDGE_FADE_CELLS = 3;

    @Nullable
    private static VertexBuffer settled;
    @Nullable
    private static VertexBuffer changing;
    @Nullable
    private static ByteBufferBuilder uploadBytes;
    // The sections' version the buffers were uploaded from
    private static int uploadedVersion = -1;

    private CloudRenderer() {
    }

    /**
     * Whether the clouds can be drawn: their shader loaded, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return SHADER.isUsable();
    }

    /**
     * Draws the clouds
     *
     * @param modelView   the view's rotation, with the camera at the origin
     * @param offsetX     from the sections' anchor, at the cloud height, to the camera, in blocks; and {@code offsetY},
     *                    {@code offsetZ}
     * @param red         the light on the clouds' tops; and {@code green}, {@code blue}
     * @param now         ticks since the sections' epoch, with the part of a tick: for the fading
     * @param edge        how far from the camera, across the ground, the grid's edge is at the nearest, in blocks
     * @param cloudBlocks how far the clouds reach, in blocks
     * @param lights      the light inside the clouds this frame, from lightning
     * @return true if they were drawn; false if they couldn't be, and vanilla's should be
     */
    public static boolean render(CloudSections sections, Matrix4f modelView, Matrix4f projection,
                                 float offsetX, float offsetY, float offsetZ, float red, float green, float blue, double now,
                                 float edge, float cloudBlocks, CloudLights lights) {
        var shader = SHADER.get();
        if (shader == null || !isUsable())
            return false;
        if (sections.settledVertices() + sections.changingVertices() == 0)
            return true;

        var minecraft = Minecraft.getInstance();
        var cloudsTarget = minecraft.levelRenderer.getCloudsTarget();
        boolean fabulous = Minecraft.useShaderTransparency() && cloudsTarget != null;
        // The terrain's fog (the weather's and the biome's in it), stretched out to the clouds' distance
        float renderBlocks = Math.max(minecraft.gameRenderer.getRenderDistance(), 1F);
        float stretch = Math.max(1F, cloudBlocks / renderBlocks);
        float fogStart = RenderSystem.getShaderFogStart() * stretch;
        float fogEnd = RenderSystem.getShaderFogEnd() * stretch;
        try {
            return SHADER.run(() -> {
                RENDER_TIMER.begin();
                if (settled == null || uploadedVersion != sections.version())
                    upload(sections);
                var settledVertices = settled;
                var changingVertices = changing;
                int settledQuads = sections.settledVertices() / 4;
                int changingQuads = sections.changingVertices() / 4;
                if (settledVertices != null && changingVertices != null) {
                    if (fabulous)
                        cloudsTarget.bindWrite(false);
                    FogRenderer.levelFogColor();
                    RenderSystem.enableCull();
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.depthMask(true);
                    RenderSystem.enableBlend();
                    // "Over", alpha and all: to the clouds' target, cleared, it leaves color already multiplied by its
                    // alpha, as the transparency pass takes it
                    RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                            GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                    shader.safeGetUniform("Offset").set(offsetX, offsetY, offsetZ);
                    shader.safeGetUniform("CloudColor").set(red, green, blue);
                    shader.safeGetUniform("Now").set((float) now);
                    shader.safeGetUniform("FadeTicks").set((float) CloudField.FADE_TICKS);
                    shader.safeGetUniform("CloudFog").set(fogStart, fogEnd);
                    shader.safeGetUniform("CloudEnd").set(cloudBlocks);
                    // Gone a cell inside the grid's edge, fading over the few cells before
                    float gone = edge - CloudField.CELL;
                    shader.safeGetUniform("EdgeFade").set(gone - EDGE_FADE_CELLS * CloudField.CELL, gone);
                    setLights(shader, lights);
                    RenderSystem.setShader(SHADER.supplier());

                    if (settledQuads > 0) {
                        settledVertices.bind();
                        // Their depth, then their color where they are nearest
                        RenderSystem.colorMask(false, false, false, false);
                        settledVertices.drawWithShader(modelView, projection, shader);
                        RenderSystem.colorMask(true, true, true, true);
                        settledVertices.drawWithShader(modelView, projection, shader);
                    }
                    if (changingQuads > 0) {
                        // Over them, fading, writing no depth
                        RenderSystem.depthMask(false);
                        changingVertices.bind();
                        changingVertices.drawWithShader(modelView, projection, shader);
                        RenderSystem.depthMask(true);
                    }
                    VertexBuffer.unbind();
                    RENDER_TIMER.count(settledQuads + changingQuads);
                }
                RENDER_TIMER.end();
            }, () -> {
                RENDER_TIMER.end();
                VertexBuffer.unbind();
                RenderSystem.colorMask(true, true, true, true);
                RenderSystem.depthMask(true);
            });
        } finally {
            if (fabulous)
                minecraft.getMainRenderTarget().bindWrite(false);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.enableCull();
        }
    }

    /**
     * The light inside the clouds, a uniform for each point and two for their powers; unused ones have none
     */
    private static void setLights(ShaderInstance shader, CloudLights lights) {
        for (int i = 0; i < CloudLights.MAX; i++) {
            if (i < lights.count())
                shader.safeGetUniform(LIGHTS[i]).set(lights.x(i), lights.y(i), lights.z(i), lights.reach(i));
        }
        shader.safeGetUniform("LightPowerA").set(lights.power(0), lights.power(1), lights.power(2), lights.power(3));
        shader.safeGetUniform("LightPowerB").set(lights.power(4), lights.power(5), lights.power(6), lights.power(7));
    }

    private static void upload(CloudSections sections) {
        if (settled == null)
            settled = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        if (changing == null)
            changing = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        upload(settled, sections.settled(), sections.settledVertices());
        upload(changing, sections.changing(), sections.changingVertices());
        uploadedVersion = sections.version();
    }

    /**
     * Uploads vertices laid out as POSITION_TEX_COLOR to a vertex buffer, as quads; none leaves the buffer as it was, as
     * it isn't drawn
     */
    private static void upload(VertexBuffer buffer, ByteBuffer vertices, int count) {
        if (count == 0)
            return;
        if (uploadBytes == null)
            uploadBytes = new ByteBufferBuilder(vertices.remaining());
        int bytes = count * CloudSections.VERTEX_BYTES;
        long to = uploadBytes.reserve(bytes);
        MemoryUtil.memCopy(MemoryUtil.memAddress(vertices), to, bytes);
        var result = uploadBytes.build();
        if (result == null)
            return;
        var state = new MeshData.DrawState(DefaultVertexFormat.POSITION_TEX_COLOR, count, count / 4 * 6, VertexFormat.Mode.QUADS,
                VertexFormat.IndexType.least(count));
        buffer.bind();
        buffer.upload(new MeshData(result, state));
        VertexBuffer.unbind();
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
        if (uploadBytes != null) {
            uploadBytes.close();
            uploadBytes = null;
        }
        uploadedVersion = -1;
    }
}
