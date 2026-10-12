package org.orecruncher.dsurround.effects.groundfog;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.Library;

/**
 * Draws the ground fog as a volume: one pass over the screen that follows each pixel's line of sight from the camera
 * to what it shows, adding up the mist along the way. There is mist where the line passes within
 * {@link #MIST_DEPTH} blocks of the ground, thickest at the ground; how much depends on the biome, and drifting noise
 * breaks it into patches. So it thickens the more of it is looked through, fills hollows and pools at the foot of
 * cliffs, and has no edges to show.
 * <p>
 * The grid ({@link GroundFogField}) is handed to the shader as a small texture, a texel a column: the ground's height
 * above a base just below the grid's lowest (smoothed, see {@link GroundFogField#smoothHeight}), the mist's thickness,
 * and the block and sky light there. Render thread only.
 */
public final class GroundFogVolume {

    /**
     * Draws the mist. Without it, there is none.
     */
    public static final ModShader SHADER = new ModShader("ground_fog_volume", DefaultVertexFormat.POSITION,
            "ground fog", "there will be no ground fog", Library.LOGGER);

    /**
     * How deep the mist is over the ground, in blocks: it thins to nothing at this height
     */
    static final float MIST_DEPTH = 1.8F;
    /**
     * How quickly the mist hides what is behind it, for each block of it looked through at full thickness
     */
    static final float THICKNESS = 0.5F;
    /**
     * Ground heights are kept in a byte, relative to a base this many blocks below the lowest ground in the grid: so
     * they fit wherever the camera is, high above it or deep below. Ground more than a byte's worth above the base (a
     * grid spanning a mountain's height) is held at the top.
     */
    private static final int HEIGHT_BASE_MARGIN = 2;
    /**
     * Where things are east-west and north-south is wrapped at this many blocks for the noise, to keep it to a float's
     * precision far from the world's middle. A whole number of cells of each of the shader's noises (3.5, 7 and 11
     * blocks across), which repeat at it, so a wrap lands on the same noise and can't be seen.
     */
    public static final int NOISE_WRAP = 4620;

    @Nullable
    private static DynamicTexture grid;
    private static int gridSize;

    private GroundFogVolume() {
    }

