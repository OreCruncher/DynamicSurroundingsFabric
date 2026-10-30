package org.orecruncher.dsurround.effects.groundfog;

import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.DepthTestFunction;
import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.system.MemoryStack;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.GroundFogQuality;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.Transforms;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the ground fog as a volume: a pass over the screen that follows each pixel's line of sight from the camera
 * to what it shows, adding up the mist along the way. There is mist where the line passes within
 * {@link #MIST_DEPTH} blocks of the ground, thickest at the ground; how much depends on the biome, and drifting noise
 * breaks it into patches. So it thickens the more of it is looked through, fills hollows and pools at the foot of
 * cliffs, and has no edges to show.
 * <p>
 * That pass ({@link #SHADER}) is drawn at a share of the screen's size (half, for {@link GroundFogQuality#FANCY}), to
 * a target of its own: at half, a quarter of the pixels to follow. A second ({@link #SMOOTH}), at that size, blends
 * each pixel's neighbours, which smooths away the stipple of their steps being offset a little differently, but only
 * those that looked about as far, so the mist doesn't smear across the edges of what is in front of it. A third
 * ({@link #COMPOSITE}) lays it over the scene at full size, the same way between the two by two around each pixel.
 * Each is a triangle over the whole of what it draws to, with no vertex buffer; the passes share their values in one
 * uniform block ({@code GroundFogInfo}, shaders/include/ground_fog.glsl).
 * <p>
 * The grid ({@link GroundFogField}) is handed to the shader as a small texture, a texel a column: the ground's height
 * above a base just below the grid's lowest (smoothed, see {@link GroundFogField#smoothHeight}), the mist's thickness,
 * and the block and sky light there. Render thread only.
 */
public final class GroundFogVolume {

    private static final String INFO = "GroundFogInfo";
    // Each vec3 of the block and the float after it are written as one vec4: in std140 the float takes the vec3's
    // fourth slot, but Std140Builder.putVec3 always takes sixteen bytes, which would put everything after it out by four
    private static final int INFO_SIZE = new Std140SizeCalculator()
            .putVec2().putFloat().putFloat()
            .putVec4()
            .putVec2().putFloat().putFloat()
            .putVec4()
            .putVec2().putFloat().putFloat()
            .putFloat()
            .get();
    private static final String DEPTH_SAMPLER = "DepthSampler";
    private static final String FOG_GRID = "FogGrid";
    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final String MIST_SAMPLER = "MistSampler";
    private static final String MIST_DEPTH_SAMPLER = "MistDepthSampler";
    // Nothing, where no mist is drawn
    private static final int CLEAR = 0;
    // As far as can be
    private static final double CLEAR_DEPTH = 1D;

    /**
     * Draws the mist, at a share of the screen's size, replacing what was there: its colour, already multiplied by its
     * alpha, and the depth where it begins, which each pixel writes itself. Without it, there is none.
     */
    public static final ModShader SHADER = new ModShader(
            screenPass("pipeline/ground_fog_volume", "core/ground_fog_volume")
                    .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                    .withSampler("Sampler2")
                    .withUniform(INFO, UniformType.UNIFORM_BUFFER)
                    .withSampler(DEPTH_SAMPLER)
                    .withSampler(FOG_GRID)
                    .withSampler(NOISE_SAMPLER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Smooths the mist, at the size it was drawn, replacing what was there. Without it, there is no ground fog either.
     */
    public static final ModShader SMOOTH = new ModShader(
            screenPass("pipeline/ground_fog_smooth", "core/ground_fog_smooth")
                    .withUniform(INFO, UniformType.UNIFORM_BUFFER)
                    .withSampler(MIST_SAMPLER)
                    .withSampler(MIST_DEPTH_SAMPLER)
                    .withSampler(DEPTH_SAMPLER)
                    .withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST)
                    .build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * Lays the smoothed mist over the scene. Its colour is already multiplied by its alpha, so "over": with Fabulous
     * graphics the weather's target keeps an alpha the transparency pass can use, as it takes the target's colours as
     * already multiplied. Without it, there is no ground fog either.
     */
    public static final ModShader COMPOSITE = new ModShader(
            composite("pipeline/ground_fog_composite").withDepthTestFunction(DepthTestFunction.NO_DEPTH_TEST).withDepthWrite(false).build(),
            "ground fog", "there will be no ground fog", Library.LOGGER);
    /**
     * The same, with Fabulous graphics, writing the depth where the mist begins along each line of sight, which the
     * transparency pass sorts the layers by: with none, the weather's target is as far as can be, and the scene is laid
     * over it. Tested against the rain and snow already there, so drops in front of the mist stay in front.
     */
    public static final ModShader COMPOSITE_DEPTH = new ModShader(
            composite("pipeline/ground_fog_composite_depth_write").withDepthWrite(true).build(),
            "ground fog (Fabulous)", "there will be no ground fog", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, SMOOTH, COMPOSITE, COMPOSITE_DEPTH);

    /**
     * How long the GPU takes over all the passes, for the diagnostics overlay, and over each: the march (with the depth
     * copy it reads), the smoothing and the composite
     */
    public static final RenderTimer RENDER_TIMER = new RenderTimer("Ground Fog");
    public static final RenderTimer MARCH_TIMER = new RenderTimer("- March");
    public static final RenderTimer SMOOTH_TIMER = new RenderTimer("- Smooth");
    public static final RenderTimer COMPOSITE_TIMER = new RenderTimer("- Composite");

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
    // The grid texture's image, which it owns and closes
    @Nullable
    private static NativeImage gridPixels;
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
    // One buffer rewritten each frame, not a MappableRingBuffer: rotating that one sets a fence, which held the GPU idle
    // for 2 ms a frame after the aurora on 1.21.11 (AMD)
    @Nullable
    private static GpuBuffer info;

    private GroundFogVolume() {
    }

    /**
     * Whether the ground fog can be drawn: its pipelines compiled, drawing hasn't failed, and no shader pack is in use
     */
    public static boolean isUsable() {
        return SHADER.isUsable() && SMOOTH.isUsable() && COMPOSITE.isUsable() && COMPOSITE_DEPTH.isUsable();
    }

    /**
     * Draws the mist where the weather is drawn, reading a copy of the scene's depth to know how far each pixel's line
     * of sight goes. Outside any render pass.
     *
     * @param target   where the weather is being drawn
     * @param quality  how finely: the share of the screen's size the mist is drawn at, and the most steps (not
     *                 {@link GroundFogQuality#NONE}, which isn't drawn)
     * @param fabulous whether Fabulous graphics are on: the weather's target is then laid over the scene by vanilla's
     *                 transparency pass, by its depth, and the mist is drawn after the rain (see the handler)
     * @param strength how strong the mist is for the conditions, 0 to 1
     * @param driftX   how far the mist has drifted east-west, in blocks, and {@code driftZ} north-south
     * @param seconds  a running time, for the mist to churn
     * @return true if it was drawn, or there was nothing to draw
     */
    public static boolean render(GroundFogField field, Camera camera, RenderTarget target, boolean fabulous,
                                 GroundFogQuality quality, float strength,
                                 float driftX, float driftZ, float seconds) {
        if (!quality.isEnabled() || field.isEmpty() || !isUsable())
            return true;

        RENDER_TIMER.begin();
        try {
            return SHADER.run(() -> draw(field, camera, target, fabulous, quality, strength, driftX, driftZ, seconds), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(GroundFogField field, Camera camera, RenderTarget target, boolean fabulous,
                             GroundFogQuality quality, float strength, float driftX, float driftZ, float seconds) {
        if (grid == null || gridSize != field.size() || gridVersion != field.version()) {
            gridBaseY = Mth.floor(lowestGround(field)) - HEIGHT_BASE_MARGIN;
            gridHighest = upload(field, gridBaseY);
            gridVersion = field.version();
        }

        // How far each pixel's line of sight goes: the scene's depth, water's surface included, which with Fabulous
        // graphics isn't in the weather's target; the translucent target has it. All the passes read it
        var depthSource = fabulous ? GameUtils.getMC().levelRenderer.getTranslucentTarget() : target;
        if (depthSource == null || !SoftParticles.captureDepth(depthSource))
            return;

        int scale = quality.scale();
        var reduced = marched = reducedFor(marched, "Dynamic Surroundings ground fog", target, scale);
        var smoothedReduced = smoothed = reducedFor(smoothed, "Dynamic Surroundings ground fog, smoothed", target, scale);
        writeInfo(field, camera, target, fabulous, quality, strength, driftX, driftZ, seconds);
        var transforms = Transforms.camera();
        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var linear = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR);
        var nearest = RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
        // Made the first time, which uploads it: not while a render pass is open
        var noiseView = noiseTexture().getTextureView();

        // The mist at a share of the screen's size, replacing what was there: its colour and where it begins
        MARCH_TIMER.begin();
        try (RenderPass pass = encoder.createRenderPass(() -> "Dynamic Surroundings ground fog march", reduced.getColorTextureView(),
                OptionalInt.of(CLEAR), reduced.getDepthTextureView(), OptionalDouble.of(CLEAR_DEPTH))) {
            begin(pass, SHADER.pipeline(), transforms);
            SoftParticles.bindSceneDepth(pass);
            // Read smoothly between columns, ground heights and thickness both: the mist rounds off over ledges
            pass.bindTexture(FOG_GRID, grid.getTextureView(), linear);
            pass.bindTexture(NOISE_SAMPLER, noiseView, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.bindTexture("Sampler2", GameUtils.getMC().gameRenderer.lightTexture().getTextureView(), linear);
            pass.draw(0, 3);
        } finally {
            MARCH_TIMER.end();
        }

        // Smoothed at that size, replacing what was there: its colour and where it begins, as before
        SMOOTH_TIMER.begin();
        try (RenderPass pass = encoder.createRenderPass(() -> "Dynamic Surroundings ground fog smoothing", smoothedReduced.getColorTextureView(),
                OptionalInt.of(CLEAR), smoothedReduced.getDepthTextureView(), OptionalDouble.of(CLEAR_DEPTH))) {
            begin(pass, SMOOTH.pipeline(), transforms);
            pass.bindTexture(MIST_SAMPLER, reduced.getColorTextureView(), nearest);
            pass.bindTexture(MIST_DEPTH_SAMPLER, reduced.getDepthTextureView(), nearest);
            SoftParticles.bindSceneDepth(pass);
            pass.draw(0, 3);
        } finally {
            SMOOTH_TIMER.end();
        }

        // Laid over the scene at full size; with Fabulous graphics, writing where the mist begins
        COMPOSITE_TIMER.begin();
        try (RenderPass pass = encoder.createRenderPass(() -> "Dynamic Surroundings ground fog composite", target.getColorTextureView(),
                OptionalInt.empty(), fabulous ? target.getDepthTextureView() : null, OptionalDouble.empty())) {
            begin(pass, (fabulous ? COMPOSITE_DEPTH : COMPOSITE).pipeline(), transforms);
            pass.bindTexture(MIST_SAMPLER, smoothedReduced.getColorTextureView(), nearest);
            pass.bindTexture(MIST_DEPTH_SAMPLER, smoothedReduced.getDepthTextureView(), nearest);
            SoftParticles.bindSceneDepth(pass);
            pass.draw(0, 3);
        } finally {
            COMPOSITE_TIMER.end();
        }
    }

    /**
     * Sets a pass's pipeline and binds what all of them read
     */
    private static void begin(RenderPass pass, RenderPipeline pipeline, GpuBufferSlice transforms) {
        pass.setPipeline(pipeline);
        RenderSystem.bindDefaultUniforms(pass);
        pass.setUniform("DynamicTransforms", transforms);
        pass.setUniform(INFO, info);
    }

    /**
     * The values the passes share, this frame (see shaders/include/ground_fog.glsl)
     */
    private static void writeInfo(GroundFogField field, Camera camera, RenderTarget target, boolean fabulous,
                                  GroundFogQuality quality, float strength, float driftX, float driftZ, float seconds) {
        if (info == null)
            info = RenderSystem.getDevice().createBuffer(() -> "Dynamic Surroundings ground fog UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, INFO_SIZE);
        var cam = camera.position();
        int baseY = gridBaseY;
        float top = gridHighest + MIST_DEPTH;
        // The mist thins to nothing a block inside the area's edge, from where the camera is: beyond that, the field has
        // no cells (see GroundFogField.prepare)
        float fadeWidth = field.radius() * GroundFogField.EDGE_FADE;
        var zeroToOne = Transforms.DEPTH_ZERO_TO_ONE;
        try (var stack = MemoryStack.stackPush()) {
            var data = Std140Builder.onStack(stack, INFO_SIZE)
                    .putVec2(target.width, target.height)
                    .putFloat(quality.scale())
                    .putFloat(quality.maxSteps())
                    // The camera, in the grid's terms: a block to a texel, heights from the base; and where the base is
                    // in the world, so the noise stays put as the camera goes up and down. Not wrapped: heights are
                    // small, and a wrap would shift the noise
                    .putVec4((float) (cam.x - field.cellX(0)), (float) (cam.y - baseY), (float) (cam.z - field.cellZ(0)), baseY)
                    .putVec2(Math.floorMod(field.cellX(0), NOISE_WRAP), Math.floorMod(field.cellZ(0), NOISE_WRAP))
                    .putFloat(gridSize)
                    .putFloat(top - baseY)
                    .putVec4(driftX, driftZ, seconds, MIST_DEPTH)
                    .putVec2(field.radius() - 1F, fadeWidth)
                    .putFloat(THICKNESS * strength)
                    .putFloat(zeroToOne ? 1F : 0F)
                    .putFloat(fabulous ? 1F : 0F)
                    .get();
            RenderSystem.getDevice().createCommandEncoder().writeToBuffer(info.slice(), data);
        }
    }

    // A pass over the whole of what it draws to: a triangle made from the vertex's index, no vertex buffer
    private static RenderPipeline.Builder screenPass(String location, String fragmentShader) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/ground_fog_screen"))
                .withFragmentShader(Constants.asId(fragmentShader))
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withVertexFormat(DefaultVertexFormat.EMPTY, VertexFormat.Mode.TRIANGLES)
                .withCull(false);
    }

    private static RenderPipeline.Builder composite(String location) {
        return screenPass(location, "core/ground_fog_composite")
                .withUniform(INFO, UniformType.UNIFORM_BUFFER)
                .withSampler(MIST_SAMPLER)
                .withSampler(MIST_DEPTH_SAMPLER)
                .withSampler(DEPTH_SAMPLER)
                .withBlend(BlendFunction.TRANSLUCENT_PREMULTIPLIED_ALPHA);
    }

    /**
     * A target at a share of {@code target}'s size, created or resized, rounded up: a pixel for each {@code scale} by
     * {@code scale} of its
     *
     * @param existing the one there is, if any
     */
    private static TextureTarget reducedFor(@Nullable TextureTarget existing, String label, RenderTarget target, int scale) {
        int width = (target.width + scale - 1) / scale;
        int height = (target.height + scale - 1) / scale;
        if (existing == null)
            existing = new TextureTarget(label, width, height, true);
        else if (existing.width != width || existing.height != height)
            existing.resize(width, height);
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
        var pixels = gridPixels;
        if (grid == null || pixels == null || gridSize != size) {
            if (grid != null)
                grid.close();
            pixels = new NativeImage(size, size, true);
            grid = new DynamicTexture(() -> "Dynamic Surroundings ground fog grid", pixels);
            gridPixels = pixels;
            gridSize = size;
        }

        float highest = Float.NEGATIVE_INFINITY;
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
                pixels.setPixelABGR(i, j, (a << 24) | (b << 16) | (g << 8) | r);
            }
        }
        grid.upload();
        return highest;
    }

    /**
     * The noise texture, made the first time
     */
    private static DynamicTexture noiseTexture() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6D697374L);
        return noise;
    }
}
