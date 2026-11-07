// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.lightning;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.BlendFactor;
import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.MeshDraw;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderFailSafe;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.compat.IrisCompat;

import java.util.List;
import java.util.function.Consumer;

/**
 * Draws lightning: each channel of a bolt as a ribbon turned to face the camera, a white hot core along its middle in
 * a pale violet glow, and the cloud lit up around it as patches of light lying along the cloud base, broken up by
 * noise into the cloud's texture (see {@link CloudGlow}): when the mod's clouds aren't drawn, as they are lit by it
 * from inside instead. The light adds to what is behind it. A far strike shows less, the more so the closer the fog
 * (see {@link Strikes#visibility}).
 * <p>
 * Drawn after the ground fog and the weather (see IWorldRender), to the main target, tested against its depth, which
 * hides it behind hills, and cut off by the clouds'.
 * <p>
 * Ribbons are as wide as a channel looks close by, and never thinner than about a pixel far away. Without its
 * pipelines ({@link #SHADERS}) there is no lightning of the mod's, and the game's bolts are drawn; with a shader pack
 * the strikes are submitted the game's way instead (see {@link #submitForShaderPack}). Everything here runs on the
 * render thread.
 */
public final class LightningRenderer {

    private static final String NOISE_SAMPLER = "NoiseSampler";
    private static final BindGroupLayout NOISE_LAYOUT = BindGroupLayout.builder().withUniform(NOISE_SAMPLER, UniformType.COMBINED_IMAGE_SAMPLER).build();
    // Light, added to what is there
    private static final BlendFunction ADD = new BlendFunction(BlendFactor.ONE, BlendFactor.ONE);
    private static final DepthStencilState TEST_ONLY = new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);

    // Channels, with their white core, and the cloud's glow, without
    private static final RenderPipeline CHANNELS_PIPELINE = pipeline("pipeline/lightning_channels", true);
    private static final RenderPipeline GLOW_PIPELINE = pipeline("pipeline/lightning_glow", false);

    public static final ModShader CHANNELS = new ModShader(CHANNELS_PIPELINE, "lightning", "lightning will be vanilla's", Library.LOGGER);
    public static final ModShader GLOW = new ModShader(GLOW_PIPELINE, "lightning's cloud glow", "lightning will be vanilla's", Library.LOGGER);

    /**
     * All of them, for {@code ModShaders}
     */
    public static final List<ModShader> SHADERS = List.of(CHANNELS, GLOW);

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
    // Colors (the core is white)
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
    // Submitting it the game's way, with a shader pack (see submitForShaderPack)
    private static final RenderFailSafe SHADER_PACK_FAIL_SAFE = new RenderFailSafe("lightning with a shader pack", Library.LOGGER);

    private LightningRenderer() {
    }

    /**
     * Whether the strikes are drawn the game's way, for a shader pack (see {@link #submitForShaderPack})
     */
    public static boolean forShaderPack() {
        return IrisCompat.isShaderPackInUse();
    }

    private static RenderPipeline pipeline(String location, boolean channels) {
        var builder = RenderPipeline.builder()
                .withLocation(Constants.asId(location))
                .withVertexShader(Constants.asId("core/lightning"))
                .withFragmentShader(Constants.asId("core/lightning"))
                .withBindGroupLayout(BindGroupLayouts.GLOBALS)
                .withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
                .withBindGroupLayout(BindGroupLayouts.PROJECTION)
                .withVertexBinding(0, DefaultVertexFormat.POSITION_TEX_COLOR)
                .withPrimitiveTopology(PrimitiveTopology.QUADS)
                .withColorTargetState(new ColorTargetState(ADD))
                .withDepthStencilState(TEST_ONLY)
                .withCull(false);
        // The cloud's glow reads noise for its texture; the channels need nothing more
        if (channels)
            builder.withShaderDefine("CORE");
        else
            builder.withBindGroupLayout(NOISE_LAYOUT);
        return builder.build();
    }

    /**
     * Whether lightning can be drawn: its pipelines compiled and drawing hasn't failed since; or, with a shader pack in
     * use, drawing it the game's way hasn't failed (see {@link #submitForShaderPack})
     */
    public static boolean isUsable() {
        if (forShaderPack())
            return !SHADER_PACK_FAIL_SAFE.isFailed();
        for (var shader : SHADERS)
            if (!shader.isUsable())
                return false;
        return true;
    }

    /**
     * Draws the strikes, each as it is at {@code nowTicks}, outside any render pass
     *
     * @param target   where: the main target
     * @param nowTicks the game time, in ticks, with the part of a tick
     * @param fogEnd   where the fog is thickest, in blocks, for how much of a far strike shows (see
     *                 {@link Strikes#visibility})
     * @param glow     whether to draw the cloud's glow: not when the mod's clouds are lit by it instead
     * @return how many strikes were drawn, for the diagnostics
     */
    public static int draw(List<Strike> strikes, Camera camera, RenderTarget target, double nowTicks,
                           float fogEnd, boolean glow) {
        // With a shader pack the strikes are submitted with the frame's geometry instead (see submitForShaderPack)
        if (strikes.isEmpty() || forShaderPack())
            return 0;
        int[] drawn = {0};
        RENDER_TIMER.begin();
        RENDER_TIMER.count(strikes.size());
        CHANNELS.run(() -> drawn[0] += drawChannels(CHANNELS_PIPELINE, strikes, camera, target, nowTicks, fogEnd), () -> {});
        if (glow)
            GLOW.run(() -> drawn[0] += drawGlow(GLOW_PIPELINE, strikes, camera, target, nowTicks, fogEnd), () -> {});
        RENDER_TIMER.end();
        return drawn[0];
    }

    private static Consumer<RenderPass> bindNoise() {
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6C69676EL);
        var view = noise.getTextureView();
        return pass -> pass.setUniform(NOISE_SAMPLER, view, RenderSystem.getSamplerCache().getRepeat(FilterMode.LINEAR));
    }

    private static BufferBuilder builder() {
        if (meshBytes == null)
            meshBytes = new ByteBufferBuilder(INITIAL_BYTES);
        return new BufferBuilder(meshBytes, PrimitiveTopology.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
    }

    private static int drawChannels(RenderPipeline pipeline, List<Strike> strikes, Camera camera, RenderTarget target,
                                    double nowTicks, float fogEnd) {
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
            float seen = Strikes.visibility(strike.distance(), fogEnd);
            for (var channel : shape.channels())
                ribbon(builder, strike, channel, ox, oy, oz, age, seen);
            drawn++;
        }
        var mesh = builder.build();
        if (mesh != null)
            CHANNEL_DRAW.draw(mesh, pipeline, target, pass -> {});
        return drawn;
    }

    private static int drawGlow(RenderPipeline pipeline, List<Strike> strikes, Camera camera, RenderTarget target,
                                double nowTicks, float fogEnd) {
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
            float seen = Strikes.visibility(strike.distance(), fogEnd);
            for (int p = 0; p < glow.count(); p++) {
                float a = alpha(strike.glowBrightness(p, age) * seen);
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
    private static void ribbon(BufferBuilder builder, Strike strike, BoltShape.Channel channel, float ox, float oy, float oz,
                               float age, float seen) {
        int n = channel.size();
        if (n < 2)
            return;
        var arrival = channel.arrival();
        int depth = channel.depth();
        // Nothing of it lit yet
        if (strike.channelBrightness(depth, arrival[0], age) * seen < MIN_LIGHT)
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
            float light = strike.channelBrightness(depth, arrival[i], age) * seen * fade;

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
     * With a shader pack in use, no shader of ours can run, and the pack draws the world to targets of its own, which a
     * pass of ours doesn't reach: the strikes are submitted with the frame's geometry as it is collected, the game's
     * way, as its bolts are, with its lightning render type, which Iris draws with the pack's lightning program. The
     * channels are plain colored ribbons, layered as the game layers its bolts to glow (a wide faint halo, a narrower
     * glow, a white core). The cloud's glow, without the shader's cloud texture, is soft discs lying along the cloud
     * base, fading out to their rims: a flash hidden in the cloud (sheet lightning) is nothing else.
     *
     * @param glow whether to draw the cloud's glow: not when the mod's clouds are lit by it instead
     * @return how many strikes were submitted, for the diagnostics
     */
    public static int submitForShaderPack(SubmitNodeCollector collector, CameraRenderState camera, List<Strike> strikes,
                                          double nowTicks, float fogEnd, boolean glow) {
        if (strikes.isEmpty() || SHADER_PACK_FAIL_SAFE.isFailed())
            return 0;
        // Copied: the list is the handler's, filled again next frame, and this is drawn later in the frame
        var toDraw = List.copyOf(strikes);
        double camX = camera.pos.x, camY = camera.pos.y, camZ = camera.pos.z;
        RENDER_TIMER.count(toDraw.size());
        collector.submitCustomGeometry(new PoseStack(), RenderTypes.lightning(), (pose, consumer) -> SHADER_PACK_FAIL_SAFE.run(() -> {
            var matrix = pose.pose();
            for (var strike : toDraw) {
                float age = strike.age(nowTicks);
                if (age < 0F || strike.isDone(age))
                    continue;
                float ox = (float) (strike.x() - camX);
                float oy = (float) (strike.y() - camY);
                float oz = (float) (strike.z() - camZ);
                float seen = Strikes.visibility(strike.distance(), fogEnd);
                var shape = strike.shape();
                if (shape != null)
                    for (var channel : shape.channels())
                        layeredRibbon(consumer, matrix, strike, channel, ox, oy, oz, age, seen);
                if (glow) {
                    var cloud = strike.glow();
                    for (int p = 0; p < cloud.count(); p++) {
                        float alpha = Math.min(strike.glowBrightness(p, age) * seen * PACK_GLOW_LIGHT, 1F);
                        if (alpha >= MIN_LIGHT)
                            glowDisc(consumer, matrix, ox + cloud.x(p), oy + cloud.y(p), oz + cloud.z(p), cloud.radius(p), alpha);
                    }
                }
            }
        }, () -> {}));
        return toDraw.size();
    }

    // The layers of a channel drawn the game's way, from the outside in: how wide, against the ribbon's full width
    // (the halo the shader draws, fading out, where these are flat: at its full width they looked thick); how much of
    // the channel's light each gives as alpha (the game's lightning blend adds the color times alpha); the last is the
    // white core, as wide as the shader's
    private static final float[] LAYER_WIDTH = {0.6F, 0.2F, 1F / HALO};
    private static final float[] LAYER_LIGHT = {0.3F, 0.9F, 2.5F};
    /**
     * How much of a glow patch's light its middle gives as alpha, drawn with a shader pack: with nothing of the shader's
     * cloud texture to break it up, a disc lit as brightly reads as a lamp
     */
    private static final float PACK_GLOW_LIGHT = 0.6F;
    // The rings and slices of a glow disc drawn with a shader pack
    private static final int GLOW_RINGS = 4;
    private static final int GLOW_SLICES = 16;
    private static final float[] GLOW_COS = new float[GLOW_SLICES + 1];
    private static final float[] GLOW_SIN = new float[GLOW_SLICES + 1];

    static {
        for (int i = 0; i <= GLOW_SLICES; i++) {
            GLOW_COS[i] = Mth.cos(Mth.TWO_PI * i / GLOW_SLICES);
            GLOW_SIN[i] = Mth.sin(Mth.TWO_PI * i / GLOW_SLICES);
        }
    }

    /**
     * How much nearer the camera each layer drawn with a shader pack is than the one around it, against its distance
     */
    private static final float LAYER_NEARER = 0.003F;

    /**
     * A patch of lit cloud drawn with a shader pack: a disc lying flat along the cloud base, as {@link #cloudPatch}
     * lies, brightest in the middle and fading out to its rim, the color's own blend standing in for the shader's soft
     * edge. Rings of small quads, not long slices from the middle: the game's lightning writes depth, and its quads are
     * sorted far to near by their middles, which for long slices of overlapping discs put a nearer one first, cutting
     * dark wedges out of those behind. Written both ways round, as the ribbons are: the pipeline culls back faces, and
     * a disc is seen from above or below.
     */
    private static void glowDisc(VertexConsumer builder, Matrix4f matrix, float cx, float cy, float cz, float r, float alpha) {
        for (int ring = 0; ring < GLOW_RINGS; ring++) {
            float in = r * ring / GLOW_RINGS, out = r * (ring + 1) / GLOW_RINGS;
            float inAlpha = glowFade(alpha, ring), outAlpha = glowFade(alpha, ring + 1);
            for (int i = 0; i < GLOW_SLICES; i++) {
                float ix0 = cx + GLOW_COS[i] * in, iz0 = cz + GLOW_SIN[i] * in;
                float ix1 = cx + GLOW_COS[i + 1] * in, iz1 = cz + GLOW_SIN[i + 1] * in;
                float ox0 = cx + GLOW_COS[i] * out, oz0 = cz + GLOW_SIN[i] * out;
                float ox1 = cx + GLOW_COS[i + 1] * out, oz1 = cz + GLOW_SIN[i + 1] * out;
                builder.addVertex(matrix, ix0, cy, iz0).setColor(GLOW_R, GLOW_G, GLOW_B, inAlpha);
                builder.addVertex(matrix, ix1, cy, iz1).setColor(GLOW_R, GLOW_G, GLOW_B, inAlpha);
                builder.addVertex(matrix, ox1, cy, oz1).setColor(GLOW_R, GLOW_G, GLOW_B, outAlpha);
                builder.addVertex(matrix, ox0, cy, oz0).setColor(GLOW_R, GLOW_G, GLOW_B, outAlpha);
                builder.addVertex(matrix, ix1, cy, iz1).setColor(GLOW_R, GLOW_G, GLOW_B, inAlpha);
                builder.addVertex(matrix, ix0, cy, iz0).setColor(GLOW_R, GLOW_G, GLOW_B, inAlpha);
                builder.addVertex(matrix, ox0, cy, oz0).setColor(GLOW_R, GLOW_G, GLOW_B, outAlpha);
                builder.addVertex(matrix, ox1, cy, oz1).setColor(GLOW_R, GLOW_G, GLOW_B, outAlpha);
            }
        }
    }

    /**
     * A glow disc's alpha at the inside of a ring (the rings' count at its rim): falling off as the square of the
     * distance from the rim, softer than straight down
     */
    private static float glowFade(float alpha, int ring) {
        float toRim = 1F - (float) ring / GLOW_RINGS;
        return alpha * toRim * toRim;
    }

    /**
     * One channel as ribbons facing the camera, as {@link #ribbon} lays it out, one ribbon a layer
     */
    private static void layeredRibbon(VertexConsumer builder, Matrix4f matrix, Strike strike, BoltShape.Channel channel,
                                      float ox, float oy, float oz, float age, float seen) {
        int n = channel.size();
        if (n < 2)
            return;
        var arrival = channel.arrival();
        int depth = channel.depth();
        if (strike.channelBrightness(depth, arrival[0], age) * seen < MIN_LIGHT)
            return;
        float width = CORE_HALF_WIDTH * (float) Math.pow(BRANCH_WIDTH, depth);
        int layers = LAYER_WIDTH.length;
        // Each layer's last corners, left then right, and alpha
        float[] prev = new float[layers * 7];
        float sideX = 0F, sideY = 0F, sideZ = 1F;
        for (int i = 0; i < n; i++) {
            float px = ox + channel.x(i), py = oy + channel.y(i), pz = oz + channel.z(i);
            int a = Math.max(0, i - 1), b = Math.min(n - 1, i + 1);
            float tx = channel.x(b) - channel.x(a), ty = channel.y(b) - channel.y(a), tz = channel.z(b) - channel.z(a);
            float cx = ty * pz - tz * py, cy = tz * px - tx * pz, cz = tx * py - ty * px;
            float cl = Mth.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl > 1.0E-6F) {
                sideX = cx / cl;
                sideY = cy / cl;
                sideZ = cz / cl;
            }
            float distance = Mth.sqrt(px * px + py * py + pz * pz);
            float along = (float) i / (n - 1);
            float taper = depth == 0 ? 1F : 1F - 0.6F * along;
            float fade = depth == 0 ? 1F : 1F - 0.85F * along;
            float half = Math.max(width * taper, distance * MIN_CORE_ANGLE) * HALO;
            float light = strike.channelBrightness(depth, arrival[i], age) * seen * fade;
            if (light < MIN_LIGHT)
                light = 0F;
            for (int l = 0; l < layers; l++) {
                float h = half * LAYER_WIDTH[l];
                // Each layer a little nearer than the one around it, along the line of sight, so it looks the same:
                // the game's lightning writes depth, and layers in one plane fought over it, leaving the core in dashes
                float k = 1F - LAYER_NEARER * l;
                float lx = (px - sideX * h) * k, ly = (py - sideY * h) * k, lz = (pz - sideZ * h) * k;
                float rx = (px + sideX * h) * k, ry = (py + sideY * h) * k, rz = (pz + sideZ * h) * k;
                float alpha = Math.min(light * LAYER_LIGHT[l], 1F);
                boolean core = l == layers - 1;
                float r = core ? 1F : CHANNEL_R, g = core ? 1F : CHANNEL_G, bl = core ? 1F : CHANNEL_B;
                int o = l * 7;
                // Both ways round: the game's lightning pipeline culls back faces, and where a sharp bend twists a
                // quad, half of it faces away whichever way it is wound (one way round, every quad was culled)
                if (i > 0 && (prev[o + 6] > 0F || alpha > 0F)) {
                    builder.addVertex(matrix, prev[o + 3], prev[o + 4], prev[o + 5]).setColor(r, g, bl, prev[o + 6]);
                    builder.addVertex(matrix, prev[o], prev[o + 1], prev[o + 2]).setColor(r, g, bl, prev[o + 6]);
                    builder.addVertex(matrix, lx, ly, lz).setColor(r, g, bl, alpha);
                    builder.addVertex(matrix, rx, ry, rz).setColor(r, g, bl, alpha);
                    builder.addVertex(matrix, prev[o], prev[o + 1], prev[o + 2]).setColor(r, g, bl, prev[o + 6]);
                    builder.addVertex(matrix, prev[o + 3], prev[o + 4], prev[o + 5]).setColor(r, g, bl, prev[o + 6]);
                    builder.addVertex(matrix, rx, ry, rz).setColor(r, g, bl, alpha);
                    builder.addVertex(matrix, lx, ly, lz).setColor(r, g, bl, alpha);
                }
                prev[o] = lx;
                prev[o + 1] = ly;
                prev[o + 2] = lz;
                prev[o + 3] = rx;
                prev[o + 4] = ry;
                prev[o + 5] = rz;
                prev[o + 6] = alpha;
            }
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
