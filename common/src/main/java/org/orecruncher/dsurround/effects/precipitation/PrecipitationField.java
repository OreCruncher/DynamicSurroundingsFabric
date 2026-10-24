package org.orecruncher.dsurround.effects.precipitation;

import org.orecruncher.dsurround.runtime.weather.Wind;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;

/**
 * The rain drops, snowflakes and dust falling or blowing around the camera. How many there are follows the rain
 * level: at full strength the field holds {@link #capacity()} of them. They are spread through a cylinder around the
 * camera and dropped when the camera leaves them behind. Rain and snow fall until they reach the ground of their
 * column; dust is blown along it, low down, and settles after a while. Carried in under an overhang or a canopy, any
 * of them drifts on a little way out of the wind, fading, and lands where it reaches something.
 * <p>
 * New drops appear anywhere in the cylinder rather than only at its top, so the field fills at once when the camera
 * moves, and fade in over their first ticks so they don't pop into view. The drops are kept in flat arrays, not
 * objects, so thousands of them cost little to tick. Ticked on the client thread and read on the render thread,
 * which in Minecraft is the same thread.
 */
public final class PrecipitationField {

    static final byte RAIN = 0;
    static final byte SNOW = 1;
    static final byte DUST = 2;

    /**
     * How far from the camera, horizontally, there are drops, unless a field is given another range. The counts tuned
     * for a range (drops, landings, spots, flecks) are tuned for this one and scaled by area for others
     * ({@link #forArea}); the option's default can differ.
     */
    public static final int DEFAULT_RADIUS = 12;
    /**
     * How far above and below the camera there are drops
     */
    public static final int ABOVE = 16;
    public static final int BELOW = 8;

    // Blocks a tick. Rain falls faster than real rain, as vanilla's does, so it reads as rain at a glance.
    // From the smallest drizzle drop to the largest drop of a downpour: bigger drops fall faster
    static final float RAIN_SPEED_MIN = 0.45F;
    static final float RAIN_SPEED_RANGE = 0.85F;
    // A rain drop's size, 0 to 1, centres on this plus the storm's strength times the next, spread either way by the
    // last: light rain is mostly small drops, a downpour mostly large, with some of each in between
    static final float RAIN_SIZE_BASE = 0.1F;
    static final float RAIN_SIZE_PER_LEVEL = 0.8F;
    static final float RAIN_SIZE_SPREAD = 0.25F;
    static final float SNOW_SPEED_MIN = 0.05F;
    static final float SNOW_SPEED_RANGE = 0.05F;
    // How far a snowflake drifts side to side, and how fast, in blocks a tick and radians a tick
    static final float SNOW_SWAY = 0.03F;
    static final float SNOW_SWAY_RATE = 0.12F;
    // Fraction of the way to the wind's speed a drop's own horizontal speed covers each tick. Snowflakes are carried
    // by the air more lazily than rain, so a gust reaches them later and they keep drifting after it passes; dust,
    // being the air's own load, goes with it almost at once.
    static final float RAIN_WIND_RESPONSE = 0.35F;
    static final float SNOW_WIND_RESPONSE = 0.12F;
    static final float DUST_WIND_RESPONSE = 0.5F;
    // How much more or less than others a drop is carried by the wind, so they don't all fall exactly parallel
    static final float MIN_DRIFT = 0.85F;
    static final float DRIFT_RANGE = 0.3F;
    // Ticks a new drop takes to fade in
    static final int RAIN_FADE_IN = 3;
    static final int SNOW_FADE_IN = 15;

    /**
     * The share of the field dust may take at full strength. A puff of dust is far larger than a drop, so fewer fill
     * the air.
     */
    static final float DUST_SHARE = 0.25F;
    // A puff's centre is at least this far above the ground, and starts up to this much higher again, most of them low
    static final float DUST_MIN_LIFT = 0.4F;
    static final float DUST_HEIGHT = 5F;
    // How far below the ground of its column a puff may be before it counts as blown into a wall or cliff, and stops
    static final float DUST_CLIMB = 1.5F;
    // A puff rises over rising ground smoothly rather than jumping up each block: it looks this many ticks downwind,
    // to the ground it is about to reach, and closes this fraction of the gap to it each tick. It aims no more than
    // DUST_LOOKAHEAD_CLIMB above the ground it is over, so a wall ahead doesn't draw it up the wall's height.
    static final float DUST_LOOKAHEAD_TICKS = 6F;
    static final float DUST_LOOKAHEAD_CLIMB = 3F;
    static final float DUST_RISE_RATE = 0.3F;
    // However far it has to go, a puff climbs no more steeply than this, in blocks up for each block blown along: it
    // glides up a slope with the wind rather than shooting up it. In still air it can still rise this much a tick.
    static final float DUST_MAX_CLIMB = 1F;
    static final float DUST_MIN_RISE = 0.05F;
    // How far a puff rises and falls in the turbulence, either way, in blocks, and how fast, in radians a tick. A swing
    // about its height rather than a drift, so it doesn't wander off into the ground or the sky.
    static final float DUST_TURBULENCE_HEIGHT = 0.25F;
    static final float DUST_TURBULENCE_RATE = 0.15F;
    // Ticks a puff lasts before it has settled, and the ticks it takes to fade in and, at the end, out
    static final int DUST_MIN_LIFETIME = 60;
    static final int DUST_LIFETIME_RANGE = 80;
    static final int DUST_FADE = 20;

