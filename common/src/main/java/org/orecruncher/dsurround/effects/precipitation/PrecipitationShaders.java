package org.orecruncher.dsurround.effects.precipitation;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.DestFactor;
import com.mojang.blaze3d.platform.SourceFactor;
import com.mojang.blaze3d.shaders.UniformType;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.effects.particles.SoftParticles;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;

/**
 * The pipelines particle rain and snow are drawn with. All fade what they draw into the fog. Kept apart from
 * {@link PrecipitationField} and {@link DampSpots}, so the drops and spots can be tested without the game.
 * <p>
 * Each comes in two: as vanilla's weather, depth is written only with Fabulous graphics, which draws the weather to a
 * target of its own that the transparency pass lays over the scene by depth (see {@link #fabulous}).
 */
public final class PrecipitationShaders {

    /**
     * How far in front of the ground, in blocks, a puff of dust is at full strength: closer, it fades, so a puff
     * partly in the ground blends into it rather than showing a hard edge
     */
    static final float DUST_SOFT_DISTANCE = 1.5F;

    // What is drawn there is scaled by one less the spot's alpha
    private static final BlendFunction DARKEN = new BlendFunction(SourceFactor.ZERO, DestFactor.ONE_MINUS_SRC_ALPHA);

    // The rain, snow, snow flecks and window streaks: lit by the lightmap, the snow textured, seen from both sides
    private static final RenderPipeline DROPS_PIPELINE = drops("pipeline/precipitation").withDepthWrite(false).build();
    private static final RenderPipeline DROPS_DEPTH_PIPELINE = drops("pipeline/precipitation_depth_write").withDepthWrite(true).build();
    // The dust: as the drops, but soft, reading a copy of the scene's depth
    private static final RenderPipeline DUST_PIPELINE = dust("pipeline/precipitation_dust").withDepthWrite(false).build();
    private static final RenderPipeline DUST_DEPTH_PIPELINE = dust("pipeline/precipitation_dust_depth_write").withDepthWrite(true).build();
    // Damp spots: darkening the ground drawn under them; with Fabulous graphics, laid over it as a dark colour, as the
    // weather's own target starts out empty with nothing to darken. Their depth is written then: the transparency pass
    // sorts by it, and without it the scene is laid over them. They lie on the ground, so it hides nothing that the
    // ground wouldn't.
    private static final RenderPipeline DAMP_SPOTS_PIPELINE = dampSpots("pipeline/damp_spot")
            .withBlend(DARKEN)
            .withDepthWrite(false)
            .build();
    private static final RenderPipeline DAMP_SPOTS_DEPTH_PIPELINE = dampSpots("pipeline/damp_spot_depth_write").withDepthWrite(true).build();

    public static final ModShader DROPS = new ModShader(DROPS_PIPELINE, "particle rain and snow", "vanilla's rain and snow will be drawn instead", Library.LOGGER);
    public static final ModShader DROPS_DEPTH = new ModShader(DROPS_DEPTH_PIPELINE, "particle rain and snow (Fabulous)", "vanilla's rain and snow will be drawn instead", Library.LOGGER);
    public static final ModShader DUST = new ModShader(DUST_PIPELINE, "soft dust", "dust will be drawn without it", Library.LOGGER);
    public static final ModShader DUST_DEPTH = new ModShader(DUST_DEPTH_PIPELINE, "soft dust (Fabulous)", "dust will be drawn without it", Library.LOGGER);
    public static final ModShader DAMP_SPOTS = new ModShader(DAMP_SPOTS_PIPELINE, "damp spots", "rain will not leave damp spots", Library.LOGGER);
    public static final ModShader DAMP_SPOTS_DEPTH = new ModShader(DAMP_SPOTS_DEPTH_PIPELINE, "damp spots (Fabulous)", "rain will not leave damp spots", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(DROPS, DROPS_DEPTH, DUST, DUST_DEPTH, DAMP_SPOTS, DAMP_SPOTS_DEPTH);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Precipitation");
    public static final RenderTimer DAMP_SPOTS_TIMER = new RenderTimer("- Damp Spots");
    public static final RenderTimer DROPS_TIMER = new RenderTimer("- Drops");

    private PrecipitationShaders() {
    }

    /**
     * Whether the drops can be drawn: both of their pipelines compiled, drawing hasn't failed, and no shader pack is
     * in use. Without them, vanilla's weather is drawn.
     */
    public static boolean isAvailable() {
        return DROPS.isUsable() && DROPS_DEPTH.isUsable();
    }

    /**
     * The one of a pair for the graphics mode
     *
     * @param fabulous whether Fabulous graphics are on
     */
    public static ModShader fabulous(boolean fabulous, ModShader plain, ModShader depthWrite) {
        return fabulous ? depthWrite : plain;
    }

    private static RenderPipeline.Builder drops(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/precipitation"))
                .withFragmentShader(Constants.asId("core/precipitation"))
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withSampler("Sampler2")
                .withVertexFormat(DefaultVertexFormat.PARTICLE, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false);
    }

    private static RenderPipeline.Builder dust(String location) {
        return SoftParticles.withSoftDepth(drops(location)
                .withShaderDefine("SOFT_DISTANCE", DUST_SOFT_DISTANCE));
    }

    private static RenderPipeline.Builder dampSpots(String location) {
        return RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/damp_spot"))
                .withFragmentShader(Constants.asId("core/damp_spot"))
                .withUniform("Globals", UniformType.UNIFORM_BUFFER)
                .withUniform("DynamicTransforms", UniformType.UNIFORM_BUFFER)
                .withUniform("Projection", UniformType.UNIFORM_BUFFER)
                .withUniform("Fog", UniformType.UNIFORM_BUFFER)
                .withSampler("Sampler0")
                .withVertexFormat(DefaultVertexFormat.POSITION_TEX_COLOR, VertexFormat.Mode.QUADS)
                .withBlend(BlendFunction.TRANSLUCENT)
                .withCull(false);
    }
}
