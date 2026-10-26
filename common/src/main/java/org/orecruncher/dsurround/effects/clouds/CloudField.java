package org.orecruncher.dsurround.effects.clouds;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.runtime.weather.CloudCover;

import java.util.Arrays;

/**
 * The clouds around the camera, as blocks: a grid of {@link #CELL} by {@link #CELL} cells, each with a column of cloud
 * in each layer, from a base to a top. The low layer's columns all start at the same base (a cloud's base is flat, where
 * the air is cool enough for it to form) and reach up as far as noise and the {@link CloudCover} let them, in steps of
 * {@link #STEP} blocks: scattered puffs, towers, or a deck. The high layer's are thin streaks, drawn out one way.
 * <p>
 * Cells are in "cloud space": where they are in the world less how far the clouds have drifted. The grid is worked out
 * around the cell the camera is in, so the same cell has the same column wherever it is seen from. Heights are in blocks
 * from the dimension's cloud height.
 * <p>
 * The clouds change a cell at a time, not all at once: each cell is worked out again only on its own turn (see
 * {@link #update}), so as they grow, shrink and come and go, single blocks change here and there rather than the whole
 * sky jumping. A cell already in a cloud stays until the noise is clearly out of it, and its height changes only once
 * the noise is clearly a step away, so a cell near the edge doesn't flicker in and out. When the camera moves into another
 * cell the grid's columns are moved with it, and only the cells new to it are worked out.
 * <p>
 * A cell that changes keeps the column it had before for {@link #FADE_TICKS}, so the change can be drawn fading in or
 * out (see {@link CloudMesh}): what is in both columns is settled, what is in only one is changing. It isn't worked out
 * again until that is over, which, as it is a turn's length, is by its next turn.
 */
public final class CloudField {

    /**
     * A cell's width, in blocks: vanilla's
     */
    public static final int CELL = 12;
    /**
     * The low clouds' heights go up and down in steps this many blocks tall: vanilla's thickness
     */
    public static final int STEP = 4;
    /**
     * How far above the cloud height the high streaks are, in blocks, and how thick
     */
    public static final int HIGH = 80;
    public static final int HIGH_THICKNESS = 1;
    /**
     * How many cells across the low clouds' shapes are, about; and the streaks' along their length, and across it
     */
    static final double LOW_SCALE = 1D / 7D;
    static final double STREAK_LENGTH = 1D / 24D;
    static final double STREAK_WIDTH = 1D / 3D;
    /**
     * How far past the edge of a cloud, as a share of the sky, the noise must go before a cell already in it leaves it
     */
    static final float HOLD = 0.04F;
    /**
     * How far past half a step the noise's thickness must go, in steps, before a cell's height changes
     */
    static final float HOLD_STEPS = 0.2F;
    /**
     * How long a change takes to fade in or out, in ticks: a cell's turn comes this often (see CloudHandler), so one
     * change is over before the next can start
     */
    public static final int FADE_TICKS = 20;
    // A change long enough ago to be over
    private static final int LONG_AGO = Integer.MIN_VALUE;
    // A share of the cells under a deck hang a step lower, ragged, once the base has lowered this far
    private static final float RAGGED_FROM = 2 * STEP;
    private static final float RAGGED_SHARE = 0.2F;

    private static final long LOW_SEED = 0x6C6F77L;
    private static final long DETAIL_SEED = 0x7461L;
    private static final long RAGGED_SEED = 0x726167L;
    private static final long HIGH_SEED = 0x63697272L;
    private static final long TURN_SEED = 0x7475726EL;

    /**
     * What the clouds are like over a cell: they follow the biome under it, so they build over wet ground and thin over
     * dry, as they drift
     */
    @FunctionalInterface
    public interface Cover {
        /**
         * @param cx the cloud space cell, east-west; and {@code cz} north-south
         */
        CloudCover at(int cx, int cz);

