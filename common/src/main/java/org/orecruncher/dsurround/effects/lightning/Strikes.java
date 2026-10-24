package org.orecruncher.dsurround.effects.lightning;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.IRandomizer;

/**
 * Makes strikes ({@link Strike}) of each kind, and works out the numbers the lightning handler goes by: how strong the
 * lightning is in a storm, how often it flashes, how far away and how loud.
 */
public final class Strikes {

    /**
     * Flashes a second in the strongest thunderstorm
     */
    static final float FLASHES_PER_SECOND = 0.3F;
    /**
     * The nearest the sky's own strikes come, in blocks: closer is left to the game's lightning
     */
    public static final float NEAREST = 64F;
    /**
     * The farthest strikes are, in blocks, if the view reaches that far
     */
    public static final float FARTHEST = 400F;
    // Strikes stay this far inside the far end of the view, as a share of it
    private static final float VIEW_MARGIN = 0.85F;
    /**
     * How fast thunder travels, in blocks a second: as sound does in air
     */
    static final float SPEED_OF_SOUND = 343F;
    /**
     * A strike in the sky this close or closer lights up the whole sky and the ground, as the game's own do
     */
    public static final float FLASH_RANGE = 160F;

    // Leaders take this long to cross the main channel, in seconds; spider lightning crawls
    static final float GROUND_LEADER = 0.12F;
    static final float SPIDER_LEADER = 0.35F;

    private Strikes() {
    }

    /**
     * How strong the lightning is, 0 to 1: as much as it is thundering, more the stronger the storm
     *
     * @param thunderLevel the world's thunder level, 0 to 1
     * @param stormLevel   the storm's strength (WeatherSimulation's rain level), 0 to 1
     */
    public static float intensity(float thunderLevel, float stormLevel) {
        float thunder = Mth.clamp(thunderLevel, 0F, 1F);
        return thunder * (0.3F + 0.7F * Mth.clamp(stormLevel, 0F, 1F));
    }

    /**
     * The chance of a flash in the sky each tick, at a strength
     */
    public static float chancePerTick(float intensity) {
        if (intensity <= 0F)
            return 0F;
        return FLASHES_PER_SECOND / 20F * (0.2F + 0.8F * Math.min(intensity, 1F));
    }

    /**
     * The farthest a strike is drawn, in blocks: {@link #FARTHEST}, or less, inside the far end of the view
     *
     * @param depthFar how far the view reaches, in blocks (GameRenderer.getDepthFar)
     */
    public static float farthest(float depthFar) {
        return Math.min(FARTHEST, depthFar * VIEW_MARGIN);
    }

    /**
     * A distance between two, picked so strikes are spread evenly over the ground between: there is more ground far
     * away, so more strikes are
     */
    public static float spreadDistance(float nearest, float farthest, IRandomizer random) {
        if (farthest <= nearest)
            return nearest;
        float r = random.nextFloat();
        return Mth.sqrt(Mth.lerp(r, nearest * nearest, farthest * farthest));
    }

    /**
     * The share of strikes that land anywhere around the player, whatever the wind; the rest come from upwind
     */
    static final float ANYWHERE = 0.2F;
    /**
     * How far either side of dead upwind a strike from upwind lands, at most, in radians (about 100 degrees). The
     * nearer upwind, the more strikes there are.
     */
    static final float UPWIND_SPREAD = 1.75F;

    /**
     * Which way from the player a strike lands, as an angle (x is its cosine, z its sine). Storms are most active along
     * their leading edge, so most strikes come from upwind: from where the wind blows. With no wind, from anywhere.
     *
     * @param windX the way the wind blows, x and z, of length 1; or 0, 0 for none
     */
    public static float strikeAngle(float windX, float windZ, IRandomizer random) {
        if (windX * windX + windZ * windZ < 0.25F || random.nextFloat() < ANYWHERE)
            return random.nextFloat() * Mth.TWO_PI;
        float upwind = (float) Mth.atan2(-windZ, -windX);
        // Two picks less each other: most near the middle, fewer toward either side
        return upwind + (random.nextFloat() - random.nextFloat()) * UPWIND_SPREAD;
    }

    /**
     * Which kind of flash comes next: more bolts and spider lightning the stronger the storm, flashes in the cloud
     * otherwise
     */
    public static Strike.Kind pickKind(float intensity, IRandomizer random) {
        float i = Mth.clamp(intensity, 0F, 1F);
        float spider = 0.1F + 0.2F * i;
        float ground = 0.3F + 0.15F * i;
        float r = random.nextFloat();
        if (r < spider)
            return Strike.Kind.SPIDER;
        if (r < spider + ground)
            return Strike.Kind.GROUND;
        return Strike.Kind.SHEET;
    }

