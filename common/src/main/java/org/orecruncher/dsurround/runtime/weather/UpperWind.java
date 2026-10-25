package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.lib.random.Mixers;

/**
 * The wind up where the clouds are, which carries them: worked out from the game time alone, so every player in a
 * world sees the clouds in the same places, however long each has been playing. Every in-game minute
 * ({@link #KNOT_TICKS}) it has a heading and a speed, picked from the game time, and it turns and changes speed smoothly
 * from one to the next. Its heading wanders about the day's wind direction ({@link Wind#dayAngle}), and its speed is
 * the day's windiness, a little more or less from minute to minute. It doesn't follow the storm: no one arriving
 * could know how the storm has gone, so how far it has carried the clouds couldn't be worked out.
 * <p>
 * How far it has carried them ({@link #drift}) is added up from the start of the world: the first time, a minute at a
 * time to now, and from there on only what has passed since. Speeds are in blocks a tick, as {@link Wind}'s are.
 * Client thread only.
 */
public final class UpperWind {

    /**
     * How often, in ticks, the wind has a heading and speed of its own: it changes smoothly between them
     */
    static final int KNOT_TICKS = 1200;
    /**
     * The calmest and the windiest days' speeds, in blocks a tick. The calmest is vanilla's clouds' speed.
     */
    static final float CALMEST = 0.03F;
    static final float WINDIEST = 0.12F;
    /**
     * How far the heading wanders from the day's either way, in radians (about 20 degrees)
     */
    static final float WANDER = 0.35F;
    /**
     * How much faster or slower than the day's speed a minute's can be, as a share of it
     */
    static final float GUSTINESS = 0.25F;
    private static final long TICKS_PER_DAY = 24000L;
    private static final long HEADING_SALT = 0x75707065726A6964L;
    private static final long SPEED_SALT = 0x77696E6479646179L;
    private static final long GUST_SALT = 0x6D696E7574656C79L;

    // How far it had carried the clouds by the start of a minute, which is where the adding up has got to
    private long knot = -1;
    private double sumX;
    private double sumZ;
    // The wind at either end of a minute, kept to save making them each call (every frame)
    private final float[] from = new float[2];
    private final float[] to = new float[2];

    /**
     * How far the clouds have been carried by a time, in blocks east-west and north-south, as {x, z}
     *
     * @param gameTime the game time, in ticks, with the part of a tick
     * @param out      where it goes, two long
     */
    public void drift(double gameTime, double[] out) {
        double time = Math.max(0D, gameTime);
        long at = (long) Math.floor(time / KNOT_TICKS);
        if (this.knot < 0 || at < this.knot) {
            // The first time, or time has gone back (another world): from the start
            this.knot = 0;
            this.sumX = 0D;
            this.sumZ = 0D;
        }
        var from = this.from;
        var to = this.to;
        if (this.knot < at) {
            velocity(this.knot, from);
            while (this.knot < at) {
                velocity(this.knot + 1, to);
                // The speed changes evenly over the minute: as far as the average of the two
                this.sumX += KNOT_TICKS * (from[0] + to[0]) * 0.5D;
                this.sumZ += KNOT_TICKS * (from[1] + to[1]) * 0.5D;
                from[0] = to[0];
                from[1] = to[1];
                this.knot++;
            }
        }
        velocity(at, from);
        velocity(at + 1, to);
        double along = time - (double) at * KNOT_TICKS;
        double share = along * along / (2D * KNOT_TICKS);
        out[0] = this.sumX + from[0] * along + (to[0] - from[0]) * share;
        out[1] = this.sumZ + from[1] * along + (to[1] - from[1]) * share;
    }

    /**
     * The wind at a minute, as {x, z}, in blocks a tick
     */
    static void velocity(long knot, float[] out) {
        long day = Math.floorDiv(knot * KNOT_TICKS, TICKS_PER_DAY);
        float angle = heading(day) + (unit(knot ^ HEADING_SALT) * 2F - 1F) * WANDER;
        float speed = daySpeed(day) * (1F + (unit(knot ^ GUST_SALT) * 2F - 1F) * GUSTINESS);
        out[0] = Mth.cos(angle) * speed;
        out[1] = Mth.sin(angle) * speed;
    }

    /**
     * The way the wind blows on a day, as an angle in radians from east toward south (see {@link Wind#dayAngle}); for
     * the day of the game time, {@code floor(gameTime / 24000)}
     */
    public static float heading(long day) {
        return Wind.dayAngle(day);
    }

    /**
     * A day's speed, between the calmest and the windiest
     */
    static float daySpeed(long day) {
        return Mth.lerp(unit(day ^ SPEED_SALT), CALMEST, WINDIEST);
    }

    /**
     * The day of a game time, for {@link #heading}
     */
    public static long dayOf(long gameTime) {
        return Math.floorDiv(gameTime, TICKS_PER_DAY);
    }

    // 0 to 1, from a number
    private static float unit(long value) {
        return (Mixers.splitMix64(value) >>> 40) / (float) (1L << 24);
    }
}