        /**
         * Whether what the clouds are like over a cell is known, not estimated: where it comes from what is under the
         * cell, whether that has loaded. A cell worked out from an estimate is worked out again once it is known.
         */
        default boolean isKnown(int cx, int cz) {
            return true;
        }
    }

    /**
     * The layers of cloud
     */
    public enum Layer {
        LOW, HIGH
    }

    private int radius = -1;
    private int size;
    // The cloud space cell at the grid's corner
    private int originX;
    private int originZ;
    // Each layer's columns, by cell: base and top, in blocks from the cloud height; no cloud where the top isn't above
    // the base. And the low layer's steps of thickness above the deck's base, 0 for none, which holding a height needs
    private int[] lowBase = new int[0];
    private int[] lowTop = new int[0];
    private int[] lowSteps = new int[0];
    private int[] highBase = new int[0];
    private int[] highTop = new int[0];
    // Each layer's column before its cell's last change, and the tick it changed on
    private int[] prevLowBase = new int[0];
    private int[] prevLowTop = new int[0];
    private int[] prevHighBase = new int[0];
    private int[] prevHighTop = new int[0];
    private int[] changeTick = new int[0];
    // The tick of the last update, and of the one before
    private long tick;
    private long lastTick = Long.MIN_VALUE;
    // Whether a cell has been worked out since it came into the grid
    private boolean[] known = new boolean[0];
    // Whether a cell was last worked out from an estimate of what the clouds are like over it
    private boolean[] estimated = new boolean[0];
    // Whether a cell's quads are to be built again: it changed, its fade ended, or it came into the grid (see
    // CloudSections). Cleared by whoever builds them.
    private boolean[] dirty = new boolean[0];
    // Goes up when every cell is to be built again: the grid made anew, or cleared
    private int generation;
    // Each cell's turn's hash (see turnOf), worked out as it came into the grid
    private int[] turnHash = new int[0];
    // Spares, for moving the grid's columns without making new arrays each time
    private int[] spare = new int[0];
    private boolean[] spareKnown = new boolean[0];
    private int version;
    private boolean empty = true;

    /**
     * Works out every cell again, as one call to
     * {@link #update(CloudCover, int, int, int, double, float, float, long, int, int)} with a single turn, a fade's length
     * after the last, so any change before is over: for tests
     */
    public boolean update(CloudCover cover, int centerX, int centerZ, int radius, double time, float streakX, float streakZ) {
        long next = this.lastTick == Long.MIN_VALUE ? 0L : this.tick + FADE_TICKS;
        return this.update((cx, cz) -> cover, centerX, centerZ, radius, time, streakX, streakZ, next, 0, 1);
    }

    /**
     * As {@link #update(Cover, int, int, int, double, float, float, long, int, int)}, the clouds the same everywhere:
     * for tests
     */
    public boolean update(CloudCover cover, int centerX, int centerZ, int radius, double time, float streakX, float streakZ,
                          long tick, int turn, int turns) {
        return this.update((cx, cz) -> cover, centerX, centerZ, radius, time, streakX, streakZ, tick, turn, turns);
    }

