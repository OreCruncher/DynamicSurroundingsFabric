package org.orecruncher.dsurround.effects.aurora;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;

import java.util.function.Supplier;

/**
 * Draws an aurora into the sky: each curtain a strip of quads, its lower edge following the curtain's path and its
 * top above that, filled in by the aurora shader. Rebuilt every frame, since the curtains ripple; at a few hundred
 * vertices that costs next to nothing.
 * <p>
 * The shader is registered by each platform and handed over with {@link #onShaderLoaded}. Without it there is no
 * aurora. Everything here runs on the render thread.
 */
public final class AuroraRenderer {

    public static final ResourceLocation SHADER_ID = Constants.asId("aurora");

    // Quads along each curtain
    private static final int SEGMENTS = 96;

    @Nullable
    private static ShaderInstance shader;
    private static final Supplier<ShaderInstance> SHADER_SUPPLIER = () -> shader;

    private static final Vector3f BOTTOM = new Vector3f();
    private static final Vector3f TOP = new Vector3f();

    private AuroraRenderer() {
    }

    public static void onShaderLoaded(ShaderInstance loaded) {
        shader = loaded;
        Library.LOGGER.info("Loaded the %s shader", SHADER_ID);
    }

    public static void onShaderFailed(Throwable error) {
        shader = null;
        Library.LOGGER.error(error, "Unable to load the %s shader; auroras will not be drawn", SHADER_ID);
    }

    public static boolean isAvailable() {
        return shader != null;
    }

    /**
     * Draws the aurora.
     *
     * @param aurora        what to draw
     * @param alpha         how strongly, 0 to 1
     * @param frustumMatrix the camera's rotation, from the sky rendering
     * @param time          seconds, for the animation
     */
    public static void render(Aurora aurora, float alpha, Matrix4f frustumMatrix, float time) {
        var current = shader;
        if (current == null || alpha <= 0F)
            return;

        var palette = aurora.palette();
        current.safeGetUniform("AuroraTime").set(time);
        current.safeGetUniform("BottomColor").set(palette.bottom().red(), palette.bottom().green(), palette.bottom().blue());
        current.safeGetUniform("MiddleColor").set(palette.middle().red(), palette.middle().green(), palette.middle().blue());
        current.safeGetUniform("TopColor").set(palette.top().red(), palette.top().green(), palette.top().blue());
        current.safeGetUniform("Alpha").set(Math.clamp(alpha * aurora.brightness(), 0F, 1F));
        RenderSystem.setShader(SHADER_SUPPLIER);

        // Light adds to the sky behind it. Curtains are seen from both sides, and nothing is drawn into the depth
        // buffer, so the world drawn after the sky covers them.
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.disableCull();
        RenderSystem.depthMask(false);

        // The model view matrix is the identity while the sky is drawn, so the camera's rotation goes into the
        // vertices, as it does for the sun and moon
        var builder = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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

                vertex(builder, frustumMatrix, pbx, pby, pbz, prevU, 0F, prevA);
                vertex(builder, frustumMatrix, BOTTOM.x, BOTTOM.y, BOTTOM.z, u, 0F, a);
                vertex(builder, frustumMatrix, TOP.x, TOP.y, TOP.z, u, 1F, a);
                vertex(builder, frustumMatrix, ptx, pty, ptz, prevU, 1F, prevA);

                pbx = BOTTOM.x; pby = BOTTOM.y; pbz = BOTTOM.z;
                ptx = TOP.x; pty = TOP.y; ptz = TOP.z;
                prevU = u;
                prevA = a;
            }
        }

        var mesh = builder.build();
        if (mesh != null)
            BufferUploader.drawWithShader(mesh);

        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
    }

    private static void vertex(BufferBuilder builder, Matrix4f pose, float x, float y, float z, float u, float v, float alpha) {
        builder.addVertex(pose, x * Aurora.SCALE, y * Aurora.SCALE, z * Aurora.SCALE).setUv(u, v).setColor(1F, 1F, 1F, alpha);
    }
}
