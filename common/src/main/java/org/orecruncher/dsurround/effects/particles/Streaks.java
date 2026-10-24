package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.RenderFailSafe;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.effects.precipitation.ParticleQuads;

/**
 * Particles drawn as streaks: a thin quad from where each is back along the way it came, bright at its head and fading
 * to nothing at its tail, turned to face the camera across its length. For sparks and molten droplets.
 * <p>
 * A particle on 26.2 hands the particle engine a square facing the camera; a streak is a long quad along a path, with
 * its ends coloured differently. So a streak is {@link #add added} here as its particle is extracted for drawing,
 * relative to the camera, and all of them are drawn together just before the translucent particles ({@link #draw},
 * from MixinQuadParticleFeatureRenderer, as the point lights are), the way the flames are: soft, adding their light,
 * and with Fabulous graphics their depth first. Render thread only.
 */
public final class Streaks {

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Streaks");

    private static final ParticleQuads QUADS = new ParticleQuads();
    private static final MeshDraw DEPTH_DRAW = new MeshDraw("streak depth");
    private static final MeshDraw COLOR_DRAW = new MeshDraw("streaks");
    private static final RenderFailSafe FAIL_SAFE = new RenderFailSafe("spark and droplet streaks", Library.LOGGER);
    // Whether streaks have been added this frame
    private static boolean begun;

    private Streaks() {
    }

    /**
     * Records a streak to draw this frame.
     *
     * @param prevX     where it was last tick
     * @param x         where it is now
     * @param length    the tail's length, as a multiple of how far it moved in the last tick
     * @param halfWidth half the streak's width, in blocks
     * @param color     its red, green and blue, 0 to 1
     * @param alpha     its head's opacity; the tail fades to none
     */
    static void add(Camera camera, float partialTick,
                    double prevX, double prevY, double prevZ, double x, double y, double z,
                    float length, float halfWidth, float[] color, float alpha, int light,
                    float u0, float u1, float v0, float v1) {
        if (!begun) {
            QUADS.begin(64);
            begun = true;
        }

        // Its head, relative to the camera, and its tail trailing back along the way it came
        var cam = camera.position();
        float hx = (float) (Mth.lerp(partialTick, prevX, x) - cam.x);
        float hy = (float) (Mth.lerp(partialTick, prevY, y) - cam.y);
        float hz = (float) (Mth.lerp(partialTick, prevZ, z) - cam.z);
        float tx = hx - (float) (x - prevX) * length;
        float ty = hy - (float) (y - prevY) * length;
        float tz = hz - (float) (z - prevZ) * length;

        // Across the streak: square to its length and the line to the camera
        float lx = hx - tx, ly = hy - ty, lz = hz - tz;
        float sx = ly * hz - lz * hy, sy = lz * hx - lx * hz, sz = lx * hy - ly * hx;
        float across = sx * sx + sy * sy + sz * sz;
        if (across < 1.0E-10F) {
            // Still, or coming straight at the camera: a dot facing it
            var left = camera.leftVector();
            sx = left.x();
            sy = left.y();
            sz = left.z();
            across = 1F;
        }
        float scale = halfWidth * Mth.invSqrt(across);
        sx *= scale;
        sy *= scale;
        sz *= scale;

        int head = ParticleQuads.color(color[0], color[1], color[2], alpha);
        int tail = ParticleQuads.color(color[0], color[1], color[2], 0F);
        // Counter-clockwise as the camera sees it, as a front face is: the flames' pipeline, as vanilla's particles,
        // doesn't draw back faces (across is length x the line to the camera, so it turns the same way for every streak)
        QUADS.vertex(hx + sx, hy + sy, hz + sz, u0, v1, head, light);
        QUADS.vertex(tx + sx, ty + sy, tz + sz, u0, v0, tail, light);
        QUADS.vertex(tx - sx, ty - sy, tz - sz, u1, v0, tail, light);
        QUADS.vertex(hx - sx, hy - sy, hz - sz, u1, v1, head, light);
    }

    /**
     * Draws the streaks recorded this frame into {@code target}, then forgets them. Called outside any render pass,
     * after the scene's depth has been captured.
     *
     * @param transforms the camera's, as the particles are drawn with them (see MixinQuadParticleFeatureRenderer)
     */
    public static void draw(RenderTarget target, GpuBufferSlice transforms) {
        if (!begun)
            return;
        begun = false;
        int vertices = QUADS.vertexCount();
        if (vertices == 0)
            return;

        // Drawn as the flames are this frame: soft and adding their light, or the vanilla way without the scene's depth,
        // and with Fabulous graphics their depth first
        var colour = SoftParticles.choosePipeline(SoftParticles.FLAME.pipeline());
        var depth = SoftParticles.depthPassFor(colour);
        var minecraft = Minecraft.getInstance();
        var atlas = minecraft.getTextureManager().getTexture(TextureAtlas.LOCATION_PARTICLES);
        var lightmap = minecraft.gameRenderer.lightmap();
        var lightmapSampler = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        RENDER_TIMER.begin();
        FAIL_SAFE.run(() -> {
            if (depth != null) {
                DEPTH_DRAW.drawQuads(QUADS.vertices(), vertices, depth, target, transforms, pass -> {
                    pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
                    pass.bindTexture("Sampler2", lightmap, lightmapSampler);
                    SoftParticles.bind(pass, depth);
                });
            }
            COLOR_DRAW.drawQuads(QUADS.vertices(), vertices, colour, target, transforms, pass -> {
                pass.bindTexture("Sampler0", atlas.getTextureView(), atlas.getSampler());
                pass.bindTexture("Sampler2", lightmap, lightmapSampler);
                SoftParticles.bind(pass, colour);
            });
        }, () -> {});
        RENDER_TIMER.end();
    }

    /**
     * The frame is done: any streaks not drawn (no translucent particles were) are dropped, not carried into the next
     */
    public static void endFrame() {
        begun = false;
    }
}