    /**
     * Works out the cells whose turn it is again, and any new to the grid
     *
     * @param covers  what the clouds are like over each cell
     * @param centerX the cloud space cell the camera is in, east-west, and {@code centerZ} north-south
     * @param radius  how many cells out from it to go
     * @param time    how far the clouds have changed shape, in cells of noise: they change slowly as this goes up
     * @param streakX the way the high streaks run, for their noise; and {@code streakZ}
     * @param tick    the tick it is, which a change is timed from: one more than the last update's, as a rule
     * @param turn    whose turn it is, 0 to {@code turns - 1}: each cell has its own, scattered
     * @param turns   how many turns there are: each cell is worked out again once in this many calls
     * @return whether anything changed
     */
    public boolean update(Cover covers, int centerX, int centerZ, int radius, double time, float streakX, float streakZ,
                          long tick, int turn, int turns) {
        this.lastTick = this.lastTick == Long.MIN_VALUE ? tick - 1 : this.tick;
        this.tick = tick;
        boolean changed = false;
        if (radius != this.radius) {
            this.resize(radius);
            changed = true;
        }
        int newOriginX = centerX - radius;
        int newOriginZ = centerZ - radius;
        if (newOriginX != this.originX || newOriginZ != this.originZ) {
            this.moveTo(newOriginX, newOriginZ);
            changed = true;
        }

        float length = Mth.sqrt(streakX * streakX + streakZ * streakZ);
        float alongX = length > 0F ? streakX / length : 1F;
        float alongZ = length > 0F ? streakZ / length : 0F;

        for (int j = 0; j < this.size; j++) {
            int cz = this.originZ + j;
            for (int i = 0; i < this.size; i++) {
                int cx = this.originX + i;
                int index = i + j * this.size;
                boolean known = this.known[index];
                // Worked out from an estimate, and now known: again at once, and nothing held from the estimate
                boolean nowKnown = known && this.estimated[index] && covers.isKnown(cx, cz);
                boolean hold = known && !this.estimated[index];
                if (!known) {
                    this.turnHash[index] = turnHash(cx, cz);
                } else if (!nowKnown) {
                    // A change that finishes fading now: drawn settled from here on
                    long since = tick - (long) this.changeTick[index];
                    long sinceLast = this.lastTick - (long) this.changeTick[index];
                    if (since >= FADE_TICKS && sinceLast < FADE_TICKS) {
                        changed = true;
                        this.dirty[index] = true;
                    }
                    // Still fading: left as it is until that is over
                    if (since < FADE_TICKS)
                        continue;
                    if (turns > 1 && Integer.remainderUnsigned(this.turnHash[index], turns) != turn)
                        continue;
                }

                // What the clouds are like over it
                var cover = covers.at(cx, cz);
                float lowFrom = 1F - cover.coverage();
                float highFrom = 1F - cover.cirrus();
                int base = -Math.round(cover.baseDrop() / STEP) * STEP;
                boolean ragged = cover.baseDrop() >= RAGGED_FROM;
                float span = cover.maxThickness() - cover.minThickness();

                // The low layer
                int steps = 0;
                int b = 0;
                int t = 0;
                if (cover.coverage() > 0F) {
                    boolean was = hold && this.lowSteps[index] > 0;
                    float rank = CloudNoise.rank(CloudNoise.layered(cx * LOW_SCALE, cz * LOW_SCALE, time, LOW_SEED));
                    if (rank >= (was ? lowFrom - HOLD : lowFrom)) {
                        // How far inside the cloud the cell is: its middle builds up the most
                        float core = cover.coverage() >= 1F ? 1F : Mth.clamp((rank - lowFrom) / cover.coverage(), 0F, 1F);
                        float detail = CloudNoise.value(cx * 0.6D, cz * 0.6D, time * 2D, DETAIL_SEED);
                        float thickness = cover.minThickness() + span * (float) Math.pow(core, 1.3D) * (0.7F + 0.6F * detail);
                        thickness = Mth.clamp(thickness, cover.minThickness(), cover.maxThickness());
                        float wanted = thickness / STEP;
                        int held = this.lowSteps[index];
                        steps = was && Math.abs(wanted - held) < 0.5F + HOLD_STEPS ? held : Math.max(1, Math.round(wanted));
                        b = base;
                        if (ragged) {
                            boolean wasLower = was && this.lowBase[index] < this.lowTop[index] - held * STEP;
                            float lowerFrom = 1F - RAGGED_SHARE - (wasLower ? HOLD : 0F);
                            if (CloudNoise.value(cx * 0.8D, cz * 0.8D, time * 3D, RAGGED_SEED) > lowerFrom)
                                b -= STEP;
                        }
                        t = base + steps * STEP;
                    }
                }

                // The high streaks, drawn out one way: noise stretched along it
                int hb = 0;
                int ht = 0;
                if (cover.cirrus() > 0F) {
                    boolean was = hold && this.highTop[index] > this.highBase[index];
                    double along = cx * alongX + cz * alongZ;
                    double across = -cx * alongZ + cz * alongX;
                    float rank = CloudNoise.rank(CloudNoise.layered(along * STREAK_LENGTH, across * STREAK_WIDTH, time * 0.5D, HIGH_SEED));
                    // Not where a tower reaches up into them
                    if (rank >= (was ? highFrom - HOLD : highFrom) && t < HIGH) {
                        hb = HIGH;
                        ht = HIGH + HIGH_THICKNESS;
                    }
                }
                boolean differs = this.lowBase[index] != b || this.lowTop[index] != t
                        || this.highBase[index] != hb || this.highTop[index] != ht;
                if (!known) {
                    // New to the grid, at its edge or as the clouds start: there at once
                    this.prevLowBase[index] = b;
                    this.prevLowTop[index] = t;
                    this.prevHighBase[index] = hb;
                    this.prevHighTop[index] = ht;
                    this.changeTick[index] = LONG_AGO;
                } else if (differs) {
                    this.prevLowBase[index] = this.lowBase[index];
                    this.prevLowTop[index] = this.lowTop[index];
                    this.prevHighBase[index] = this.highBase[index];
                    this.prevHighTop[index] = this.highTop[index];
                    this.changeTick[index] = (int) tick;
                }
                changed |= differs;
                if (differs || !known)
                    this.dirty[index] = true;
                this.lowBase[index] = b;
                this.lowTop[index] = t;
                this.lowSteps[index] = steps;
                this.highBase[index] = hb;
                this.highTop[index] = ht;
                this.known[index] = true;
                this.estimated[index] = !covers.isKnown(cx, cz);
            }
        }
        if (changed) {
            this.version++;
            this.empty = this.noneLeft();
        }
        return changed;
    }