    // Carried in under an overhang, a drop drifts on into the shelter, no longer pushed by the wind: its sideways speed
    // keeps this share of itself each tick, so it goes a little way in, and it fades out over this many ticks, unless it
    // reaches something first. Rain is gone in a moment; snow and dust hang in the air.
    static final float COVER_DRAG = 0.85F;
    static final int RAIN_COVER_TICKS = 8;
    static final int SNOW_COVER_TICKS = 40;
    static final int DUST_COVER_TICKS = 30;

    /**
     * The most rain drop and snowflake landings kept from a tick, for splashes and flecks, at the default range. More
     * land at full strength, but more splashes than this would not be seen as more. A field at another range keeps as many for its area, so
     * splashes are as dense at any range (see {@link #forArea}).
     */
    public static final int MAX_LANDINGS = 128;

    private final int capacity;
    private final int radius;
    private final int maxSpawnPerTick;
    private final PrecipitationColumns columns;

    // Where each drop is now and was last tick, for drawing between ticks. East-west and north-south as doubles: far
    // from the world's middle a float moves in steps as large as half a block, which a drop's drift each tick is lost in
    final double[] x, z, prevX, prevZ;
    final float[] y, prevY;
    // How fast a rain drop or snowflake falls; not used for dust
    final float[] speed;
    // The drop's horizontal speed, carried by the wind, and how strongly the wind carries it
    final float[] windX, windZ, drift;
    final float[] phase;
    // The cos and sin of how far a snowflake or puff of dust has turned (see PrecipitationRenderer), as of its last
    // tick: worked out as its phase changes, a tick at a time, rather than for each drop in each frame drawn
    final float[] spinCos, spinSin;
    // A random number for each drop, 0 to 1, that doesn't change: which drops are left out when thinning those far
    // off, so the same ones are, frame after frame
    final float[] thinning;
    final int[] age;
    // Ticks since it was carried in under cover (see COVER_DRAG), or 0 out in the open
    final int[] covered;
    final int[] light;
    final byte[] kind;
    // Dust only: its colour, 0xRRGGBB, and how many ticks it lasts
    final int[] color;
    final int[] lifetime;
    int count;
    private int dustCount;

    // Where rain drops and snowflakes landed this tick: x and z, the height of the ground they landed on, how big they
    // were, and what they were. A snowflake also keeps which flake it was drawn as, and its light; and either, when it
    // was carried into the side of something, where it was the tick before (x, y, z) and how it was moving.
    private final int maxLandings;
    private final double[] landingX;
    private final double[] landingZ;
    private final int[] landingGround;
    private final float[] landingSize;
    private final byte[] landingKind;
    private final float[] landingVariant;
    private final int[] landingLight;
    private final boolean[] landingBlownIn;
    private final float[] landingY, landingMotionX, landingMotionY, landingMotionZ;
    private int landings;

    // The columns' source during a tick, for following drops in under cover
    private IColumnSource source;

    public PrecipitationField(int capacity) {
        this(capacity, DEFAULT_RADIUS);
    }

