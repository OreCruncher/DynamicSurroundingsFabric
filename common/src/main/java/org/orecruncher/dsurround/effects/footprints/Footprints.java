package org.orecruncher.dsurround.effects.footprints;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.FootprintStyle;
import org.orecruncher.dsurround.lib.compat.IrisCompat;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.runtime.weather.Shelter;
import org.orecruncher.dsurround.runtime.weather.WeatherSimulation;

import java.util.ArrayDeque;

/**
 * The footprints on the ground: flat prints from the footprint texture, lit as the ground they are on, fading away.
 * They last {@link #LIFE} ticks, {@link #SNOW_LIFE} in snow, wash away faster where it is raining on them, and go when
 * the ground under them changes (dug up, melted, covered). At most {@link #MAX}; past that the oldest go.
 * <p>
 * Not particles: they don't move, last long and can be many, so they are kept here and drawn together as decals
 * (nudged toward the camera by a polygon offset, as sign text is, so they don't flicker into the ground), through the
 * buffers vanilla's debug renderers use ({@code MixinDebugRenderer}). Client thread only.
 */
public final class Footprints {

    public static final ResourceLocation TEXTURE = Constants.asId("textures/footprints.png");

    // Their life, in ticks: 10 seconds, or a minute in snow
    static final float LIFE = 200F;
    static final float SNOW_LIFE = 1200F;
    // At most this much faster they age under a full storm, out in the open
    static final float RAIN_AGING = 3F;
    // How dark a fresh print is, 0 to 1; it fades from there
    static final float PEAK_ALPHA = 0.45F;
    // A print's half width and half length, in blocks, at scale 1
    private static final float HALF_WIDTH = 0.125F;
    private static final float HALF_LENGTH = 0.25F;
    // Above the ground: with the polygon offset, enough not to flicker into it
    private static final float LIFT = 1F / 512F;
    // How often, in ticks, the ground under them is checked
    private static final int CHECK_INTERVAL = 20;
    private static final int MAX = 1024;
    // Not drawn farther than this from the camera, in blocks
    private static final double DRAW_RANGE_SQ = 64D * 64D;

    private final WeatherSimulation weather;
    private final ArrayDeque<Print> prints = new ArrayDeque<>();
    private int untilCheck = CHECK_INTERVAL;

    public Footprints(WeatherSimulation weather) {
        this.weather = weather;
    }

    /**
     * One print. Its corners are worked out once, as it never moves: front left, back left, back right, front right,
     * relative to its middle.
     */
    private static final class Print {
        final double x, y, z;
        final float[] corners = new float[8];
        final float u0, u1;
        final int light;
        final BlockPos ground;
        // The ground as it was pressed: a print goes when it changes (snow added or melted)
        final BlockState groundState;
        // How much the rain washes it: 0 under cover or where it doesn't rain, to 1 in the open
        final float washing;
        final float life;
        float age;

