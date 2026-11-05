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
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.compat.IrisCompat;


/**
 * "Soft" particles: drawn with a shader that fades a particle out as it nears whatever is behind it, so where it
 * passes into terrain or the surface of water it blends away instead of ending in a hard line. The shader needs the
 * scene's depth, so a copy of it is made just before translucent particles are drawn ({@link #captureSceneDepth}),
 * and bound when a soft layer's pipeline is set ({@link #bind}); see MixinQuadParticleFeatureRenderer.
 * <p>
 * If the shader isn't available, because it failed to compile or an Iris shader pack is in use (which replaces the
 * rendering, so a shader of ours wouldn't fit in), the particles are drawn with vanilla's particle shader instead.
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

    public static final ModShader SHADER = new ModShader(MIST_PIPELINE, "soft particles", "particles will be drawn without it", Library.LOGGER);
    public static final ModShader FLAME_SHADER = new ModShader(FLAME_PIPELINE, "soft flames", "flames will be drawn without it", Library.LOGGER);
    public static final ModShader EYE_FLAME_SHADER = new ModShader(EYE_FLAME_PIPELINE, "soft skull flames", "skull flames will be drawn without it", Library.LOGGER);

    public static final SingleQuadParticle.Layer WATERFALL_MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE, RenderPipelines.OIT_PARTICLE);
    public static final SingleQuadParticle.Layer WATERFALL_MIST_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_FALLBACK_PIPELINE, RenderPipelines.OIT_PARTICLE);
    public static final SingleQuadParticle.Layer FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_PIPELINE, RenderPipelines.OIT_PARTICLE);
    public static final SingleQuadParticle.Layer FLAME_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_FALLBACK_PIPELINE, RenderPipelines.OIT_PARTICLE);
    public static final SingleQuadParticle.Layer EYE_FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, EYE_FLAME_PIPELINE, RenderPipelines.OIT_PARTICLE);

    static {
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
        return SHADER.isUsable() ? WATERFALL_MIST : WATERFALL_MIST_FALLBACK;
    }

    /**
     * The layer for flames, sparks and molten droplets: soft if the shader can be used, otherwise drawn the vanilla
     * way; either way adding their light.
     */
    public static SingleQuadParticle.Layer flameLayer() {
        return FLAME_SHADER.isUsable() ? FLAME : FLAME_FALLBACK;
    }

    /**
     * The layer for flames in a skull's eyes: as {@link #flameLayer}, with a short fade
     */
    public static SingleQuadParticle.Layer eyeFlameLayer() {
        return EYE_FLAME_SHADER.isUsable() ? EYE_FLAME : FLAME_FALLBACK;
    }

    /**
     * Once the level's translucent things are drawn: the depth they left, for the next frame's soft particles (their
     * own render pass is open from the solid world on, so a copy can't be made before them this frame), and the lights
     * and streaks, which are drawn apart from the particles.
     */
    public static void renderWorld() {
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        captureSceneDepth(target);
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        PointLights.draw(target, transforms);
        Streaks.draw(target, transforms);
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

    /**
     * Copies {@code source}'s depth now, for an effect drawn after the particles that fades against the scene (the
     * weather's dust). Outside any render pass. The copy the particles read is used again: they are drawn by now.
     *
     * @return whether the copy is ready to bind with {@link #bindSoftDepth}
     */
    public static boolean captureDepth(RenderTarget source) {
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
        if (pipeline == MIST_PIPELINE)
            return hasSceneDepth() ? pipeline : MIST_FALLBACK_PIPELINE;
        if (pipeline == FLAME_PIPELINE || pipeline == EYE_FLAME_PIPELINE)
            return hasSceneDepth() ? pipeline : FLAME_FALLBACK_PIPELINE;
        return pipeline;
    }

    /**
     * Called once a particle layer's pipeline is set; binds what a soft pipeline needs.
     */
    public static void bind(RenderPass pass, RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE || pipeline == FLAME_PIPELINE || pipeline == EYE_FLAME_PIPELINE)
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
