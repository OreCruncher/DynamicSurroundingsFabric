package org.orecruncher.dsurround.effects.clouds;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

/**
 * Draws the {@link CloudField}: its quads ({@link CloudMesh}) are built into a vertex buffer when the clouds change, and
 * drawn each frame moved to where the clouds have drifted, as vanilla's are. Twice, as vanilla's are: first only their
 * depth, then their color where they are nearest, so a cloud's faces behind its front don't show through it. With
 * Fabulous graphics, to the clouds' own target, which vanilla's transparency pass lays over the scene by its depth.
 * <p>
 * The parts of clouds that are changing are a second buffer, drawn after, fading in or out in the shader as the game's
 * time goes on, so the buffers are only built again when the clouds change. They write no depth: a part fading out
 * would otherwise hide what is behind it even once it is all but gone. Render thread only.
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
    // Room for the settled part's and the changing part's vertices, to start with: they grow as needed
    private static final int SETTLED_BYTES = 256 * 1024;
    private static final int CHANGING_BYTES = 32 * 1024;

    @Nullable
    private static VertexBuffer settled;
    @Nullable
    private static VertexBuffer changing;
    @Nullable
    private static ByteBufferBuilder settledBytes;
    @Nullable
    private static ByteBufferBuilder changingBytes;
    // The field's version the buffers were built from, the field's tick then, and how many quads each has
    private static int builtVersion = -1;
    private static long builtTick;
    private static int settledQuads;
    private static int changingQuads;

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
     * @param modelView the view's rotation, with the camera at the origin
     * @param offsetX   from the grid's corner, at the cloud height, to the camera, in blocks; and {@code offsetY},
     *                  {@code offsetZ}
     * @param red       the light on the clouds' tops; and {@code green}, {@code blue}
     * @param now       the tick it is, with the part of a tick, as the field's ticks are counted: for the fading
     * @param lights    the light inside the clouds this frame, from lightning
     * @return true if they were drawn; false if they couldn't be, and vanilla's should be
     */
    public static boolean render(CloudField field, Matrix4f modelView, Matrix4f projection,
                                 float offsetX, float offsetY, float offsetZ, float red, float green, float blue, double now,
                                 CloudLights lights) {
        var shader = SHADER.get();
        if (shader == null || !isUsable())
            return false;
        if (field.isEmpty())
            return true;

        var minecraft = Minecraft.getInstance();
        var cloudsTarget = minecraft.levelRenderer.getCloudsTarget();
        boolean fabulous = Minecraft.useShaderTransparency() && cloudsTarget != null;
        try {
            return SHADER.run(() -> {
                RENDER_TIMER.begin();
                if (settled == null || builtVersion != field.version())
                    rebuild(field);
                var settledVertices = settled;
                var changingVertices = changing;
                if (settledVertices != null && changingVertices != null && settledQuads + changingQuads > 0) {
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
                    shader.safeGetUniform("Now").set((float) (now - builtTick));
                    shader.safeGetUniform("FadeTicks").set((float) CloudField.FADE_TICKS);
                    // The camera is in the grid's middle cell, so its edge is at least the radius in cells away: gone a
                    // cell inside that, fading over the few cells before
                    float edge = (field.radius() - 1) * CloudField.CELL;
                    shader.safeGetUniform("EdgeFade").set(edge - EDGE_FADE_CELLS * CloudField.CELL, edge);
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

    private static void rebuild(CloudField field) {
        if (settledBytes == null)
            settledBytes = new ByteBufferBuilder(SETTLED_BYTES);
        if (changingBytes == null)
            changingBytes = new ByteBufferBuilder(CHANGING_BYTES);
        var settledBuilder = new BufferBuilder(settledBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var changingBuilder = new BufferBuilder(changingBytes, VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        int[] counts = new int[2];
        CloudMesh.build(field,
                (x, y, z, shade, alpha, start, direction) -> {
                    settledBuilder.addVertex(x, y, z).setUv(start, direction).setColor(shade, shade, shade, alpha);
                    counts[0]++;
                },
                (x, y, z, shade, alpha, start, direction) -> {
                    changingBuilder.addVertex(x, y, z).setUv(start, direction).setColor(shade, shade, shade, alpha);
                    counts[1]++;
                });
        builtVersion = field.version();
        builtTick = field.tick();
        settledQuads = counts[0] / 4;
        changingQuads = counts[1] / 4;
        settled = upload(settled, settledBuilder);
        changing = upload(changing, changingBuilder);
    }

    /**
     * Uploads what a builder has to a vertex buffer, made the first time; an empty builder leaves the buffer as it was,
     * as it isn't drawn
     */
    private static VertexBuffer upload(@Nullable VertexBuffer buffer, BufferBuilder builder) {
        if (buffer == null)
            buffer = new VertexBuffer(VertexBuffer.Usage.DYNAMIC);
        var mesh = builder.build();
        if (mesh != null) {
            buffer.bind();
            buffer.upload(mesh);
            VertexBuffer.unbind();
        }
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
        if (settledBytes != null) {
            settledBytes.close();
            settledBytes = null;
        }
        if (changingBytes != null) {
            changingBytes.close();
            changingBytes = null;
        }
        builtVersion = -1;
        settledQuads = 0;
        changingQuads = 0;
    }
}