    /**
     * Which turn a cell is worked out on: scattered, so the cells changing at any one time are spread over the sky
     */
    static int turnOf(int cx, int cz, int turns) {
        return turns <= 1 ? 0 : Integer.remainderUnsigned(turnHash(cx, cz), turns);
    }

    private static int turnHash(int cx, int cz) {
        long h = TURN_SEED ^ (cx * 0x9E3779B97F4A7C15L) ^ (cz * 0xC2B2AE3D27D4EB4FL);
        h ^= h >>> 31;
        h *= 0xBF58476D1CE4E5B9L;
        h ^= h >>> 29;
        return (int) (h >>> 32);
    }

    private void resize(int radius) {
        this.radius = radius;
        this.size = 2 * radius + 1;
        int cells = this.size * this.size;
        this.lowBase = new int[cells];
        this.lowTop = new int[cells];
        this.lowSteps = new int[cells];
        this.highBase = new int[cells];
        this.highTop = new int[cells];
        this.known = new boolean[cells];
        this.estimated = new boolean[cells];
        this.dirty = new boolean[cells];
        this.generation++;
        this.turnHash = new int[cells];
        this.prevLowBase = new int[cells];
        this.prevLowTop = new int[cells];
        this.prevHighBase = new int[cells];
        this.prevHighTop = new int[cells];
        this.changeTick = new int[cells];
        this.spare = new int[cells];
        this.spareKnown = new boolean[cells];
    }

