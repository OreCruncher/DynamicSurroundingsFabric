// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;

/**
 * The pipelines particle rain and snow are drawn with. All fade what they draw into the fog. Kept apart from
 * {@link PrecipitationField} and {@link DampSpots}, so the drops and spots can be tested without the game.
 */
public final class PrecipitationShaders {

    /**
     * How far in front of the ground, in blocks, a puff of dust is at full strength: closer, it fades, so a puff
     * partly in the ground blends into it rather than showing a hard edge
     */
    static final float DUST_SOFT_DISTANCE = 1.5F;

    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    // What is drawn there is scaled by one less the spot's alpha
    private static final BlendFunction DARKEN = new BlendFunction(BlendFactor.ZERO, BlendFactor.ONE_MINUS_SRC_ALPHA);

    // The rain, snow, snow flecks and window streaks: lit by the lightmap, the snow textured, seen from both sides
    private static final RenderPipeline DROPS_PIPELINE = drops("pipeline/precipitation").withDepthStencilState(TEST_ONLY).build();
    // The dust: as the drops, but soft, reading a copy of the scene's depth
    private static final RenderPipeline DUST_PIPELINE = dust("pipeline/precipitation_dust").withDepthStencilState(TEST_ONLY).build();
    // Damp spots: darkening the ground drawn under them
    private static final RenderPipeline DAMP_SPOTS_PIPELINE = dampSpots("pipeline/damp_spot")
            .withColorTargetState(new ColorTargetState(DARKEN))
            .withDepthStencilState(TEST_ONLY)
            .build();

    public static final ModShader DROPS = new ModShader(DROPS_PIPELINE, "particle rain and snow", "vanilla's rain and snow will be drawn instead", Library.LOGGER);
    public static final ModShader DUST = new ModShader(DUST_PIPELINE, "soft dust", "dust will be drawn without it", Library.LOGGER);
    public static final ModShader DAMP_SPOTS = new ModShader(DAMP_SPOTS_PIPELINE, "damp spots", "rain will not leave damp spots", Library.LOGGER);

