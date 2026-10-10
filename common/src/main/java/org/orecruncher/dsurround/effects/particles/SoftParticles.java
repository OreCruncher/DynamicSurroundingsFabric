package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

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
    private static final BindGroupLayout SOFT_PARTICLE_LAYOUT = BindGroupLayout.builder()
            .withUniform(SOFT_PARTICLE_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(DEPTH_SAMPLER)
            .build();

    // Vanilla's translucent particles, with depth writes off so the puffs don't hide each other
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    /**
     * Waterfall mist: small puffs, so a short fade (0.4 blocks), or a puff near the water would be mostly faded out.
     * The distance is part of the pipeline; another distance would need a pipeline of its own.
     */
    private static final RenderPipeline MIST_PIPELINE = particlePipeline("pipeline/waterfall_mist", Constants.asId("core/soft_particle"))
            .withShaderDefine("SOFT_DISTANCE", 0.4F)
            .withBindGroupLayout(SOFT_PARTICLE_LAYOUT)
            .build();
    // The same, drawn the vanilla way
    private static final RenderPipeline MIST_FALLBACK_PIPELINE = particlePipeline("pipeline/waterfall_mist_vanilla", null).build();

    public static final ModShader SHADER = new ModShader(MIST_PIPELINE, "soft particles", "particles will be drawn without it", Library.LOGGER);

    public static final SingleQuadParticle.Layer WATERFALL_MIST = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_PIPELINE);
    public static final SingleQuadParticle.Layer WATERFALL_MIST_FALLBACK = new SingleQuadParticle.Layer(true, TextureAtlas.LOCATION_PARTICLES, MIST_FALLBACK_PIPELINE);

    // The copy of the scene's depth the shaders read; can't be the depth buffer being drawn to
    @Nullable
    private static TextureTarget depthCopy;
    // Whether the copy holds this frame's depth
    private static boolean depthCaptured;
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
     * The layer for waterfall mist: soft if the shader can be used, otherwise drawn the vanilla way.
     */
    public static SingleQuadParticle.Layer mistLayer() {
        return SHADER.isUsable() ? WATERFALL_MIST : WATERFALL_MIST_FALLBACK;
    }

    /**
     * Called before translucent particles are drawn, outside any render pass: copies the scene's depth as it is now
     * from {@code target}, the target the particles are drawn to, for the shaders that need to know what is behind
     * them. On a failure the copy isn't used this frame, and soft particles are drawn the vanilla way.
     */
    public static void captureSceneDepth(RenderTarget target) {
        depthCaptured = false;
        if (!SHADER.isUsable() && !FireflyLights.isActive())
            return;
        SHADER.run(() -> {
            var copy = depthCopyFor(target);
            copy.copyDepthFrom(target);
            if (!depthInfoWritten) {
                writeDepthInfo();
                depthInfoWritten = true;
            }
            depthCaptured = true;
        }, () -> depthCaptured = false);
    }

    /**
     * Whether a copy of this frame's depth is ready to be bound.
     */
    static boolean hasSceneDepth() {
        return depthCaptured && depthCopy != null;
    }

    /**
     * Binds the depth copy, as DepthSampler, to a pass whose pipeline reads it.
     */
    static void bindSceneDepth(RenderPass pass) {
        pass.bindTexture(DEPTH_SAMPLER, depthCopy.getDepthTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST));
    }

    /**
     * Called as a particle layer's pipeline is about to be set. Returns the pipeline to use: a soft pipeline whose
     * depth copy isn't ready is swapped for its vanilla equivalent.
     */
    public static RenderPipeline choosePipeline(RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE && !hasSceneDepth())
            return MIST_FALLBACK_PIPELINE;
        return pipeline;
    }

    /**
     * Called once a particle layer's pipeline is set; binds what a soft pipeline needs.
     */
    public static void bind(RenderPass pass, RenderPipeline pipeline) {
        if (pipeline == MIST_PIPELINE) {
            bindSceneDepth(pass);
            pass.setUniform(SOFT_PARTICLE_INFO, depthInfo.currentBuffer());
        }
    }

    /**
     * Called after the particles are drawn: the next frame gets a new copy.
     */
    public static void endFrame() {
        if (depthInfoWritten && depthInfo != null)
            depthInfo.rotate();
        depthInfoWritten = false;
        depthCaptured = false;
    }

    private static void writeDepthInfo() {
        if (depthInfo == null)
            depthInfo = new MappableRingBuffer(() -> "Dynamic Surroundings depth info UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, DEPTH_INFO_SIZE);
        var zeroToOne = RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
        try (var view = depthInfo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(zeroToOne ? 1F : 0F);
        }
    }

    /**
     * The depth copy, created or resized to match {@code target}. A depth copy is only possible between textures of
     * the same format, so it has a stencil buffer if the target does (see {@link StencilCompat}).
     */
    private static TextureTarget depthCopyFor(RenderTarget target) {
        if (depthCopy != null && StencilCompat.hasStencil(depthCopy) != StencilCompat.hasStencil(target)) {
            depthCopy.destroyBuffers();
            depthCopy = null;
        }
        if (depthCopy == null) {
            depthCopy = StencilCompat.create("Dynamic Surroundings scene depth", target.width, target.height, StencilCompat.hasStencil(target));
        } else if (depthCopy.width != target.width || depthCopy.height != target.height) {
            depthCopy.resize(target.width, target.height);
        }
        return depthCopy;
    }

    /**
     * Render targets can have a stencil buffer on NeoForge, which adds a useStencil field to RenderTarget and a
     * TextureTarget constructor taking it; another mod may turn it on for the main target. Vanilla (and so Fabric)
     * has neither, so they are found by reflection, and targets are made without a stencil buffer where they don't
     * exist.
     */
    private static final class StencilCompat {

        @Nullable
        private static final Field USE_STENCIL = findField();
        @Nullable
        private static final Constructor<TextureTarget> WITH_STENCIL = findConstructor();

        @Nullable
        private static Field findField() {
            try {
                return RenderTarget.class.getField("useStencil");
            } catch (NoSuchFieldException e) {
                return null;
            }
        }

        @Nullable
        private static Constructor<TextureTarget> findConstructor() {
            try {
                return TextureTarget.class.getConstructor(String.class, int.class, int.class, boolean.class, boolean.class, GpuFormat.class);
            } catch (NoSuchMethodException e) {
                return null;
            }
        }

        static boolean hasStencil(RenderTarget target) {
            if (USE_STENCIL == null)
                return false;
            try {
                return USE_STENCIL.getBoolean(target);
            } catch (ReflectiveOperationException | RuntimeException e) {
                return false;
            }
        }

        static TextureTarget create(String label, int width, int height, boolean stencil) {
            if (stencil && WITH_STENCIL != null) {
                try {
                    return WITH_STENCIL.newInstance(label, width, height, true, true, GpuFormat.RGBA8_UNORM);
                } catch (ReflectiveOperationException | RuntimeException e) {
                    // Without a stencil buffer the formats differ and the depth copy fails, which turns the effects
                    // off; but this only happens if reflection breaks
                }
            }
            return new TextureTarget(label, width, height, true, GpuFormat.RGBA8_UNORM);
        }
    }
}