    /**
     * @param radius how far from the camera, horizontally, there are drops, in blocks
     */
    public PrecipitationField(int capacity, int radius) {
        this.capacity = capacity;
        this.radius = radius;
        this.columns = new PrecipitationColumns(radius);
        this.maxLandings = forArea(MAX_LANDINGS, radius);
        this.landingX = new double[this.maxLandings];
        this.landingZ = new double[this.maxLandings];
        this.landingGround = new int[this.maxLandings];
        this.landingSize = new float[this.maxLandings];
        this.landingKind = new byte[this.maxLandings];
        this.landingVariant = new float[this.maxLandings];
        this.landingLight = new int[this.maxLandings];
        this.landingBlownIn = new boolean[this.maxLandings];
        this.landingY = new float[this.maxLandings];
        this.landingMotionX = new float[this.maxLandings];
        this.landingMotionY = new float[this.maxLandings];
        this.landingMotionZ = new float[this.maxLandings];
        // Fills from empty in a few ticks; the rain level itself builds up more slowly than that
        this.maxSpawnPerTick = Math.max(1, capacity / 4);
        this.x = new double[capacity];
        this.y = new float[capacity];
        this.z = new double[capacity];
        this.prevX = new double[capacity];
        this.prevY = new float[capacity];
        this.prevZ = new double[capacity];
        this.speed = new float[capacity];
        this.windX = new float[capacity];
        this.windZ = new float[capacity];
        this.drift = new float[capacity];
        this.phase = new float[capacity];
        this.spinCos = new float[capacity];
        this.spinSin = new float[capacity];
        this.thinning = new float[capacity];
        this.age = new int[capacity];
        this.covered = new int[capacity];
        this.light = new int[capacity];
        this.kind = new byte[capacity];
        this.color = new int[capacity];
        this.lifetime = new int[capacity];
    }

    public int capacity() {
        return this.capacity;
    }

    /**
     * How far from the camera, horizontally, there are drops, in blocks
     */
    /**
     * The most landings it keeps from a tick: {@link #MAX_LANDINGS} at the default range, as many for the
     * area at another
     */
    public int maxLandings() {
        return this.maxLandings;
    }

    /**
     * A number that suits the default range ({@link #DEFAULT_RADIUS}), for a field reaching {@code radius}: as many
     * more as its area is larger, or fewer as it is smaller, so whatever it counts is as dense at any range
     */
    public static int forArea(int atDefaultRange, int radius) {
        float scale = radius / (float) DEFAULT_RADIUS;
        return Math.max(1, Math.round(atDefaultRange * scale * scale));
    }

    public int radius() {
        return this.radius;
    }

    public int count() {
        return this.count;
    }

    /**
     * How many of the drops are dust
     */
    public int dustCount() {
        return this.dustCount;
    }

    public void clear() {
        this.count = 0;
        this.dustCount = 0;
        this.landings = 0;
        this.columns.invalidate();
    }

    /**
     * Whether there is nothing to do: no storm, and nothing left falling or blowing. The field needn't be ticked then,
     * sparing the columns being looked up; {@link #idle} says so.
     */
    public boolean isIdle(float rainLevel) {
        return rainLevel <= 0F && this.count == 0;
    }

    /**
     * Says a tick went by without {@link #tick}, so the columns looked up before may be out of date by the next
     */
    public void idle() {
        this.landings = 0;
        this.columns.invalidate();
    }

    /**
     * The colour of the dust across the area around the camera, 0xRRGGBB, as the puffs take it; or
     * {@link IColumnSource#NOT_A_DUST_SOURCE} if, as of the last look, none of the area has dust
     */
    public int regionalDustColor() {
        return this.columns.dustShare() > 0F ? this.columns.regionalDustColor() : IColumnSource.NOT_A_DUST_SOURCE;
    }

    /**
     * Whether, as of the last tick, the storm brings dust at a position
     */
    public boolean isDustAt(double x, double z) {
        int column = this.columns.index(x, z);
        return column >= 0 && this.columns.kind(column) == PrecipitationKind.DUST;
    }

    /**
     * How many rain drops and snowflakes landed in the last tick, up to {@link #maxLandings}. Dust settles without a
     * trace.
     */
    public int landings() {
        return this.landings;
    }

    public double landingX(int i) {
        return this.landingX[i];
    }

    public double landingZ(int i) {
        return this.landingZ[i];
    }

    /**
     * The ground height of the column the drop landed in: the block it landed on is the one below
     */
    public int landingGround(int i) {
        return this.landingGround[i];
    }

    /**
     * How big the drop or flake that landed was, 0 to 1
     */
    public float landingSize(int i) {
        return this.landingSize[i];
    }

    /**
     * Whether what landed was a snowflake rather than a rain drop
     */
    public boolean landingIsSnow(int i) {
        return this.landingKind[i] == SNOW;
    }

    /**
     * For a snowflake that landed: which flake it was drawn as, 0 to 1, as {@link #variant} picks one
     */
    public float landingVariant(int i) {
        return this.landingVariant[i];
    }

