package org.orecruncher.dsurround.effects.precipitation;

import org.orecruncher.dsurround.runtime.weather.Wind;

import com.mojang.blaze3d.vertex.Tesselator;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * The rain drops, snowflakes and dust falling or blowing around the camera. How many there are follows the rain
 * level: at full strength the field holds {@link #capacity()} of them. They are spread through a cylinder around the
 * camera and dropped when the camera leaves them behind. Rain and snow fall until they reach the ground of their
 * column; dust is blown along it, low down, and settles after a while. Carried in under an overhang or a canopy, any
 * of them drifts on a little way out of the wind, fading, and lands where it reaches something.
 * <p>
 * New drops appear anywhere in the cylinder rather than only at its top, so the field fills at once when the camera
 * moves, and fade in over their first ticks so they don't pop into view. The drops are kept in flat arrays, not
 * objects, so thousands of them cost little to tick.
 * <p>
 * A tick of the drops can run on another thread, while frames are drawn ({@link #start}, then {@link #finish} the
 * next tick): there are two sets of the drops, one drawn while the step fills the other. The step reads only the
 * columns, looked up as it starts, and the wind as it was then; what needs the world (whether a drop carried in under
 * something is in open air there) it leaves for {@link #finish}, on the client thread. Everything else is the client
 * thread's, which in Minecraft is also the render thread.
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
    // A rain drop's size, 0 to 1, centers on this plus the storm's strength times the next, spread either way by the
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
    // A puff's center is at least this far above the ground, and starts up to this much higher again, most of them low
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

    // What a step leaves for the world to settle, on the thread that may read it (see finish): a rain drop, snowflake
    // or puff carried below the top of the column it moved into, to take cover if it is in open air there; or one
    // already under cover, to drift on if it still is
    private static final byte TAKE_COVER = 0;
    private static final byte DRIFT = 1;

    private final int capacity;
    private final int radius;
    private final float radiusSquared;
    private final int maxSpawnPerTick;
    private final PrecipitationColumns columns;

    // The drops as of the last step, drawn and read; and those the step under way fills, meanwhile
    private Drops drops;
    private Drops next;

    // Where rain drops and snowflakes landed this tick: x and z, the height of the ground they landed on, how big they
    // were, and what they were. A snowflake also keeps which flake it was drawn as, and its light; and either, when it
    // was carried into the side of something, where it was the tick before (x, y, z) and how it was moving.
    private final int maxLandings;
    private final float[] landingX;
    private final float[] landingZ;
    private final int[] landingGround;
    private final float[] landingSize;
    private final byte[] landingKind;
    private final float[] landingVariant;
    private final int[] landingLight;
    private final boolean[] landingBlownIn;
    private final float[] landingY, landingMotionX, landingMotionY, landingMotionZ;
    private int landings;

    // The questions the step left for the world: which drop in the next buffer, which question, and the ground of the
    // column it was carried into (for a landing where it isn't open air)
    private final int[] questionDrop;
    private final byte[] questionKind;
    private final int[] questionGround;
    private int questions;
    // The camera's height the step was made for, for the questions' answers
    private double stepCameraY;
    // The step under way, or null
    private CompletableFuture<Void> step;

    public PrecipitationField(int capacity) {
        this(capacity, DEFAULT_RADIUS);
    }

    /**
     * @param radius how far from the camera, horizontally, there are drops, in blocks
     */
    public PrecipitationField(int capacity, int radius) {
        this.capacity = capacity;
        this.radius = radius;
        this.radiusSquared = radius * radius;
        this.columns = new PrecipitationColumns(radius);
        this.maxLandings = forArea(MAX_LANDINGS, radius);
        this.landingX = new float[this.maxLandings];
        this.landingZ = new float[this.maxLandings];
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
        this.drops = new Drops(capacity);
        this.next = new Drops(capacity);
        this.questionDrop = new int[capacity];
        this.questionKind = new byte[capacity];
        this.questionGround = new int[capacity];
    }

    public int capacity() {
        return this.capacity;
    }

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

    /**
     * How far from the camera, horizontally, there are drops, in blocks
     */
    public int radius() {
        return this.radius;
    }

    /**
     * The drops as of the last finished step: what is drawn
     */
    Drops drops() {
        return this.drops;
    }

    public int count() {
        return this.drops.count;
    }

    /**
     * How many of the drops are dust
     */
    public int dustCount() {
        return this.drops.dustCount;
    }

    public void clear() {
        this.abandon();
        this.drops.clear();
        this.next.clear();
        this.landings = 0;
        this.columns.invalidate();
    }

    /**
     * Whether there is nothing to do: no storm, and nothing left falling or blowing. The field needn't be ticked then,
     * sparing the columns being looked up; {@link #idle} says so.
     */
    public boolean isIdle(float rainLevel) {
        return rainLevel <= 0F && this.drops.count == 0;
    }

    /**
     * Says a tick went by without {@link #tick}, so the columns looked up before may be out of date by the next. A
     * step under way is dropped: with nothing falling, there was nothing for it to move.
     */
    public void idle() {
        this.abandon();
        this.landings = 0;
        this.columns.invalidate();
    }

    /**
     * The color of the dust across the area around the camera, 0xRRGGBB, as the puffs take it; or
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

    public float landingX(int i) {
        return this.landingX[i];
    }

    public float landingZ(int i) {
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
     * the rain level calls for. With the rain stopped, the drops already falling finish their fall. A step started and
     * finished at once (see {@link #start} and {@link #finish}).
     *
     * @param rainLevel 0 to 1
     * @param density   the share of the drops the storm calls for that there are, 0 to 1: fewer, for slower machines
     * @param wind      carries the drops sideways; ticked before this
     */
    public void tick(IColumnSource source, double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind wind, RandomSource random) {
        this.start(source, cameraX, cameraY, cameraZ, rainLevel, density, wind, random, Runnable::run);
        this.finish(source);
    }

    /**
     * Starts the drops' next tick on {@code executor} (see {@link #tick}), from the drops as they are. Until
     * {@link #finish}, the drops drawn and counted stay as they are, and the landings are the step's to write. The
     * columns are looked up here, from {@code source}, on the thread that may read the world; the step itself reads
     * only them and the wind as it is now, and leaves what needs the world to {@link #finish}.
     *
     * @param random only the step uses it, until it finishes
     * @throws IllegalStateException if a step is under way
     */
    public void start(IColumnSource source, double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind wind,
                      RandomSource random, Executor executor) {
        if (this.step != null)
            throw new IllegalStateException("A step is under way");
        this.columns.refresh(source, Mth.floor(cameraX), Mth.floor(cameraY), Mth.floor(cameraZ));
        var now = wind.snapshot();
        this.step = CompletableFuture.runAsync(() -> this.step(cameraX, cameraY, cameraZ, rainLevel, density, now, random), executor);
    }

    /**
     * Waits for the step under way, if there is one, answers what it left for the world from {@code source}, on the
     * thread that may read it, and makes its drops the ones drawn. Its landings are then the tick's.
     *
     * @return false if no step was under way
     * @throws RuntimeException what the step threw, if it failed: the drops stay as they were, with no landings
     */
    public boolean finish(IColumnSource source) {
        var running = this.step;
        if (running == null)
            return false;
        this.step = null;
        try {
            running.join();
        } catch (CompletionException e) {
            this.questions = 0;
            this.landings = 0;
            if (e.getCause() instanceof RuntimeException failure)
                throw failure;
            if (e.getCause() instanceof Error error)
                throw error;
            throw e;
        }
        // From the last back: a drop that goes leaves the last in its place, which has been answered already
        var to = this.next;
        for (int q = this.questions - 1; q >= 0; q--)
            this.answer(source, to, this.questionDrop[q], this.questionKind[q], this.questionGround[q]);
        this.questions = 0;
        this.next = this.drops;
        this.drops = to;
        return true;
    }

    /**
     * Whether a step is under way: started and not yet finished
     */
    public boolean isStepping() {
        return this.step != null;
    }

    // Waits out a step under way and drops what it made
    private void abandon() {
        var running = this.step;
        if (running == null)
            return;
        this.step = null;
        try {
            running.join();
        } catch (CompletionException ignored) {
            // Dropped anyway
        }
        this.questions = 0;
    }

    // The step itself: on the executor's thread, reading the drops drawn, the columns and the wind as it was, writing
    // only the next drops, the landings and the questions
    private void step(double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind.Snapshot wind, RandomSource random) {
        this.landings = 0;
        this.questions = 0;
        this.stepCameraY = cameraY;
        this.move(this.drops, this.next, cameraX, cameraY, cameraZ, wind);
        this.spawn(this.next, cameraX, cameraY, cameraZ, rainLevel, density, wind, random);
    }

    /**
     * Moves each drop of {@code from} a tick into {@code to}, packed at its front: those that go are left behind
     */
    private void move(Drops from, Drops to, double cameraX, double cameraY, double cameraZ, Wind.Snapshot wind) {
        // The drops' positions are floats: the camera's, as floats, is as near as they can tell
        float cx = (float) cameraX, cz = (float) cameraZ;
        to.count = 0;
        to.dustCount = 0;
        for (int i = 0; i < from.count; i++) {
            int j = to.count;
            from.copyTo(i, to, j);
            to.prevX[j] = to.x[j];
            to.prevY[j] = to.y[j];
            to.prevZ[j] = to.z[j];

            // Eased toward the wind where the drop is, so a passing gust shows as a ripple through the drops
            float response = switch (to.kind[j]) {
                case SNOW -> SNOW_WIND_RESPONSE;
                case DUST -> DUST_WIND_RESPONSE;
                default -> RAIN_WIND_RESPONSE;
            };
            if (to.covered[j] > 0) {
                // Out of the wind: it slows to a drift
                to.windX[j] *= COVER_DRAG;
                to.windZ[j] *= COVER_DRAG;
            } else {
                float carried = windAt(wind, to.x[j] - cx, to.z[j] - cz) * to.drift[j];
                to.windX[j] += (wind.x() * carried - to.windX[j]) * response;
                to.windZ[j] += (wind.z() * carried - to.windZ[j]) * response;
            }
            to.x[j] += to.windX[j];
            to.z[j] += to.windZ[j];
            to.age[j]++;

            boolean keep = to.kind[j] == DUST
                    ? this.moveDust(to, j, cx, cameraY, cz)
                    : this.fall(to, j, cx, cameraY, cz);
            if (keep) {
                to.count++;
                if (to.kind[j] == DUST)
                    to.dustCount++;
            }
        }
    }

    /**
     * Moves a rain drop or snowflake down a tick
     *
     * @return false if it landed or left the cylinder
     */
    private boolean fall(Drops d, int i, float cameraX, double cameraY, float cameraZ) {
        d.y[i] -= d.speed[i];
        if (d.kind[i] == SNOW) {
            d.phase[i] += SNOW_SWAY_RATE;
            turned(d, i);
            d.x[i] += Mth.sin(d.phase[i]) * SNOW_SWAY;
            d.z[i] += Mth.cos(d.phase[i] * 0.7F) * SNOW_SWAY;
        }

        int column = this.inCylinder(d, i, cameraX, cameraZ);
        if (column < 0)
            return false;
        if (d.covered[i] > 0)
            return this.ask(i, DRIFT, 0);
        var what = this.columns.kind(column);
        if (what != PrecipitationKind.RAIN && what != PrecipitationKind.SNOW)
            return false;
        int ground = this.columns.ground(column);
        if (d.y[i] <= ground) {
            // Still above the ground on its last tick: it came down on top. Otherwise it was carried into the side of
            // something taller, a wall or a tree, or in under an overhang or a canopy. In open air under cover it drifts
            // on in (see driftUnderCover); into something solid, it goes on to wherever the world says it ends up (a
            // snowflake may stick there, a rain drop may run down a window). It doesn't land on the column's top. Which
            // it is, is the world's to say, once the step is done; one that didn't move into another column (the
            // ground rose under it) wasn't carried in at all.
            if (d.prevY[i] <= ground)
                return movedColumn(d, i) && this.ask(i, TAKE_COVER, ground);
            if (d.kind[i] == RAIN)
                this.landed(d.x[i], d.z[i], ground, rainSize(d, i), RAIN, 0F, 0);
            else
                this.landed(d.x[i], d.z[i], ground, snowSize(d, i), SNOW, (d.drift[i] - MIN_DRIFT) / DRIFT_RANGE, d.light[i]);
            return false;
        }
        if (d.y[i] <= cameraY - BELOW)
            return false;
        d.light[i] = this.columns.light(column);
        return true;
    }

    /**
     * Moves a puff of dust a tick: up and down in the turbulence, and up over rising ground, starting before it gets
     * there and easing up rather than stepping up a block at a time. Partly in the ground meanwhile, it is drawn soft,
     * so it blends in.
     *
     * @return false if it settled (soon after it leaves the dust), was stopped by a wall, or left the cylinder
     */
    private boolean moveDust(Drops d, int i, float cameraX, double cameraY, float cameraZ) {
        float before = Mth.sin(d.phase[i]);
        d.phase[i] += DUST_TURBULENCE_RATE;
        turned(d, i);
        d.y[i] += (Mth.sin(d.phase[i]) - before) * DUST_TURBULENCE_HEIGHT;

        int column = this.inCylinder(d, i, cameraX, cameraZ);
        if (column < 0 || d.age[i] >= d.lifetime[i])
            return false;
        if (d.covered[i] > 0)
            return this.ask(i, DRIFT, 0);
        // Blown out of the dust: it drifts on and fades out, as it would settling, rather than vanishing at the edge
        if (this.columns.kind(column) != PrecipitationKind.DUST)
            d.lifetime[i] = Math.min(d.lifetime[i], d.age[i] + DUST_FADE);
        float floor = this.columns.ground(column) + DUST_MIN_LIFT;
        // Far below the column's top: blown in under an overhang, or into a wall
        if (floor - d.y[i] > DUST_CLIMB)
            return movedColumn(d, i) && this.ask(i, TAKE_COVER, 0);
        float target = Math.max(floor, Math.min(this.groundAhead(d, i), floor + DUST_LOOKAHEAD_CLIMB));
        if (d.y[i] < target) {
            float along = Mth.sqrt(d.windX[i] * d.windX[i] + d.windZ[i] * d.windZ[i]);
            d.y[i] += Math.min((target - d.y[i]) * DUST_RISE_RATE, Math.max(along * DUST_MAX_CLIMB, DUST_MIN_RISE));
        }
        if (d.y[i] <= cameraY - BELOW || d.y[i] > cameraY + ABOVE)
            return false;
        d.light[i] = this.columns.light(column);
        return true;
    }

    /**
     * Where a puff should be over the ground it is blowing toward, a few ticks downwind: the ground there plus the
     * puff's lift, or negative infinity if that is beyond the area looked up, or not dust
     */
    private float groundAhead(Drops d, int i) {
        int ahead = this.columns.index(d.x[i] + d.windX[i] * DUST_LOOKAHEAD_TICKS, d.z[i] + d.windZ[i] * DUST_LOOKAHEAD_TICKS);
        if (ahead < 0 || this.columns.kind(ahead) != PrecipitationKind.DUST)
            return Float.NEGATIVE_INFINITY;
        return this.columns.ground(ahead) + DUST_MIN_LIFT;
    }

    /**
     * The column a drop is in, or -1 if it has left the cylinder around the camera
     */
    private int inCylinder(Drops d, int i, float cameraX, float cameraZ) {
        float dx = d.x[i] - cameraX;
        float dz = d.z[i] - cameraZ;
        if (dx * dx + dz * dz > this.radiusSquared)
            return -1;
        return this.columns.index(d.x[i], d.z[i]);
    }

    /**
     * Whether a drop moved into another column this tick, rather than the ground rising under it
     */
    private static boolean movedColumn(Drops d, int i) {
        return Mth.floor(d.prevX[i]) != Mth.floor(d.x[i]) || Mth.floor(d.prevZ[i]) != Mth.floor(d.z[i]);
    }

    /**
     * How much of the steady wind blows at a point, relative to the camera: more where a gust is passing
     */
    private static float windAt(Wind.Snapshot wind, float dx, float dz) {
        return wind.gustFactor(dx * wind.directionX() + dz * wind.directionZ());
    }

    /**
     * Leaves a question about a drop for the world (see {@link #finish}); it is kept until answered
     *
     * @return true: the drop stays for now
     */
    private boolean ask(int drop, byte question, int ground) {
        this.questionDrop[this.questions] = drop;
        this.questionKind[this.questions] = question;
        this.questionGround[this.questions] = ground;
        this.questions++;
        return true;
    }

    /**
     * Answers a step's question about a drop from the world: whether it is in open air, and how it is lit there. One
     * that isn't goes, landing where it reached something if it is rain or snow.
     */
    private void answer(IColumnSource source, Drops d, int i, byte question, int ground) {
        int bx = Mth.floor(d.x[i]), by = Mth.floor(d.y[i]), bz = Mth.floor(d.z[i]);
        boolean lands = d.kind[i] != DUST;
        boolean open = source.isOpen(bx, by, bz);
        if (question == TAKE_COVER) {
            // Carried below the top of the column it moved into: in open air there, in under an overhang or a canopy,
            // it is under cover from now on (see driftUnderCover); in the side of something, it lands
            if (open) {
                d.covered[i] = 1;
                // Lit where it is, not as the top of the column: under cover it is darker
                d.light[i] = source.light(bx, by, bz);
                return;
            }
            if (lands)
                this.followedOn(d, i, ground);
            this.remove(d, i);
            return;
        }
        // Drifting on under cover: through open air, fading, until it reaches something (where a rain drop or
        // snowflake lands, as the world says) or fades out. The heights the columns know are the cover's, so the world
        // is asked directly.
        if (!open) {
            if (lands)
                this.followedOn(d, i, by);
            this.remove(d, i);
            return;
        }
        if (++d.covered[i] > coverTicks(d.kind[i]) || d.y[i] <= this.stepCameraY - BELOW || d.y[i] > this.stepCameraY + ABOVE) {
            this.remove(d, i);
            return;
        }
        d.light[i] = source.light(bx, by, bz);
    }

    private void spawn(Drops d, double cameraX, double cameraY, double cameraZ, float rainLevel, float density, Wind.Snapshot wind, RandomSource random) {
        // As many as the storm calls for over the part of the area each falls in, so the drops are as dense at the
        // edge of the rain as in the middle of it, and no tries are spent where nothing falls
        var level = Mth.clamp(rainLevel, 0F, 1F) * Mth.clamp(density, 0F, 1F);
        int fallingWanted = (int) (this.capacity * level * this.columns.fallingShare());
        int dustWanted = (int) (this.capacity * DUST_SHARE * level * this.columns.dustShare());
        int fallingNeeded = Math.max(0, fallingWanted - (d.count - d.dustCount));
        int dustNeeded = Math.max(0, dustWanted - d.dustCount);
        int toAdd = Math.min(Math.min(fallingNeeded + dustNeeded, this.capacity - d.count), this.maxSpawnPerTick);
        // A try can miss (a dry column, or ground above the cylinder), so allow a few more tries than drops
        for (int tries = toAdd * 2; tries > 0 && toAdd > 0; tries--) {
            // Even over the disc's area, not bunched at its center
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
                if (d.dustCount >= dustWanted)
                    continue;
                // Most low down, thinning with height
                float h = random.nextFloat();
                py = this.columns.ground(column) + DUST_MIN_LIFT + DUST_HEIGHT * h * h;
                if (py <= cameraY - BELOW || py > cameraY + ABOVE)
                    continue;
            } else {
                if (d.count - d.dustCount >= fallingWanted)
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

            int i = d.count++;
            d.x[i] = d.prevX[i] = (float) px;
            d.y[i] = d.prevY[i] = (float) py;
            d.z[i] = d.prevZ[i] = (float) pz;
            switch (what) {
                case SNOW -> {
                    d.kind[i] = SNOW;
                    d.speed[i] = SNOW_SPEED_MIN + random.nextFloat() * SNOW_SPEED_RANGE;
                }
                case DUST -> {
                    d.kind[i] = DUST;
                    d.speed[i] = 0F;
                    d.lifetime[i] = DUST_MIN_LIFETIME + random.nextInt(DUST_LIFETIME_RANGE);
                    d.color[i] = this.columns.dustColorAt(column);
                    d.dustCount++;
                }
                default -> {
                    d.kind[i] = RAIN;
                    d.speed[i] = RAIN_SPEED_MIN + rainDropSize(rainLevel, random) * RAIN_SPEED_RANGE;
                }
            }
            // Already moving with the wind, rather than starting from still and visibly catching up
            d.drift[i] = MIN_DRIFT + random.nextFloat() * DRIFT_RANGE;
            float carried = windAt(wind, (float) (px - cameraX), (float) (pz - cameraZ)) * d.drift[i];
            d.windX[i] = wind.x() * carried;
            d.windZ[i] = wind.z() * carried;
            d.phase[i] = random.nextFloat() * Mth.TWO_PI;
            turned(d, i);
            d.thinning[i] = random.nextFloat();
            d.age[i] = 0;
            d.covered[i] = 0;
            d.light[i] = this.columns.light(column);
            toAdd--;
        }
    }

    /**
     * The size of a new rain drop, 0 to 1, for a storm of the given strength. The spread is the sum of two random
     * numbers, so sizes near the storm's own are the most likely.
     */
    static float rainDropSize(float rainLevel, RandomSource random) {
        var center = RAIN_SIZE_BASE + RAIN_SIZE_PER_LEVEL * Mth.clamp(rainLevel, 0F, 1F);
        var spread = (random.nextFloat() + random.nextFloat() - 1F) * RAIN_SIZE_SPREAD;
        return Mth.clamp(center + spread, 0F, 1F);
    }

    /**
     * How big a rain drop is, 0 to 1. Taken from how fast it falls, which follows from its size and doesn't change.
     */
    float rainSize(int i) {
        return rainSize(this.drops, i);
    }

    private static float rainSize(Drops d, int i) {
        return Mth.clamp((d.speed[i] - RAIN_SPEED_MIN) / RAIN_SPEED_RANGE, 0F, 1F);
    }

    /**
     * Notes a rain drop or snowflake as a landing for the world to follow on from: where it was the tick before, and
     * how it was moving
     */
    private void followedOn(Drops d, int i, int ground) {
        boolean landed = d.kind[i] == RAIN
                ? this.landed(d.prevX[i], d.prevZ[i], ground, rainSize(d, i), RAIN, 0F, 0)
                : this.landed(d.prevX[i], d.prevZ[i], ground, snowSize(d, i), SNOW, (d.drift[i] - MIN_DRIFT) / DRIFT_RANGE, d.light[i]);
        if (!landed)
            return;
        int at = this.landings - 1;
        this.landingBlownIn[at] = true;
        this.landingY[at] = d.prevY[i];
        this.landingMotionX[at] = d.x[i] - d.prevX[i];
        this.landingMotionY[at] = d.y[i] - d.prevY[i];
        this.landingMotionZ[at] = d.z[i] - d.prevZ[i];
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
    private boolean landed(float x, float z, int ground, float size, byte kind, float variant, int light) {
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
    private static void turned(Drops d, int i) {
        float spin = d.phase[i] * (d.kind[i] == DUST ? PrecipitationRenderer.DUST_SPIN : PrecipitationRenderer.SNOW_SPIN);
        d.spinCos[i] = Mth.cos(spin);
        d.spinSin[i] = Mth.sin(spin);
    }

    // Moves the last drop into the gap, so the drops stay packed at the front of the arrays
    private void remove(Drops d, int i) {
        if (d.kind[i] == DUST)
            d.dustCount--;
        int last = --d.count;
        if (i != last)
            d.copyTo(last, d, i);
    }

    /**
     * Draws the drops with the current shader, which should be {@link PrecipitationShaders#DROPS}, and the particle
     * atlas bound. Render thread only.
     *
     * @param dustOnly   true to draw just the dust, false to draw just the rain and snow
     * @param snowflakes the flakes of the snow texture to choose from, or none to draw plain flakes
     * @param largest    the largest of them, in pixels
     * @param dust       the dust sprites to choose from
     * @param scales     how big rain and snow are drawn, from the player's settings
     * @return how many were drawn
     */
    public int draw(Tesselator tesselator, Camera camera, float partialTick, boolean dustOnly, List<Snowflakes.Flake> snowflakes,
                    int largest, TextureAtlasSprite[] dust, DropScales scales, RainColor rainColor) {
        return PrecipitationRenderer.draw(this, tesselator, camera, partialTick, dustOnly, snowflakes, largest, dust, scales, rainColor);
    }

    /**
     * How big a snowflake is, 0 for the smallest to 1 for the largest. Taken from how fast it falls, which is random
     * and doesn't change, so larger flakes fall a little faster, as clumps of snow do. Squared, so most flakes are
     * small and a few are large.
     */
    float snowSize(int i) {
        return snowSize(this.drops, i);
    }

    private static float snowSize(Drops d, int i) {
        var fraction = Mth.clamp((d.speed[i] - SNOW_SPEED_MIN) / SNOW_SPEED_RANGE, 0F, 1F);
        return fraction * fraction;
    }

    /**
     * How big a puff of dust is, 0 to 1. Taken from its drift, which is random and doesn't change, so larger puffs
     * are carried a little further by the wind.
     */
    float dustSize(int i) {
        return Mth.clamp((this.drops.drift[i] - MIN_DRIFT) / DRIFT_RANGE, 0F, 1F);
    }

    /**
     * Which of {@code variants} looks a drop takes: picked by its drift, which is random and doesn't change, so a
     * snowflake keeps its look for its whole fall even as drops are moved about in the arrays
     */
    int variant(int i, int variants) {
        var picked = (int) ((this.drops.drift[i] - MIN_DRIFT) / DRIFT_RANGE * variants);
        return Mth.clamp(picked, 0, variants - 1);
    }

    /**
     * How faded in a drop is, 0 to 1. Every drop fades in when new; dust also fades out as it settles.
     */
    float fade(int i, float partialTick) {
        var d = this.drops;
        var age = d.age[i] + partialTick;
        float fade = switch (d.kind[i]) {
            case SNOW -> Math.min(1F, age / SNOW_FADE_IN);
            case DUST -> Mth.clamp(Math.min(age, d.lifetime[i] - age) / DUST_FADE, 0F, 1F);
            default -> Math.min(1F, age / RAIN_FADE_IN);
        };
        // Under cover it fades out as it drifts in
        if (d.covered[i] > 0)
            fade *= coverFade(d.covered[i] - 1 + partialTick, coverTicks(d.kind[i]));
        return fade;
    }

    /**
     * How much of a drop is left, 1 to 0, after this many ticks under cover, out of the ticks it lasts there
     */
    static float coverFade(float ticks, int lasts) {
        return Mth.clamp(1F - ticks / lasts, 0F, 1F);
    }

    /**
     * One set of the drops, in flat arrays, packed at the front: the field keeps two, one drawn while the next step
     * fills the other
     */
    static final class Drops {

        // Where each drop is now and was last tick, for drawing between ticks
        final float[] x, y, z, prevX, prevY, prevZ;
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
        // Dust only: its color, 0xRRGGBB, and how many ticks it lasts
        final int[] color;
        final int[] lifetime;
        int count;
        int dustCount;

        Drops(int capacity) {
            this.x = new float[capacity];
            this.y = new float[capacity];
            this.z = new float[capacity];
            this.prevX = new float[capacity];
            this.prevY = new float[capacity];
            this.prevZ = new float[capacity];
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

        void clear() {
            this.count = 0;
            this.dustCount = 0;
        }

        /**
         * Copies drop {@code i} to {@code at} in {@code to}, which may be this set
         */
        void copyTo(int i, Drops to, int at) {
            to.prevX[at] = this.prevX[i];
            to.prevY[at] = this.prevY[i];
            to.prevZ[at] = this.prevZ[i];
            to.x[at] = this.x[i];
            to.y[at] = this.y[i];
            to.z[at] = this.z[i];
            to.speed[at] = this.speed[i];
            to.windX[at] = this.windX[i];
            to.windZ[at] = this.windZ[i];
            to.drift[at] = this.drift[i];
            to.phase[at] = this.phase[i];
            to.spinCos[at] = this.spinCos[i];
            to.spinSin[at] = this.spinSin[i];
            to.thinning[at] = this.thinning[i];
            to.age[at] = this.age[i];
            to.covered[at] = this.covered[i];
            to.light[at] = this.light[i];
            to.kind[at] = this.kind[i];
            to.color[at] = this.color[i];
            to.lifetime[at] = this.lifetime[i];
        }
    }
}
