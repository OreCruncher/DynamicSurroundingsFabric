package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

import java.util.Optional;

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
            .withSampler(DEPTH_SAMPLER)
            .build();

    // Light added to what is behind, as much as the particle is opaque
    private static final BlendFunction ADDITIVE = new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE);
    // Vanilla's translucent particles, with depth writes off so the puffs don't hide each other
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    /*
     * With Fabulous graphics, the particles' target is laid over the scene by its depth, which, where no particle
     * writes its own, is the scene's from before the water was drawn: the ground under it. The water then sorts in front
     * of mist over it, and dims it there and nowhere else. So a soft layer is drawn twice there (see
     * MixinQuadParticleFeatureRenderer): first only its depth, tested as usual, which leaves the nearest puff's at each
     * pixel (the particles aren't drawn in any order, so writing depth as they are drawn would leave whichever came
     * last, in patches); then its colour, with no test and no writes, as it would be drawn otherwise: the fade against
     * the scene's depth already hides what is behind the scene, and a test against the depth just written would cut one
     * puff off behind another.
     */
    // The depth pass: nearest wins, and no colour
    private static final ColorTargetState NO_COLOR = new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE);
    // The colour pass after it
    private static final DepthStencilState UNTESTED = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    /**
     * Waterfall mist: small puffs, so a short fade (0.4 blocks), or a puff near the water would be mostly faded out.
     * The distance is part of the pipeline; another distance would need a pipeline of its own.
     */
    private static final RenderPipeline MIST_PIPELINE = particlePipeline("pipeline/waterfall_mist", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .build();
    // The same, for Fabulous graphics: its depth, then its colour
    private static final RenderPipeline MIST_DEPTH_PIPELINE = particlePipeline("pipeline/waterfall_mist_depth", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withShaderDefine("WRITE_DEPTH")
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withColorTargetState(NO_COLOR)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    private static final RenderPipeline MIST_FABULOUS_PIPELINE = particlePipeline("pipeline/waterfall_mist_fabulous", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withDepthStencilState(UNTESTED)
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
    // The same, for Fabulous graphics: its depth, then its colour
    private static final RenderPipeline FLAME_DEPTH_PIPELINE = particlePipeline("pipeline/flame_depth", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withShaderDefine("WRITE_DEPTH")
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withColorTargetState(NO_COLOR)
            .withDepthStencilState(DepthStencilState.DEFAULT)
            .build();
    private static final RenderPipeline FLAME_FABULOUS_PIPELINE = particlePipeline("pipeline/flame_fabulous", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withBindGroupLayout(SOFT_DEPTH_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADDITIVE))
            .withDepthStencilState(UNTESTED)
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline FLAME_FALLBACK_PIPELINE = particlePipeline("pipeline/flame_vanilla", null)
            .withColorTargetState(new ColorTargetState(ADDITIVE))
            .build();

    public static final ModShader SHADER = new ModShader(MIST_PIPELINE, "soft particles", "particles will be drawn without it", Library.LOGGER);
    public static final ModShader FLAME_SHADER = new ModShader(FLAME_PIPELINE, "soft flames", "flames will be drawn without it", Library.LOGGER);
    public static final ModShader DEPTH_SHADER = new ModShader(MIST_DEPTH_PIPELINE, "the depth of soft particles (Fabulous)", "water will be laid over mist above it", Library.LOGGER);
    public static final ModShader FABULOUS_SHADER = new ModShader(MIST_FABULOUS_PIPELINE, "soft particles (Fabulous)", "water will be laid over mist above it", Library.LOGGER);
    public static final ModShader FLAME_DEPTH_SHADER = new ModShader(FLAME_DEPTH_PIPELINE, "the depth of soft flames (Fabulous)", "water will be laid over flames above it", Library.LOGGER);
    public static final ModShader FLAME_FABULOUS_SHADER = new ModShader(FLAME_FABULOUS_PIPELINE, "soft flames (Fabulous)", "water will be laid over flames above it", Library.LOGGER);

    public static final SingleQuadParticle.Layer WATERFALL_MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE);
    public static final SingleQuadParticle.Layer WATERFALL_MIST_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_FALLBACK_PIPELINE);
    public static final SingleQuadParticle.Layer FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_PIPELINE);
    public static final SingleQuadParticle.Layer FLAME_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_FALLBACK_PIPELINE);

    // The copy of the scene's depth the shaders read; can't be the depth buffer being drawn to
    private static final DepthCopy DEPTH_COPY = new DepthCopy("Dynamic Surroundings scene depth");
    // Whether the copy holds this frame's depth
    private static boolean depthCaptured;
    // The pipeline the particle layer being drawn was set to (see MixinQuadParticleFeatureRenderer)
    @Nullable
    private static RenderPipeline currentPipeline;
    @Nullable
    private static MappableRingBuffer depthInfo;
    // Whether the depth information has been written this frame
    private static boolean depthInfoWritten;

    private SoftParticles() {
    }

    /**
     * A translucent particle pipeline: vanilla's particle shader unless {@code shader} names another.
     */
    static RenderPipeline.Builder particlePipeline(String location, @Nullable net.minecraft.resources.Identifier shader) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
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
            if (!depthInfoWritten) {
                writeDepthInfo();
                depthInfoWritten = true;
            }
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
            if (!depthInfoWritten) {
                writeDepthInfo();
                depthInfoWritten = true;
            }
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
        pass.setUniform(SOFT_PARTICLE_INFO, depthInfo.currentBuffer());
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
        pass.bindTexture(DEPTH_SAMPLER, DEPTH_COPY.view(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    /**
     * Called as a particle layer's pipeline is about to be set. Returns the pipeline to use: a soft pipeline whose
     * depth copy isn't ready is swapped for its vanilla equivalent, and with Fabulous graphics for the one writing its
     * depth, if that can be used.
     */
    public static RenderPipeline choosePipeline(RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE)
            return choose(pipeline, MIST_FALLBACK_PIPELINE, DEPTH_SHADER, FABULOUS_SHADER);
        if (pipeline == FLAME_PIPELINE)
            return choose(pipeline, FLAME_FALLBACK_PIPELINE, FLAME_DEPTH_SHADER, FLAME_FABULOUS_SHADER);
        return pipeline;
    }

    private static RenderPipeline choose(RenderPipeline soft, RenderPipeline fallback, ModShader depth, ModShader fabulous) {
        if (!hasSceneDepth())
            return fallback;
        if (Minecraft.getInstance().gameRenderer.gameRenderState().useShaderTransparency() && depth.isUsable() && fabulous.isUsable())
            return fabulous.pipeline();
        return soft;
    }

    /**
     * The pipeline the particle layer being drawn was set to, as chosen by {@link #choosePipeline}
     */
    @Nullable
    public static RenderPipeline currentPipeline() {
        return currentPipeline;
    }

    public static void setCurrentPipeline(@Nullable RenderPipeline pipeline) {
        currentPipeline = pipeline;
    }

    /**
     * The pipeline that draws the depth of a layer drawn with {@code pipeline}, first, with Fabulous graphics; null for
     * one drawn in a single pass
     */
    @Nullable
    public static RenderPipeline depthPassFor(RenderPipeline pipeline) {
        if (pipeline == MIST_FABULOUS_PIPELINE)
            return MIST_DEPTH_PIPELINE;
        if (pipeline == FLAME_FABULOUS_PIPELINE)
            return FLAME_DEPTH_PIPELINE;
        return null;
    }

    /**
     * Called once a particle layer's pipeline is set; binds what a soft pipeline needs.
     */
    public static void bind(RenderPass pass, RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE || pipeline == FLAME_PIPELINE || pipeline == MIST_DEPTH_PIPELINE || pipeline == FLAME_DEPTH_PIPELINE
                || pipeline == MIST_FABULOUS_PIPELINE || pipeline == FLAME_FABULOUS_PIPELINE)
            bindSoftDepth(pass);
    }

    /**
     * Called after the particles are drawn: the next frame gets a new copy.
     */
    public static void endFrame() {
        if (depthInfoWritten && depthInfo != null)
            depthInfo.rotate();
        depthInfoWritten = false;
        depthCaptured = false;
        currentPipeline = null;
    }

    private static void writeDepthInfo() {
        if (depthInfo == null)
            depthInfo = new MappableRingBuffer(() -> "Dynamic Surroundings depth info UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, DEPTH_INFO_SIZE);
        var zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        try (var view = depthInfo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(zeroToOne ? 1F : 0F);
        }
    }
}
