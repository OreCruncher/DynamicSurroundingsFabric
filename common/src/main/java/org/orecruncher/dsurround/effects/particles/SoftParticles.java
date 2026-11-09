// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * "Soft" particles: drawn with a shader that fades a particle out as it nears whatever is behind it, so where it
 * passes into terrain or the surface of water it blends away instead of ending in a hard line. The shader needs the
 * scene's depth, so a copy of it is made just before translucent particles are drawn ({@link #captureSceneDepth}),
 * and bound when a soft layer's pipeline is set ({@link #bind}); see MixinQuadParticleFeatureRenderer.
 * <p>
 * If the shader isn't available, because it failed to compile or an Iris shader pack is in use (which replaces the
 * rendering, so a shader of ours wouldn't fit in), the particles are drawn with vanilla's particle shader instead.
 * <p>
 * With Improved Transparency on, a particle is drawn in three stages, each its own pipeline ({@link OitPipelineSet}):
 * soft, and adding their light where they should, through the same shader. Those stages' depth has to be copied before
 * they start ({@link #captureBeforeTransparency}), as what they leave has the particles in it.
 * <p>
 * Everything here runs on the render thread.
 */
public final class SoftParticles {

    static final String DEPTH_SAMPLER = "DepthSampler";
    private static final String SOFT_PARTICLE_INFO = "SoftParticleInfo";
    // Whether clip space depth runs 0 to 1 (1) or -1 to 1 (0), which the shader needs to turn depth into distance
    private static final int DEPTH_INFO_SIZE = new Std140SizeCalculator().putFloat().get();
    /**
     * The depth copy and its information, as a soft shader reads them (DepthSampler, SoftParticleInfo); bound by
     * {@link #bindSoftDepth}
     */
    public static final BindGroupLayout SOFT_DEPTH_LAYOUT = BindGroupLayout.builder()
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withUniform(DEPTH_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER)
            .build();

    // Light added to what is behind, as much as the particle is opaque
    private static final BlendFunction ADDITIVE = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE);
    // Vanilla's translucent particles, with depth writes off so the puffs don't hide each other
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    /**
     * Waterfall mist: small puffs, so a short fade (0.4 blocks), or a puff near the water would be mostly faded out.
     * The distance is part of the pipeline; another distance would need a pipeline of its own.
     */
    private static final RenderPipeline MIST_PIPELINE = particlePipeline("pipeline/waterfall_mist", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline MIST_FALLBACK_PIPELINE = particlePipeline("pipeline/waterfall_mist_vanilla", null).build();

    /**
     * Tongues of flame, sparks and molten droplets: soft, with a shorter fade (0.3 blocks), and adding their light to
     * what is behind them, so overlapping flames burn brighter.
     */
    private static final RenderPipeline FLAME_PIPELINE = particlePipeline("pipeline/flame", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADDITIVE))
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline FLAME_FALLBACK_PIPELINE = particlePipeline("pipeline/flame_vanilla", null)
            .withColorTargetState(new ColorTargetState(ADDITIVE))
            .build();

    /**
     * Flames in a skull's eyes: drawn as the other flames are, but they burn a few hundredths of a block in front of
     * the face, which the flames' fade would all but hide when the skull is seen from the front. Drawn the vanilla way,
     * they use the flames' fallback.
     */
    private static final RenderPipeline EYE_FLAME_PIPELINE = particlePipeline("pipeline/eye_flame", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.02F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADDITIVE))
            .build();

    // The same again for Improved Transparency's stages, and the flames' way of drawing the vanilla way there
    private static final OitPipelineSet MIST_OIT = softOit("waterfall_mist", 0.4F, false);
    private static final OitPipelineSet FLAME_OIT = softOit("flame", 0.3F, true);
    private static final OitPipelineSet EYE_FLAME_OIT = softOit("eye_flame", 0.02F, true);
    private static final OitPipelineSet FLAME_FALLBACK_OIT = oitPipelines("flame_vanilla", RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET).withShaderDefine("OIT_ADDITIVE"));

    public static final ModShader SHADER = new ModShader(MIST_PIPELINE, "soft particles", "particles will be drawn without it", Library.LOGGER);
    public static final ModShader FLAME_SHADER = new ModShader(FLAME_PIPELINE, "soft flames", "flames will be drawn without it", Library.LOGGER);
    public static final ModShader EYE_FLAME_SHADER = new ModShader(EYE_FLAME_PIPELINE, "soft skull flames", "skull flames will be drawn without it", Library.LOGGER);

    public static final SingleQuadParticle.Layer WATERFALL_MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE, MIST_OIT);
    public static final SingleQuadParticle.Layer WATERFALL_MIST_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_FALLBACK_PIPELINE, RenderPipelines.OIT_PARTICLE);
    public static final SingleQuadParticle.Layer FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_PIPELINE, FLAME_OIT);
    public static final SingleQuadParticle.Layer FLAME_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_FALLBACK_PIPELINE, FLAME_FALLBACK_OIT);
    public static final SingleQuadParticle.Layer EYE_FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, EYE_FLAME_PIPELINE, EYE_FLAME_OIT);

    // Each soft pipeline's vanilla equivalent, for when it can't be used
    private static final Map<RenderPipeline, RenderPipeline> FALLBACKS = new HashMap<>();
    // The stages' shaders, compiled with the others; an effect's layer is chosen as it spawns, a stage's as it draws
    private static final Map<RenderPipeline, ModShader> OIT_SHADERS = new HashMap<>();

    static {
        FALLBACKS.put(MIST_PIPELINE, MIST_FALLBACK_PIPELINE);
        FALLBACKS.put(FLAME_PIPELINE, FLAME_FALLBACK_PIPELINE);
        FALLBACKS.put(EYE_FLAME_PIPELINE, FLAME_FALLBACK_PIPELINE);
        addOitFallbacks(MIST_OIT, RenderPipelines.OIT_PARTICLE, "soft particles");
        addOitFallbacks(FLAME_OIT, FLAME_FALLBACK_OIT, "soft flames");
        addOitFallbacks(EYE_FLAME_OIT, FLAME_FALLBACK_OIT, "soft skull flames");

        // The fallbacks draw while a shader pack is in use, with the pack's program for translucent particles
        IrisCompat.assignPipeline(MIST_FALLBACK_PIPELINE, IrisCompat.Program.PARTICLES_TRANSLUCENT);
        IrisCompat.assignPipeline(FLAME_FALLBACK_PIPELINE, IrisCompat.Program.PARTICLES_TRANSLUCENT);
    }

    // The copy of the scene's depth the shaders read; can't be the depth buffer being drawn to
    private static final DepthCopy DEPTH_COPY = new DepthCopy("Dynamic Surroundings scene depth");
    // Whether the copy holds a frame's depth: the last frame's as particles are drawn, this one's after them
    private static boolean depthCaptured;
    // What it holds doesn't change while the device lives: made once, with no MappableRingBuffer (rotating one sets a
    // fence, which held the GPU idle for 2 ms a frame after the aurora on 1.21.11, AMD)
    @Nullable
    private static GpuBuffer depthInfo;

    private SoftParticles() {
    }

    /**
     * The stages of a soft particle, for Improved Transparency: vanilla's translucent particle's, with our shader
     */
    private static OitPipelineSet softOit(String name, float softDistance, boolean additive) {
        var shader = Constants.asId("core/soft_particle");
        var builder = RenderPipeline.builder(RenderPipelines.OIT_PARTICLE_SNIPPET)
                .withVertexShader(shader)
                .withFragmentShader(shader)
                .withShaderDefine("SOFT_DISTANCE", softDistance)
                .withBindGroupLayout(SOFT_DEPTH_LAYOUT);
        if (additive)
            builder.withShaderDefine("OIT_ADDITIVE");
        return oitPipelines(name, builder);
    }

    /**
     * A pipeline for each of Improved Transparency's stages, named as ours are (OitPipelineSet.Builder puts them in
     * vanilla's namespace)
     */
    private static OitPipelineSet oitPipelines(String name, RenderPipeline.Builder builder) {
        var base = builder.buildSnippet();
        return new OitPipelineSet(
                oitStage(base, RenderPipelines.OIT_DEPTH_BOUNDS_SNIPPET, "pipeline/oit_depth_bounds_" + name),
                oitStage(base, RenderPipelines.OIT_TRANSMITTANCE_SNIPPET, "pipeline/oit_transmittance_" + name),
                oitStage(base, RenderPipelines.OIT_ACCUMULATE_SNIPPET, "pipeline/oit_accumulate_" + name));
    }

    private static RenderPipeline oitStage(RenderPipeline.Snippet base, RenderPipeline.Snippet stage, String location) {
        return RenderPipeline.builder(base, stage).withLocation(Constants.asId(location)).build();
    }

    private static void addOitFallbacks(OitPipelineSet soft, OitPipelineSet fallback, String effect) {
        for (var stage : List.of(OitStage.DEPTH_BOUNDS, OitStage.TRANSMITTANCE, OitStage.ACCUMULATE)) {
            var pipeline = soft.getPipeline(stage);
            FALLBACKS.put(pipeline, fallback.getPipeline(stage));
            OIT_SHADERS.put(pipeline, new ModShader(pipeline, effect, "they will be drawn without it with Improved Transparency on", Library.LOGGER));
        }
    }

    /**
     * The shaders of Improved Transparency's stages, for {@link org.orecruncher.dsurround.effects.ModShaders}
     */
    public static List<ModShader> oitShaders() {
        return List.copyOf(OIT_SHADERS.values());
    }

    /**
     * Whether a layer has to be drawn after the others: puffs write no depth, so what is drawn after them lands on top,
     * whatever is really nearer
     */
    public static boolean drawsLast(SingleQuadParticle.Layer layer) {
        return layer == WATERFALL_MIST || layer == WATERFALL_MIST_FALLBACK;
    }

    /**
     * A translucent particle pipeline: vanilla's particle shader unless {@code shader} names another.
     */
    static RenderPipeline.Builder particlePipeline(String location, @Nullable net.minecraft.resources.Identifier shader) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withBindGroupLayout(BindGroupLayouts.FOG)
                .withBindGroupLayout(BindGroupLayouts.SAMPLER0_SAMPLER2)
                .withVertexBinding(0, DefaultVertexFormat.PARTICLE)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
                .withDepthStencilState(TEST_ONLY);
        if (shader != null)
            builder.withVertexShader(shader).withFragmentShader(shader);
        else
            builder.withVertexShader("core/particle").withFragmentShader("core/particle");
        return builder;
    }

    /**
     * The layer for puffs of mist, steam and smoke: soft if the shader can be used, otherwise drawn the vanilla way.
     */
    public static SingleQuadParticle.Layer mistLayer() {
        return soft(SHADER) ? WATERFALL_MIST : WATERFALL_MIST_FALLBACK;
    }

    /**
     * The layer for flames, sparks and molten droplets: soft if the shader can be used, otherwise drawn the vanilla
     * way; either way adding their light.
     */
    public static SingleQuadParticle.Layer flameLayer() {
        return soft(FLAME_SHADER) ? FLAME : FLAME_FALLBACK;
    }

    /**
     * The layer for flames in a skull's eyes: as {@link #flameLayer}, with a short fade
     */
    public static SingleQuadParticle.Layer eyeFlameLayer() {
        return soft(EYE_FLAME_SHADER) ? EYE_FLAME : FLAME_FALLBACK;
    }

    private static boolean soft(ModShader shader) {
        return shader.isUsable() && !FeatureConflict.SOFT_PARTICLES.isBlocked();
    }

    /**
     * Once the level's translucent things are drawn: the depth they left, for the next frame's soft particles (their
     * own render pass is open from the solid world on, so a copy can't be made before them this frame), and the lights
     * and streaks, which are drawn apart from the particles. With Improved Transparency on, the copy made before its
     * stages stands ({@link #captureBeforeTransparency}), and goes back to the target for everything of ours drawn from
     * here: mist in the depth would otherwise hide the clouds and weather behind it, in a hole of its shape.
     */
    public static void renderWorld() {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        if (!improvedTransparency())
            captureSceneDepth(target);
        else if (hasSceneDepth())
            SHADER.run(() -> DEPTH_COPY.copyTo(target), () -> {});
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        PointLights.draw(target);
        Streaks.draw(target, transforms);
    }

    /**
     * With Improved Transparency on, as its stages are about to start: this frame's depth, for the particles in them and
     * everything after them. A copy made later won't do: by then the stages have written the nearest translucent thing
     * to the depth, the faintest edge of a particle included. A particle would fade out against where it just was, the
     * lights would pick out those edges as specks in the air, and ground fog and clouds would stop at them, as if at a
     * wall.
     */
    public static void captureBeforeTransparency() {
        captureSceneDepth(Minecraft.getInstance().gameRenderer.mainRenderTarget());
    }

    /**
     * Called before translucent particles are drawn, outside any render pass: copies the scene's depth as it is now
     * from {@code target}, the target the particles are drawn to, for the shaders that need to know what is behind
     * them. On a failure the copy isn't used this frame, and soft particles are drawn the vanilla way.
     */
    public static void captureSceneDepth(RenderTarget target) {
        depthCaptured = false;
        if (!SHADER.isUsable() && !FLAME_SHADER.isUsable() && !PointLights.isActive())
            return;
        SHADER.run(() -> {
            DEPTH_COPY.copyFrom(target);
            makeDepthInfo();
            depthCaptured = true;
        }, () -> depthCaptured = false);
    }

    private static boolean improvedTransparency() {
        return Minecraft.getInstance().gameRenderer.useImprovedTransparency();
    }

    /**
     * Copies {@code source}'s depth now, for an effect drawn after the particles that fades against the scene (the
     * weather's dust). Outside any render pass. The copy the particles read is used again: they are drawn by now.
     *
     * @return whether the copy is ready to bind with {@link #bindSoftDepth}
     */
    public static boolean captureDepth(RenderTarget source) {
        if (improvedTransparency() && hasSceneDepth())
            return true;
        depthCaptured = false;
        try {
            DEPTH_COPY.copyFrom(source);
            makeDepthInfo();
            depthCaptured = true;
        } catch (VirtualMachineError fatal) {
            throw fatal;
        } catch (Throwable t) {
            Library.LOGGER.error(t, "Unable to copy the scene's depth");
        }
        return depthCaptured;
    }

    /**
     * Binds the depth copy and its information to a pass whose pipeline has {@link #SOFT_DEPTH_LAYOUT}.
     */
    public static void bindSoftDepth(RenderPass pass) {
        bindSceneDepth(pass);
        pass.setUniform(SOFT_PARTICLE_INFO, depthInfo);
    }

    /**
     * Whether a copy of this frame's depth is ready to be bound.
     */
    static boolean hasSceneDepth() {
        return depthCaptured && DEPTH_COPY.view() != null;
    }

    /**
     * Binds the depth copy, as DepthSampler, to a pass whose pipeline reads it.
     */
    public static void bindSceneDepth(RenderPass pass) {
        pass.setUniform(DEPTH_SAMPLER, DEPTH_COPY.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    /**
     * Called as a particle layer's pipeline is about to be set. Returns the pipeline to use: a soft pipeline whose
     * depth copy isn't ready is swapped for its vanilla equivalent.
     */
    public static RenderPipeline choosePipeline(RenderPipeline pipeline) {
        var fallback = FALLBACKS.get(pipeline);
        if (fallback == null)
            return pipeline;
        var shader = OIT_SHADERS.get(pipeline);
        return hasSceneDepth() && (shader == null || shader.isUsable()) ? pipeline : fallback;
    }

    /**
     * Called once a particle layer's pipeline is set; binds what a soft pipeline needs.
     */
    public static void bind(RenderPass pass, RenderPipeline pipeline) {
        if (FALLBACKS.containsKey(pipeline))
            bindSoftDepth(pass);
    }

    private static void makeDepthInfo() {
        if (depthInfo != null)
            return;
        var device = RenderSystem.getDevice();
        var zeroToOne = device.getDeviceInfo().isZZeroToOne();
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, DEPTH_INFO_SIZE).putFloat(zeroToOne ? 1F : 0F).get();
            depthInfo = device.createBuffer(() -> "Dynamic Surroundings depth info UBO", GpuBuffer.USAGE_UNIFORM, data);
        }
    }
}
