package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
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
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Small point lights, lighting the grass, leaves and ground near them: around fireflies, and the flicker of a flame
 * jet on its surroundings. Each is drawn with a shader that works out from the scene's depth where each pixel near the
 * light is, and adds light by how close it is and whether it faces the light. Being added on top of the scene, it
 * doesn't change Minecraft's lighting, and it can't cast shadows.
 * <p>
 * Particles {@link #add} their light as they are extracted for drawing, and the lights are all drawn together just
 * before the translucent particles ({@link #draw}), in a pass of their own (see MixinQuadParticleFeatureRenderer).
 * <p>
 * Without the shader ({@link #SHADER}), or while an Iris shader pack is in use, there are no lights. Everything here
 * runs on the render thread.
 */
public final class PointLights {

    private static final String POINT_LIGHT_INFO = "PointLightInfo";
    private static final int POINT_LIGHT_INFO_SIZE = new Std140SizeCalculator().putFloat().get();
    private static final BindGroupLayout POINT_LIGHT_LAYOUT = BindGroupLayout.builder()
            .withUniform(POINT_LIGHT_INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(SoftParticles.DEPTH_SAMPLER)
            .build();

    // The shader finds what each light reaches itself, so nothing is tested against depth; and its squares may face
    // either way. The shader gives its light already scaled by its strength, with next to no alpha, and both are
    // simply added: with Fabulous graphics the particles' target is laid over the scene as behind * (1 - alpha) +
    // colour, and an alpha that piled up would replace the lit ground with the light's colour, a flat white. Not no
    // alpha at all, or that pass skips the pixel.
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/point_light"))
            .withVertexShader(Constants.asId("core/point_light"))
            .withFragmentShader(Constants.asId("core/point_light"))
            .withBindGroupLayout(BindGroupLayouts.GLOBALS)
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(POINT_LIGHT_LAYOUT)
            .withColorTargetState(new ColorTargetState(new BlendFunction(BlendFactor.ONE, BlendFactor.ONE)))
            .withCull(false)
            .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "point lights", "fireflies and flame jets will not light their surroundings", Library.LOGGER);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Point Lights");

    /**
     * How far a firefly's light reaches, in blocks.
     */
    public static final float FIREFLY_RADIUS = 1.75F;
    /**
     * Strength of a firefly's light at its brightest, 0 to 1.
     */
    public static final float FIREFLY_STRENGTH = 0.45F;

    /**
     * The most lights drawn: the nearest, which are the brightest on screen and cost the most to draw (each is a
     * square over the pixels it may light, the whole screen when the camera is inside its reach). A swamp full of
     * fireflies could otherwise draw hundreds.
     */
    static final int MAX_DRAWN = 64;
    /**
     * From this far down the nearest, the lights fade, to nothing at {@link #MAX_DRAWN}: one moving across that limit
     * fades in or out rather than switching.
     */
    static final int FADE_FROM = 48;
    // A light this faint changes no pixel: under half a step of an 8 bit colour, at its brightest
    static final float MIN_STRENGTH = 0.5F / 255F;

    // Lights recorded longer ago than this weren't from this frame (nothing lit was drawn), so are dropped
    private static final long STALE_NANOS = 250_000_000L;
    // x, y, z (relative to the camera: small enough for a float wherever the world is), red, green, blue, strength,
    // radius
    static final int STRIDE = 8;

    private static float[] lights = new float[STRIDE * 64];
    // For sorting the lights nearest first: each one's distance and index, and the lights in that order
    private static long[] sortKeys = new long[64];
    private static float[] sorted = new float[STRIDE * 64];
    private static int count;
    private static long lastAdded;

    // Created on first use, on the render thread
    @Nullable
    private static ByteBufferBuilder meshBytes;
    @Nullable
    private static GpuBuffer vertexBuffer;
    @Nullable
    private static MappableRingBuffer lightInfo;

    private PointLights() {
    }

    /**
     * Whether lights can be drawn: the shader compiled, drawing hasn't failed, and no shader pack is in use. Each kind
     * of light also has its own option.
     */
    public static boolean isAvailable() {
        return SHADER.isUsable();
    }

    /**
     * Whether fireflies light their surroundings
     */
    public static boolean fireflyLightsOn() {
        return Client.Config.fireflyOptions.enableLight && isAvailable();
    }

    /**
     * Whether flame jets light their surroundings
     */
    public static boolean flameLightsOn() {
        return Client.Config.flameJetOptions.enableLight && isAvailable();
    }

    /**
     * Whether any kind of light is being drawn
     */
    public static boolean isActive() {
        return fireflyLightsOn() || flameLightsOn();
    }

    /**
     * The frame is done: any lights not drawn (no translucent particles were) are dropped, not carried into the next,
     * where they would be placed from where the camera was
     */
    public static void endFrame() {
        count = 0;
    }

    /**
     * Whether there are lights waiting to be drawn.
     */
    public static boolean hasLights() {
        return count > 0;
    }

    /**
     * Records a light to draw. Kept relative to the camera, which is where it is drawn from this frame: far from the
     * world's middle, a float can't hold a position in the world finely enough, and the lights would jitter.
     *
     * @param x        where, in the world
     * @param strength 0 to 1
     * @param radius   how far it reaches, in blocks
     */
    public static void add(double x, double y, double z, float red, float green, float blue, float strength, float radius) {
        if (strength < MIN_STRENGTH || radius <= 0F)
            return;
        if ((count + 1) * STRIDE > lights.length)
            lights = Arrays.copyOf(lights, lights.length * 2);
        int i = count * STRIDE;
        var camera = Minecraft.getInstance().gameRenderer.mainCamera().position();
        lights[i] = (float) (x - camera.x);
        lights[i + 1] = (float) (y - camera.y);
        lights[i + 2] = (float) (z - camera.z);
        lights[i + 3] = red;
        lights[i + 4] = green;
        lights[i + 5] = blue;
        lights[i + 6] = strength;
        lights[i + 7] = radius;
        count++;
        lastAdded = System.nanoTime();
    }

    /**
     * Draws the lights recorded since the last call into {@code target}, then forgets them. Called outside any render
     * pass, after the scene's depth has been captured.
     *
     * @param transforms the camera's, as the particles are drawn with them (see MixinQuadParticleFeatureRenderer)
     */
    public static void draw(RenderTarget target, GpuBufferSlice transforms) {
        int n = count;
        count = 0;
        if (n == 0 || System.nanoTime() - lastAdded > STALE_NANOS || !isAvailable() || !SoftParticles.hasSceneDepth())
            return;
        RENDER_TIMER.begin();
        SHADER.run(() -> drawLights(target, n, transforms), () -> {});
        RENDER_TIMER.end();
    }

    private static void drawLights(RenderTarget target, int n, GpuBufferSlice transforms) {
        var device = RenderSystem.getDevice();

        // Enough that some fade or are left out: the nearest first. Their positions are already relative to the camera
        if (n > FADE_FROM)
            sortNearest(lights, n, 0D, 0D, 0D);
        if (meshBytes == null)
            meshBytes = new ByteBufferBuilder(64 * 4 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize());
        var builder = new BufferBuilder(meshBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
        for (int l = 0; l < Math.min(n, MAX_DRAWN); l++) {
            int i = l * STRIDE;
            float a = lights[i + 6] * rankFade(l);
            if (a < MIN_STRENGTH)
                continue;
            float x = lights[i], y = lights[i + 1], z = lights[i + 2];
            float r = lights[i + 3], g = lights[i + 4], b = lights[i + 5];
            // All four at the centre; the vertex shader spreads them out. Each corner is given at the light's
            // radius, which is how the shader knows it.
            float radius = lights[i + 7];
            builder.addVertex(x, y, z).setUv(-radius, -radius).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(radius, -radius).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(radius, radius).setColor(r, g, b, a);
            builder.addVertex(x, y, z).setUv(-radius, radius).setColor(r, g, b, a);
        }

        int indexCount;
        try (var mesh = builder.build()) {
            if (mesh == null)
                return;
            indexCount = mesh.drawState().indexCount();
            uploadVertices(mesh.vertexBuffer());
        }

        if (lightInfo == null)
            lightInfo = new MappableRingBuffer(() -> "Dynamic Surroundings point light UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, POINT_LIGHT_INFO_SIZE);
        var zeroToOne = device.getDeviceInfo().isZZeroToOne();
        try (var view = lightInfo.currentBuffer().map(false, true)) {
            Std140Builder.intoBuffer(view.data()).putFloat(zeroToOne ? 1F : 0F);
        }

        var indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        var indexBuffer = indices.getBuffer(indexCount);

        try (RenderPass pass = device.createCommandEncoder()
                .createRenderPass(() -> "Dynamic Surroundings point lights", target.getColorTextureView(), Optional.empty(), target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            SoftParticles.bindSceneDepth(pass);
            pass.setUniform(POINT_LIGHT_INFO, lightInfo.currentBuffer());
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
            vertexBuffer = device.createBuffer(() -> "Dynamic Surroundings point light vertices", GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, vertices.remaining());
        }
        device.createCommandEncoder().writeToBuffer(vertexBuffer.slice(), vertices);
    }

    /**
     * How much the light {@code rank} lights down the nearest is drawn at: in full up to {@link #FADE_FROM}, fading
     * to nothing at {@link #MAX_DRAWN}.
     */
    static float rankFade(int rank) {
        if (rank < FADE_FROM)
            return 1F;
        return Math.max(0F, (float) (MAX_DRAWN - rank) / (MAX_DRAWN - FADE_FROM));
    }

    /**
     * Puts the first {@code n} lights in {@code lights} in order of how near each is to the camera, nearest first.
     */
    static void sortNearest(float[] lights, int n, double cameraX, double cameraY, double cameraZ) {
        if (sortKeys.length < n)
            sortKeys = new long[lights.length / STRIDE];
        if (sorted.length < n * STRIDE)
            sorted = new float[lights.length];
        // Each light's distance squared, then its index: a float's bits order as it does when it isn't negative
        for (int l = 0; l < n; l++) {
            int i = l * STRIDE;
            double x = lights[i] - cameraX, y = lights[i + 1] - cameraY, z = lights[i + 2] - cameraZ;
            sortKeys[l] = ((long) Float.floatToIntBits((float) (x * x + y * y + z * z)) << 32) | l;
        }
        Arrays.sort(sortKeys, 0, n);
        for (int r = 0; r < n; r++)
            System.arraycopy(lights, (int) sortKeys[r] * STRIDE, sorted, r * STRIDE, STRIDE);
        System.arraycopy(sorted, 0, lights, 0, n * STRIDE);
    }
}