    /**
     * For a snowflake that landed: its packed light as of its last tick
     */
    public int landingLight(int i) {
        return this.landingLight[i];
    }

    /**
     * Whether it was carried into a column below its top: into the side of something taller, or in under
     * an overhang or a canopy. Where it ends up is for the world to say, along its way from where it was the tick before:
     * {@link #landingX}, {@link #landingY} and {@link #landingZ}, moving by {@link #landingMotionX} and the others a
     * tick. Otherwise it landed on top of its column's ground.
     */
    public boolean landingBlownIn(int i) {
        return this.landingBlownIn[i];
    }

    /**
     * For a drop or snowflake blown in: its height the tick before
     */
    public float landingY(int i) {
        return this.landingY[i];
    }

    /**
     * For a drop or snowflake blown in: how far it moved in its last tick
     */
    public float landingMotionX(int i) {
        return this.landingMotionX[i];
    }

    public float landingMotionY(int i) {
        return this.landingMotionY[i];
    }

    public float landingMotionZ(int i) {
        return this.landingMotionZ[i];
    }

    /**
     * Moves the drops a tick, drops those that landed, settled or were left behind, and adds drops up to the number
     * the rain level calls for. With the rain stopped, the drops already falling finish their fall.
     *
     * @param rainLevel 0 to 1
     * @param density   the share of the drops the storm calls for that there are, 0 to 1: fewer, for slower machines
     * @param wind      carries the drops sideways; ticked before this
     */
    public void tick(IColumnSource source, double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind wind, RandomSource random) {
        this.columns.refresh(source, Mth.floor(cameraX), Mth.floor(cameraY), Mth.floor(cameraZ));
        this.landings = 0;
        this.source = source;
        try {
            this.move(cameraX, cameraY, cameraZ, wind);
        } finally {
            this.source = null;
        }
        this.spawn(cameraX, cameraY, cameraZ, rainLevel, density, wind, random);
    }

    private void move(double cameraX, double cameraY, double cameraZ, Wind wind) {
        int i = 0;
        while (i < this.count) {
            this.prevX[i] = this.x[i];
            this.prevY[i] = this.y[i];
            this.prevZ[i] = this.z[i];

            // Eased toward the wind where the drop is, so a passing gust shows as a ripple through the drops
            float response = switch (this.kind[i]) {
                case SNOW -> SNOW_WIND_RESPONSE;
                case DUST -> DUST_WIND_RESPONSE;
                default -> RAIN_WIND_RESPONSE;
            };
            if (this.covered[i] > 0) {
                // Out of the wind: it slows to a drift
                this.windX[i] *= COVER_DRAG;
                this.windZ[i] *= COVER_DRAG;
            } else {
                float carried = windAt(wind, this.x[i] - cameraX, this.z[i] - cameraZ) * this.drift[i];
                this.windX[i] += (wind.x() * carried - this.windX[i]) * response;
                this.windZ[i] += (wind.z() * carried - this.windZ[i]) * response;
            }
            this.x[i] += this.windX[i];
            this.z[i] += this.windZ[i];
            this.age[i]++;

            boolean keep = this.kind[i] == DUST
                    ? this.moveDust(i, cameraX, cameraY, cameraZ)
                    : this.fall(i, cameraX, cameraY, cameraZ);
            if (keep)
                i++;
            else
                this.remove(i);
        }
    }

    /**
     * Moves a rain drop or snowflake down a tick
     *
     * @return false if it landed or left the cylinder
     */
    private boolean fall(int i, double cameraX, double cameraY, double cameraZ) {
        this.y[i] -= this.speed[i];
        if (this.kind[i] == SNOW) {
            this.phase[i] += SNOW_SWAY_RATE;
            this.turned(i);
            this.x[i] += Mth.sin(this.phase[i]) * SNOW_SWAY;
            this.z[i] += Mth.cos(this.phase[i] * 0.7F) * SNOW_SWAY;
        }

        int column = this.inCylinder(i, cameraX, cameraZ);
        if (column < 0)
            return false;
        if (this.covered[i] > 0)
            return this.driftUnderCover(i, cameraY, coverTicks(this.kind[i]), true);
        var what = this.columns.kind(column);
        if (what != PrecipitationKind.RAIN && what != PrecipitationKind.SNOW)
            return false;
        if (this.y[i] <= this.columns.ground(column)) {
            // Still above the ground on its last tick: it came down on top. Otherwise it was carried into the side of
            // something taller, a wall or a tree, or in under an overhang or a canopy. In open air under cover it drifts
            // on in (see driftUnderCover); into something solid, it goes on to wherever the world says it ends up (a
            // snowflake may stick there, a rain drop may run down a window). It doesn't land on the column's top.
            if (this.prevY[i] <= this.columns.ground(column)) {
                if (this.takesCover(i))
                    return true;
                this.blownIn(i, this.columns.ground(column));
            } else if (this.kind[i] == RAIN) {
                this.landed(this.x[i], this.z[i], this.columns.ground(column), this.rainSize(i), RAIN, 0F, 0);
            } else {
                this.landed(this.x[i], this.z[i], this.columns.ground(column), this.snowSize(i), SNOW,
                        (this.drift[i] - MIN_DRIFT) / DRIFT_RANGE, this.light[i]);
            }
            return false;
        }
        if (this.y[i] <= cameraY - BELOW)
            return false;
        this.light[i] = this.columns.light(column);
        return true;
    }