    /**
     * Moves the grid's corner, keeping each column with its cell: those that fall off the grid are dropped, and those
     * new to it are left to be worked out
     */
    private void moveTo(int newOriginX, int newOriginZ) {
        int dx = newOriginX - this.originX;
        int dz = newOriginZ - this.originZ;
        this.originX = newOriginX;
        this.originZ = newOriginZ;
        this.lowBase = this.shifted(this.lowBase, dx, dz);
        this.lowTop = this.shifted(this.lowTop, dx, dz);
        this.lowSteps = this.shifted(this.lowSteps, dx, dz);
        this.highBase = this.shifted(this.highBase, dx, dz);
        this.highTop = this.shifted(this.highTop, dx, dz);
        this.turnHash = this.shifted(this.turnHash, dx, dz);
        this.prevLowBase = this.shifted(this.prevLowBase, dx, dz);
        this.prevLowTop = this.shifted(this.prevLowTop, dx, dz);
        this.prevHighBase = this.shifted(this.prevHighBase, dx, dz);
        this.prevHighTop = this.shifted(this.prevHighTop, dx, dz);
        this.changeTick = this.shifted(this.changeTick, dx, dz);

        this.known = this.shifted(this.known, dx, dz);
        this.estimated = this.shifted(this.estimated, dx, dz);
        this.dirty = this.shifted(this.dirty, dx, dz);
    }

    /**
     * Flags moved with their cells, into the spare array; false for those new to the grid. The one handed in becomes
     * the spare.
     */
    private boolean[] shifted(boolean[] values, int dx, int dz) {
        var moved = this.spareKnown;
        for (int j = 0; j < this.size; j++)
            for (int i = 0; i < this.size; i++) {
                int oi = i + dx;
                int oj = j + dz;
                moved[i + j * this.size] = oi >= 0 && oj >= 0 && oi < this.size && oj < this.size
                        && values[oi + oj * this.size];
            }
        this.spareKnown = values;
        return moved;
    }

    /**
     * A layer's values moved with their cells, into the spare array; the one handed in becomes the spare
     */
    private int[] shifted(int[] values, int dx, int dz) {
        var moved = this.spare;
        for (int j = 0; j < this.size; j++)
            for (int i = 0; i < this.size; i++) {
                int oi = i + dx;
                int oj = j + dz;
                boolean inside = oi >= 0 && oj >= 0 && oi < this.size && oj < this.size;
                moved[i + j * this.size] = inside ? values[oi + oj * this.size] : 0;
            }
        this.spare = values;
        return moved;
    }

    /**
     * Forgets the clouds
     */
    public void clear() {
        Arrays.fill(this.lowBase, 0);
        Arrays.fill(this.lowTop, 0);
        Arrays.fill(this.lowSteps, 0);
        Arrays.fill(this.highBase, 0);
        Arrays.fill(this.highTop, 0);
        Arrays.fill(this.prevLowBase, 0);
        Arrays.fill(this.prevLowTop, 0);
        Arrays.fill(this.prevHighBase, 0);
        Arrays.fill(this.prevHighTop, 0);
        Arrays.fill(this.known, false);
        Arrays.fill(this.estimated, false);
        Arrays.fill(this.dirty, false);
        this.empty = true;
        this.generation++;
        this.version++;
    }

    /**
     * Cells along each side of the grid
     */
    public int size() {
        return this.size;
    }

    public int radius() {
        return this.radius;
    }

    /**
     * The cloud space cell at the grid's corner, east-west, and {@link #originZ} north-south
     */
    public int originX() {
        return this.originX;
    }

    public int originZ() {
        return this.originZ;
    }

    /**
     * Goes up when every cell is to be built again: the grid made anew, or cleared
     */
    public int generation() {
        return this.generation;
    }

    /**
     * Whether a cell's quads are to be built again since {@link #clearDirty}: it changed, its fade ended, or it came into
     * the grid. False outside the grid.
     */
    public boolean isDirty(int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return false;
        return this.dirty[i + j * this.size];
    }

    /**
     * Marks every cell's quads as built
     */
    public void clearDirty() {
        Arrays.fill(this.dirty, false);
    }

    /**
     * Goes up each time the clouds change
     */
    public int version() {
        return this.version;
    }

