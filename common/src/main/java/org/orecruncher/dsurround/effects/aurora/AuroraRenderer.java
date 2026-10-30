package org.orecruncher.dsurround.effects.aurora;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.profiling.AuroraSamples;

import java.nio.ByteBuffer;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws an aurora into the sky: each curtain a strip of quads, its lower edge following the curtain's path and its
 * top above that, filled in by the aurora shader. Rebuilt every frame, since the curtains ripple; at a few hundred
 * vertices that costs next to nothing.
 * <p>
 * Without the shader ({@link #SHADER}) there is no aurora. Everything here runs on the render thread.
 */
public final class AuroraRenderer {

    // The shader's uniforms other than vanilla's: three colours and the time and strength
    private static final String AURORA_INFO = "AuroraInfo";
    private static final int AURORA_INFO_SIZE = new Std140SizeCalculator().putVec4().putVec4().putVec4().putFloat().putFloat().get();
    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final BindGroupLayout AURORA_INFO_LAYOUT = BindGroupLayout.builder()
            .withUniform(AURORA_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(NOISE_SAMPLER)
            .build();

    // Light adds to the sky behind it. Curtains are seen from both sides, and nothing is tested against or written to
    // the depth buffer, so the world drawn after the sky covers them.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/aurora"))
            .withVertexShader(Constants.asId("core/aurora"))
            .withFragmentShader(Constants.asId("core/aurora"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(AURORA_INFO_LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the aurora", "auroras will not be drawn", Library.LOGGER);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Aurora");

    /**
     * The noise texture's size, in cells, which it repeats at (see {@link NoiseTexture}). Wider than a curtain's finest
     * noise: 23 cells to a unit along a curtain up to 10 across, and folded up to 0.675 either way, about 260; any
     * fewer and the rays would repeat within one.
     */
    public static final int NOISE_SIZE = 512;

    // Quads along each curtain
    private static final int SEGMENTS = 96;

    // Made on first use, on the render thread
    @Nullable
    private static DynamicTexture noise;

    private static final Vector3f BOTTOM = new Vector3f();
    private static final Vector3f TOP = new Vector3f();

    // Created on first use, on the render thread
    @Nullable
    private static ByteBufferBuilder meshBytes;
    @Nullable
    private static GpuBuffer vertexBuffer;
    // One buffer rewritten each frame, not a MappableRingBuffer: rotating that one sets a fence, which held the GPU idle
    // for 2 ms a frame after the aurora on 1.21.11 (AMD)
    @Nullable
    private static GpuBuffer auroraInfo;

    // How much of the screen the quads cover, worked out only while /dsprofile records
    private static final AuroraCoverage COVERAGE = new AuroraCoverage();
    private static final Matrix4f CLIP = new Matrix4f();

    private AuroraRenderer() {
    }

    /**
     * Whether auroras can be drawn: the shader compiled, and drawing hasn't failed since.
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable();
    }

    /**
     * Draws the aurora.
     *
     * @param aurora    what to draw
     * @param alpha     how strongly, 0 to 1
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time      seconds, for the animation
     */
    public static void render(Aurora aurora, float alpha, Matrix4f viewMatrix, float time) {
        if (alpha <= 0F || !SHADER.isAvailable())
            return;
        RENDER_TIMER.begin();
        SHADER.run(() -> draw(aurora, alpha, viewMatrix, time), () -> {});
        RENDER_TIMER.end();
    }

    private static void draw(Aurora aurora, float alpha, Matrix4f viewMatrix, float time) {
        var device = RenderSystem.getDevice();

        if (meshBytes == null)
            meshBytes = new ByteBufferBuilder(aurora.bands() * SEGMENTS * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        var builder = new BufferBuilder(meshBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        var target = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var coverage = AuroraSamples.isRecording() ? COVERAGE : null;
        if (coverage != null) {
            // The sky is drawn with the camera's rotation and projection, the quads scaled from sky units to blocks
            Minecraft.getInstance().gameRenderer.mainCamera().getViewRotationProjectionMatrix(CLIP).scale(Aurora.SCALE);
            coverage.start(CLIP);
        }
        buildCurtains(builder, aurora, time, coverage);

        int indexCount;
        try (var mesh = builder.build()) {
            if (mesh == null)
                return;
            indexCount = mesh.drawState().indexCount();
            uploadVertices(mesh.vertexBuffer());
        }

        if (auroraInfo == null)
            auroraInfo = device.createBuffer(() -> "Dynamic Surroundings aurora UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, AURORA_INFO_SIZE);
        var palette = aurora.palette();
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, AURORA_INFO_SIZE)
                    .putVec4(palette.bottom().red(), palette.bottom().green(), palette.bottom().blue(), 1F)
                    .putVec4(palette.middle().red(), palette.middle().green(), palette.middle().blue(), 1F)
                    .putVec4(palette.top().red(), palette.top().green(), palette.top().blue(), 1F)
                    .putFloat(time)
                    .putFloat(Math.clamp(alpha * aurora.brightness(), 0F, 1F))
                    .get();
            device.createCommandEncoder().writeToBuffer(auroraInfo.slice(), data);
        }
        if (coverage != null)
            AuroraSamples.drawn(aurora, Math.clamp(alpha * aurora.brightness(), 0F, 1F),
                    coverage.pixels(target.width, target.height), target.width * target.height);

        var transforms = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = device.createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings aurora", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform(AURORA_INFO, auroraInfo);
            pass.bindTexture(NOISE_SAMPLER, noise().getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertexBuffer.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
    }

    private static void uploadVertices(ByteBuffer vertices) {
        var device = RenderSystem.getDevice();
        if (vertexBuffer == null || vertexBuffer.size() < vertices.remaining()) {
            if (vertexBuffer != null)
                vertexBuffer.close();
            vertexBuffer = device.createBuffer(() -> "Dynamic Surroundings aurora vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices.remaining());
        }
        device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertices);
    }

    private static void buildCurtains(BufferBuilder builder, Aurora aurora, float time, @Nullable AuroraCoverage coverage) {
        // Farthest curtain first; with additive blending the order doesn't change the result, but it's tidy
        for (int band = aurora.bands() - 1; band >= 0; band--) {
            var bandAlpha = aurora.bandBrightness(band);
            float prevS = -1F;
            float prevU = aurora.textureU(band, prevS);
            float prevA = Aurora.taper(prevS) * bandAlpha;
            aurora.bottom(band, prevS, time, BOTTOM);
            aurora.top(band, BOTTOM, TOP);
            float pbx = BOTTOM.x, pby = BOTTOM.y, pbz = BOTTOM.z;
            float ptx = TOP.x, pty = TOP.y, ptz = TOP.z;

            for (int i = 1; i <= SEGMENTS; i++) {
                float s = -1F + 2F * i / SEGMENTS;
                float u = aurora.textureU(band, s);
                float a = Aurora.taper(s) * bandAlpha;
                aurora.bottom(band, s, time, BOTTOM);
                aurora.top(band, BOTTOM, TOP);

                vertex(builder, pbx, pby, pbz, prevU, 0F, prevA);
                vertex(builder, BOTTOM.x, BOTTOM.y, BOTTOM.z, u, 0F, a);
                vertex(builder, TOP.x, TOP.y, TOP.z, u, 1F, a);
                vertex(builder, ptx, pty, ptz, prevU, 1F, prevA);
                if (coverage != null)
                    coverage.quad(pbx, pby, pbz, BOTTOM.x, BOTTOM.y, BOTTOM.z, TOP.x, TOP.y, TOP.z, ptx, pty, ptz);

                pbx = BOTTOM.x; pby = BOTTOM.y; pbz = BOTTOM.z;
                ptx = TOP.x; pty = TOP.y; ptz = TOP.z;
                prevU = u;
                prevA = a;
            }
        }
    }

    private static void vertex(BufferBuilder builder, float x, float y, float z, float u, float v, float alpha) {
        builder.addVertex(x * Aurora.SCALE, y * Aurora.SCALE, z * Aurora.SCALE).setUv(u, v).setColor(1F, 1F, 1F, alpha);
    }

    private static DynamicTexture noise() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6175726FL);
        return noise;
    }
}