    /**
     * Moves a puff of dust a tick: up and down in the turbulence, and up over rising ground, starting before it gets
     * there and easing up rather than stepping up a block at a time. Partly in the ground meanwhile, it is drawn soft,
     * so it blends in.
     *
     * @return false if it settled, was stopped by a wall, or left the cylinder or the dust
     */
    private boolean moveDust(int i, double cameraX, double cameraY, double cameraZ) {
        float before = Mth.sin(this.phase[i]);
        this.phase[i] += DUST_TURBULENCE_RATE;
        this.turned(i);
        this.y[i] += (Mth.sin(this.phase[i]) - before) * DUST_TURBULENCE_HEIGHT;

        int column = this.inCylinder(i, cameraX, cameraZ);
        if (column < 0 || this.age[i] >= this.lifetime[i])
            return false;
        if (this.covered[i] > 0)
            return this.driftUnderCover(i, cameraY, coverTicks(DUST), false);
        if (this.columns.kind(column) != PrecipitationKind.DUST)
            return false;
        float floor = this.columns.ground(column) + DUST_MIN_LIFT;
        // Far below the column's top: blown in under an overhang, or into a wall
        if (floor - this.y[i] > DUST_CLIMB)
            return this.takesCover(i);
        float target = Math.max(floor, Math.min(this.groundAhead(i), floor + DUST_LOOKAHEAD_CLIMB));
        if (this.y[i] < target) {
            float along = Mth.sqrt(this.windX[i] * this.windX[i] + this.windZ[i] * this.windZ[i]);
            this.y[i] += Math.min((target - this.y[i]) * DUST_RISE_RATE, Math.max(along * DUST_MAX_CLIMB, DUST_MIN_RISE));
        }
        if (this.y[i] <= cameraY - BELOW || this.y[i] > cameraY + ABOVE)
            return false;
        this.light[i] = this.columns.light(column);
        return true;
    }

    /**
     * Where a puff should be over the ground it is blowing toward, a few ticks downwind: the ground there plus the
     * puff's lift, or negative infinity if that is beyond the area looked up, or not dust
     */
    private float groundAhead(int i) {
        int ahead = this.columns.index(this.x[i] + this.windX[i] * DUST_LOOKAHEAD_TICKS, this.z[i] + this.windZ[i] * DUST_LOOKAHEAD_TICKS);
        if (ahead < 0 || this.columns.kind(ahead) != PrecipitationKind.DUST)
            return Float.NEGATIVE_INFINITY;
        return this.columns.ground(ahead) + DUST_MIN_LIFT;
    }

    /**
     * The column a drop is in, or -1 if it has left the cylinder around the camera
     */
    private int inCylinder(int i, double cameraX, double cameraZ) {
        float dx = (float) (this.x[i] - cameraX);
        float dz = (float) (this.z[i] - cameraZ);
        if (dx * dx + dz * dz > this.radius * this.radius)
            return -1;
        return this.columns.index(this.x[i], this.z[i]);
    }

    /**
     * How much of the steady wind blows at a point, relative to the camera: more where a gust is passing
     */
    private static float windAt(Wind wind, double dx, double dz) {
        var along = (float) (dx * wind.directionX() + dz * wind.directionZ());
        return wind.gustFactor(along, 0F);
    }