    /**
     * How long the thunder takes to arrive, in ticks
     */
    public static int thunderDelay(float distance) {
        return Math.max(0, Math.round(distance / SPEED_OF_SOUND * 20F));
    }

    /**
     * How loud the thunder is, 0 to 1: full close by, a third at the farthest
     */
    public static float thunderVolume(float distance) {
        float far = Mth.clamp((distance - NEAREST) / (FARTHEST - NEAREST), 0F, 1F);
        return Mth.lerp(far, 1F, 0.3F);
    }

    /**
     * The thunder's pitch: a crack close by, a low rumble far off
     */
    public static float thunderPitch(float distance) {
        float far = Mth.clamp((distance - NEAREST) / (FARTHEST - NEAREST), 0F, 1F);
        return Mth.lerp(far, 1F, 0.75F);
    }

    /**
     * How much of a strike shows from a distance, 0 to 1: the rain between hides some of a far one
     */
    static float visibility(float distance) {
        float far = Mth.clamp((distance - 100F) / (FARTHEST - 100F), 0F, 1F);
        return 1F - 0.5F * far;
    }

    /**
     * A cloud to ground strike, starting at {@code (x, y, z)} in the cloud and reaching the ground
     * {@code (dx, dy, dz)} from there
     *
     * @param flicker whether it may have more than one stroke
     * @param boltId  the game's lightning bolt it stands in for, or -1
     */
    public static Strike ground(double x, double y, double z, float dx, float dy, float dz, float intensity, float distance,
                                boolean flicker, double startTicks, int boltId, IRandomizer random) {
        var shape = BoltGenerator.groundStrike(dx, dy, dz, intensity, random);
        int strokes = flicker ? Strike.strokeCount(intensity, random) : 1;
        float leader = GROUND_LEADER * random.nextFloat(0.8F, 1.2F);
        // The cloud lit where the bolt leaves it
        var glow = CloudGlow.around(new float[]{0F, 0F, 0F, 0F, 0F, 0F}, 20F, 30F + 25F * intensity, strokes, 0.45F, random);
        return new Strike(Strike.Kind.GROUND, x, y, z, shape, leader,
                Strike.strokeTimes(leader, strokes, random), Strike.strokePeaks(strokes, random),
                glow, distance, visibility(distance), startTicks, boltId);
    }

    /**
     * Spider lightning, starting at {@code (x, y, z)} under the cloud base and crawling to {@code (dx, dy, dz)} from
     * there
     */
    public static Strike spider(double x, double y, double z, float dx, float dy, float dz, float intensity, float distance,
                                boolean flicker, double startTicks, IRandomizer random) {
        var shape = BoltGenerator.spider(dx, dy, dz, intensity, random);
        int strokes = flicker ? Strike.strokeCount(intensity, random) : 1;
        float leader = SPIDER_LEADER * random.nextFloat(0.8F, 1.2F);
        // The cloud glows along the way: where it starts, midway and at the end
        var main = shape.main();
        int mid = main.size() / 2, end = main.size() - 1;
        var centres = new float[]{0F, 0F, 0F, main.x(mid), main.y(mid), main.z(mid), main.x(end), main.y(end), main.z(end)};
        var glow = CloudGlow.around(centres, 20F, 40F, strokes, 0.4F, random);
        return new Strike(Strike.Kind.SPIDER, x, y, z, shape, leader,
                Strike.strokeTimes(leader, strokes, random), Strike.strokePeaks(strokes, random),
                glow, distance, visibility(distance), startTicks, -1);
    }

    /**
     * A flash in the cloud at {@code (x, y, z)}, with no channel to be seen
     */
    public static Strike sheet(double x, double y, double z, float intensity, float distance, boolean flicker,
                               double startTicks, IRandomizer random) {
        int strokes = flicker ? Strike.strokeCount(intensity, random) : 1;
        var glow = CloudGlow.sheet(60F + 60F * intensity, strokes, 0.9F, random);
        return new Strike(Strike.Kind.SHEET, x, y, z, null, 0F,
                Strike.strokeTimes(0F, strokes, random), Strike.strokePeaks(strokes, random),
                glow, distance, visibility(distance), startTicks, -1);
    }
}
