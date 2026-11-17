// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.groundfog;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.config.GroundFogQuality;
import org.orecruncher.dsurround.effects.LevelProjection;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.ScreenPasses;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.Optional;

/**
 * Draws the ground fog as a volume: a pass over the screen that follows each pixel's line of sight from the camera
 * to what it shows, adding up the mist along the way. There is mist where the line passes within
 * {@link #MIST_DEPTH} blocks of the ground, thickest at the ground; how much depends on the biome, and drifting noise
 * breaks it into patches. So it thickens the more of it is looked through, fills hollows and pools at the foot of
 * cliffs, and has no edges to show.
 * <p>
 * That pass ({@link #SHADER}) is drawn at a share of the screen's size (half, for {@link GroundFogQuality#FANCY}), to
 * a target of its own: at half, a quarter of the pixels to follow. A second ({@link #SMOOTH}), at that size, blends
 * each pixel's neighbors, which smooths away the stipple of their steps being offset a little differently, but only
 * those that looked about as far, so the mist doesn't smear across the edges of what is in front of it. A third
 * ({@link #COMPOSITE}) lays it over the scene at full size, the same way between the two by two around each pixel.
 * Each is a triangle over the whole of what it draws to, with no vertex buffer; the passes share their values in one
 * uniform block ({@code GroundFogInfo}, shaders/include/ground_fog.glsl).
 * <p>
 * The grid ({@link GroundFogField}) is handed to the shader as a small texture, a texel a column: the ground's height
 * above a base just below the grid's lowest (smoothed, see {@link GroundFogField#smoothHeight}), the mist's thickness,
 * and the block and sky light there. Render thread only.
 */
public final class GroundFogVolume {

