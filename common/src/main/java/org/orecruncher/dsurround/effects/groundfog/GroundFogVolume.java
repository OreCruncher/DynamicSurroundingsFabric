package org.orecruncher.dsurround.effects.groundfog;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.lwjgl.opengl.GL11;
import org.orecruncher.dsurround.config.GroundFogQuality;
import org.orecruncher.dsurround.effects.GpuTimer;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * Draws the ground fog as a volume: a pass over the screen that follows each pixel's line of sight from the camera
 * to what it shows, adding up the mist along the way. There is mist where the line passes within
 * {@link #MIST_DEPTH} blocks of the ground, thickest at the ground; how much depends on the biome, and drifting noise
 * breaks it into patches. So it thickens the more of it is looked through, fills hollows and pools at the foot of
 * cliffs, and has no edges to show.
 * <p>
 * That pass is drawn at a share of the screen's size (half, for {@link GroundFogQuality#FANCY}), to a target of its
 * own: at half, a quarter of the pixels to follow. A second
 * ({@link #SMOOTH}), at that size, blends each pixel's neighbours, which smooths away the stipple of their steps being
 * offset a little differently, but only those that looked about as far, so the mist doesn't smear across the edges of
 * what is in front of it. A third ({@link #COMPOSITE}) lays it over the scene at full size, the same way between the
 * two by two around each pixel.
 * <p>
 * The grid ({@link GroundFogField}) is handed to the shader as a small texture, a texel a column: the ground's height
 * above a base just below the grid's lowest (smoothed, see {@link GroundFogField#smoothHeight}), the mist's thickness,
 * and the block and sky light there. Render thread only.
 */
public final class GroundFogVolume {

    /**
     * Draws the mist, at a share of the screen's size. Without it, there is none.
     */
    public static final ModShader SHADER = new ModShader("ground_fog_volume", DefaultVertexFormat.POSITION,
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Smooths the mist, at the size it was drawn. Without it, there is no ground fog either.
     */
    public static final ModShader SMOOTH = new ModShader("ground_fog_smooth", DefaultVertexFormat.POSITION,
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Lays the smoothed mist over the scene. Without it, there is no ground fog either.
     */
    public static final ModShader COMPOSITE = new ModShader("ground_fog_composite", DefaultVertexFormat.POSITION,
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * How long the GPU takes over all the passes, for the diagnostics overlay, and over each: the march (with the depth
     * copy it reads), the smoothing and the composite
     */
    public static final GpuTimer GPU_TIMER = new GpuTimer("Ground Fog GPU");
    public static final GpuTimer MARCH_TIMER = new GpuTimer("- March");
    public static final GpuTimer SMOOTH_TIMER = new GpuTimer("- Smooth");
    public static final GpuTimer COMPOSITE_TIMER = new GpuTimer("- Composite");

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
     * precision far from the world's middle. A whole number of times the noise texture repeats ({@link #NOISE_SIZE}
     * cells) for each of the shader's noises (1.75, 3.5, 7 and 11 blocks a cell), so a wrap lands on the same noise
     * and can't be seen.
     */
    public static final int NOISE_WRAP = 4620;
    /**
     * The noise texture's size, in cells, which it repeats at: a random value a texel, read smoothly between them.
     * One read for each noise in place of working out the four corners of its cell.
     */
    public static final int NOISE_SIZE = 60;

    @Nullable
    private static DynamicTexture grid;
    @Nullable
    private static DynamicTexture noise;
    private static int gridSize;
    // What is in the texture: the field's version it was filled from, and the base and highest ground under any mist
    // worked out then. Filled again only when the field has changed, which is at most once a tick
    private static int gridVersion;
    private static int gridBaseY;
    private static float gridHighest;
    // Where the mist is drawn, at a share of the screen's size: its colour, already multiplied by its alpha, and the
    // depth where it begins
    @Nullable
    private static TextureTarget marched;
    // The same, smoothed
    @Nullable
    private static TextureTarget smoothed;

    private GroundFogVolume() {
    }

    /**
     * Whether the ground fog can be drawn: its shaders loaded, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return SHADER.isUsable() && SMOOTH.isUsable() && COMPOSITE.isUsable();
    }

    /**
     * Draws the mist where the weather is drawn, reading a copy of that target's depth to know how far each pixel's
     * line of sight goes
     *
     * @param target   where the weather is being drawn
     * @param quality  how finely: the share of the screen's size the mist is drawn at, and the most steps (not
     *                 {@link GroundFogQuality#NONE}, which isn't drawn)
     * @param fabulous whether Fabulous graphics are on: the weather's target is then laid over the scene by vanilla's
     *                 transparency pass, by its depth, and the mist is drawn after the rain (see the handler)
     * @param strength how strong the mist is for the conditions, 0 to 1
     * @param driftX  how far the mist has drifted east-west, in blocks, and {@code driftZ} north-south
     * @param seconds a running time, for the mist to churn
     * @return true if it was drawn, or there was nothing to draw
     */
    public static boolean render(GroundFogField field, Camera camera, RenderTarget target, boolean fabulous,
                                 GroundFogQuality quality, float strength,
                                 float driftX, float driftZ, float seconds) {
        var shader = SHADER.get();
        var smooth = SMOOTH.get();
        var composite = COMPOSITE.get();
        if (!quality.isEnabled() || field.isEmpty() || shader == null || smooth == null || composite == null || !isUsable())
            return true;

        var cam = camera.getPosition();
        if (grid == null || gridSize != field.size() || gridVersion != field.version()) {
            gridBaseY = Mth.floor(lowestGround(field)) - HEIGHT_BASE_MARGIN;
            gridHighest = upload(field, gridBaseY);
            gridVersion = field.version();
        }
        int baseY = gridBaseY;
        float top = gridHighest + MIST_DEPTH;

        // From where things are, relative to the camera, to the screen, and back
        var forward = new Matrix4f(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix());
        var inverse = new Matrix4f(forward).invert();
        int scale = quality.scale();
        marched = reducedFor(marched, target, scale);
        smoothed = reducedFor(smoothed, target, scale);
        var reduced = marched;
        var smoothedHalf = smoothed;
        // How far along the view each pixel's line of sight goes, from its depth: two rows of the projection's inverse,
        // for a pixel at (x, y, depth) on the screen
        var unproject = new Matrix4f(RenderSystem.getProjectionMatrix()).invert();

        var tesselator = Tesselator.getInstance();
        RenderSystem.disableCull();
        try {
            return SHADER.run(() -> {
                // How far each pixel's line of sight goes: the scene's depth, water's surface included, which with
                // Fabulous graphics isn't in the weather's target. Both passes read it
                GPU_TIMER.begin();
                MARCH_TIMER.begin();
                int sceneDepth = SoftParticles.copySceneDepth(SoftParticles.sceneDepthTarget(), target);

                // The mist at a share of the screen's size, replacing what was there: its colour and where it begins
                reduced.clear(Minecraft.ON_OSX);
                reduced.bindWrite(true);
                RenderSystem.disableBlend();
                RenderSystem.enableDepthTest();
                RenderSystem.depthFunc(GL11.GL_ALWAYS);
                RenderSystem.depthMask(true);
                shader.setSampler("DepthSampler", sceneDepth);
                shader.safeGetUniform("FullSize").set((float) target.width, (float) target.height);
                shader.safeGetUniform("Scale").set((float) scale);
                shader.safeGetUniform("MaxSteps").set((float) quality.maxSteps());
                shader.safeGetUniform("ViewProj").set(forward);
                shader.setSampler("FogGrid", grid.getId());
                shader.setSampler("NoiseSampler", noiseTexture().getId());
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
                // The mist thins to nothing a block inside the area's edge, from where the camera is: beyond that, the
                // field has no cells (see GroundFogField.prepare)
                float fadeWidth = field.radius() * GroundFogField.EDGE_FADE;
                shader.safeGetUniform("EdgeFade").set(field.radius() - 1F, fadeWidth);
                shader.safeGetUniform("MistTop").set(top - baseY);
                shader.safeGetUniform("MistDepth").set(MIST_DEPTH);
                shader.safeGetUniform("Thickness").set(THICKNESS * strength);
                RenderSystem.setShader(SHADER.supplier());
                drawScreenQuad(tesselator);
                MARCH_TIMER.end();

                // Smoothed at that size, replacing what was there: its colour and where it begins, as before
                SMOOTH_TIMER.begin();
                smoothedHalf.clear(Minecraft.ON_OSX);
                smoothedHalf.bindWrite(true);
                smooth.setSampler("MistSampler", reduced.getColorTextureId());
                smooth.setSampler("MistDepthSampler", reduced.getDepthTextureId());
                smooth.setSampler("DepthSampler", sceneDepth);
                smooth.safeGetUniform("DepthRowZ").set(unproject.m02(), unproject.m12(), unproject.m22(), unproject.m32());
                smooth.safeGetUniform("DepthRowW").set(unproject.m03(), unproject.m13(), unproject.m23(), unproject.m33());
                smooth.safeGetUniform("FullSize").set((float) target.width, (float) target.height);
                smooth.safeGetUniform("Scale").set((float) scale);
                RenderSystem.setShader(SMOOTH.supplier());
                drawScreenQuad(tesselator);
                SMOOTH_TIMER.end();

                // Laid over the scene at full size. Its colour is already multiplied by its alpha, so "over": with
                // Fabulous graphics the weather's target keeps an alpha the transparency pass can use, as it takes
                // the target's colours as already multiplied. And there, the depth where the mist begins along each
                // line of sight, which the pass sorts the layers by: with none, the target is as far as can be, and
                // the scene is laid over it
                target.bindWrite(true);
                RenderSystem.enableBlend();
                RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
                if (fabulous) {
                    RenderSystem.enableDepthTest();
                    RenderSystem.depthFunc(GL11.GL_LEQUAL);
                    RenderSystem.depthMask(true);
                } else {
                    RenderSystem.disableDepthTest();
                    RenderSystem.depthMask(false);
                }
                COMPOSITE_TIMER.begin();
                composite.setSampler("MistSampler", smoothedHalf.getColorTextureId());
                composite.setSampler("MistDepthSampler", smoothedHalf.getDepthTextureId());
                composite.setSampler("DepthSampler", sceneDepth);
                composite.safeGetUniform("DepthRowZ").set(unproject.m02(), unproject.m12(), unproject.m22(), unproject.m32());
                composite.safeGetUniform("DepthRowW").set(unproject.m03(), unproject.m13(), unproject.m23(), unproject.m33());
                composite.safeGetUniform("FullSize").set((float) target.width, (float) target.height);
                composite.safeGetUniform("Scale").set((float) scale);
                composite.safeGetUniform("WriteDepth").set(fabulous ? 1F : 0F);
                RenderSystem.setShader(COMPOSITE.supplier());
                drawScreenQuad(tesselator);
                COMPOSITE_TIMER.end();
                GPU_TIMER.end();
            }, () -> {
                MARCH_TIMER.end();
                SMOOTH_TIMER.end();
                COMPOSITE_TIMER.end();
                GPU_TIMER.end();
                tesselator.clear();
                target.bindWrite(true);
            });
        } finally {
            RenderSystem.enableCull();
            RenderSystem.enableDepthTest();
            RenderSystem.depthFunc(GL11.GL_LEQUAL);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
            RenderSystem.depthMask(true);
        }
    }

    /**
     * A quad over the whole of what is being drawn to
     */
    private static void drawScreenQuad(Tesselator tesselator) {
        var builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.addVertex(-1F, -1F, 0F);
        builder.addVertex(1F, -1F, 0F);
        builder.addVertex(1F, 1F, 0F);
        builder.addVertex(-1F, 1F, 0F);
        BufferUploader.drawWithShader(builder.buildOrThrow());
    }

    /**
     * A target at a share of {@code target}'s size, created or resized, rounded up: a pixel for each {@code scale} by
     * {@code scale} of its
     *
     * @param existing the one there is, if any
     */
    private static TextureTarget reducedFor(@Nullable TextureTarget existing, RenderTarget target, int scale) {
        int width = (target.width + scale - 1) / scale;
        int height = (target.height + scale - 1) / scale;
        if (existing == null) {
            existing = new TextureTarget(width, height, true, Minecraft.ON_OSX);
            existing.setClearColor(0F, 0F, 0F, 0F);
        } else if (existing.width != width || existing.height != height) {
            existing.resize(width, height, Minecraft.ON_OSX);
        }
        return existing;
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
        grid.upload();
        // Read smoothly between columns, ground heights and thickness both: the mist rounds off over ledges. After
        // uploading, which sets it to be read a texel at a time
        grid.setFilter(true, false);
        return highest;
    }

    /**
     * The noise texture, made the first time: a random value a texel, the same each time, read smoothly between texels
     * and repeating
     */
    private static DynamicTexture noiseTexture() {
        if (noise == null) {
            noise = new DynamicTexture(NOISE_SIZE, NOISE_SIZE, false);
            var pixels = noise.getPixels();
            var random = Randomizer.create(0x6D697374L);
            for (int j = 0; j < NOISE_SIZE; j++) {
                for (int i = 0; i < NOISE_SIZE; i++) {
                    int value = random.nextInt(256);
                    pixels.setPixelRGBA(i, j, 0xFF000000 | value);
                }
            }
            noise.upload();
            noise.setFilter(true, false);
        }
        return noise;
    }
}
