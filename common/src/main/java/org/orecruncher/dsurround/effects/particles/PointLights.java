package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.lib.Library;

import java.util.Arrays;

/**
 * Small point lights, lighting the grass, leaves and ground near them: around fireflies, and the flicker of a flame
 * jet on its surroundings. Each is drawn with a shader that works out from the scene's depth where each pixel near the
 * light is, and adds light by how close it is and whether it faces the light. Being added on top of the scene, it
 * doesn't change Minecraft's lighting, and it can't cast shadows.
 * <p>
 * Particles {@link #add} their light as they are drawn, and the lights are all drawn together just before the next
 * frame's fireflies or flames ({@link #draw}), from the begin() of their render types, whichever comes first. That is
 * one frame late: a firefly moves about a hundredth of a block in that time. Drawing them there lets them set up and
 * put back the drawing state they need, which a render type of their own couldn't (render types have no end()).
 * <p>
 * The shader ({@link #SHADER}) is registered by each platform. Without it, or while an Iris shader pack is in use,
 * there are no lights. Everything here runs on the render thread.
 */
public final class PointLights {

    public static final ModShader SHADER = new ModShader("point_light", DefaultVertexFormat.POSITION_TEX_COLOR,
            "point lights", "fireflies and flame jets will not light their surroundings", Library.LOGGER);

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

    // Lights recorded longer ago than this weren't from the last frame (nothing lit was drawn since), so are dropped
    private static final long STALE_NANOS = 250_000_000L;
    // x, y, z, red, green, blue, strength, radius
    static final int STRIDE = 8;

    private static float[] lights = new float[STRIDE * 64];
    // For sorting the lights nearest first: each one's distance and index, and the lights in that order
    private static long[] sortKeys = new long[64];
    private static float[] sorted = new float[STRIDE * 64];
    private static int count;
    private static long lastAdded;

    private PointLights() {
    }

    /**
     * Whether lights can be drawn: the shader loaded, drawing hasn't failed, and no shader pack is in use. Each kind of
     * light also has its own option.
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
     * Records a light to draw.
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
        lights[i] = (float) x;
        lights[i + 1] = (float) y;
        lights[i + 2] = (float) z;
        lights[i + 3] = red;
        lights[i + 4] = green;
        lights[i + 5] = blue;
        lights[i + 6] = strength;
        lights[i + 7] = radius;
        count++;
        lastAdded = System.nanoTime();
    }

    /**
     * Draws the lights recorded since the last call, then forgets them. Leaves the drawing state as it found it,
     * apart from the shader, which is set back to the vanilla particle shader.
     */
    public static void draw(Tesselator tesselator) {
        int n = count;
        count = 0;
        var current = SHADER.get();
        if (n == 0 || current == null || System.nanoTime() - lastAdded > STALE_NANOS || !isAvailable())
            return;

        RENDER_TIMER.begin();
        SHADER.run(() -> drawLights(tesselator, current, n), () -> {
            // Leave things as the particles drawn next expect them
            tesselator.clear();
            SoftParticles.bindParticleTarget();
            RenderSystem.setShader(GameRenderer::getParticleShader);
        });
        RENDER_TIMER.end();
    }

    private static void drawLights(Tesselator tesselator, ShaderInstance current, int n) {
        SoftParticles.bindSceneDepth(current);
        current.safeGetUniform("InverseProjMat").set(RenderSystem.getProjectionMatrix().invert(new Matrix4f()));
        boolean fabulous = Minecraft.useShaderTransparency();
        current.safeGetUniform("AddOnly").set(fabulous ? 1F : 0F);
        RenderSystem.setShader(SHADER.supplier());

        // The shader finds what each light reaches itself, so nothing is tested against depth; and its squares
        // may face either way
        RenderSystem.enableBlend();
        if (fabulous) {
            // Light is added to the particles' own target, which the transparency pass lays over the scene as
            // behind * (1 - alpha) + colour: with the usual adding, the alpha piles up too and the lit ground is
            // replaced by the light's colour, a flat white. The shader gives its light already scaled by its strength,
            // and next to no alpha (not none, or the pass skips the pixel), and both are simply added.
            RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        } else {
            RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        }
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();
        try {
            // Positions relative to the camera, as for all particles
            var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            // Enough that some fade or are left out: the nearest first
            if (n > FADE_FROM)
                sortNearest(lights, n, camera.x, camera.y, camera.z);
            var builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
            for (int l = 0; l < Math.min(n, MAX_DRAWN); l++) {
                int i = l * STRIDE;
                float a = lights[i + 6] * rankFade(l);
                if (a < MIN_STRENGTH)
                    continue;
                float x = (float) (lights[i] - camera.x);
                float y = (float) (lights[i + 1] - camera.y);
                float z = (float) (lights[i + 2] - camera.z);
                float r = lights[i + 3], g = lights[i + 4], b = lights[i + 5];
                // All four at the centre; the vertex shader spreads them out. Each corner is given at the light's
                // radius, which is how the shader knows it.
                float radius = lights[i + 7];
                builder.addVertex(x, y, z).setUv(-radius, -radius).setColor(r, g, b, a);
                builder.addVertex(x, y, z).setUv(radius, -radius).setColor(r, g, b, a);
                builder.addVertex(x, y, z).setUv(radius, radius).setColor(r, g, b, a);
                builder.addVertex(x, y, z).setUv(-radius, radius).setColor(r, g, b, a);
            }
            var mesh = builder.build();
            if (mesh != null)
                BufferUploader.drawWithShader(mesh);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShader(GameRenderer::getParticleShader);
        }
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
