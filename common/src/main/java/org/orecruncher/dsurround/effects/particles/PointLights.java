// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.LevelProjection;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.QuadWriter;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Small point lights, lighting the grass, leaves and ground near them: around fireflies, and the flicker of a flame
 * jet on its surroundings. Each is drawn with a shader that works out from the scene's depth where each pixel near the
 * light is, and adds light by how close it is and whether it faces the light. Being added on top of the scene, it
 * doesn't change Minecraft's lighting, and it can't cast shadows.
 * <p>
 * Particles {@link #add} their light as they are extracted for drawing, and the lights are all drawn together just
 * once the level's translucent things are ({@link #draw}), in a pass of their own (see SoftParticles.renderWorld).
 * <p>
 * Without the shader ({@link #SHADER}), or while an Iris shader pack is in use, there are no lights. Everything here
 * runs on the render thread.
 */
public final class PointLights {

    private static final String POINT_LIGHT_INFO = "PointLightInfo";
    private static final int POINT_LIGHT_INFO_SIZE = new Std140SizeCalculator().putMat4f().putFloat().get();
    private static final Matrix4f INV_PROJECTION = new Matrix4f();
    private static final BindGroupLayout POINT_LIGHT_LAYOUT = BindGroupLayout.builder()
            .withUniform(POINT_LIGHT_INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(SoftParticles.DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // The shader finds what each light reaches itself, so nothing is tested against depth; and its squares may face
    // either way. The shader gives its light already scaled by its strength, and both are simply added.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/point_light"))
            .withVertexShader(Constants.asId("core/point_light"))
            .withFragmentShader(Constants.asId("core/point_light"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
            .withBindGroupLayout(BindGroupLayouts.PROJECTION)
            .withBindGroupLayout(POINT_LIGHT_LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "point lights", "fireflies and flame jets will not light their surroundings", Library.LOGGER);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Point Lights");

    /**
     * How far a firefly's light reaches, in blocks.
     */
    public static final float FIREFLY_RADIUS = 1.75F;
    /**
     * Strength of a firefly's light at its brightest, 0 to 1.
     */
    public static final float FIREFLY_STRENGTH = 0.45F;

    // Lights recorded longer ago than this weren't from this frame (nothing lit was drawn), so are dropped
    private static final long STALE_NANOS = 250_000_000L;
    private static final int STRIDE = PointLightFrame.STRIDE;

    // As PointLightFrame lays them out
    private static float[] lights = new float[STRIDE * 64];
    // Which of them are drawn
    private static final PointLightFrame FRAME = new PointLightFrame();
    private static int count;
    private static long lastAdded;
    // Run just before the lights are drawn, to add lights gathered during the frame (see addSource)
    private static final List<Runnable> SOURCES = new CopyOnWriteArrayList<>();

    /**
     * Registers something that adds its lights just before they are drawn, rather than as it goes: to pick the ones
     * worth drawing once all of the frame's are known (burning mobs add theirs this way; nothing caps them beyond the
     * {@link PointLightFrame#MAX_DRAWN} nearest lights of the frame). Render thread.
     */
    public static void addSource(Runnable source) {
        SOURCES.add(source);
    }

    // Created on first use, on the render thread
    @Nullable
    private static QuadWriter quads;
    private static final MeshDraw MESH_DRAW = new MeshDraw("point lights");
    // What it holds doesn't change while the device lives: made once, with no MappableRingBuffer (rotating one sets a
    // fence, which held the GPU idle for 2 ms a frame after the aurora on 1.21.11, AMD)
    @Nullable
    private static GpuBuffer lightInfo;

    private PointLights() {
    }

    /**
     * Whether lights can be drawn: the shader compiled, drawing hasn't failed, and no shader pack is in use. Each kind
     * of light also has its own option.
     */
    public static boolean isAvailable() {
        return SHADER.isUsable();
    }

    /**
     * Whether fireflies light their surroundings
     */
    public static boolean fireflyLightsOn() {
        return Client.Config.fireflyOptions.enableLight && isAvailable();
    }

    /**
     * Whether flame jets light their surroundings
     */
    public static boolean flameLightsOn() {
        return Client.Config.flameJetOptions.enableLight && isAvailable();
    }

    /**
     * Whether any kind of light is being drawn
     */
    public static boolean isActive() {
        return fireflyLightsOn() || flameLightsOn();
    }

    /**
     * The frame is done: any lights not drawn (no translucent particles were) are dropped, not carried into the next,
     * where they would be placed from where the camera was
     */
    public static void endFrame() {
        count = 0;
    }

    /**
     * Whether there are lights waiting to be drawn.
     */
    public static boolean hasLights() {
        return count > 0;
    }

    /**
     * Records a light to draw. Kept relative to the camera, which is where it is drawn from this frame: far from the
     * world's middle, a float can't hold a position in the world finely enough, and the lights would jitter.
     *
     * @param x        where, in the world
     * @param strength 0 to 1
     * @param radius   how far it reaches, in blocks
     */
    public static void add(double x, double y, double z, float red, float green, float blue, float strength, float radius) {
        if (strength < PointLightFrame.MIN_STRENGTH || radius <= 0F)
            return;
        if ((count + 1) * STRIDE > lights.length)
            lights = Arrays.copyOf(lights, lights.length * 2);
        int i = count * STRIDE;
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        lights[i] = (float) (x - camera.x);
        lights[i + 1] = (float) (y - camera.y);
        lights[i + 2] = (float) (z - camera.z);
        lights[i + 3] = red;
        lights[i + 4] = green;
        lights[i + 5] = blue;
        lights[i + 6] = strength;
        lights[i + 7] = radius;
        count++;
        lastAdded = System.nanoTime();
    }

    /**
     * Draws the lights recorded since the last call into {@code target}, then forgets them. Called outside any render
     * pass, after the scene's depth has been captured.
     *
     * @param transforms the camera's, as the particles are drawn with them (see SoftParticles.renderWorld)
     */
    public static void draw(RenderTarget target, GpuBufferSlice transforms) {
        // Lights gathered elsewhere during the frame, to be added now that it is known which are wanted
        for (int i = 0; i < SOURCES.size(); i++)
            SOURCES.get(i).run();
        int n = count;
        count = 0;
        if (n == 0 || System.nanoTime() - lastAdded > STALE_NANOS || !isAvailable() || !SoftParticles.hasSceneDepth())
            return;
        RENDER_TIMER.begin();
        SHADER.run(() -> drawLights(target, n, transforms), () -> {});
        RENDER_TIMER.end();
    }

    private static void drawLights(RenderTarget target, int n, GpuBufferSlice transforms) {
        var device = RenderSystem.getDevice();

        FRAME.select(lights, n);
        if (FRAME.count() == 0)
            return;
        if (quads == null)
            quads = new QuadWriter(QuadWriter.Format.POSITION_TEX_COLOR);
        quads.begin(FRAME.count());
        for (int l = 0; l < FRAME.count(); l++) {
            float x = FRAME.value(l, 0), y = FRAME.value(l, 1), z = FRAME.value(l, 2);
            // All four at the center; the vertex shader spreads them out. Each corner is given at the light's
            // radius, which is how the shader knows it.
            float radius = FRAME.value(l, 7);
            int color = QuadWriter.color(FRAME.value(l, 3), FRAME.value(l, 4), FRAME.value(l, 5), FRAME.value(l, 6));
            quads.vertex(x, y, z, -radius, -radius, color);
            quads.vertex(x, y, z, radius, -radius, color);
            quads.vertex(x, y, z, radius, radius, color);
            quads.vertex(x, y, z, -radius, radius, color);
        }

        // The projection's inverse, worked out once a frame here rather than for each pixel of each light in the shader
        if (lightInfo == null)
            lightInfo = device.createBuffer(() -> "Dynamic Surroundings point light UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, POINT_LIGHT_INFO_SIZE);
        var zeroToOne = device.getDeviceInfo().isZZeroToOne();
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, POINT_LIGHT_INFO_SIZE)
                    .putMat4f(INV_PROJECTION.set(LevelProjection.get()).invert())
                    .putFloat(zeroToOne ? 1F : 0F)
                    .get();
            device.createCommandEncoder().writeToBuffer(lightInfo.slice(), data);
        }

        var info = lightInfo;
        MESH_DRAW.draw(quads, PIPELINE, target, transforms, pass -> {
            SoftParticles.bindSceneDepth(pass);
            pass.setUniform(POINT_LIGHT_INFO, info);
        });
    }
}
