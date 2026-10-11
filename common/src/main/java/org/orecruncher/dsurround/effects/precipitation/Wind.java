package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import org.orecruncher.dsurround.lib.random.Mixers;

/**
 * The wind that slants falling rain and snow. It blows steadily, from a direction that wanders slowly, and harder the
 * heavier the storm; every so often a gust passes through. A gust is a front: it reaches the upwind side of the area
 * around the camera first and sweeps across it, so its effect can be seen travelling through the drops.
 * <p>
 * The wind's direction starts from the day, so players in the same world see it blowing the same way; the gusts and
 * the wandering are each player's own. Speeds are in blocks a tick. Client thread only.
 */
public final class Wind {

    // Keeps the wind's random numbers apart from other per-day ones
    private static final long WIND_SALT = 0x6A09E667F3BCC909L;

    /**
     * The steady wind of a storm at full strength. Rain falls at about 1 block a tick, so this slants it by about 15
     * degrees; snow, falling far slower, much more.
     */
    static final float MAX_SPEED = 0.25F;
    // The steady wind of the lightest storm, as a fraction of a full storm's
    static final float CALM_FRACTION = 0.2F;
    /**
     * Between storms, a light breeze: enough for mist and steam to drift, in blocks a tick
     */
    static final float BREEZE = 0.03F;
    // Thunderstorms blow harder, and dust storms harder still: it is the wind that raises the dust
    static final float THUNDER_FACTOR = 1.3F;
    static final float DUST_FACTOR = 2F;
    // Fraction of the way to the storm's wind covered each tick, so it builds and dies away over a few seconds
    static final float EASE_PER_TICK = 0.02F;
    // How far the direction can wander each tick, in radians, and how fast that wandering itself changes
    static final float MAX_WANDER = 0.004F;
    static final float WANDER_CHANGE = 0.0005F;

    // A gust adds up to this fraction of the steady wind at its peak
    static final float MIN_GUST = 0.5F;
    static final float GUST_RANGE = 0.7F;
    // Ticks a gust takes to pass any one point, and between the end of one and the start of the next
    static final int MIN_GUST_TICKS = 20;
    static final int GUST_TICKS_RANGE = 30;
    static final int MIN_GUST_GAP = 60;
    static final int GUST_GAP_RANGE = 200;
    /**
     * How fast a gust's front travels, in blocks a tick
     */
    static final float GUST_TRAVEL = 0.6F;
    /**
     * How far upwind of the camera a gust's front starts, unless set to another: the edge of the area with drops
     */
    static final float DEFAULT_REACH = PrecipitationField.DEFAULT_RADIUS;

    private float reach = DEFAULT_REACH;
    private float angle;
    private float wander;
    private float speed;
    // From the angle and speed, worked out once a tick: every drop reads them, thousands of times a tick
    private float directionX = 1F;
    private float directionZ;
    private float x;
    private float z;
    private long day = Long.MIN_VALUE;

    // Ticks since the current gust's front set out, its length, and its strength; between gusts, ticks until the next
    private int gustAge;
    private int gustTicks;
    private float gustPower;
    private int untilGust = MIN_GUST_GAP;

    /**
     * The steady wind, x and z
     */
    public float x() {
        return this.x;
    }

    public float z() {
        return this.z;
    }

    /**
     * The way the wind is blowing, as a unit vector, x and z
     */
    public float directionX() {
        return this.directionX;
    }

    public float directionZ() {
        return this.directionZ;
    }

    private void updateVector() {
        this.directionX = Mth.cos(this.angle);
        this.directionZ = Mth.sin(this.angle);
        this.x = this.directionX * this.speed;
        this.z = this.directionZ * this.speed;
    }

    public float speed() {
        return this.speed;
    }

    /**
     * The way the wind is blowing as a compass bearing, in degrees clockwise from north (toward negative z)
     */
    public float bearing() {
        return bearing(this.directionX(), this.directionZ());
    }