    private void spawn(double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind wind, RandomSource random) {
        // As many as the storm calls for over the part of the area each falls in, so the drops are as dense at the
        // edge of the rain as in the middle of it, and no tries are spent where nothing falls
        var level = Mth.clamp(rainLevel, 0F, 1F) * Mth.clamp(density, 0F, 1F);
        int fallingWanted = (int) (this.capacity * level * this.columns.fallingShare());
        int dustWanted = (int) (this.capacity * DUST_SHARE * level * this.columns.dustShare());
        int fallingNeeded = Math.max(0, fallingWanted - (this.count - this.dustCount));
        int dustNeeded = Math.max(0, dustWanted - this.dustCount);
        int toAdd = Math.min(Math.min(fallingNeeded + dustNeeded, this.capacity - this.count), this.maxSpawnPerTick);
        // A try can miss (a dry column, or ground above the cylinder), so allow a few more tries than drops
        for (int tries = toAdd * 2; tries > 0 && toAdd > 0; tries--) {
            // Even over the disc's area, not bunched at its centre
            float angle = random.nextFloat() * Mth.TWO_PI;
            float r = this.radius * Mth.sqrt(random.nextFloat());
            double px = cameraX + Mth.cos(angle) * r;
            double pz = cameraZ + Mth.sin(angle) * r;
            int column = this.columns.index(px, pz);
            if (column < 0)
                continue;
            var what = this.columns.kind(column);
            if (what == PrecipitationKind.NONE)
                continue;

            double py;
            if (what == PrecipitationKind.DUST) {
                if (this.dustCount >= dustWanted)
                    continue;
                // Most low down, thinning with height
                float h = random.nextFloat();
                py = this.columns.ground(column) + DUST_MIN_LIFT + DUST_HEIGHT * h * h;
                if (py <= cameraY - BELOW || py > cameraY + ABOVE)
                    continue;
            } else {
                if (this.count - this.dustCount >= fallingWanted)
                    continue;
                double top = cameraY + ABOVE;
                double bottom = Math.max(this.columns.ground(column), cameraY - BELOW);
                if (bottom >= top)
                    continue;
                py = bottom + random.nextFloat() * (top - bottom);
            }

            // Near the snow line, some of the rain falls as snow
            if (what == PrecipitationKind.RAIN && random.nextFloat() < this.columns.snowShare(column))
                what = PrecipitationKind.SNOW;

            int i = this.count++;
            this.x[i] = this.prevX[i] = px;
            this.y[i] = this.prevY[i] = (float) py;
            this.z[i] = this.prevZ[i] = pz;
            switch (what) {
                case SNOW -> {
                    this.kind[i] = SNOW;
                    this.speed[i] = SNOW_SPEED_MIN + random.nextFloat() * SNOW_SPEED_RANGE;
                }
                case DUST -> {
                    this.kind[i] = DUST;
                    this.speed[i] = 0F;
                    this.lifetime[i] = DUST_MIN_LIFETIME + random.nextInt(DUST_LIFETIME_RANGE);
                    this.color[i] = this.columns.dustColorAt(column);
                    this.dustCount++;
                }
                default -> {
                    this.kind[i] = RAIN;
                    this.speed[i] = RAIN_SPEED_MIN + rainDropSize(rainLevel, random) * RAIN_SPEED_RANGE;
                }
            }
            // Already moving with the wind, rather than starting from still and visibly catching up
            this.drift[i] = MIN_DRIFT + random.nextFloat() * DRIFT_RANGE;
            float carried = windAt(wind, px - cameraX, pz - cameraZ) * this.drift[i];
            this.windX[i] = wind.x() * carried;
            this.windZ[i] = wind.z() * carried;
            this.phase[i] = random.nextFloat() * Mth.TWO_PI;
            this.turned(i);
            this.thinning[i] = random.nextFloat();
            this.age[i] = 0;
            this.covered[i] = 0;
            this.light[i] = this.columns.light(column);
            toAdd--;
        }
    }

    /**
     * The size of a new rain drop, 0 to 1, for a storm of the given strength. The spread is the sum of two random
     * numbers, so sizes near the storm's own are the most likely.
     */
    static float rainDropSize(float rainLevel, RandomSource random) {
        var centre = RAIN_SIZE_BASE + RAIN_SIZE_PER_LEVEL * Mth.clamp(rainLevel, 0F, 1F);
        var spread = (random.nextFloat() + random.nextFloat() - 1F) * RAIN_SIZE_SPREAD;
        return Mth.clamp(centre + spread, 0F, 1F);
    }

    /**
     * How big a rain drop is, 0 to 1. Taken from how fast it falls, which follows from its size and doesn't change.
     */
    float rainSize(int i) {
        return Mth.clamp((this.speed[i] - RAIN_SPEED_MIN) / RAIN_SPEED_RANGE, 0F, 1F);
    }