    private static final String INFO = "GroundFogInfo";
    // Makes the triangle each pass draws
    private static final String SCREEN_VERTEX = "core/ground_fog_screen";
    // The matrix, then the frame's vectors (see GroundFogFrame for how the block's vec3s and vec2s are laid out as them)
    private static final int INFO_SIZE;
    private static final String DEPTH_SAMPLER = "DepthSampler";
    private static final String FOG_GRID = "FogGrid";
    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final String MIST_SAMPLER = "MistSampler";
    private static final String DISTANCE_SAMPLER = "DistanceSampler";
    private static final BindGroupLayout MARCH_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(FOG_GRID, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(NOISE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final BindGroupLayout SMOOTH_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(MIST_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DISTANCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final BindGroupLayout COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(MIST_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .withUniform(DISTANCE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    // Far off, where nothing was drawn (no pixel is left so: the march writes every one)
    private static final Vector4f CLEAR_DISTANCE = new Vector4f(1.0e6F, 0F, 0F, 0F);
    private static final Vector4f CLEAR = new Vector4f(0F, 0F, 0F, 0F);

    static {
        var size = new Std140SizeCalculator().putMat4f();
        for (int i = 0; i < GroundFogFrame.VECTORS; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    /**
     * Draws the mist, at a share of the screen's size, replacing what was there: its color, already multiplied by its
     * alpha; and, to a target of its own, how far each pixel's line of sight goes, which
     * the smoothing and the composite read rather than work it out from the depth for each pixel around theirs.
     * Without it, there is none.
     */
    public static final ModShader SHADER = new ModShader(
            ScreenPasses.pipeline("pipeline/ground_fog_volume", SCREEN_VERTEX, "core/ground_fog_volume")
                    .withBindGroupLayout(BindGroupLayouts.FOG)
                    .withBindGroupLayout(BindGroupLayouts.SAMPLER2)
                    .withBindGroupLayout(MARCH_LAYOUT)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .withColorTargetState(1, new ColorTargetState(Optional.empty(), GpuFormat.R32_FLOAT, ColorTargetState.WRITE_RED))
                    .build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Smooths the mist, at the size it was drawn, replacing what was there. Without it, there is no ground fog either.
     */
    public static final ModShader SMOOTH = new ModShader(
            ScreenPasses.pipeline("pipeline/ground_fog_smooth", SCREEN_VERTEX, "core/ground_fog_smooth")
                    .withBindGroupLayout(SMOOTH_LAYOUT)
                    .withColorTargetState(ColorTargetState.DEFAULT)
                    .build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Lays the smoothed mist over the scene. Its color is already multiplied by its alpha, so "over". Without it, there
     * is no ground fog either.
     */
    public static final ModShader COMPOSITE = new ModShader(
            composite("pipeline/ground_fog_composite").withDepthStencilState(Optional.empty()).build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, SMOOTH, COMPOSITE);

    /**
     * How long the GPU takes over all the passes, for the diagnostics overlay, and over each: the march (with the depth
     * copy it reads), the smoothing and the composite
     */
    public static final RenderTimer RENDER_TIMER = new RenderTimer("Ground Fog");
    public static final RenderTimer MARCH_TIMER = new RenderTimer("- March");
    public static final RenderTimer SMOOTH_TIMER = new RenderTimer("- Smooth");
    public static final RenderTimer COMPOSITE_TIMER = new RenderTimer("- Composite");

    /**
     * How deep the mist is over the ground, in blocks: it thins to nothing at this height
     */
    static final float MIST_DEPTH = 1.8F;
    /**
     * How quickly the mist hides what is behind it, for each block of it looked through at full thickness
     */
    static final float THICKNESS = 0.5F;
    /**
     * Ground heights are kept in a byte, relative to a base this many blocks below the lowest ground in the grid: so
     * they fit wherever the camera is, high above it or deep below. Ground more than a byte's worth above the base (a
     * grid spanning a mountain's height) is held at the top.
     */
    private static final int HEIGHT_BASE_MARGIN = 2;
    /**
     * Where things are east-west and north-south is wrapped at this many blocks for the noise, to keep it to a float's
     * precision far from the world's middle. A whole number of times the noise texture repeats ({@link #NOISE_SIZE}
     * cells) for each of the shader's noises (1.75, 3.5, 7 and 11 blocks a cell), so a wrap lands on the same noise
     * and can't be seen.
     */
    public static final int NOISE_WRAP = 4620;
    /**
     * The noise texture's size, in cells, which it repeats at: a random value a texel, read smoothly between them.
     * One read for each noise in place of working out the four corners of its cell.
     */
    public static final int NOISE_SIZE = 60;

    @Nullable
    private static DynamicTexture grid;
    // The grid texture's image, which it owns and closes
    @Nullable
    private static NativeImage gridPixels;
    @Nullable
    private static DynamicTexture noise;
    private static int gridSize;
    // What is in the texture: the field's version it was filled from, and the base and highest ground under any mist
    // worked out then. Filled again only when the field has changed, which is at most once a tick
    private static int gridVersion;
    private static int gridBaseY;
    private static float gridHighest;
    // Where the mist is drawn, at a share of the screen's size: its color, already multiplied by its alpha
    private static final ScreenPasses.Reduced MARCHED = new ScreenPasses.Reduced("Dynamic Surroundings ground fog");
    // The same, smoothed
    private static final ScreenPasses.Reduced SMOOTHED = new ScreenPasses.Reduced("Dynamic Surroundings ground fog, smoothed");
    // How far each of the mist's pixels' line of sight goes, at the same size
    @Nullable
    private static GpuTexture distances;
    @Nullable
    private static GpuTextureView distancesView;
    // One buffer rewritten each frame, not a MappableRingBuffer: rotating that one sets a fence, which held the GPU idle
    // for 2 ms a frame after the aurora on 1.21.11 (AMD)
    @Nullable
    private static GpuBuffer info;

    private GroundFogVolume() {
    }

    /**
     * Whether the ground fog can be drawn: its pipelines compiled, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return SHADER.isUsable() && SMOOTH.isUsable() && COMPOSITE.isUsable();
    }

    /**
     * Draws the mist, reading a copy of the scene's depth to know how far each pixel's line of sight goes. Outside any
     * render pass.
     *
     * @param target   where it is drawn: the main target
     * @param quality  how finely: the share of the screen's size the mist is drawn at, and the most steps
     * @param strength how strong the mist is for the conditions, 0 to 1
     * @param driftX   how far the mist has drifted east-west, in blocks, and {@code driftZ} north-south
     * @param seconds  a running time, for the mist to churn
     * @return true if it was drawn, or there was nothing to draw
     */
    public static boolean render(GroundFogField field, Camera camera, RenderTarget target,
                                 GroundFogQuality quality, float strength,
                                 float driftX, float driftZ, float seconds) {
        if (field.isEmpty() || !isUsable())
            return true;

        RENDER_TIMER.begin();
        try {
            return SHADER.run(() -> draw(field, camera, target, quality, strength, driftX, driftZ, seconds), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(GroundFogField field, Camera camera, RenderTarget target,
                             GroundFogQuality quality, float strength, float driftX, float driftZ, float seconds) {
        if (grid == null || gridSize != field.size() || gridVersion != field.version()) {
            gridBaseY = Mth.floor(GroundFogFrame.lowestGround(field)) - HEIGHT_BASE_MARGIN;
            gridHighest = upload(field, gridBaseY);
            gridVersion = field.version();
        }

        // How far each pixel's line of sight goes: the scene's depth, water's surface included. All the passes read it
        if (!SoftParticles.captureDepth(target))
            return;

        int scale = quality.scale();
        var reduced = MARCHED.of(target, scale);
        var smoothedReduced = SMOOTHED.of(target, scale);
        var distance = distancesFor(reduced);
        var cam = camera.position();
        pack(GroundFogFrame.of(field, cam.x, cam.y, cam.z, gridBaseY, gridHighest, LevelProjection.get(), RenderSystem.getModelViewMatrixCopy(),
                target.width, target.height, quality, strength, driftX, driftZ, seconds, RenderSystem.getDevice().getDeviceInfo().isZZeroToOne()));
        // Made the first time it is used, which can't be while a render pass is open
        var noiseView = noiseTexture().getTextureView();
        var lightmap = GameUtils.getMC().gameRenderer.lightmap();
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);

        // The mist at a share of the screen's size, replacing what was there
        var gridView = grid.getTextureView();
        MARCH_TIMER.begin();
        try {
            ScreenPasses.draw(RenderPassDescriptor.builder(() -> "Dynamic Surroundings ground fog march")
                    .withColorAttachment(reduced.getColorTextureView(), Optional.of(CLEAR))
                    .withColorAttachment(distance, Optional.of(CLEAR_DISTANCE))
                    .withRenderArea(new RenderPass.RenderArea(0, 0, reduced.width, reduced.height)).build(), SHADER.pipeline(), pass -> {
                bindShared(pass);
                SoftParticles.bindSceneDepth(pass);
                // Read smoothly between columns, ground heights and thickness both: the mist rounds off over ledges
                pass.setUniform(FOG_GRID, gridView, linear);
                pass.setUniform(NOISE_SAMPLER, noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                pass.setUniform("Sampler2", lightmap, linear);
            });
        } finally {
            MARCH_TIMER.end();
        }

        // Smoothed at that size, replacing what was there
        SMOOTH_TIMER.begin();
        try {
            ScreenPasses.draw(() -> "Dynamic Surroundings ground fog smoothing", smoothedReduced.getColorTextureView(), Optional.of(CLEAR),
                    SMOOTH.pipeline(), pass -> {
                        bindShared(pass);
                        pass.setUniform(MIST_SAMPLER, reduced.getColorTextureView(), nearest);
                        pass.setUniform(DISTANCE_SAMPLER, distance, nearest);
                    });
        } finally {
            SMOOTH_TIMER.end();
        }

        // Laid over the scene at full size
        COMPOSITE_TIMER.begin();
        try {
            ScreenPasses.draw(() -> "Dynamic Surroundings ground fog composite", target.getColorTextureView(), Optional.empty(),
                    COMPOSITE.pipeline(), pass -> {
                        bindShared(pass);
                        pass.setUniform(MIST_SAMPLER, smoothedReduced.getColorTextureView(), nearest);
                        SoftParticles.bindSceneDepth(pass);
                        pass.setUniform(DISTANCE_SAMPLER, distance, nearest);
                    });
        } finally {
            COMPOSITE_TIMER.end();
        }
    }

    /**
     * Binds what all the passes read
     */
    private static void bindShared(RenderPass pass) {
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform(INFO, info);
    }

    /**
     * Writes the values the passes share this frame into the uniform block (see shaders/include/ground_fog.glsl)
     */
    private static void pack(GroundFogFrame frame) {
        if (info == null)
            info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings ground fog UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var v = frame.vec4s();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE).putMat4f(frame.invViewProj());
            for (int i = 0; i < v.length; i += 4)
                builder.putVec4(v[i], v[i + 1], v[i + 2], v[i + 3]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(), builder.get());
        }
    }

    private static RenderPipeline.Builder composite(String location) {
        return ScreenPasses.pipeline(location, SCREEN_VERTEX, "core/ground_fog_composite")
                .withBindGroupLayout(COMPOSITE_LAYOUT)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA));
    }

    /**
     * The texture the march writes each pixel's distance to, created or made again to match {@code reduced}'s size
     */
    private static GpuTextureView distancesFor(TextureTarget reduced) {
        if (distances != null && (distances.getWidth(0) != reduced.width || distances.getHeight(0) != reduced.height)) {
            distancesView.close();
            distances.close();
            distances = null;
        }
        if (distances == null) {
            var device = RenderSystem.getDevice();
            distances = device.createTexture(() -> "Dynamic Surroundings ground fog distances",
                    GpuTexture.USAGE_RENDER_ATTACHMENT | GpuTexture.USAGE_TEXTURE_BINDING, GpuFormat.R32_FLOAT,
                    reduced.width, reduced.height, 1, 1);
            distancesView = device.createTextureView(distances);
        }
        return distancesView;
    }

    /**
     * Puts the grid in the texture, a texel a column (see {@link GroundFogFrame#texel})
     *
     * @return the highest ground under any mist, in blocks
     */
    private static float upload(GroundFogField field, int baseY) {
        int size = field.size();
        var pixels = gridPixels;
        if (grid == null || pixels == null || gridSize != size) {
            if (grid != null)
                grid.close();
            pixels = new NativeImage(size, size, true);
            grid = new DynamicTexture(() -> "Dynamic Surroundings ground fog grid", pixels);
            gridPixels = pixels;
            gridSize = size;
        }

        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++)
                pixels.setPixelABGR(i, j, GroundFogFrame.texel(field, i, j, baseY));
        grid.upload();
        return GroundFogFrame.highestUnderMist(field);
    }

    /**
     * The noise texture, made the first time
     */
    private static DynamicTexture noiseTexture() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6D697374L);
        return noise;
    }
}