    static float bearing(float x, float z) {
        var degrees = (float) Math.toDegrees(Math.atan2(x, -z));
        return degrees < 0F ? degrees + 360F : degrees;
    }

    private static final String[] COMPASS_POINTS = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

    /**
     * The nearest of the eight compass points to a bearing
     */
    public static String compassPoint(float bearing) {
        return COMPASS_POINTS[Math.floorMod(Math.round(bearing / 45F), 8)];
    }

    public boolean isGusting() {
        return this.gustTicks > 0;
    }

    /**
     * Moves the wind on a tick.
     *
     * @param rainLevel   the storm's strength, 0 to 1; a light breeze without rain
     * @param thundering  whether it is a thunderstorm
     * @param dusty       whether the storm is raising dust where the camera is
     * @param day         the day number, which sets the direction the wind starts from
     */
    public void tick(float rainLevel, boolean thundering, boolean dusty, long day, RandomSource random) {
        if (day != this.day) {
            // A new day, or the first tick: the day's own direction. Mid storm this swings the wind round, rarely.
            this.day = day;
            this.angle = Mixers.splitMix64(day ^ WIND_SALT) / (float) Long.MAX_VALUE * Mth.PI;
        }

        this.wander = Mth.clamp(this.wander + (random.nextFloat() * 2F - 1F) * WANDER_CHANGE, -MAX_WANDER, MAX_WANDER);
        this.angle += this.wander;

        var target = rainLevel <= 0F ? BREEZE : MAX_SPEED * (CALM_FRACTION + (1F - CALM_FRACTION) * rainLevel);
        if (thundering)
            target *= THUNDER_FACTOR;
        if (dusty)
            target *= DUST_FACTOR;
        this.speed += (target - this.speed) * EASE_PER_TICK;
        this.updateVector();

        this.tickGust(random);
    }

    private void tickGust(RandomSource random) {
        if (this.gustTicks > 0) {
            // Over once its front has crossed the whole area and its tail has passed the far side
            if (++this.gustAge > this.gustTicks + 2F * this.reach / GUST_TRAVEL) {
                this.gustTicks = 0;
                this.untilGust = MIN_GUST_GAP + random.nextInt(GUST_GAP_RANGE);
            }
        } else if (--this.untilGust <= 0) {
            this.gustAge = 0;
            this.gustTicks = MIN_GUST_TICKS + random.nextInt(GUST_TICKS_RANGE);
            this.gustPower = MIN_GUST + random.nextFloat() * GUST_RANGE;
        }
    }

    /**
     * How much stronger than the steady wind it is at a point, from a passing gust: 1 when there is none, rising
     * smoothly to 1 plus the gust's power at its height and falling away again.
     *
     * @param along how far downwind of the camera the point is, in blocks; negative is upwind
     */
    public float gustFactor(float along, float partialTick) {
        if (this.gustTicks == 0)
            return 1F;
        // When the front reaches this point, counting from when it set out upwind
        var arrives = (along + this.reach) / GUST_TRAVEL;
        var passing = this.gustAge + partialTick - arrives;
        if (passing <= 0F || passing >= this.gustTicks)
            return 1F;
        var envelope = Mth.sin(Mth.PI * passing / this.gustTicks);
        return 1F + this.gustPower * envelope * envelope;
    }

    /**
     * Sets the steady wind and stops it changing by day, for tests
     */
    void set(float angle, float speed, long day) {
        this.day = day;
        this.angle = angle;
        this.speed = speed;
        this.updateVector();
    }

    /**
     * How far upwind of the camera a gust's front starts: the edge of the area with drops
     */
    public float reach() {
        return this.reach;
    }

    /**
     * Sets how far upwind a gust's front starts, to the range drops are drawn to, so a gust sweeps the whole of it
     */
    public void setReach(float reach) {
        this.reach = reach;
    }

    /**
     * Starts a gust now, for tests
     */
    void gust(int ticks, float power) {
        this.gustAge = 0;
        this.gustTicks = ticks;
        this.gustPower = power;
    }
}