    /**
     * Draws the mist where the weather is drawn, reading a copy of that target's depth to know how far each pixel's
     * line of sight goes
     *
     * @param target   where the weather is being drawn
     * @param fabulous whether Fabulous graphics are on: the weather's target is then laid over the scene by vanilla's
     *                 transparency pass, by its depth, and the mist is drawn after the rain (see the handler)
     * @param strength how strong the mist is for the conditions, 0 to 1
     * @param driftX  how far the mist has drifted east-west, in blocks, and {@code driftZ} north-south
     * @param seconds a running time, for the mist to churn
     * @return true if it was drawn, or there was nothing to draw
     */
    public static boolean render(GroundFogField field, Camera camera, RenderTarget target, boolean fabulous, float strength,
                                 float driftX, float driftZ, float seconds) {
        var shader = SHADER.get();
        if (field.isEmpty() || shader == null || !SHADER.isUsable())
            return true;

        var cam = camera.getPosition();
        int baseY = Mth.floor(lowestGround(field)) - HEIGHT_BASE_MARGIN;
        float top = upload(field, baseY) + MIST_DEPTH;

        // From where things are, relative to the camera, to the screen, and back
        var forward = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        var inverse = new Matrix4f(forward).invert();

        var tesselator = Tesselator.getInstance();
        RenderSystem.disableCull();
        RenderSystem.enableBlend();
        if (fabulous) {
            // Over what is already in the weather's target (the rain): its alpha kept, as the transparency pass takes
            // the target's colours as already multiplied by their alpha. And the depth where the mist begins along
            // each line of sight, which the pass sorts the layers by: with none, the target is as far as can be, and
            // the scene is laid over it
            RenderSystem.blendFuncSeparate(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA,
                    GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.depthMask(true);
        } else {
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
        }
        try {
            return SHADER.run(() -> {
                // How far each pixel's line of sight goes: the scene's depth, water's surface included, which with
                // Fabulous graphics isn't in the weather's target
                SoftParticles.bindSceneDepth(shader, SoftParticles.sceneDepthTarget(), target);
                shader.safeGetUniform("ViewProj").set(forward);
                shader.safeGetUniform("WriteDepth").set(fabulous ? 1F : 0F);
                shader.setSampler("FogGrid", grid.getId());
                shader.safeGetUniform("InvViewProj").set(inverse);
                // The camera, in the grid's terms: a block to a texel, heights from the base
                shader.safeGetUniform("CameraLocal").set((float) (cam.x - field.cellX(0)), (float) (cam.y - baseY),
                        (float) (cam.z - field.cellZ(0)));
                shader.safeGetUniform("NoiseOrigin").set((float) Math.floorMod(field.cellX(0), NOISE_WRAP),
                        (float) Math.floorMod(field.cellZ(0), NOISE_WRAP));
                shader.safeGetUniform("Drift").set(driftX, driftZ, seconds);
                // Where the base is in the world, so the noise stays put as the camera goes up and down. Not wrapped:
                // heights are small, and a wrap would shift the noise
                shader.safeGetUniform("BaseY").set((float) baseY);
                shader.safeGetUniform("GridSize").set((float) gridSize);
                shader.safeGetUniform("MistTop").set(top - baseY);
                shader.safeGetUniform("MistDepth").set(MIST_DEPTH);
                shader.safeGetUniform("Thickness").set(THICKNESS * strength);
                RenderSystem.setShader(SHADER.supplier());

                // A quad over the whole screen
                var builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
                builder.addVertex(-1F, -1F, 0F);
                builder.addVertex(1F, -1F, 0F);
                builder.addVertex(1F, 1F, 0F);
                builder.addVertex(-1F, 1F, 0F);
                BufferUploader.drawWithShader(builder.buildOrThrow());
            }, () -> {
                tesselator.clear();
                target.bindWrite(false);
            });
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
    }

    /**
     * The lowest the smoothed ground is anywhere in the grid
     */
    private static float lowestGround(GroundFogField field) {
        int size = field.size();
        float lowest = Float.POSITIVE_INFINITY;
        for (int j = 0; j < size; j++)
            for (int i = 0; i < size; i++)
                lowest = Math.min(lowest, field.smoothHeight(i, j));
        return lowest;
    }

    /**
     * Puts the grid in the texture: a texel a column, red the smoothed ground's height above the base in whole blocks
     * and alpha the part of a block over, green the mist's thickness, blue the block and sky light
     *
     * @return the highest ground under any mist, in blocks
     */
    private static float upload(GroundFogField field, int baseY) {
        int size = field.size();
        if (grid == null || gridSize != size) {
            if (grid != null)
                grid.close();
            grid = new DynamicTexture(size, size, true);
            // Read smoothly between columns, ground heights and thickness both: the mist rounds off over ledges
            grid.setFilter(true, false);
            gridSize = size;
        }

        float highest = Float.NEGATIVE_INFINITY;
        var pixels = grid.getPixels();
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float density = field.density(i, j);
                float height = field.smoothHeight(i, j);
                if (density > 0F)
                    highest = Math.max(highest, height);
                float above = Mth.clamp(height - baseY, 0F, 255.99F);
                int r = Mth.floor(above);
                int a = Mth.clamp(Math.round((above - r) * 255F), 0, 255);
                int g = Mth.clamp(Math.round(density * 255F), 0, 255);
                int light = field.light(i, j);
                // Block light and sky light, 0 to 15 each, together in a byte
                int b = ((light >> 4) & 0xF) << 4 | ((light >> 20) & 0xF);
                // Packed as ABGR
                pixels.setPixelRGBA(i, j, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        // Every frame: it is small, and the base moves with the grid
        grid.upload();
        return highest;
    }
}
