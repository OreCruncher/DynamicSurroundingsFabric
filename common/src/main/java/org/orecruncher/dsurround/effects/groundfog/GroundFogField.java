package org.orecruncher.dsurround.effects.groundfog;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * The ground fog around the camera: a cell for each column of blocks, out to the radius, each with the height of the
 * ground there and how thick the mist is. The mist lies in the lowest blocks over the ground, shaped by the ground
 * smoothed over a few blocks so it banks up against a step and thins toward a ledge (see {@link GroundFogVolume}).
 * <p>
 * A column standing well above all around it (a post, a tree trunk) is taken as the ground around it, so the mist
 * passes through it instead of sitting on top. A cell's thickness is its biome's ground fog, blurred with its
 * neighbours' so the mist thins at a biome's edge rather than stopping in a line, and faded out toward the edge of the
 * area.
 * <p>
 * Columns are kept in a cache that wraps around as the camera moves, so only those coming into the area are looked
 * at, and those {@linkplain #columnChanged changed} (a block placed or broken, a chunk arriving). Light spreads from a
 * change and is worked out a little after it, so a column's light alone is read again when the light engine
 * {@linkplain #lightChanged changes it}, and every so often, a few rows a tick, for any it doesn't tell of. The cells are only worked out again when the camera moves into another block or a column has changed, and
 * {@link #version} counts the times.
 */
public final class GroundFogField {

    /**
     * How far from the camera the mist reaches, in blocks
     */
    public static final int DEFAULT_RADIUS = 40;
    /**
     * The mist thins to nothing over this outer share of the radius. The shader does the thinning, from where the camera
     * is exactly, so the cells needn't be worked out again as the camera moves within a block
     */
    public static final float EDGE_FADE = 0.3F;
    /**
     * Ticks in which every column's light is read again, for any change the light engine doesn't tell of (see
     * {@link #lightChanged}): rarely needed, so slowly
     */
    static final int REFRESH_TICKS = 200;
    /**
     * A column more than this many blocks above the middle of the eight around it stands in the mist rather than
     * holding it up
     */
    static final int OBSTACLE_HEIGHT = 2;
    /**
     * Cells within this many blocks of each other's height are at the same level, and their thickness is blended: a
     * step of a block included, as the mist is deeper than that
     */
    static final float SAME_LEVEL = 1F;
    /**
     * How far the smoothed ground the mist is shaped by may be above the ground itself (how far the mist banks up
     * against a step), and below it (how much it thins toward a ledge), in blocks
     */
    static final float SMOOTH_ABOVE = 1F;
    static final float SMOOTH_BELOW = 1.5F;

    private final int radius;
    private final int size;

    // The column cache, wrapping around: where each slot's column is, and what was found there
    private final int[] slotX;
    private final int[] slotZ;
    private final int[] slotHeight;
    private final float[] slotDensity;
    private final int[] slotLight;
    // Slots whose column has changed since it was looked at, and whose light has
    private final boolean[] slotStale;
    private final boolean[] slotRelight;

    // The area as of the last update, cell (i, j) at column (originX + i, originZ + j)
    private final float[] height;
    // The ground smoothed over a few blocks, for the shape of the mist: it banks up against a step and thins toward a
    // ledge, rather than changing at a column's edge
    private final float[] smoothHeight;
    private final float[] density;
    private final int[] light;
    private final float[] scratch;
    private final float[] scratch2;
    private final int[] around = new int[8];

    private int originX = Integer.MIN_VALUE;
    private int originZ = Integer.MIN_VALUE;
    private int refreshRow;
    private float forced = Float.NaN;
    private float peak;
    private int foggyCells;
    private int version;

    public GroundFogField() {
        this(DEFAULT_RADIUS);
    }

    public GroundFogField(int radius) {
        this.radius = radius;
        this.size = 2 * radius + 1;
        int cells = this.size * this.size;
        this.slotX = new int[cells];
        this.slotZ = new int[cells];
        this.slotHeight = new int[cells];
        this.slotDensity = new float[cells];
        this.slotLight = new int[cells];
        this.slotStale = new boolean[cells];
        this.slotRelight = new boolean[cells];
        this.height = new float[cells];
        this.smoothHeight = new float[cells];
        this.density = new float[cells];
        this.light = new int[cells];
        this.scratch = new float[cells];
        this.scratch2 = new float[cells];
        this.forget();
    }

    /**
     * Brings the area up to date for where the camera is: looks at the columns coming into it and a few rows due a
     * fresh look, then works out the heights and thicknesses the mist is drawn with
     *
     * @param forced a thickness to give the mist everywhere, 0 to 1, in place of the biomes'; NaN for the biomes'
     */
    public void update(IGroundFogSource source, double cameraX, double cameraZ, float forced) {
        boolean sameForced = Float.isNaN(forced) ? Float.isNaN(this.forced) : forced == this.forced;
        if (!sameForced) {
            this.forced = forced;
            this.forget();
        }
        int originX = Mth.floor(cameraX) - this.radius;
        int originZ = Mth.floor(cameraZ) - this.radius;
        boolean changed = originX != this.originX || originZ != this.originZ;
        this.originX = originX;
        this.originZ = originZ;

        // Every row gets a fresh look once in REFRESH_TICKS
        int rowsPerTick = Math.max(1, Mth.ceil(this.size / (float) REFRESH_TICKS));
        int refreshFrom = this.refreshRow;
        this.refreshRow = (this.refreshRow + rowsPerTick) % this.size;

        for (int j = 0; j < this.size; j++) {
            int z = this.originZ + j;
            boolean refresh = Math.floorMod(j - refreshFrom, this.size) < rowsPerTick;
            for (int i = 0; i < this.size; i++) {
                int x = this.originX + i;
                int slot = this.slot(x, z);
                if (this.slotStale[slot] || this.slotX[slot] != x || this.slotZ[slot] != z)
                    changed |= this.look(source, slot, x, z);
                else if (refresh || this.slotRelight[slot])
                    changed |= this.relight(source, slot, x, z);
            }
        }
        if (changed)
            this.prepare();
    }

    /**
     * Looks at a column, keeping what is found in its slot
     *
     * @return whether anything is different from what the slot held
     */
    private boolean look(IGroundFogSource source, int slot, int x, int z) {
        int ground = source.groundHeight(x, z);
        float density = Float.isNaN(this.forced) ? Mth.clamp(source.density(x, ground, z), 0F, 1F) : this.forced;
        int light = source.light(x, ground, z);
        boolean changed = this.slotX[slot] != x || this.slotZ[slot] != z || this.slotHeight[slot] != ground
                || this.slotDensity[slot] != density || this.slotLight[slot] != light;
        this.slotX[slot] = x;
        this.slotZ[slot] = z;
        this.slotHeight[slot] = ground;
        this.slotDensity[slot] = density;
        this.slotLight[slot] = light;
        this.slotStale[slot] = false;
        this.slotRelight[slot] = false;
        return changed;
    }

    /**
     * Reads a column's light again, at the ground already found
     *
     * @return whether it is different
     */
    private boolean relight(IGroundFogSource source, int slot, int x, int z) {
        int light = source.light(x, this.slotHeight[slot], z);
        boolean changed = this.slotLight[slot] != light;
        this.slotLight[slot] = light;
        this.slotRelight[slot] = false;
        return changed;
    }

    /**
     * Has the light read again on the next update in the columns of a section whose light has changed, those in the
     * area whose ground is in the section: the light is read just above the ground
     */
    public void lightChanged(int sectionX, int sectionY, int sectionZ) {
        // Light changes come by the hundred as chunks load, mostly well away from the area: only its columns are
        // looked at
        if (this.originX == Integer.MIN_VALUE)
            return;
        int fromX = Math.max(sectionX << 4, this.originX), toX = Math.min((sectionX << 4) + 16, this.originX + this.size);
        int fromZ = Math.max(sectionZ << 4, this.originZ), toZ = Math.min((sectionZ << 4) + 16, this.originZ + this.size);
        for (int z = fromZ; z < toZ; z++) {
            for (int x = fromX; x < toX; x++) {
                int slot = this.slot(x, z);
                if (this.slotX[slot] == x && this.slotZ[slot] == z && this.slotHeight[slot] >> 4 == sectionY)
                    this.slotRelight[slot] = true;
            }
        }
    }

    /**
     * Has a column looked at again on the next update, if it is in the area: a block in it was placed or broken
     */
    public void columnChanged(int x, int z) {
        int slot = this.slot(x, z);
        if (this.slotX[slot] == x && this.slotZ[slot] == z)
            this.slotStale[slot] = true;
    }

    /**
     * Has the columns of a chunk looked at again on the next update, those in the area: it has arrived from the
     * server, and any looked at before it did found no ground
     */
    public void chunkChanged(int chunkX, int chunkZ) {
        if (this.originX == Integer.MIN_VALUE)
            return;
        int fromX = Math.max(chunkX << 4, this.originX), toX = Math.min((chunkX << 4) + 16, this.originX + this.size);
        int fromZ = Math.max(chunkZ << 4, this.originZ), toZ = Math.min((chunkZ << 4) + 16, this.originZ + this.size);
        for (int z = fromZ; z < toZ; z++)
            for (int x = fromX; x < toX; x++)
                this.columnChanged(x, z);
    }

    private int slot(int x, int z) {
        return Math.floorMod(z, this.size) * this.size + Math.floorMod(x, this.size);
    }

    /**
     * Works out, for each cell of the area, the height the mist lies at and how thick it is
     */
    private void prepare() {
        this.version++;
        int size = this.size;
        // Heights and thicknesses as found, in the area's order
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int slot = this.slot(this.originX + i, this.originZ + j);
                int k = j * size + i;
                this.height[k] = this.slotHeight[slot];
                this.scratch[k] = this.slotDensity[slot];
                this.light[k] = this.slotLight[slot];
            }
        }

        // A column standing well above all around it is an obstacle: the mist is at the height around it
        for (int j = 1; j + 1 < size; j++) {
            for (int i = 1; i + 1 < size; i++) {
                int k = j * size + i;
                int n = 0;
                for (int dj = -1; dj <= 1; dj++)
                    for (int di = -1; di <= 1; di++)
                        if (di != 0 || dj != 0)
                            this.around[n++] = this.slotHeight[this.slot(this.originX + i + di, this.originZ + j + dj)];
                Arrays.sort(this.around);
                float middle = (this.around[3] + this.around[4]) * 0.5F;
                if (this.height[k] > middle + OBSTACLE_HEIGHT)
                    this.height[k] = middle;
            }
        }

        // The ground smoothed: twice over the nine around each column, which weighs the nearest the most
        this.boxBlur(this.height, this.smoothHeight);
        System.arraycopy(this.smoothHeight, 0, this.scratch2, 0, this.scratch2.length);
        this.boxBlur(this.scratch2, this.smoothHeight);
        // Kept near the ground itself: the mist banks up a step a little, not up the face of a cliff, and thins toward
        // a ledge without leaving it
        for (int k = 0; k < this.smoothHeight.length; k++)
            this.smoothHeight[k] = Mth.clamp(this.smoothHeight[k], this.height[k] - SMOOTH_BELOW, this.height[k] + SMOOTH_ABOVE);

        // Blurred with the neighbours at the same level. None outside the area's circle: the shader thins the mist to
        // nothing a block inside it, so the cells beyond are never seen, and would only raise the highest ground under
        // any mist
        float peak = 0F;
        int foggy = 0;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                int k = j * size + i;
                int fromMiddleX = i - this.radius, fromMiddleZ = j - this.radius;
                if (fromMiddleX * fromMiddleX + fromMiddleZ * fromMiddleZ > this.radius * this.radius) {
                    this.density[k] = 0F;
                    continue;
                }
                float h = this.height[k];
                float sum = 0F;
                int count = 0;
                for (int dj = -1; dj <= 1; dj++) {
                    int nj = j + dj;
                    if (nj < 0 || nj >= size)
                        continue;
                    for (int di = -1; di <= 1; di++) {
                        int ni = i + di;
                        if (ni < 0 || ni >= size)
                            continue;
                        int nk = nj * size + ni;
                        if (Math.abs(this.height[nk] - h) <= SAME_LEVEL) {
                            sum += this.scratch[nk];
                            count++;
                        }
                    }
                }
                float d = sum / count;
                this.density[k] = d;
                if (d > 0F) {
                    foggy++;
                    peak = Math.max(peak, d);
                }
            }
        }
        this.peak = peak;
        this.foggyCells = foggy;
    }

    /**
     * The mean of the nine around each cell, as many of them as are in the area
     */
    private void boxBlur(float[] from, float[] to) {
        int size = this.size;
        for (int j = 0; j < size; j++) {
            for (int i = 0; i < size; i++) {
                float sum = 0F;
                int count = 0;
                for (int dj = -1; dj <= 1; dj++) {
                    int nj = j + dj;
                    if (nj < 0 || nj >= size)
                        continue;
                    for (int di = -1; di <= 1; di++) {
                        int ni = i + di;
                        if (ni < 0 || ni >= size)
                            continue;
                        sum += from[nj * size + ni];
                        count++;
                    }
                }
                to[j * size + i] = sum / count;
            }
        }
    }

    private void forget() {
        // No area until the next update: nothing is remembered for a change to be noted against
        this.originX = Integer.MIN_VALUE;
        this.originZ = Integer.MIN_VALUE;
        Arrays.fill(this.slotX, Integer.MIN_VALUE);
        Arrays.fill(this.slotZ, Integer.MIN_VALUE);
        Arrays.fill(this.slotStale, false);
        Arrays.fill(this.slotRelight, false);
    }

    /**
     * Forgets everything; the next update looks at every column afresh
     */
    public void clear() {
        this.forget();
        Arrays.fill(this.density, 0F);
        this.peak = 0F;
        this.foggyCells = 0;
        this.version++;
    }

    /**
     * Goes up each time the cells are worked out again or cleared: while it stays the same, so do they
     */
    public int version() {
        return this.version;
    }

    /**
     * Whether there is no mist anywhere in the area
     */
    public boolean isEmpty() {
        return this.foggyCells == 0;
    }

    /**
     * The thickest the mist is anywhere in the area, 0 to 1
     */
    public float peak() {
        return this.peak;
    }

    /**
     * Cells with any mist, as of the last update
     */
    public int foggyCells() {
        return this.foggyCells;
    }

    public int radius() {
        return this.radius;
    }

    /**
     * Cells along a side
     */
    public int size() {
        return this.size;
    }

    public int cellX(int i) {
        return this.originX + i;
    }

    public int cellZ(int j) {
        return this.originZ + j;
    }

    /**
     * The height of the ground the mist lies on at a cell
     */
    public float height(int i, int j) {
        return this.height[j * this.size + i];
    }

    /**
     * The ground smoothed over a few blocks around a cell, for the shape of the mist
     */
    public float smoothHeight(int i, int j) {
        return this.smoothHeight[j * this.size + i];
    }

    public float density(int i, int j) {
        return this.density[j * this.size + i];
    }

    public int light(int i, int j) {
        return this.light[j * this.size + i];
    }
}
