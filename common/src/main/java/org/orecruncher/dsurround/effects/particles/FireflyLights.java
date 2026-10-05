package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Small point lights around fireflies, lighting the grass, leaves and ground near them. Each is drawn with a shader
 * that works out from the scene's depth where each pixel near the light is, and adds light by how close it is and
 * whether it faces the light. Being added on top of the scene, it doesn't change Minecraft's lighting, and it can't
 * cast shadows.
 * <p>
 * Fireflies {@link #add} their light as the particles are extracted for drawing, and the lights are all drawn
 * together just before the translucent particles ({@link #draw}), in a pass of their own (see
 * MixinQuadParticleFeatureRenderer).
 * <p>
 * Without the shader ({@link #SHADER}), or while an Iris shader pack is in use, there are no lights. Everything here
 * runs on the render thread.
 */
public final class FireflyLights {

    private static final String FIREFLY_LIGHT_INFO = "FireflyLightInfo";
    private static final int FIREFLY_LIGHT_INFO_SIZE = new Std140SizeCalculator().putFloat().putFloat().get();
    private static final BindGroupLayout FIREFLY_LIGHT_LAYOUT = BindGroupLayout.builder()
            .withUniform(FIREFLY_LIGHT_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(SoftParticles.DEPTH_SAMPLER)
            .build();

    // The shader finds what each light reaches itself, so nothing is tested against depth; and its squares may face
    // either way. Light adds to what is there.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/firefly_light"))
            .withVertexShader(Constants.asId("core/firefly_light"))
            .withFragmentShader(Constants.asId("core/firefly_light"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(FIREFLY_LIGHT_LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.SRC_ALPHA, BlendFactor.ONE)))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "firefly lights", "fireflies will not light their surroundings", Library.LOGGER);

    /**
     * How far a firefly's light reaches, in blocks.
     */
    public static final float RADIUS = 1.75F;
    /**
     * Strength of a firefly's light at its brightest, 0 to 1.
     */
    public static final float STRENGTH = 0.45F;

    // Lights recorded longer ago than this weren't from this frame (fireflies stopped being drawn), so are dropped
    private static final long STALE_NANOS = 250_000_000L;
    // x, y, z, red, green, blue, strength
    private static final int STRIDE = 7;

    private static float[] lights = new float[STRIDE * 64];
    private static int count;
    private static long lastAdded;

    // Created on first use, on the render thread
    @Nullable
    private static ByteBufferBuilder meshBytes;
    @Nullable
    private static GpuBuffer vertexBuffer;
    @Nullable
    private static MappableRingBuffer lightInfo;

    private FireflyLights() {
    }

    /**
     * Whether firefly lights are being drawn.
     */
    public static boolean isActive() {
        return Client.Config.fireflyOptions.enableLight && SHADER.isUsable();
    }

    /**
     * Whether there are lights waiting to be drawn.
     */
    public static boolean hasLights() {
        return count > 0;
    }

    /**
     * Records a light to draw.
     *
     * @param x        where, in the world
     * @param strength 0 to 1
     */
    public static void add(double x, double y, double z, float red, float green, float blue, float strength) {
        if (strength <= 0F)
            return;
        if ((count + 1) * STRIDE > lights.length)
            lights = Arrays.copyOf(lights, lights.length * 2);
        int i = count * STRIDE;
        lights[i] = (float) x;
        lights[i + 1] = (float) y;
        lights[i + 2] = (float) z;
        lights[i + 3] = red;
        lights[i + 4] = green;
        lights[i + 5] = blue;
        lights[i + 6] = strength;
        count++;
        lastAdded = System.nanoTime();
    }

    /**
     * Draws the lights recorded since the last call into {@code target}, then forgets them. Called outside any render
     * pass, after the scene's depth has been captured.
     */
    public static void draw(RenderTarget target) {
        int n = count;
        count = 0;
        if (n == 0 || System.nanoTime() - lastAdded > STALE_NANOS || !isActive() || !SoftParticles.hasSceneDepth())
            return;
        SHADER.run(() -> drawLights(target, n), () -> {});
    }

    private static void drawLights(RenderTarget target, int n) {
        var device = RenderSystem.getDevice();

        // Positions relative to the camera, as for all particles
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        if (meshBytes == null)
            meshBytes = new ByteBufferBuilder(64 * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        var builder = new BufferBuilder(meshBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int l = 0; l < n; l++) {
            int i = l * STRIDE;
            float x = (float) (lights[i] - camera.x);
            float y = (float) (lights[i + 1] - camera.y);
            float z = (float) (lights[i + 2] - camera.z);
            float r = lights[i + 3], g = lights[i + 4], b = lights[i + 5], a = lights[i + 6];
            // All four at the centre; the vertex shader spreads them out
            builder.addVertex(x, y, z).setUv(-1F, -1F).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(1F, -1F).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(1F, 1F).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(-1F, 1F).setColor(r, g, b, a);
        }

        int indexCount;
        try (var mesh = builder.build()) {
            if (mesh == null)
                return;
            indexCount = mesh.drawState().indexCount();
            uploadVertices(mesh.vertexBuffer());
        }

        if (lightInfo == null)
            lightInfo = new MappableRingBuffer(() -> "Dynamic Surroundings firefly light UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, FIREFLY_LIGHT_INFO_SIZE);
        var zeroToOne = device.getDeviceInfo().isZZeroToOne();
        try (var view = lightInfo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(zeroToOne ? 1F : 0F).putFloat(RADIUS);
        }

        var transforms = RenderSystem.getDynamicUniforms().writeTransform(RenderSystem.getModelViewMatrixCopy());
        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = device.createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings firefly lights", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            SoftParticles.bindSceneDepth(pass);
            pass.setUniform(FIREFLY_LIGHT_INFO, lightInfo.currentBuffer());
            pass.setVertexBuffer(0, vertexBuffer.slice());
            pass.setIndexBuffer(indexBuffer, indices.type());
            pass.drawIndexed(indexCount, 1, 0, 0, 0);
        }
        lightInfo.rotate();
    }

    private static void uploadVertices(ByteBuffer vertices) {
        var device = RenderSystem.getDevice();
        if (vertexBuffer == null || vertexBuffer.size() < vertices.remaining()) {
            if (vertexBuffer != null)
                vertexBuffer.close();
            vertexBuffer = device.createBuffer(() -> "Dynamic Surroundings firefly light vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices.remaining());
        }
        device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertices);
    }
}
