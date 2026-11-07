// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.config.AuroraQuality;
import org.orecruncher.dsurround.effects.LevelProjection;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.ScreenPasses;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.Optional;

/**
 * Draws a {@link RealisticAurora}: a pass over the whole screen, after the stars, that follows each pixel's line of
 * sight up through the layer of the sky where auroras shine, adding the light of each arc it passes through, and of its
 * ray field if it has one. The light adds to the sky behind it; the world, drawn after the sky, covers it.
 * <p>
 * An arc is a sheet a few kilometers thick, far thinner than any step along a line of sight hundreds of kilometers
 * long, so the shader doesn't sample the light along the way: it steps along looking for where the line crosses each
 * arc, and adds what it sees there all at once. How much depends on how much of the sheet the line goes through: a
 * sheet seen edge on is far brighter than one seen face on, as a real one is. The ray field, a belt of separate rays
 * along the magnetic field, is sampled along the way. See shaders/core/aurora_realistic.fsh.
 * <p>
 * At {@link AuroraQuality#FANCY} it is drawn straight onto the sky. At {@link AuroraQuality#FAST} it is drawn at half
 * the screen's size, to a target of its own, then laid over the sky ({@link #COMPOSITE}), read smoothly between its
 * pixels.
 * <p>
 * The colors are the light the upper air gives off: green from oxygen low in the arc, red from oxygen high above it,
 * and a pink fringe at its lower edge from nitrogen. Render thread only.
 */
public final class RealisticAuroraRenderer {

    // The shader's uniforms: the matrix turning pixels into directions, three colors, the frame's values, how the light is given off, how active it is and which
    // way magnetic north is, the ray field, the sunlit tops, and the arcs (four vec4s each, in four arrays)
    private static final String INFO = "RealisticAuroraInfo";
    private static final int INFO_SIZE;
    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final String AURORA_SAMPLER = "AuroraSampler";
    private static final BindGroupLayout INFO_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(NOISE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final BindGroupLayout COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withUniform(AURORA_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();
    private static final Vector4f CLEAR = new Vector4f(0F, 0F, 0F, 0F);

    static {
        var size = new Std140SizeCalculator().putMat4f();
        for (int i = 0; i < RealisticAuroraFrame.VECTORS; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    // Light adds to what is there: the sky, or (at FAST) the cleared target
    private static final BlendFunction ADD = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);

    // A triangle over the whole of what it draws to, from the vertex's index alone. Nothing is tested against or
    // written to the depth buffer
    private static final RenderPipeline PIPELINE = ScreenPasses.pipeline("pipeline/aurora_realistic", "core/aurora_realistic", "core/aurora_realistic")
            .withBindGroupLayout(INFO_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .build();

    // Lays the aurora, drawn at a share of the screen's size, over the sky
    private static final RenderPipeline COMPOSITE_PIPELINE = ScreenPasses.pipeline("pipeline/aurora_composite", "core/aurora_composite", "core/aurora_composite")
            .withBindGroupLayout(COMPOSITE_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the realistic aurora", "realistic auroras will not be drawn", Library.LOGGER);
    public static final ModShader COMPOSITE = new ModShader(COMPOSITE_PIPELINE, "the realistic aurora", "realistic auroras will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, COMPOSITE);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Aurora (realistic)");

    /**
     * The noise texture's size, in cells, which it repeats at. The rays' finest noise has a cell to {@link #FINE_RAY}
     * kilometers along an arc, so their pattern repeats every few hundred kilometers; the coarser noise over it repeats
     * at a different length, which hides it.
     */
    public static final int NOISE_SIZE = 512;
    /**
     * Kilometers along an arc to a cell of the rays' finest noise (in the shader, FINE_RAY)
     */
    public static final float FINE_RAY = 0.7F;
    /**
     * Kilometers along an arc to a cell of the rays' coarser noise (in the shader, COARSE_RAY)
     */
    public static final float COARSE_RAY = 2.3F;
    /**
     * Kilometers across the ground to a cell of the ray field's noise, which places its rays (in the shader, FIELD_CELL)
     */
    public static final float FIELD_CELL = 3F;

    @Nullable
    private static DynamicTexture noise;
    // One buffer rewritten each frame, not a MappableRingBuffer: rotating that one sets a fence, which held the GPU idle
    // for 2 ms a frame after the classic aurora on 1.21.11 (AMD)
    @Nullable
    private static GpuBuffer info;
    // Where it is drawn at FAST, at a share of the screen's size
    private static final ScreenPasses.Reduced REDUCED = new ScreenPasses.Reduced("Dynamic Surroundings aurora (realistic)");

    private RealisticAuroraRenderer() {
    }

    /**
     * Whether the realistic aurora can be drawn: its pipelines compiled, and drawing hasn't failed since
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable() && COMPOSITE.isAvailable();
    }

    /**
     * Draws the aurora.
     *
     * @param aurora     what to draw
     * @param moment     how it is at this point in its night
     * @param motion     where its waves, curls and rays have moved to
     * @param quality    how finely
     * @param shadow     how high the Earth's shadow is over the viewer, in kilometers (see
     *                   {@link RealisticAurora#shadowHeight}): the tops of the rays above it are sunlit
     * @param washout    how much moonlight washes out its faint parts (see {@link RealisticAurora#moonWashout})
     * @param alpha      how strongly, 0 to 1
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time       seconds, for the animation
     */
    public static void render(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                              AuroraQuality quality, float shadow, float washout, float alpha, Matrix4f viewMatrix, float time) {
        if (alpha <= 0F || !isAvailable())
            return;
        RENDER_TIMER.begin();
        try {
            SHADER.run(() -> draw(aurora, moment, motion, quality, shadow, washout, alpha, viewMatrix, time), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                             AuroraQuality quality, float shadow, float washout, float alpha, Matrix4f viewMatrix, float time) {
        var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var fast = quality.scale() > 1;
        RenderTarget target = fast ? REDUCED.of(screen, quality.scale()) : screen;
        pack(RealisticAuroraFrame.of(aurora, moment, motion, quality, shadow, washout, alpha, LevelProjection.get(), viewMatrix,
                time, target.height));

        // Made the first time it is used, which can't be while a render pass is open
        var noiseView = noiseTexture().getTextureView();
        var block = info;
        ScreenPasses.draw(() -> "Dynamic Surroundings aurora (realistic)", target.getColorTextureView(),
                fast ? Optional.of(CLEAR) : Optional.empty(), PIPELINE, pass -> {
                    pass.setUniform(INFO, block);
                    pass.setUniform(NOISE_SAMPLER, noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                });

        if (fast) {
            COMPOSITE.run(() -> ScreenPasses.draw(() -> "Dynamic Surroundings aurora (realistic) composite", screen.getColorTextureView(),
                    Optional.empty(), COMPOSITE_PIPELINE,
                    pass -> pass.setUniform(AURORA_SAMPLER, target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))),
                    () -> {});
        }
    }

    /**
     * Writes the frame's values into the uniform block (see shaders/include/aurora_realistic.glsl)
     */
    private static void pack(RealisticAuroraFrame frame) {
        if (info == null)
            info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings aurora (realistic) UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var v = frame.vec4s();
        try (var stack = MemoryStack.stackPush()) {
            var builder = Std140Builder.onStack(stack, INFO_SIZE).putMat4f(frame.invViewProj());
            for (int i = 0; i < v.length; i += 4)
                builder.putVec4(v[i], v[i + 1], v[i + 2], v[i + 3]);
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(), builder.get());
        }
    }

    private static DynamicTexture noiseTexture() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x7261797DL);
        return noise;
    }
}
