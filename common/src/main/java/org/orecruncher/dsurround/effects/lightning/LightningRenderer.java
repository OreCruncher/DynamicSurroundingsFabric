package org.orecruncher.dsurround.effects.lightning;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.effects.ModShader;
import org.orecruncher.dsurround.effects.NoiseTexture;
import org.orecruncher.dsurround.effects.RenderTimer;
import org.orecruncher.dsurround.lib.Library;

import java.util.List;

/**
 * Draws lightning: each channel of a bolt as a ribbon turned to face the camera, a white hot core along its middle in
 * a pale violet glow, and the cloud lit up around it as patches of light lying along the cloud base, broken up by
 * noise into the cloud's texture (see {@link CloudGlow}): when the mod's clouds aren't drawn, as they are lit by it
 * from inside instead. The light adds to what is behind it. A far strike shows less, the more so the closer the fog
 * (see {@link Strikes#visibility}).
 * Drawn where vanilla draws its weather, after it: with Fabulous graphics to the weather's target, writing depth so
 * the transparency pass lays it over the scene where it is in front (that target is cleared, without the scene's
 * depth); otherwise to the scene, tested against its depth, which hides it behind hills, and cut off by the clouds'.
 * <p>
 * Ribbons are as wide as a channel looks close by, and never thinner than about a pixel far away. The shader
 * ({@link #SHADER}) is registered by each platform. Everything here runs on the render thread.
 */
public final class LightningRenderer {

    public static final ModShader SHADER = new ModShader("lightning", DefaultVertexFormat.POSITION_TEX_COLOR,
            "lightning", "lightning will be vanilla's", Library.LOGGER);

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
     * How much wider a ribbon is than its core: the glow around it. The shader's CORE is one over this.
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

    @Nullable
    private static DynamicTexture noise;

    private LightningRenderer() {
    }

    /**
     * Draws the strikes, each as it is at {@code nowTicks}. Sets up the drawing state it needs and puts it back.
     *
     * @param nowTicks the game time, in ticks, with the part of a tick
     * @param fogEnd   where the fog is thickest, in blocks, for how much of a far strike shows (see
     *                 {@link Strikes#visibility})
     * @param glow     whether to draw the cloud's glow: not when the mod's clouds are lit by it instead
     * @return how many strikes were drawn, for the diagnostics
     */
    public static int draw(List<Strike> strikes, Camera camera, double nowTicks, float fogEnd, boolean glow) {
        var current = SHADER.get();
        if (strikes.isEmpty() || current == null)
            return 0;
        var tesselator = Tesselator.getInstance();
        int[] drawn = {0};
        RENDER_TIMER.begin();
        RENDER_TIMER.count(strikes.size());
        SHADER.run(() -> drawn[0] = drawStrikes(current, tesselator, strikes, camera, nowTicks, fogEnd, glow), tesselator::clear);
        RENDER_TIMER.end();
        return drawn[0];
    }

    private static int drawStrikes(ShaderInstance shader, Tesselator tesselator, List<Strike> strikes, Camera camera,
                                   double nowTicks, float fogEnd, boolean drawGlow) {
        boolean fabulous = Minecraft.useShaderTransparency();
        shader.safeGetUniform("AddOnly").set(fabulous ? 1F : 0F);
        if (noise == null)
            noise = NoiseTexture.create(NOISE_SIZE, 0x6C69676EL);
        shader.setSampler("NoiseSampler", noise.getId());
        RenderSystem.setShader(SHADER.supplier());

        // Light, added to what is there. With Fabulous graphics it goes to the weather's target, which the
        // transparency pass lays over the scene as behind * (1 - alpha) + color: the shader gives its light with next
        // to no alpha (not none, or the pass skips the pixel), and depth is written so the pass can sort it.
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(fabulous);
        RenderSystem.disableCull();
        int drawn = 0;
        try {
            var position = camera.getPosition();
            // The channels first: with depth written, the cloud's glow drawn first would hide them behind it
            shader.safeGetUniform("CoreStrength").set(1F);
            var builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
            upload(builder);

            if (!drawGlow)
                return drawn;
            shader.safeGetUniform("CoreStrength").set(0F);
            builder = tesselator.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);
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
                if (any && strike.shape() == null)
                    drawn++;
            }
            upload(builder);
        } finally {
            RenderSystem.enableCull();
            RenderSystem.depthMask(true);
            RenderSystem.defaultBlendFunc();
            RenderSystem.disableBlend();
        }
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

    private static void upload(BufferBuilder builder) {
        var mesh = builder.build();
        if (mesh != null)
            BufferUploader.drawWithShader(mesh);
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
     * Light up to 2 goes in the vertex alpha, which holds 0 to 1: halved here, doubled again by the shader. Too faint
     * to see is 0.
     */
    static float alpha(float light) {
        return light < MIN_LIGHT ? 0F : Math.min(light * 0.5F, 1F);
    }

    /**
     * Whether lightning can be drawn: the shader loaded, drawing hasn't failed since, and no shader pack is in use
     */
    public static boolean isUsable() {
        return SHADER.isUsable();
    }
}
