package org.orecruncher.dsurround.effects.aurora;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.buffers.Std140SizeCalculator;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.MappableRingBuffer;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.AuroraQuality;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Draws a {@link RealisticAurora}: a pass over the whole screen, after the stars, that follows each pixel's line of
 * sight up through the layer of the sky where auroras shine, adding the light of each arc it passes through, and of its
 * ray field if it has one. The light adds to the sky behind it; the world, drawn after the sky, covers it.
 * <p>
 * An arc is a sheet a few kilometres thick, far thinner than any step along a line of sight hundreds of kilometres
 * long, so the shader doesn't sample the light along the way: it steps along looking for where the line crosses each
 * arc, and adds what it sees there all at once. How much depends on how much of the sheet the line goes through: a
 * sheet seen edge on is far brighter than one seen face on, as a real one is. The ray field, a belt of separate rays
 * along the magnetic field, is sampled along the way. See shaders/core/aurora_realistic.fsh.
 * <p>
 * At {@link AuroraQuality#FANCY} it is drawn straight onto the sky. At {@link AuroraQuality#FAST} it is drawn at half
 * the screen's size, to a target of its own, then laid over the sky ({@link #COMPOSITE}), read smoothly between its
 * pixels.
 * <p>
 * The colours are the light the upper air gives off: green from oxygen low in the arc, red from oxygen high above it,
 * and a pink fringe at its lower edge from nitrogen. Render thread only.
 */
public final class RealisticAuroraRenderer {

    // The shader's uniforms: three colours, the frame's values, how the light is given off, how active it is and which
    // way magnetic north is, the ray field, the sunlit tops, and the arcs (four vec4s each, in four arrays)
    private static final String INFO = "RealisticAuroraInfo";
    private static final int INFO_SIZE;
    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final String AURORA_SAMPLER = "AuroraSampler";
    private static final BindGroupLayout INFO_LAYOUT = BindGroupLayout.builder()
            .withUniform(INFO, UniformType.UNIFORM_BUFFER)
            .withSampler(NOISE_SAMPLER)
            .build();
    private static final BindGroupLayout COMPOSITE_LAYOUT = BindGroupLayout.builder()
            .withSampler(AURORA_SAMPLER)
            .build();
    private static final Vector4f CLEAR = new Vector4f(0F, 0F, 0F, 0F);

    static {
        var size = new Std140SizeCalculator();
        for (int i = 0; i < 8 + RealisticAurora.MAX_ARCS * 4; i++)
            size.putVec4();
        INFO_SIZE = size.get();
    }

    // Light adds to what is there: the sky, or (at FAST) the cleared target
    private static final BlendFunction ADD = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);

    // A triangle over the whole of what it draws to, from the vertex's index alone. Nothing is tested against or
    // written to the depth buffer
    private static final RenderPipeline PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/aurora_realistic"))
            .withVertexShader(Constants.asId("core/aurora_realistic"))
            .withFragmentShader(Constants.asId("core/aurora_realistic"))
            .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
            .withBindGroupLayout(INFO_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    // Lays the aurora, drawn at a share of the screen's size, over the sky
    private static final RenderPipeline COMPOSITE_PIPELINE = RenderPipeline.builder()
            .withLocation(Constants.asId("pipeline/aurora_composite"))
            .withVertexShader(Constants.asId("core/aurora_composite"))
            .withFragmentShader(Constants.asId("core/aurora_composite"))
            .withBindGroupLayout(COMPOSITE_LAYOUT)
            .withColorTargetState(new ColorTargetState(ADD))
            .withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
            .withCull(false)
            .build();

    public static final ModShader SHADER = new ModShader(PIPELINE, "the realistic aurora", "realistic auroras will not be drawn", Library.LOGGER);
    public static final ModShader COMPOSITE = new ModShader(COMPOSITE_PIPELINE, "the realistic aurora", "realistic auroras will not be drawn", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(SHADER, COMPOSITE);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Aurora (realistic)");

    /**
     * The noise texture's size, in cells, which it repeats at. The rays' finest noise has a cell to {@link #FINE_RAY}
     * kilometres along an arc, so their pattern repeats every few hundred kilometres; the coarser noise over it repeats
     * at a different length, which hides it.
     */
    public static final int NOISE_SIZE = 512;
    /**
     * Kilometres along an arc to a cell of the rays' finest noise (in the shader, FINE_RAY)
     */
    public static final float FINE_RAY = 0.7F;
    /**
     * Kilometres along an arc to a cell of the rays' coarser noise (in the shader, COARSE_RAY)
     */
    public static final float COARSE_RAY = 2.3F;
    /**
     * Kilometres across the ground to a cell of the ray field's noise, which places its rays (in the shader, FIELD_CELL)
     */
    public static final float FIELD_CELL = 3F;

    // The colours of the light, as a photograph shows it: oxygen's green (557.7 nm) and red (630 nm), and nitrogen's
    // pink at the lower edge
    private static final float[] GREEN = {0.25F, 1.0F, 0.45F};
    private static final float[] RED = {1.0F, 0.12F, 0.2F};
    private static final float[] FRINGE = {1.0F, 0.3F, 0.75F};
    // And the blue-violet of nitrogen lit by the sun, at the tops of the rays above the Earth's shadow (427.8 nm)
    private static final float[] SUNLIT = {0.45F, 0.3F, 1.0F};

    @Nullable
    private static DynamicTexture noise;
    @Nullable
    private static MappableRingBuffer info;
    // Where it is drawn at FAST, at a share of the screen's size
    @Nullable
    private static TextureTarget reduced;

    private RealisticAuroraRenderer() {
    }

    /**
     * Whether the realistic aurora can be drawn: its pipelines compiled, and drawing hasn't failed since
     */
    public static boolean isAvailable() {
        return SHADER.isAvailable() && COMPOSITE.isAvailable();
    }

    /**
     * Draws the aurora.
     *
     * @param aurora     what to draw
     * @param moment     how it is at this point in its night
     * @param motion     where its waves, curls and rays have moved to
     * @param quality    how finely
     * @param shadow     how high the Earth's shadow is over the viewer, in kilometres (see
     *                   {@link RealisticAurora#shadowHeight}): the tops of the rays above it are sunlit
     * @param washout    how much moonlight washes out its faint parts (see {@link RealisticAurora#moonWashout})
     * @param alpha      how strongly, 0 to 1
     * @param viewMatrix the camera's rotation, from the sky rendering
     * @param time       seconds, for the animation
     */
    public static void render(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                              AuroraQuality quality, float shadow, float washout, float alpha, Matrix4f viewMatrix, float time) {
        if (alpha <= 0F || !isAvailable())
            return;
        RENDER_TIMER.begin();
        try {
            SHADER.run(() -> draw(aurora, moment, motion, quality, shadow, washout, alpha, viewMatrix, time), () -> {});
        } finally {
            RENDER_TIMER.end();
        }
    }

    private static void draw(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                             AuroraQuality quality, float shadow, float washout, float alpha, Matrix4f viewMatrix, float time) {
        var screen = Minecraft.getInstance().gameRenderer.mainRenderTarget();
        var fast = quality.scale() > 1;
        RenderTarget target = fast ? reducedFor(screen, quality.scale()) : screen;
        writeInfo(aurora, moment, motion, shadow, washout, alpha, time, target.height);

        var encoder = RenderSystem.getDevice().createCommandEncoder();
        var transforms = RenderSystem.getDynamicUniforms().writeTransform(viewMatrix);
        try (RenderPass pass = encoder.createRenderPass(() -> "Dynamic Surroundings aurora (realistic)", target.getColorTextureView(),
                fast ? Optional.of(CLEAR) : Optional.empty(), null, OptionalDouble.empty())) {
            pass.setPipeline(PIPELINE);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform("DynamicTransforms", transforms);
            pass.setUniform(INFO, info.currentBuffer());
            pass.bindTexture(NOISE_SAMPLER, noiseTexture().getTextureView(), RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
            pass.draw(3, 1, 0, 0);
        }
        info.rotate();

        if (fast) {
            COMPOSITE.run(() -> {
                try (RenderPass pass = encoder.createRenderPass(() -> "Dynamic Surroundings aurora (realistic) composite", screen.getColorTextureView(),
                        Optional.empty(), null, OptionalDouble.empty())) {
                    pass.setPipeline(COMPOSITE_PIPELINE);
                    pass.bindTexture(AURORA_SAMPLER, target.getColorTextureView(), RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
                    pass.draw(3, 1, 0, 0);
                }
            }, () -> {});
        }
    }

    /**
     * The target it is drawn to at a share of the screen's size, created or resized, rounded up
     */
    private static TextureTarget reducedFor(RenderTarget screen, int scale) {
        int width = (screen.width + scale - 1) / scale;
        int height = (screen.height + scale - 1) / scale;
        if (reduced == null)
            reduced = new TextureTarget("Dynamic Surroundings aurora (realistic)", width, height, false, GpuFormat.RGBA8_UNORM);
        else if (reduced.width != width || reduced.height != height)
            reduced.resize(width, height);
        return reduced;
    }

    /**
     * The shader's values this frame (see shaders/include/aurora_realistic.glsl)
     */
    private static void writeInfo(RealisticAurora aurora, RealisticAurora.Moment moment, RealisticAuroraMotion motion,
                                  float shadow, float washout, float alpha, float time, int height) {
        if (info == null)
            info = new MappableRingBuffer(() -> "Dynamic Surroundings aurora (realistic) UBO", GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_MAP_WRITE, INFO_SIZE);
        var arcs = aurora.arcs();
        int count = Math.min(arcs.size(), RealisticAurora.MAX_ARCS);
        try (var view = info.currentBuffer().map(false, true)) {
            var builder = Std140Builder.intoBuffer(view.data())
                    .putVec4(GREEN[0] * moment.green(), GREEN[1] * moment.green(), GREEN[2] * moment.green(), washout)
                    .putVec4(RED[0], RED[1], RED[2], 0F)
                    .putVec4(FRINGE[0], FRINGE[1], FRINGE[2], 0F)
                    .putVec4(time, Math.clamp(alpha, 0F, 1F), height, count)
                    .putVec4(moment.lowerEdge(), RealisticAurora.FIELD_TILT, moment.red(), moment.fringe())
                    .putVec4(moment.rayContrast(), moment.pulse(), (float) Math.cos(aurora.declination()), (float) Math.sin(aurora.declination()))
                    .putVec4(moment.fieldNorth(), moment.fieldWidth(), moment.rayField() * moment.gain(), motion.field())
                    .putVec4(SUNLIT[0] * RealisticAurora.SUNLIT, SUNLIT[1] * RealisticAurora.SUNLIT, SUNLIT[2] * RealisticAurora.SUNLIT, shadow);
            for (int i = 0; i < RealisticAurora.MAX_ARCS; i++) {
                if (i < count) {
                    var arc = arcs.get(i);
                    // Where it is now: as much closer as the aurora has come
                    builder.putVec4(arc.distance() - moment.approach(), arc.foldAmplitude(), arc.foldWavelength(), arc.phase());
                } else {
                    builder.putVec4(0F, 0F, 1F, 0F);
                }
            }
            for (int i = 0; i < RealisticAurora.MAX_ARCS; i++) {
                if (i < count) {
                    var arc = arcs.get(i);
                    builder.putVec4(arc.waveAmplitude(), arc.waveWavelength(), motion.wave(i), arc.curlAmplitude());
                } else {
                    builder.putVec4(0F, 1F, 0F, 0F);
                }
            }
            for (int i = 0; i < RealisticAurora.MAX_ARCS; i++) {
                if (i < count) {
                    var arc = arcs.get(i);
                    builder.putVec4(arc.brightness() * moment.gain(), arc.thickness(), motion.curl(i), motion.ray(i));
                } else {
                    builder.putVec4(0F, 1F, 0F, 0F);
                }
            }
            for (int i = 0; i < RealisticAurora.MAX_ARCS; i++)
                builder.putVec4(i < count ? arcs.get(i).bow() : 0F, 0F, 0F, 0F);
        }
    }

    private static DynamicTexture noiseTexture() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x7261797DL);
        return noise;
    }
}