    // The rain and snow with their quads made on the GPU from the packed drops (see GpuDrops): no vertex buffer, the
    // drops in a texel buffer, the same fragment shader
    static final String DROP_INFO = "DropInfo";
    static final String DROP_RECORDS = "Drops";
    private static final BindGroupLayout GPU_DROPS_LAYOUT = BindGroupLayout.builder()
            .withUniform(DROP_INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(DROP_RECORDS, UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .build();
    private static final RenderPipeline GPU_DROPS_PIPELINE = gpuDrops("pipeline/precipitation_gpu").withDepthStencilState(TEST_ONLY).build();
    // The dust made on the GPU, soft as the CPU's is (without it, the dust made on the GPU is drawn hard, with the above)
    private static final RenderPipeline GPU_DUST_PIPELINE = gpuDrops("pipeline/precipitation_gpu_dust")
            .withShaderDefine("SOFT_DISTANCE", DUST_SOFT_DISTANCE)
            .withBindGroupLayout(SoftParticles.SOFT_DEPTH_LAYOUT)
            .withDepthStencilState(TEST_ONLY)
            .build();
    public static final ModShader GPU_DROPS = new ModShader(GPU_DROPS_PIPELINE, "rain, snow and dust built on the GPU", "their quads will be built on the CPU", Library.LOGGER);
    public static final ModShader GPU_DUST = new ModShader(GPU_DUST_PIPELINE, "soft dust built on the GPU", "dust built on the GPU will be drawn without it", Library.LOGGER);

    // What the drops leave where they land, made on the GPU (see GpuMarks): the snow flecks and window streaks as the
    // drops are drawn, the damp spots darkening the ground
    static final String MARK_INFO = "MarkInfo";
    static final String MARK_RECORDS = "Marks";
    private static final BindGroupLayout GPU_MARKS_LAYOUT = BindGroupLayout.builder()
            .withUniform(MARK_INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(MARK_RECORDS, UniformType.TEXEL_BUFFER, GpuFormat.RGBA32_FLOAT)
            .build();
    private static final RenderPipeline GPU_MARKS_PIPELINE = gpuMarks("pipeline/precipitation_marks", "core/precipitation")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
            .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
            .withDepthStencilState(TEST_ONLY)
            .build();
    private static final RenderPipeline GPU_SPOTS_PIPELINE = gpuMarks("pipeline/precipitation_marks_spots", "core/damp_spot")
            .withShaderDefine("SPOTS")
            .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
            .withColorTargetState(new ColorTargetState(DARKEN))
            .withDepthStencilState(TEST_ONLY)
            .build();
    public static final ModShader GPU_MARKS = new ModShader(GPU_MARKS_PIPELINE, "snow flecks and window streaks built on the GPU", "they will be built on the CPU", Library.LOGGER);
    public static final ModShader GPU_SPOTS = new ModShader(GPU_SPOTS_PIPELINE, "damp spots built on the GPU", "they will be built on the CPU", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(DROPS, DUST, DAMP_SPOTS, GPU_DROPS, GPU_DUST, GPU_MARKS, GPU_SPOTS);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Precipitation");
    // The weather's pass waiting for the quads built ahead (DropMeshes.await): a thread starved of the CPU shows here
    public static final RenderTimer WAIT_TIMER = new RenderTimer("- Waiting for Quads");
    public static final RenderTimer DAMP_SPOTS_TIMER = new RenderTimer("- Damp Spots");
    public static final RenderTimer DROPS_TIMER = new RenderTimer("- Drops");
    // Drawn among the drops, so within their time too
    public static final RenderTimer STREAKS_TIMER = new RenderTimer("- Window Streaks");
    // The rain and snow made on the GPU (GpuDrops), drawn among the drops, so within their time too: present in a
    // recording only for frames drawn that way
    public static final RenderTimer GPU_DROPS_TIMER = new RenderTimer("- Drops on the GPU");
    // The dust made on the GPU, likewise
    public static final RenderTimer GPU_DUST_TIMER = new RenderTimer("- Dust on the GPU");

    private PrecipitationShaders() {
    }

    /**
     * Whether the drops can be drawn: their pipeline compiled, drawing hasn't failed, and no shader pack is
     * in use. Without them, vanilla's weather is drawn.
     */
    public static boolean isAvailable() {
        return DROPS.isUsable();
    }

    private static RenderPipeline.Builder drops(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/precipitation"))
                .withFragmentShader(Constants.asId("core/precipitation"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false);
    }

    /**
     * The drops' pipeline with the quads made in the vertex shader, its constants those the CPU's build uses
     */
    private static RenderPipeline.Builder gpuDrops(String location) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/precipitation_gpu"))
                .withFragmentShader(Constants.asId("core/precipitation"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withBindGroupLayout(GPU_DROPS_LAYOUT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false);
        builder.withShaderDefine("MAX_FLAKES", GpuDrops.MAX_LOOKS)
                .withShaderDefine("KIND_RAIN", (float) PrecipitationField.RAIN)
                .withShaderDefine("KIND_SNOW", (float) PrecipitationField.SNOW)
                .withShaderDefine("KIND_DUST", (float) PrecipitationField.DUST)
                .withShaderDefine("DUST_FADE", (float) PrecipitationField.DUST_FADE)
                .withShaderDefine("DUST_COVER_TICKS", (float) PrecipitationField.DUST_COVER_TICKS)
                .withShaderDefine("DUST_MIN_HALF_SIZE", PrecipitationRenderer.DUST_MIN_HALF_SIZE)
                .withShaderDefine("DUST_HALF_SIZE_RANGE", PrecipitationRenderer.DUST_HALF_SIZE_RANGE)
                .withShaderDefine("DUST_ALPHA", PrecipitationRenderer.DUST_ALPHA)
                .withShaderDefine("DUST_PALENESS", PrecipitationRenderer.DUST_PALENESS)
                .withShaderDefine("DUST_NEAR_HIDDEN", PrecipitationRenderer.DUST_NEAR_HIDDEN)
                .withShaderDefine("DUST_NEAR_FADE", PrecipitationRenderer.DUST_NEAR_FADE)
                .withShaderDefine("RAIN_FADE_IN", (float) PrecipitationField.RAIN_FADE_IN)
                .withShaderDefine("SNOW_FADE_IN", (float) PrecipitationField.SNOW_FADE_IN)
                .withShaderDefine("RAIN_COVER_TICKS", (float) PrecipitationField.RAIN_COVER_TICKS)
                .withShaderDefine("SNOW_COVER_TICKS", (float) PrecipitationField.SNOW_COVER_TICKS)
                .withShaderDefine("THIN_FROM_SHARE", PrecipitationRenderer.THIN_FROM_SHARE)
                .withShaderDefine("THIN_KEEP_AT_EDGE", PrecipitationRenderer.THIN_KEEP_AT_EDGE)
                .withShaderDefine("THIN_FADE", PrecipitationRenderer.THIN_FADE)
                .withShaderDefine("EDGE_FADE", PrecipitationRenderer.EDGE_FADE)
                .withShaderDefine("NO_TEXTURE", PrecipitationRenderer.NO_TEXTURE)
                .withShaderDefine("SNOW_MIN_HALF_SIZE", PrecipitationRenderer.SNOW_MIN_HALF_SIZE)
                .withShaderDefine("SNOW_HALF_SIZE_RANGE", PrecipitationRenderer.SNOW_HALF_SIZE_RANGE)
                .withShaderDefine("SNOW_ALPHA", PrecipitationRenderer.SNOW_ALPHA)
                .withShaderDefine("RAIN_MIN_LENGTH", PrecipitationRenderer.RAIN_MIN_LENGTH)
                .withShaderDefine("RAIN_LENGTH_RANGE", PrecipitationRenderer.RAIN_LENGTH_RANGE)
                .withShaderDefine("RAIN_MIN_HALF_WIDTH", PrecipitationRenderer.RAIN_MIN_HALF_WIDTH)
                .withShaderDefine("RAIN_HALF_WIDTH_RANGE", PrecipitationRenderer.RAIN_HALF_WIDTH_RANGE)
                .withShaderDefine("RAIN_MIN_ALPHA", PrecipitationRenderer.RAIN_MIN_ALPHA)
                .withShaderDefine("RAIN_ALPHA_RANGE", PrecipitationRenderer.RAIN_ALPHA_RANGE);
        return builder;
    }

    /**
     * The marks' pipeline with their quads made in the vertex shader, its constants those the CPU's builds use; the
     * caller adds the textures, the blend and the depth
     */
    private static RenderPipeline.Builder gpuMarks(String location, String fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/precipitation_marks"))
                .withFragmentShader(Constants.asId(fragmentShader))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(GPU_MARKS_LAYOUT)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withCull(false)
                .withShaderDefine("MAX_FLAKES", GpuDrops.MAX_LOOKS)
                .withShaderDefine("MARK_STREAKS", (float) GpuMarks.Kind.STREAKS.id)
                .withShaderDefine("FACE_WEST", (float) SnowFlecks.FACE_WEST)
                .withShaderDefine("FACE_EAST", (float) SnowFlecks.FACE_EAST)
                .withShaderDefine("FACE_NORTH", (float) SnowFlecks.FACE_NORTH)
                .withShaderDefine("FACE_SOUTH", (float) SnowFlecks.FACE_SOUTH)
                .withShaderDefine("NO_TEXTURE", PrecipitationRenderer.NO_TEXTURE)
                .withShaderDefine("SQRT_OF_TWO", Mth.SQRT_OF_TWO)
                .withShaderDefine("SNOW_MIN_HALF_SIZE", PrecipitationRenderer.SNOW_MIN_HALF_SIZE)
                .withShaderDefine("SNOW_HALF_SIZE_RANGE", PrecipitationRenderer.SNOW_HALF_SIZE_RANGE)
                .withShaderDefine("SNOW_ALPHA", PrecipitationRenderer.SNOW_ALPHA)
                .withShaderDefine("WHOLE_SHARE", SnowFlecks.WHOLE_SHARE)
                .withShaderDefine("MELTED_SIZE", SnowFlecks.MELTED_SIZE)
                .withShaderDefine("STREAK_FADE_IN", (float) WindowStreaks.FADE_IN)
                .withShaderDefine("STREAK_FADE_OUT", (float) WindowStreaks.FADE_OUT)
                .withShaderDefine("STREAK_BEAD_MIN_HALF_SIZE", PrecipitationRenderer.STREAK_BEAD_MIN_HALF_SIZE)
                .withShaderDefine("STREAK_BEAD_HALF_SIZE_RANGE", PrecipitationRenderer.STREAK_BEAD_HALF_SIZE_RANGE)
                .withShaderDefine("STREAK_BEAD_ALPHA", PrecipitationRenderer.STREAK_BEAD_ALPHA)
                .withShaderDefine("STREAK_TRAIL_WIDTH", PrecipitationRenderer.STREAK_TRAIL_WIDTH)
                .withShaderDefine("STREAK_TRAIL_ALPHA", PrecipitationRenderer.STREAK_TRAIL_ALPHA)
                .withShaderDefine("PEAK_DARKNESS", DampSpots.PEAK_DARKNESS)
                .withShaderDefine("DARKEN_TICKS", (float) DampSpots.DARKEN_TICKS);
    }

    private static RenderPipeline.Builder dust(String location) {
        return drops(location)
                .withShaderDefine("SOFT_DISTANCE", DUST_SOFT_DISTANCE)
                .withBindGroupLayout(SoftParticles.SOFT_DEPTH_LAYOUT);
    }

    private static RenderPipeline.Builder dampSpots(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/damp_spot"))
                .withFragmentShader(Constants.asId("core/damp_spot"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withCull(false);
    }
}
