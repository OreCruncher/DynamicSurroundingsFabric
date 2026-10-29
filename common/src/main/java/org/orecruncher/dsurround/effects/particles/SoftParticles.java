package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.DepthCopy;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.Transforms;
import org.orecruncher.dsurround.lib.Library;

/**
 * "Soft" particles: drawn with a shader that fades a particle out as it nears whatever is behind it, so where it
 * passes into terrain or the surface of water it blends away instead of ending in a hard line. The shader needs the
 * scene's depth, so a copy of it is made just before translucent particles are drawn ({@link #captureSceneDepth}),
 * and bound when a soft layer's pipeline is set ({@link #bind}); see MixinQuadParticleRenderState.
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
    // Light added to what is behind, as much as the particle is opaque
    private static final BlendFunction ADDITIVE = new BlendFunction(SourceFactor.SRC_ALPHA, DestFactor.ONE);
    /*
     * With Fabulous graphics, the particles' target is laid over the scene by its depth, which, where no particle
     * writes its own, is the scene's from before the water was drawn: the ground under it. The water then sorts in front
     * of mist over it, and dims it there and nowhere else. So a soft layer is drawn twice there (see
     * MixinQuadParticleRenderState): first only its depth, tested as usual, which leaves the nearest puff's at each
     * pixel (the particles aren't drawn in any order, so writing depth as they are drawn would leave whichever came
     * last, in patches); then its color, with no test and no writes, as it would be drawn otherwise: the fade against
     * the scene's depth already hides what is behind the scene, and a test against the depth just written would cut one
     * puff off behind another.
     */

    /**
     * Waterfall mist: small puffs, so a short fade (0.4 blocks), or a puff near the water would be mostly faded out.
     * The distance is part of the pipeline; another distance would need a pipeline of its own.
     */
    private static final RenderPipeline MIST_PIPELINE = particlePipeline("pipeline/waterfall_mist", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .build();
    // The same, for Fabulous graphics: its depth, then its color
    private static final RenderPipeline MIST_DEPTH_PIPELINE = particlePipeline("pipeline/waterfall_mist_depth", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withShaderDefine("WRITE_DEPTH")
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withColorWrite(false)
            .withDepthWrite(true)
            .build();
    private static final RenderPipeline MIST_FABULOUS_PIPELINE = particlePipeline("pipeline/waterfall_mist_fabulous", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline MIST_FALLBACK_PIPELINE = particlePipeline("pipeline/waterfall_mist_vanilla", null).build();

    /**
     * Tongues of flame, sparks and molten droplets: soft, with a shorter fade (0.3 blocks), and adding their light to
     * what is behind them, so overlapping flames burn brighter.
     */
    private static final RenderPipeline FLAME_PIPELINE = particlePipeline("pipeline/flame", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withBlend(ADDITIVE)
            .build();
    // The same, for Fabulous graphics: its depth, then its color
    private static final RenderPipeline FLAME_DEPTH_PIPELINE = particlePipeline("pipeline/flame_depth", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withShaderDefine("WRITE_DEPTH")
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withColorWrite(false)
            .withDepthWrite(true)
            .build();
    private static final RenderPipeline FLAME_FABULOUS_PIPELINE = particlePipeline("pipeline/flame_fabulous", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.3F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withBlend(ADDITIVE)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline FLAME_FALLBACK_PIPELINE = particlePipeline("pipeline/flame_vanilla", null)
            .withBlend(ADDITIVE)
            .build();

    /**
     * Flames in a skull's eyes: drawn as the other flames are, but they burn a few hundredths of a block in front of
     * the face, which the flames' fade would all but hide when the skull is seen from the front. Drawn the vanilla way,
     * they use the flames' fallback.
     */
    private static final RenderPipeline EYE_FLAME_PIPELINE = particlePipeline("pipeline/eye_flame", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.02F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withBlend(ADDITIVE)
            .build();
    // The same, for Fabulous graphics: its depth, then its color
    private static final RenderPipeline EYE_FLAME_DEPTH_PIPELINE = particlePipeline("pipeline/eye_flame_depth", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.02F)
            .withShaderDefine("WRITE_DEPTH")
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withColorWrite(false)
            .withDepthWrite(true)
            .build();
    private static final RenderPipeline EYE_FLAME_FABULOUS_PIPELINE = particlePipeline("pipeline/eye_flame_fabulous", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.02F)
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .withBlend(ADDITIVE)
            .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
            .build();

    public static final ModShader SHADER = new ModShader(MIST_PIPELINE, "soft particles", "particles will be drawn without it", Library.LOGGER);
    public static final ModShader FLAME_SHADER = new ModShader(FLAME_PIPELINE, "soft flames", "flames will be drawn without it", Library.LOGGER);
    public static final ModShader DEPTH_SHADER = new ModShader(MIST_DEPTH_PIPELINE, "the depth of soft particles (Fabulous)", "water will be laid over mist above it", Library.LOGGER);
    public static final ModShader FABULOUS_SHADER = new ModShader(MIST_FABULOUS_PIPELINE, "soft particles (Fabulous)", "water will be laid over mist above it", Library.LOGGER);
    public static final ModShader FLAME_DEPTH_SHADER = new ModShader(FLAME_DEPTH_PIPELINE, "the depth of soft flames (Fabulous)", "water will be laid over flames above it", Library.LOGGER);
    public static final ModShader FLAME_FABULOUS_SHADER = new ModShader(FLAME_FABULOUS_PIPELINE, "soft flames (Fabulous)", "water will be laid over flames above it", Library.LOGGER);
    public static final ModShader EYE_FLAME_SHADER = new ModShader(EYE_FLAME_PIPELINE, "soft skull flames", "skull flames will be drawn without it", Library.LOGGER);
    public static final ModShader EYE_FLAME_DEPTH_SHADER = new ModShader(EYE_FLAME_DEPTH_PIPELINE, "the depth of soft skull flames (Fabulous)", "water will be laid over skull flames above it", Library.LOGGER);
    public static final ModShader EYE_FLAME_FABULOUS_SHADER = new ModShader(EYE_FLAME_FABULOUS_PIPELINE, "soft skull flames (Fabulous)", "water will be laid over skull flames above it", Library.LOGGER);

    public static final SingleQuadParticle.Layer WATERFALL_MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE);
    public static final SingleQuadParticle.Layer WATERFALL_MIST_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_FALLBACK_PIPELINE);
    public static final SingleQuadParticle.Layer FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_PIPELINE);
    public static final SingleQuadParticle.Layer FLAME_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, FLAME_FALLBACK_PIPELINE);
    public static final SingleQuadParticle.Layer EYE_FLAME = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, EYE_FLAME_PIPELINE);

    // The copy of the scene's depth the shaders read; can't be the depth buffer being drawn to
    private static final DepthCopy DEPTH_COPY = new DepthCopy("Dynamic Surroundings scene depth");
    // Whether the copy holds this frame's depth
    private static boolean depthCaptured;
    // The pipeline the particle layer being drawn was set to (see MixinQuadParticleRenderState)
    @Nullable
    private static RenderPipeline currentPipeline;
    @Nullable
    private static MappableRingBuffer depthInfo;
    // Whether the depth information has been written this frame
    private static boolean depthInfoWritten;

    private SoftParticles() {
    }

    /**
     * Adds what a soft shader reads, the depth copy and its information (DepthSampler, SoftParticleInfo), to a
     * pipeline; bound by {@link #bindSoftDepth}
     */
    public static RenderPipeline.Builder withSoftDepth(RenderPipeline.Builder builder) {
        return builder.withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER).withSampler(DEPTH_SAMPLER);
    }

    /**
     * A translucent particle pipeline: vanilla's particle shader unless {@code shader} names another.
     */
    static RenderPipeline.Builder particlePipeline(String location, @Nullable net.minecraft.resources.Identifier shader) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                // Vanilla's translucent particles, with depth writes off so the puffs don't hide each other
                .withDepthWrite(false);
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
     * Binds the depth copy and its information to a pass whose pipeline was given them by {@link #withSoftDepth}.
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
        if (pipeline == EYE_FLAME_PIPELINE)
            return choose(pipeline, FLAME_FALLBACK_PIPELINE, EYE_FLAME_DEPTH_SHADER, EYE_FLAME_FABULOUS_SHADER);
        return pipeline;
    }

    private static RenderPipeline choose(RenderPipeline soft, RenderPipeline fallback, ModShader depth, ModShader fabulous) {
        if (!hasSceneDepth())
            return fallback;
        if (Minecraft.useShaderTransparency() && depth.isUsable() && fabulous.isUsable())
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
        if (pipeline == EYE_FLAME_FABULOUS_PIPELINE)
            return EYE_FLAME_DEPTH_PIPELINE;
        return null;
    }

    /**
     * Called once a particle layer's pipeline is set; binds what a soft pipeline needs.
     */
    public static void bind(RenderPass pass, RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE || pipeline == FLAME_PIPELINE || pipeline == MIST_DEPTH_PIPELINE || pipeline == FLAME_DEPTH_PIPELINE
                || pipeline == MIST_FABULOUS_PIPELINE || pipeline == FLAME_FABULOUS_PIPELINE
                || pipeline == EYE_FLAME_PIPELINE || pipeline == EYE_FLAME_DEPTH_PIPELINE || pipeline == EYE_FLAME_FABULOUS_PIPELINE)
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
        var zeroToOne = Transforms.DEPTH_ZERO_TO_ONE;
        try (var view = RenderSystem.getDevice().createCommandEncoder().mapBuffer(depthInfo.currentBuffer(), false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(zeroToOne ? 1F : 0F);
        }
    }
}