        Print(double x, double y, double z, float yawDegrees, float scale, FootprintStyle style, boolean rightFoot,
              int light, BlockPos ground, BlockState groundState, float washing, float life) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.u0 = style.u0(rightFoot);
            this.u1 = style.u1(rightFoot);
            this.light = light;
            this.ground = ground;
            this.groundState = groundState;
            this.washing = washing;
            this.life = life;
            layOut(this.corners, yawDegrees, HALF_WIDTH * scale, HALF_LENGTH * scale);
        }
    }

    /**
     * Fills {@code corners} with a print's corners (x then z for each) relative to its middle: front left, back left,
     * back right, front right, for a print facing {@code yawDegrees} (Minecraft's yaw: 0 faces south, 90 west).
     */
    static void layOut(float[] corners, float yawDegrees, float halfWidth, float halfLength) {
        double yaw = Math.toRadians(yawDegrees);
        float forwardX = (float) -Math.sin(yaw), forwardZ = (float) Math.cos(yaw);
        // To the right of facing that way
        float rightX = -forwardZ, rightZ = forwardX;
        float fx = forwardX * halfLength, fz = forwardZ * halfLength;
        float rx = rightX * halfWidth, rz = rightZ * halfWidth;
        corners[0] = fx - rx;
        corners[1] = fz - rz;
        corners[2] = -fx - rx;
        corners[3] = -fz - rz;
        corners[4] = -fx + rx;
        corners[5] = -fz + rz;
        corners[6] = fx + rx;
        corners[7] = fz + rz;
    }

    /**
     * Leaves a print.
     *
     * @param y      the top of the ground as it is drawn
     * @param ground the block it is pressed into
     * @param state  that block; in snow prints last longer
     */
    void add(Level level, double x, double y, double z, float yawDegrees, float scale, FootprintStyle style,
             boolean rightFoot, BlockPos ground, BlockState state) {
        if (this.prints.size() >= MAX)
            this.prints.pollFirst();
        // Where the print is: above a full block, in a snow layer
        var above = BlockPos.containing(x, y, z);
        int light = LevelRenderer.getLightColor(level, above);
        boolean rains = level.getBiome(above).value().getPrecipitationAt(above) == Biome.Precipitation.RAIN;
        float washing = rains ? Shelter.exposure(level, above) : 0F;
        this.prints.addLast(new Print(x, y + LIFT, z, yawDegrees, scale, style, rightFoot, light, ground.immutable(),
                state, washing, state.is(BlockTags.SNOW) ? SNOW_LIFE : LIFE));
    }

    /**
     * Ages them a tick, dropping those worn away or whose ground has changed.
     */
    public void tick(Level level) {
        if (this.prints.isEmpty())
            return;
        float rain = this.weather.rainLevel(level.getRainLevel(1F));
        boolean check = --this.untilCheck <= 0;
        if (check)
            this.untilCheck = CHECK_INTERVAL;

        for (var it = this.prints.iterator(); it.hasNext(); ) {
            var print = it.next();
            print.age += agingRate(rain, print.washing);
            if (print.age >= print.life || check && !groundHolds(level, print))
                it.remove();
        }
    }

    private static boolean groundHolds(Level level, Print print) {
        return level.isLoaded(print.ground) && level.getBlockState(print.ground) == print.groundState;
    }

    /**
     * How many ticks of age a print gains in one tick: 1, and more as rain falls on it
     *
     * @param rain    the storm's strength, 0 to 1
     * @param washing how open the print is to the rain, 0 to 1
     */
    static float agingRate(float rain, float washing) {
        return 1F + rain * washing * RAIN_AGING;
    }

    /**
     * How dark a print is at an age, as a share of its life: fading slowly at first, faster toward the end
     */
    static float alphaAt(float lifeFraction) {
        float f = Math.min(1F, Math.max(0F, lifeFraction));
        return PEAK_ALPHA * (1F - f * f);
    }

    public int count() {
        return this.prints.size();
    }

    public void clear() {
        this.prints.clear();
    }

    /**
     * Draws them.
     *
     * @param camX the camera's position; the pose stack is relative to it
     */
    public void draw(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ) {
        // A shader pack draws the world its own way; whether these fit in is untested, so they stay off
        if (this.prints.isEmpty() || IrisCompat.isShaderPackInUse())
            return;

        VertexConsumer consumer = buffers.getBuffer(RenderType.textPolygonOffset(TEXTURE));
        Matrix4f pose = poseStack.last().pose();
        for (var print : this.prints) {
            double dx = print.x - camX, dz = print.z - camZ;
            if (dx * dx + dz * dz > DRAW_RANGE_SQ)
                continue;
            int alpha = (int) (alphaAt(print.age / print.life) * 255F);
            if (alpha <= 0)
                continue;
            int color = (alpha << 24) | 0xFFFFFF;
            float x = (float) dx, y = (float) (print.y - camY), z = (float) dz;
            float[] c = print.corners;
            vertex(consumer, pose, x + c[0], y, z + c[1], color, print.u0, 0F, print.light);
            vertex(consumer, pose, x + c[2], y, z + c[3], color, print.u0, 1F, print.light);
            vertex(consumer, pose, x + c[4], y, z + c[5], color, print.u1, 1F, print.light);
            vertex(consumer, pose, x + c[6], y, z + c[7], color, print.u1, 0F, print.light);
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f pose, float x, float y, float z, int color, float u, float v, int light) {
        consumer.addVertex(pose, x, y, z).setColor(color).setUv(u, v).setLight(light);
    }

    // Resolved on first use, during world rendering, when everything is registered
    private static final class Holder {
        static final Footprints INSTANCE = ContainerManager.resolve(Footprints.class);
    }

    /**
     * Draws the prints; called from world rendering (see {@code MixinDebugRenderer})
     */
    public static void render(PoseStack poseStack, MultiBufferSource buffers, double camX, double camY, double camZ) {
        Holder.INSTANCE.draw(poseStack, buffers, camX, camY, camZ);
    }
}