    /**
     * Notes a rain drop or snowflake carried from one column into the next below its top: where it was the tick before, out in the
     * open, and how it was moving, for the world to follow it from. A flake that didn't move into another column (the
     * ground rose under it) wasn't blown in.
     */
    private void blownIn(int i, int ground) {
        if (Mth.floor(this.prevX[i]) == Mth.floor(this.x[i]) && Mth.floor(this.prevZ[i]) == Mth.floor(this.z[i]))
            return;
        this.followedOn(i, ground);
    }

    /**
     * Notes a rain drop or snowflake as a landing for the world to follow on from: where it was the tick before, and
     * how it was moving
     */
    private void followedOn(int i, int ground) {
        boolean landed = this.kind[i] == RAIN
                ? this.landed(this.prevX[i], this.prevZ[i], ground, this.rainSize(i), RAIN, 0F, 0)
                : this.landed(this.prevX[i], this.prevZ[i], ground, this.snowSize(i), SNOW, (this.drift[i] - MIN_DRIFT) / DRIFT_RANGE, this.light[i]);
        if (!landed)
            return;
        int at = this.landings - 1;
        this.landingBlownIn[at] = true;
        this.landingY[at] = this.prevY[i];
        this.landingMotionX[at] = (float) (this.x[i] - this.prevX[i]);
        this.landingMotionY[at] = this.y[i] - this.prevY[i];
        this.landingMotionZ[at] = (float) (this.z[i] - this.prevZ[i]);
    }

    /**
     * Whether a drop carried below the top of the column it moved into is in open air there, in under an overhang or a
     * canopy, rather than in the side of something: then it is under cover from now on (see {@link #driftUnderCover}).
     * A drop that didn't move into another column (the ground rose under it) isn't.
     */
    private boolean takesCover(int i) {
        if (Mth.floor(this.prevX[i]) == Mth.floor(this.x[i]) && Mth.floor(this.prevZ[i]) == Mth.floor(this.z[i]))
            return false;
        int bx = Mth.floor(this.x[i]), by = Mth.floor(this.y[i]), bz = Mth.floor(this.z[i]);
        if (!this.source.isOpen(bx, by, bz))
            return false;
        this.covered[i] = 1;
        // Lit where it is, not as the top of the column: under cover it is darker
        this.light[i] = this.source.light(bx, by, bz);
        return true;
    }

    /**
     * Moves a drop on under cover a tick: through open air, fading, until it reaches something (where a rain drop or
     * snowflake lands, as the world says) or fades out. The heights the columns know are the cover's, so the world is
     * asked directly.
     *
     * @param ticks how long it lasts under cover
     * @param lands whether it lands when it reaches something: rain and snow do, dust just stops
     * @return false if it reached something, faded out or left the cylinder
     */
    private boolean driftUnderCover(int i, double cameraY, int ticks, boolean lands) {
        int bx = Mth.floor(this.x[i]), by = Mth.floor(this.y[i]), bz = Mth.floor(this.z[i]);
        if (!this.source.isOpen(bx, by, bz)) {
            if (lands)
                this.followedOn(i, by);
            return false;
        }
        if (++this.covered[i] > ticks || this.y[i] <= cameraY - BELOW || this.y[i] > cameraY + ABOVE)
            return false;
        this.light[i] = this.source.light(bx, by, bz);
        return true;
    }

    /**
     * How long a drop of this kind lasts under cover, in ticks
     */
    static int coverTicks(byte kind) {
        return switch (kind) {
            case SNOW -> SNOW_COVER_TICKS;
            case DUST -> DUST_COVER_TICKS;
            default -> RAIN_COVER_TICKS;
        };
    }

    /**
     * Notes a landing on top of a column's ground
     *
     * @param variant a snowflake's choice of flake, 0 to 1 (see {@link #variant})
     * @return false if there was no room to
     */
    private boolean landed(double x, double z, int ground, float size, byte kind, float variant, int light) {
        if (this.landings == this.maxLandings)
            return false;
        this.landingX[this.landings] = x;
        this.landingZ[this.landings] = z;
        this.landingGround[this.landings] = ground;
        this.landingSize[this.landings] = size;
        this.landingKind[this.landings] = kind;
        this.landingVariant[this.landings] = variant;
        this.landingLight[this.landings] = light;
        this.landingBlownIn[this.landings] = false;
        this.landings++;
        return true;
    }

