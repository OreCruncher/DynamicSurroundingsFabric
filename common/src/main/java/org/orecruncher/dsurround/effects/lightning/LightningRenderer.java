package org.orecruncher.dsurround.effects.lightning;

import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.BindGroupLayout;
import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.BlendFactor;
import com.mojang.blaze3d.platform.CompareOp;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws lightning: each channel of a bolt as a ribbon turned to face the camera, a white hot core along its middle in
 * a pale violet glow, and the cloud lit up around it as patches of light lying along the cloud base, broken up by
 * noise into the cloud's texture (see {@link CloudGlow}). The light adds to what is behind it.
 * <p>
 * Drawn in the weather pass, after the ground fog, to the weather's target: with Fabulous graphics a target of its
 * own, given the scene's depth at the start of the pass (see WeatherDepth), into which depth is written so the
 * transparency pass lays the lightning over the scene where it is in front; otherwise the main target, tested against
 * its depth, which hides it behind hills, and cut off by the clouds'.
 * <p>
 * Ribbons are as wide as a channel looks close by, and never thinner than about a pixel far away. Without its
 * pipelines ({@link #SHADERS}) there is no lightning of the mod's, and the game's bolts are drawn. Everything here runs
 * on the render thread.
 */
public final class LightningRenderer {

    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final BindGroupLayout NOISE_LAYOUT = BindGroupLayout.builder().withSampler(NOISE_SAMPLER).build();
    // Light, added to what is there
    private static final BlendFunction ADD = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    // Channels, with their white core, and the cloud's glow, without; each as is, and for Fabulous graphics, where the
    // light comes with next to no alpha (not none, or the transparency pass skips the pixel) and depth is written so
    // that pass can sort it
    private static final RenderPipeline CHANNELS_PIPELINE = pipeline("pipeline/lightning_channels", true, false);
    private static final RenderPipeline CHANNELS_DEPTH_PIPELINE = pipeline("pipeline/lightning_channels_depth_write", true, true);
    private static final RenderPipeline GLOW_PIPELINE = pipeline("pipeline/lightning_glow", false, false);
    private static final RenderPipeline GLOW_DEPTH_PIPELINE = pipeline("pipeline/lightning_glow_depth_write", false, true);

    public static final ModShader CHANNELS = new ModShader(CHANNELS_PIPELINE, "lightning", "lightning will be vanilla's", Library.LOGGER);
    public static final ModShader CHANNELS_DEPTH = new ModShader(CHANNELS_DEPTH_PIPELINE, "lightning (Fabulous)", "lightning will be vanilla's", Library.LOGGER);
    public static final ModShader GLOW = new ModShader(GLOW_PIPELINE, "lightning's cloud glow", "lightning will be vanilla's", Library.LOGGER);
    public static final ModShader GLOW_DEPTH = new ModShader(GLOW_DEPTH_PIPELINE, "lightning's cloud glow (Fabulous)", "lightning will be vanilla's", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(CHANNELS, CHANNELS_DEPTH, GLOW, GLOW_DEPTH);

    public static final RenderTimer RENDER_TIMER = new RenderTimer("Lightning");

    /**
     * Half the width of the main channel's white core, close by, in blocks
     */
    static final float CORE_HALF_WIDTH = 0.25F;
    /**
     * The narrowest a core is drawn, as the angle across half of it (about a pixel at 1080p): far channels would
     * otherwise be thinner than a pixel and shimmer
     */
    static final float MIN_CORE_ANGLE = 0.0011F;
    /**
     * How much wider a ribbon is than its core: the glow around it. The shader's CORE_WIDTH is one over this.
     */
    static final float HALO = 8F;
    /**
     * Each branch's width against its parent's
     */
    static final float BRANCH_WIDTH = 0.6F;
    // Light less than this isn't drawn
    static final float MIN_LIGHT = 0.003F;
    // Colours (the core is white)
    private static final float CHANNEL_R = 0.62F, CHANNEL_G = 0.66F, CHANNEL_B = 1F;
    private static final float GLOW_R = 0.62F, GLOW_G = 0.64F, GLOW_B = 0.86F;
    /**
     * A cloud patch's noise cell goes in its UV's x, this many apart, so the shader can tell the cell from where in the
     * patch it is (-1 to 1): it rounds x over this. The shader's CELL_STEP.
     */
    static final float NOISE_CELL_STEP = 4F;
    /**
     * The noise texture's size, in cells, which it repeats at (see {@link NoiseTexture})
     */
    static final int NOISE_SIZE = 128;
    // Room for a few strikes' vertices to start with; grown as needed
    private static final int INITIAL_BYTES = 4096 * DefaultVertexFormat.POSITION_TEX_COLOR.getVertexSize();

    // Made on first use, on the render thread
    @Nullable
    private static DynamicTexture noise;
    @Nullable
    private static ByteBufferBuilder meshBytes;
    private static final MeshDraw CHANNEL_DRAW = new MeshDraw("lightning");
    private static final MeshDraw GLOW_DRAW = new MeshDraw("lightning cloud glow");

    private LightningRenderer() {
    }

    private static RenderPipeline pipeline(String location, boolean channels, boolean fabulous) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/lightning"))
                .withFragmentShader(Constants.asId("core/lightning"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.MATRICES_PROJECTION)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(ADD))
                .withDepthStencilState(fabulous ? DepthStencilState.DEFAULT : TEST_ONLY)
                .withCull(false);
        // The cloud's glow reads noise for its texture; the channels need nothing more
        if (channels)
            builder.withShaderDefine("CORE");
        else
            builder.withBindGroupLayout(NOISE_LAYOUT);
        if (fabulous)
            builder.withShaderDefine("ADD_ONLY");
        return builder.build();
    }

    /**
     * Whether lightning can be drawn: its pipelines compiled, drawing hasn't failed since, and no shader pack is in use
     */
    public static boolean isUsable() {
        for (var shader : SHADERS)
            if (!shader.isUsable())
                return false;
        return true;
    }

    /**
     * Draws the strikes, each as it is at {@code nowTicks}, outside any render pass
     *
     * @param target   where: the weather's target
     * @param fabulous whether Fabulous graphics are on
     * @param nowTicks the game time, in ticks, with the part of a tick
     * @return how many strikes were drawn, for the diagnostics
     */
    public static int draw(List<Strike> strikes, Camera camera, RenderTarget target, boolean fabulous, double nowTicks) {
        if (strikes.isEmpty())
            return 0;
        var channels = fabulous ? CHANNELS_DEPTH : CHANNELS;
        var glow = fabulous ? GLOW_DEPTH : GLOW;
        int[] drawn = {0};
        RENDER_TIMER.begin();
        RENDER_TIMER.count(strikes.size());
        // The channels first: with depth written, the cloud's glow drawn first would hide them behind it
        channels.run(() -> drawn[0] += drawChannels(channels.pipeline(), strikes, camera, target, nowTicks), () -> {});
        glow.run(() -> drawn[0] += drawGlow(glow.pipeline(), strikes, camera, target, nowTicks), () -> {});
        RENDER_TIMER.end();
        return drawn[0];
    }

    private static Consumer<RenderPass> bindNoise() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6C69676EL);
        var view = noise.getTextureView();
        return pass -> pass.bindTexture(NOISE_SAMPLER, view, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
    }

    private static BufferBuilder builder() {
        if (meshBytes == null)
            meshBytes = new ByteBufferBuilder(INITIAL_BYTES);
        return new BufferBuilder(meshBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static int drawChannels(RenderPipeline pipeline, List<Strike> strikes, Camera camera, RenderTarget target, double nowTicks) {
        var position = camera.position();
        var builder = builder();
        int drawn = 0;
        for (var strike : strikes) {
            var shape = strike.shape();
            if (shape == null)
                continue;
            float age = strike.age(nowTicks);
            if (age < 0F || strike.isDone(age))
                continue;
            float ox = (float) (strike.x() - position.x);
            float oy = (float) (strike.y() - position.y);
            float oz = (float) (strike.z() - position.z);
            for (var channel : shape.channels())
                ribbon(builder, strike, channel, ox, oy, oz, age);
            drawn++;
        }
        var mesh = builder.build();
        if (mesh != null)
            CHANNEL_DRAW.draw(mesh, pipeline, target, pass -> {});
        return drawn;
    }

    private static int drawGlow(RenderPipeline pipeline, List<Strike> strikes, Camera camera, RenderTarget target, double nowTicks) {
        var position = camera.position();
        var builder = builder();
        int drawn = 0;
        for (var strike : strikes) {
            float age = strike.age(nowTicks);
            if (age < 0F || strike.isDone(age))
                continue;
            boolean any = false;
            float ox = (float) (strike.x() - position.x);
            float oy = (float) (strike.y() - position.y);
            float oz = (float) (strike.z() - position.z);
            var glow = strike.glow();
            for (int p = 0; p < glow.count(); p++) {
                float a = alpha(strike.glowBrightness(p, age) * strike.visibility());
                if (a <= 0F)
                    continue;
                any = true;
                cloudPatch(builder, ox + glow.x(p), oy + glow.y(p), oz + glow.z(p), glow.radius(p), glow.noiseCell(p), a);
            }
            // Flashes in the cloud are only glow; the rest were counted with their channels
            if (any && strike.shape() == null)
                drawn++;
        }
        var mesh = builder.build();
        if (mesh != null)
            GLOW_DRAW.draw(mesh, pipeline, target, bindNoise());
        return drawn;
    }

    /**
     * A patch of lit cloud, lying flat along the cloud base: seen from below it narrows toward the horizon, as the
     * cloud deck does. UV0 is where in it a corner is, from its middle, -1 to 1 each way, with the patch's noise cell
     * times {@link #NOISE_CELL_STEP} added to x for the shader to take back out (see lightning.fsh).
     */
    private static void cloudPatch(BufferBuilder builder, float cx, float cy, float cz, float r, int noiseCell, float alpha) {
        float cell = noiseCell * NOISE_CELL_STEP;
        builder.addVertex(cx - r, cy, cz - r).setUv(cell - 1F, -1F).setColor(GLOW_R, GLOW_G, GLOW_B, alpha);
        builder.addVertex(cx + r, cy, cz - r).setUv(cell + 1F, -1F).setColor(GLOW_R, GLOW_G, GLOW_B, alpha);
        builder.addVertex(cx + r, cy, cz + r).setUv(cell + 1F, 1F).setColor(GLOW_R, GLOW_G, GLOW_B, alpha);
        builder.addVertex(cx - r, cy, cz + r).setUv(cell - 1F, 1F).setColor(GLOW_R, GLOW_G, GLOW_B, alpha);
    }

    /**
     * One channel as a ribbon: at each point, out to either side across the channel and the line of sight, so it
     * faces the camera however the channel turns. Positions are from the camera.
     */
    private static void ribbon(BufferBuilder builder, Strike strike, BoltShape.Channel channel, float ox, float oy, float oz, float age) {
        int n = channel.size();
        if (n < 2)
            return;
        var arrival = channel.arrival();
        int depth = channel.depth();
        // Nothing of it lit yet
        if (strike.channelBrightness(depth, arrival[0], age) * strike.visibility() < MIN_LIGHT)
            return;
        float width = CORE_HALF_WIDTH * (float) Math.pow(BRANCH_WIDTH, depth);

        float sideX = 0F, sideY = 0F, sideZ = 1F;
        float prevLx = 0F, prevLy = 0F, prevLz = 0F, prevRx = 0F, prevRy = 0F, prevRz = 0F, prevA = 0F;
        for (int i = 0; i < n; i++) {
            float px = ox + channel.x(i), py = oy + channel.y(i), pz = oz + channel.z(i);
            // Which way the channel runs here
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float tx = channel.x(b) - channel.x(a), ty = channel.y(b) - channel.y(a), tz = channel.z(b) - channel.z(a);
            // Across it and the line of sight; when looking straight along it, the last way across will do
            float cx = ty * pz - tz * py, cy = tz * px - tx * pz, cz = tx * py - ty * px;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl > 1.0E-6F) {
                sideX = cx / cl;
                sideY = cy / cl;
                sideZ = cz / cl;
            }
            float distance = Mth.sqrt(px * px + py * py + pz * pz);
            // Branches narrow and fade toward their tips
            float along = (float) i / (n - 1);
            float taper = depth == 0 ? 1F : 1F - 0.6F * along;
            float fade = depth == 0 ? 1F : 1F - 0.85F * along;
            float half = Math.max(width * taper, distance * MIN_CORE_ANGLE) * HALO;
            float light = strike.channelBrightness(depth, arrival[i], age) * strike.visibility() * fade;

            float lx = px - sideX * half, ly = py - sideY * half, lz = pz - sideZ * half;
            float rx = px + sideX * half, ry = py + sideY * half, rz = pz + sideZ * half;
            float alpha = alpha(light);
            if (i > 0 && (prevA > 0F || alpha > 0F)) {
                builder.addVertex(prevLx, prevLy, prevLz).setUv(0F, -1F).setColor(CHANNEL_R, CHANNEL_G, CHANNEL_B, prevA);
                builder.addVertex(prevRx, prevRy, prevRz).setUv(0F, 1F).setColor(CHANNEL_R, CHANNEL_G, CHANNEL_B, prevA);
                builder.addVertex(rx, ry, rz).setUv(0F, 1F).setColor(CHANNEL_R, CHANNEL_G, CHANNEL_B, alpha);
                builder.addVertex(lx, ly, lz).setUv(0F, -1F).setColor(CHANNEL_R, CHANNEL_G, CHANNEL_B, alpha);
            }
            prevLx = lx;
            prevLy = ly;
            prevLz = lz;
            prevRx = rx;
            prevRy = ry;
            prevRz = rz;
            prevA = alpha;
        }
    }

    /**
     * Light up to 2 goes in the vertex alpha, which holds 0 to 1: halved here, doubled again by the shader. Too faint
     * to see is 0.
     */
    static float alpha(float light) {
        return light < MIN_LIGHT ? 0F : Math.min(light * 0.5F, 1F);
    }
}
