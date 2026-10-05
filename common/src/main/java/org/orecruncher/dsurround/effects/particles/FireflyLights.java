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
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.compat.IrisCompat;

import java.util.Arrays;
import java.util.function.Supplier;

/**
 * Small point lights around fireflies, lighting the grass, leaves and ground near them. Each is drawn with a shader
 * that works out from the scene's depth where each pixel near the light is, and adds light by how close it is and
 * whether it faces the light. Being added on top of the scene, it doesn't change Minecraft's lighting, and it can't
 * cast shadows.
 * <p>
 * Fireflies {@link #add} their light as they are drawn, and the lights are all drawn together just before the next
 * frame's fireflies ({@link #draw}), which is one frame late: a firefly moves about a hundredth of a block in that
 * time. Drawing them there, from the begin() of the firefly render type, lets them set up and put back the drawing
 * state they need, which a render type of their own couldn't (render types have no end()).
 * <p>
 * The shader is registered by each platform and handed over with {@link #onShaderLoaded}. Without it, or while an
 * Iris shader pack is in use, there are no lights. Everything here runs on the render thread.
 */
public final class FireflyLights {

    public static final ResourceLocation SHADER_ID = Constants.asId("firefly_light");

    /**
     * How far a firefly's light reaches, in blocks.
     */
    public static final float RADIUS = 1.75F;
    /**
     * Strength of a firefly's light at its brightest, 0 to 1.
     */
    public static final float STRENGTH = 0.45F;

    // Lights recorded longer ago than this weren't from the last frame (fireflies stopped being drawn), so are dropped
    private static final long STALE_NANOS = 250_000_000L;
    // x, y, z, red, green, blue, strength
    private static final int STRIDE = 7;

    @Nullable
    private static ShaderInstance shader;
    private static final Supplier<ShaderInstance> SHADER_SUPPLIER = () -> shader;

    private static float[] lights = new float[STRIDE * 64];
    private static int count;
    private static long lastAdded;

    private FireflyLights() {
    }

    public static void onShaderLoaded(ShaderInstance loaded) {
        shader = loaded;
        Library.LOGGER.info("Loaded the %s shader", SHADER_ID);
    }

    public static void onShaderFailed(Throwable error) {
        shader = null;
        Library.LOGGER.error(error, "Unable to load the %s shader; fireflies will not light their surroundings", SHADER_ID);
    }

    /**
     * Whether firefly lights are being drawn.
     */
    public static boolean isActive() {
        return shader != null && Client.Config.worksInProgressOptions.enableFireflyLight && !IrisCompat.isShaderPackInUse();
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
     * Draws the lights recorded since the last call, then forgets them. Leaves the drawing state as it found it,
     * apart from the shader, which is set back to the vanilla particle shader.
     */
    public static void draw(Tesselator tesselator) {
        int n = count;
        count = 0;
        var current = shader;
        if (n == 0 || current == null || System.nanoTime() - lastAdded > STALE_NANOS || !isActive())
            return;

        SoftParticles.bindSceneDepth(current);
        current.safeGetUniform("InverseProjMat").set(RenderSystem.getProjectionMatrix().invert(new Matrix4f()));
        current.safeGetUniform("LightRadius").set(RADIUS);
        RenderSystem.setShader(SHADER_SUPPLIER);

        // The shader finds what each light reaches itself, so nothing is tested against depth; and its squares
        // may face either way
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.depthMask(false);
        RenderSystem.disableDepthTest();
        RenderSystem.disableCull();

        // Positions relative to the camera, as for all particles
        var camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        var builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
        var mesh = builder.build();
        if (mesh != null)
            BufferUploader.drawWithShader(mesh);

        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(GameRenderer::getParticleShader);
    }
}