    /**
     * Whether there are no clouds at all
     */
    public boolean isEmpty() {
        return this.empty;
    }

    /**
     * Whether no cell has cloud, nor any fading out
     */
    private boolean noneLeft() {
        for (int c = 0; c < this.lowTop.length; c++) {
            if (this.lowTop[c] > this.lowBase[c] || this.highTop[c] > this.highBase[c])
                return false;
            boolean fading = this.tick - (long) this.changeTick[c] < FADE_TICKS;
            if (fading && (this.prevLowTop[c] > this.prevLowBase[c] || this.prevHighTop[c] > this.prevHighBase[c]))
                return false;
        }
        return true;
    }

    /**
     * The tick of the last update
     */
    public long tick() {
        return this.tick;
    }

    /**
     * Whether a cell's last change is still fading in or out, as of the last update; not outside the grid
     */
    public boolean isFading(int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return false;
        return this.tick - (long) this.changeTick[i + j * this.size] < FADE_TICKS;
    }

    /**
     * The tick a cell last changed on; long ago if it hasn't since it came into the grid
     */
    public long changeTick(int i, int j) {
        return this.changeTick[i + j * this.size];
    }

    /**
     * The base of a cell's column in a layer before its last change; 0 outside the grid
     */
    public int previousBase(Layer layer, int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return 0;
        return (layer == Layer.LOW ? this.prevLowBase : this.prevHighBase)[i + j * this.size];
    }

    /**
     * The top of a cell's column in a layer before its last change; 0 outside the grid
     */
    public int previousTop(Layer layer, int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return 0;
        return (layer == Layer.LOW ? this.prevLowTop : this.prevHighTop)[i + j * this.size];
    }

    /**
     * The base of the settled part of a cell's column in a layer: what is in it both before and after a change still
     * fading, or else all of it. None settled where {@link #settledTop} isn't above it.
     */
    public int settledBase(Layer layer, int i, int j) {
        int base = this.base(layer, i, j);
        return this.isFading(i, j) ? Math.max(base, this.previousBase(layer, i, j)) : base;
    }

    /**
     * The top of the settled part of a cell's column in a layer (see {@link #settledBase})
     */
    public int settledTop(Layer layer, int i, int j) {
        int top = this.top(layer, i, j);
        return this.isFading(i, j) ? Math.min(top, this.previousTop(layer, i, j)) : top;
    }

    /**
     * Whether there is cloud in a cell of a layer; none outside the grid
     */
    public boolean has(Layer layer, int i, int j) {
        return this.top(layer, i, j) > this.base(layer, i, j);
    }

    /**
     * The base of a cell's column in a layer, in blocks from the cloud height; 0 outside the grid
     */
    public int base(Layer layer, int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return 0;
        return (layer == Layer.LOW ? this.lowBase : this.highBase)[i + j * this.size];
    }

    /**
     * The top of a cell's column in a layer, in blocks from the cloud height; 0 outside the grid
     */
    public int top(Layer layer, int i, int j) {
        if (i < 0 || j < 0 || i >= this.size || j >= this.size)
            return 0;
        return (layer == Layer.LOW ? this.lowTop : this.highTop)[i + j * this.size];
    }

    /**
     * How many cells of a layer have cloud
     */
    public int count(Layer layer) {
        int n = 0;
        for (int j = 0; j < this.size; j++)
            for (int i = 0; i < this.size; i++)
                if (this.has(layer, i, j))
                    n++;
        return n;
    }

    /**
     * The lowest base of the low clouds, in blocks from the cloud height; 0 with none
     */
    public int lowestBase() {
        int lowest = Integer.MAX_VALUE;
        for (int c = 0; c < this.lowBase.length; c++)
            if (this.lowTop[c] > this.lowBase[c])
                lowest = Math.min(lowest, this.lowBase[c]);
        return lowest == Integer.MAX_VALUE ? 0 : lowest;
    }
}