    /**
     * Works out a drop's turn from its phase: snowflakes turn as they sway, puffs of dust as they rise and fall
     */
    private void turned(int i) {
        float spin = this.phase[i] * (this.kind[i] == DUST ? PrecipitationRenderer.DUST_SPIN : PrecipitationRenderer.SNOW_SPIN);
        this.spinCos[i] = Mth.cos(spin);
        this.spinSin[i] = Mth.sin(spin);
    }

    // Moves the last drop into the gap, so the drops stay packed at the front of the arrays
    private void remove(int i) {
        if (this.kind[i] == DUST)
            this.dustCount--;
        int last = --this.count;
        if (i == last)
            return;
        this.x[i] = this.x[last];
        this.y[i] = this.y[last];
        this.z[i] = this.z[last];
        this.prevX[i] = this.prevX[last];
        this.prevY[i] = this.prevY[last];
        this.prevZ[i] = this.prevZ[last];
        this.speed[i] = this.speed[last];
        this.windX[i] = this.windX[last];
        this.windZ[i] = this.windZ[last];
        this.drift[i] = this.drift[last];
        this.phase[i] = this.phase[last];
        this.spinCos[i] = this.spinCos[last];
        this.spinSin[i] = this.spinSin[last];
        this.thinning[i] = this.thinning[last];
        this.age[i] = this.age[last];
        this.covered[i] = this.covered[last];
        this.light[i] = this.light[last];
        this.kind[i] = this.kind[last];
        this.color[i] = this.color[last];
        this.lifetime[i] = this.lifetime[last];
    }

    /**
     * Writes the drops into {@code quads}, for drawing with {@link PrecipitationShaders#DROPS} (or the dust's
     * pipeline). Render thread only.
     *
     * @param dustOnly   true to draw just the dust, false to draw just the rain and snow
     * @param snowflakes the flakes of the snow texture to choose from, or none to draw plain flakes
     * @param largest    the largest of them, in pixels
     * @param dust       the dust sprites to choose from
     * @param scales     how big rain and snow are drawn, from the player's settings
     */
    public void draw(ParticleQuads quads, Camera camera, float partialTick, boolean dustOnly, List<Snowflakes.Flake> snowflakes,
                     int largest, TextureAtlasSprite[] dust, DropScales scales, RainColor rainColor) {
        PrecipitationRenderer.draw(this, quads, camera, partialTick, dustOnly, snowflakes, largest, dust, scales, rainColor);
    }

    /**
     * How big a snowflake is, 0 for the smallest to 1 for the largest. Taken from how fast it falls, which is random
     * and doesn't change, so larger flakes fall a little faster, as clumps of snow do. Squared, so most flakes are
     * small and a few are large.
     */
    float snowSize(int i) {
        var fraction = Mth.clamp((this.speed[i] - SNOW_SPEED_MIN) / SNOW_SPEED_RANGE, 0F, 1F);
        return fraction * fraction;
    }

    /**
     * How big a puff of dust is, 0 to 1. Taken from its drift, which is random and doesn't change, so larger puffs
     * are carried a little further by the wind.
     */
    float dustSize(int i) {
        return Mth.clamp((this.drift[i] - MIN_DRIFT) / DRIFT_RANGE, 0F, 1F);
    }

    /**
     * Which of {@code variants} looks a drop takes: picked by its drift, which is random and doesn't change, so a
     * snowflake keeps its look for its whole fall even as drops are moved about in the arrays
     */
    int variant(int i, int variants) {
        var picked = (int) ((this.drift[i] - MIN_DRIFT) / DRIFT_RANGE * variants);
        return Mth.clamp(picked, 0, variants - 1);
    }

    /**
     * How faded in a drop is, 0 to 1. Every drop fades in when new; dust also fades out as it settles.
     */
    float fade(int i, float partialTick) {
        var age = this.age[i] + partialTick;
        float fade = switch (this.kind[i]) {
            case SNOW -> Math.min(1F, age / SNOW_FADE_IN);
            case DUST -> Mth.clamp(Math.min(age, this.lifetime[i] - age) / DUST_FADE, 0F, 1F);
            default -> Math.min(1F, age / RAIN_FADE_IN);
        };
        // Under cover it fades out as it drifts in
        if (this.covered[i] > 0)
            fade *= coverFade(this.covered[i] - 1 + partialTick, coverTicks(this.kind[i]));
        return fade;
    }

    /**
     * How much of a drop is left, 1 to 0, after this many ticks under cover, out of the ticks it lasts there
     */
    static float coverFade(float ticks, int lasts) {
        return Mth.clamp(1F - ticks / lasts, 0F, 1F);
    }
}
