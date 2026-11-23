// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.aurora;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
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
import org.orecruncher.dsurround.effects.FrameBlock;
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
 * Draws a {@link CinematicAurora}: a pass over the whole screen, after the stars, that follows each pixel's line of sight
 * up through the layer of the sky the ribbons hang in, adding the light of each ribbon it passes through. The light adds to the sky behind it; the world, drawn after the sky, covers it.
 * <p>
 * A ribbon is a soft sheet, its light falling away across it as a bell curve. The shader steps along the line of sight
 * and, for each step, adds how much of the sheet the line goes through on it, worked out exactly for a line crossing a
 * bell curve evenly (with the error function), so no step misses a ribbon however long it is, and a ribbon seen edge on
 * brightens of itself. A wider, fainter sheet around each gives it a violet halo. See shaders/core/aurora_cinematic.fsh.
 * <p>
 * At {@link AuroraQuality#FANCY} it is drawn straight onto the sky. At {@link AuroraQuality#FAST} it is drawn at half the
 * screen's size, to a target of its own, then laid over the sky with the realistic aurora's composite shader.
 * Render thread only.
 */
public final class CinematicAuroraRenderer {

    // The shader's uniforms: the matrix turning pixels into directions, the frame's values, and the ribbons (four vec4s
    // each, in four arrays)
    private static final String INFO = "CinematicAuroraInfo";
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

    private static final FrameBlock BLOCK = new FrameBlock("Dynamic Surroundings aurora (cinematic) UBO", CinematicAuroraFrame.VECTORS);

    // Light adds to what is there: the sky, or (at FAST) the cleared target
    private static final BlendFunction ADD = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);

    // A triangle over the whole of what it draws to, from the vertex's index alone. Nothing is tested against or
    // written to the depth buffer
    private static final RenderPipeline PIPELINE = ScreenPasses.pipeline("pipeline/aurora_cinematic", "core/aurora_cinematic", "core/aurora_cinematic")
            .withBindGroupLayout(INFO_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .build();

    // Lays the aurora, drawn at a share of the screen's size, over the sky: the realistic aurora's composite shader
    private static final RenderPipeline COMPOSITE_PIPELINE = ScreenPasses.pipeline("pipeline/aurora_cinematic_composite", "core/aurora_composite", "core/aurora_composite")
            .withBindGroupLayout(COMPOSITE_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the cinematic aurora", "cinematic auroras will not be drawn", Library.LOGGER);
    public static final ModShader COMPOSITE = new ModShader(COMPOSITE_PIPELINE, "the cinematic aurora", "cinematic auroras will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, COMPOSITE);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Aurora (cinematic)");

    /**
     * The noise texture's size, in cells, which it repeats at (in the shader, NOISE_SIZE)
     */
    public static final int NOISE_SIZE = 512;
    /**
     * Kilometers along a ribbon to a cell of the rays' finest noise (in the shader, FINE_RAY)
     */
    public static final float FINE_RAY = 1.2F;
    /**
     * Kilometers along a ribbon to a cell of the rays' coarser noise (in the shader, COARSE_RAY)
     */
    public static final float COARSE_RAY = 4F;

    @Nullable
    private static DynamicTexture noise;
    // Where it is drawn at FAST, at a share of the screen's size
    private static final ScreenPasses.Reduced REDUCED = new ScreenPasses.Reduced("Dynamic Surroundings aurora (cinematic)");

    private CinematicAuroraRenderer() {
    }

    /**
     * Whether the cinematic aurora can be drawn: its pipelines compiled, and drawing hasn't failed since
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable() && COMPOSITE.isAvailable();
    }

    /**
     * Draws the aurora.
     *
     * @param aurora     what to draw
     * @param moment     how active the night is now (its realistic aurora's moment)
     * @param motion     where its folds, wave and rays have moved to
     * @param quality    how finely
     * @param alpha      how strongly, 0 to 1
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time       seconds, for the animation
     */
    public static void render(CinematicAurora aurora, RealisticAurora.Moment moment, CinematicAuroraMotion motion,
                              AuroraQuality quality, float alpha, Matrix4f viewMatrix, float time) {
        if (alpha <= 0F || !isAvailable())
            return;
        RENDER_TIMER.begin();
        try {
            SHADER.run(() -> draw(aurora, moment, motion, quality, alpha, viewMatrix, time), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(CinematicAurora aurora, RealisticAurora.Moment moment, CinematicAuroraMotion motion,
                             AuroraQuality quality, float alpha, Matrix4f viewMatrix, float time) {
        var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var fast = quality.scale() > 1;
        RenderTarget target = fast ? REDUCED.of(screen, quality.scale()) : screen;
        BLOCK.write(CinematicAuroraFrame.of(aurora, moment, motion, alpha, LevelProjection.get(), viewMatrix, time, target.height));

        // Made the first time it is used, which can't be while a render pass is open
        var noiseView = noiseTexture().getTextureView();
        var block = BLOCK.buffer();
        ScreenPasses.draw(() -> "Dynamic Surroundings aurora (cinematic)", target.getColorTextureView(),
                fast ? Optional.of(CLEAR) : Optional.empty(), PIPELINE, pass -> {
                    pass.setUniform(INFO, block);
                    pass.setUniform(NOISE_SAMPLER, noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
                });

        if (fast) {
            COMPOSITE.run(() -> ScreenPasses.draw(() -> "Dynamic Surroundings aurora (cinematic) composite", screen.getColorTextureView(),
                    Optional.empty(), COMPOSITE_PIPELINE,
                    pass -> pass.setUniform(AURORA_SAMPLER, target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR))),
                    () -> {});
        }
    }

    private static DynamicTexture noiseTexture() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x63696E6DL);
        return noise;
    }
}
