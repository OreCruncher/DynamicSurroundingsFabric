package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.util.Mth;

import java.util.Arrays;

/**
 * The columns around the camera, looked up once a tick: where precipitation stops, what falls, and how it is lit.
 * Drops read their column here, so the cost of the lookups follows the area covered rather than the number of drops.
 * <p>
 * Dust is the region's: the color of the loose ground (sand, red sand) across the area, averaged, whatever the dust
 * is blowing over. Dust over grass or water is still the desert's sand.
 * <p>
 * Looking a column up takes several reads of the world (its height, biome, temperature, light), so each is kept in a
 * slot of its own, found from where it is in the world, and not looked up again while it stays in the area: moving
 * into another column looks up only the columns that came into the area. A few rows are looked at again each
 * tick, every row once in {@link #REFRESH_TICKS} ticks, to see blocks placed or broken, light changing and the
 * camera's height (which what falls is taken at), spreading the work rather than doing it all at once; and all of
 * them when {@link #invalidate} says what was found is out of date. The shares of the area that rain, snow and dust
 * fall in, and the dust's color across it, are kept as running totals: a column's part is taken out when it is
 * looked up again or leaves, and the new one's added.
 * <p>
 * The drops keep to a cylinder around the camera, so the area is the disc of columns they can reach, not the square
 * around it: about 15% fewer lookups (a little short of the disc's own share of the square, as the camera can be
 * anywhere in its column), and the shares of rain and dust are of the area the drops fall in. The slots still cover
 * the square; those outside the disc are left empty.
 */
final class PrecipitationColumns {

    static final int REFRESH_TICKS = 10;

    private final int radius;
    private final int size;
    // Per row of the area, how far the disc reaches either side of the camera's column, in columns
    private final int[] rowReach;
    // How many columns the disc has
    private final int columns;
    // Each slot's column in the world, or Integer.MIN_VALUE for none
    private final int[] slotX;
    private final int[] slotZ;
    private final int[] ground;
    private final int[] light;
    private final int[] dustColor;
    private final float[] snowShare;
    private final PrecipitationKind[] kind;
    private int originX;
    private int originZ;
    private int regionalDustColor;
    // Running totals over the area: columns rain or snow falls in, columns dust blows in, and the columns dust is
    // blown from with their colors added up
    private int falling;
    private int dusty;
    private int sources;
    private long red, green, blue;
    // The first row of the area looked at again next tick
    private int refreshRow;
    private boolean stale = true;

    PrecipitationColumns(int radius) {
        this.radius = radius;
        this.size = radius * 2 + 1;
        int cells = this.size * this.size;
        this.slotX = new int[cells];
        this.slotZ = new int[cells];
        this.ground = new int[cells];
        this.light = new int[cells];
        this.dustColor = new int[cells];
        this.snowShare = new float[cells];
        this.kind = new PrecipitationKind[cells];
        this.rowReach = new int[this.size];
        int count = 0;
        for (int dz = 0; dz < this.size; dz++) {
            int reach = 0;
            while (reach < radius && inDisc(reach + 1, dz - radius, radius))
                reach++;
            this.rowReach[dz] = reach;
            count += reach * 2 + 1;
        }
        this.columns = count;
        this.forget();
    }

    /**
     * Whether a drop within {@code radius} of the camera can be in the column {@code dx}, {@code dz} columns from the
     * camera's: the camera is anywhere in its own column, so the gap between the two columns is what counts
     */
    static boolean inDisc(int dx, int dz, int radius) {
        int gapX = Math.max(0, Math.abs(dx) - 1);
        int gapZ = Math.max(0, Math.abs(dz) - 1);
        return gapX * gapX + gapZ * gapZ < radius * radius;
    }

    /**
     * How many columns are looked up
     */
    int columnCount() {
        return this.columns;
    }

    /**
     * Whether the column {@code i}, {@code j} from the area's corner is in the disc
     */
    private boolean inArea(int i, int j) {
        return Math.abs(i - this.radius) <= this.rowReach[j];
    }

    /**
     * Brings the columns up to date for where the camera is: all of them if they were {@link #invalidate invalidated}
     * (or never looked up); otherwise those that came into the area, and the rows due a fresh look. Columns that left
     * the disc are taken out. Called once a tick.
     *
     * @return true if any were looked up
     */
    boolean refresh(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        if (this.stale) {
            this.update(source, cameraX, cameraY, cameraZ);
            return true;
        }
        this.originX = cameraX - this.radius;
        this.originZ = cameraZ - this.radius;

        int rowsPerTick = rowsPerTick(this.size);
        int refreshFrom = this.refreshRow;
        this.refreshRow = (this.refreshRow + rowsPerTick) % this.size;
        boolean looked = false;
        boolean dropped = false;
        for (int j = 0; j < this.size; j++) {
            int z = this.originZ + j;
            boolean refresh = Math.floorMod(j - refreshFrom, this.size) < rowsPerTick;
            for (int i = 0; i < this.size; i++) {
                int x = this.originX + i;
                int slot = this.slot(x, z);
                if (!this.inArea(i, j)) {
                    if (this.slotX[slot] != Integer.MIN_VALUE) {
                        this.empty(slot);
                        dropped = true;
                    }
                } else if (refresh || this.slotX[slot] != x || this.slotZ[slot] != z) {
                    this.look(source, slot, x, z, cameraY);
                    looked = true;
                }
            }
        }
        if (looked || dropped)
            this.updateRegionalDustColor(source, cameraX, cameraY, cameraZ);
        return looked;
    }

    /**
     * How many of the area's rows are looked at again each tick: enough for all of them in {@link #REFRESH_TICKS}
     */
    static int rowsPerTick(int size) {
        return Math.max(1, Mth.ceil(size / (float) REFRESH_TICKS));
    }

    /**
     * Marks what was found as out of date, so the next {@link #refresh} looks at every column again: for when ticks
     * went by without one
     */
    void invalidate() {
        this.stale = true;
    }

    /**
     * Looks up every column around the camera afresh. What falls, and its light, are taken where the drops are seen:
     * at the camera's height, or the ground's if that is higher (so the temperature at altitude decides rain or snow).
     */
    void update(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        this.forget();
        this.stale = false;
        this.refreshRow = 0;
        this.originX = cameraX - this.radius;
        this.originZ = cameraZ - this.radius;
        for (int j = 0; j < this.size; j++) {
            int reach = this.rowReach[j];
            for (int i = this.radius - reach; i <= this.radius + reach; i++) {
                int x = this.originX + i, z = this.originZ + j;
                this.look(source, this.slot(x, z), x, z, cameraY);
            }
        }
        this.updateRegionalDustColor(source, cameraX, cameraY, cameraZ);
    }

    /**
     * Looks a column up into its slot, in place of what the slot held, keeping the running totals
     */
    private void look(IColumnSource source, int slot, int x, int z, int cameraY) {
        if (this.slotX[slot] != Integer.MIN_VALUE)
            this.count(slot, -1);
        int g = source.groundHeight(x, z);
        int y = Math.max(g, cameraY);
        var what = source.precipitation(x, y, z);
        this.slotX[slot] = x;
        this.slotZ[slot] = z;
        this.ground[slot] = g;
        this.kind[slot] = what;
        this.light[slot] = source.light(x, y, z);
        this.snowShare[slot] = what == PrecipitationKind.RAIN ? source.snowShare(x, y, z) : 0F;
        // From the top block of the column, if dust is blown from it
        this.dustColor[slot] = what == PrecipitationKind.DUST ? source.dustColor(x, g - 1, z) : IColumnSource.NOT_A_DUST_SOURCE;
        this.count(slot, 1);
    }

    /**
     * Takes a slot's column out of the running totals and leaves the slot empty: its column has left the disc
     */
    private void empty(int slot) {
        this.count(slot, -1);
        this.slotX[slot] = Integer.MIN_VALUE;
        this.slotZ[slot] = Integer.MIN_VALUE;
        this.kind[slot] = PrecipitationKind.NONE;
        this.dustColor[slot] = IColumnSource.NOT_A_DUST_SOURCE;
    }

    /**
     * Adds a slot's column to the running totals, or takes it out
     *
     * @param sign 1 to add, -1 to take out
     */
    private void count(int slot, int sign) {
        var what = this.kind[slot];
        if (what == PrecipitationKind.RAIN || what == PrecipitationKind.SNOW)
            this.falling += sign;
        if (what == PrecipitationKind.DUST) {
            this.dusty += sign;
            int color = this.dustColor[slot];
            if (color != IColumnSource.NOT_A_DUST_SOURCE) {
                this.sources += sign;
                this.red += sign * ((color >> 16) & 0xFF);
                this.green += sign * ((color >> 8) & 0xFF);
                this.blue += sign * (color & 0xFF);
            }
        }
    }

    /**
     * The color of the dust across the area, from the running totals: the average of the ground it is blown from, or
     * what the biome is known for where none of it is in sight. Kept as it was when there is no dust.
     */
    private void updateRegionalDustColor(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        if (this.sources > 0)
            this.regionalDustColor = (int) (this.red / this.sources) << 16 | (int) (this.green / this.sources) << 8 | (int) (this.blue / this.sources);
        else if (this.dusty > 0)
            this.regionalDustColor = source.fallbackDustColor(cameraX, cameraY, cameraZ);
    }

    private int slot(int x, int z) {
        return Math.floorMod(z, this.size) * this.size + Math.floorMod(x, this.size);
    }

    /**
     * Empties every slot, and the totals
     */
    private void forget() {
        Arrays.fill(this.slotX, Integer.MIN_VALUE);
        Arrays.fill(this.slotZ, Integer.MIN_VALUE);
        Arrays.fill(this.kind, PrecipitationKind.NONE);
        Arrays.fill(this.dustColor, IColumnSource.NOT_A_DUST_SOURCE);
        this.falling = 0;
        this.dusty = 0;
        this.sources = 0;
        this.red = this.green = this.blue = 0;
    }

    /**
     * The column a position is in, or -1 if it is outside the area looked up
     */
    int index(double x, double z) {
        int bx = Mth.floor(x), bz = Mth.floor(z);
        int dx = bx - this.originX;
        int dz = bz - this.originZ;
        if (dz < 0 || dz >= this.size || Math.abs(dx - this.radius) > this.rowReach[dz])
            return -1;
        return this.slot(bx, bz);
    }

    /**
     * The share of the columns rain or snow falls in, 0 to 1
     */
    float fallingShare() {
        return this.falling / (float) this.columns;
    }

    /**
     * The share of the columns dust blows in, 0 to 1
     */
    float dustShare() {
        return this.dusty / (float) this.columns;
    }

    /**
     * The share of what falls in a rain column that is snowflakes, 0 to 1
     */
    float snowShare(int index) {
        return this.snowShare[index];
    }

    int ground(int index) {
        return this.ground[index];
    }

    int light(int index) {
        return this.light[index];
    }

    /**
     * The color of dust blown from a column's own ground, or {@link IColumnSource#NOT_A_DUST_SOURCE}
     */
    int dustColor(int index) {
        return this.dustColor[index];
    }

    /**
     * The color of the dust across the area, 0xRRGGBB, as of the last look; only meaningful when some of the area
     * has dust ({@link #dustShare()} above 0)
     */
    int regionalDustColor() {
        return this.regionalDustColor;
    }

    /**
     * The color a puff of dust takes where it rises: the region's, or, over a block dust is blown from, half the
     * region's and half the block's own, so patches of red sand read a little redder close to without the dust looking
     * spotty
     */
    int dustColorAt(int index) {
        int own = this.dustColor[index];
        int regional = this.regionalDustColor;
        if (own == IColumnSource.NOT_A_DUST_SOURCE)
            return regional;
        return mix(own, regional);
    }

    private static int mix(int a, int b) {
        int red = (((a >> 16) & 0xFF) + ((b >> 16) & 0xFF)) / 2;
        int green = (((a >> 8) & 0xFF) + ((b >> 8) & 0xFF)) / 2;
        int blue = ((a & 0xFF) + (b & 0xFF)) / 2;
        return red << 16 | green << 8 | blue;
    }

    PrecipitationKind kind(int index) {
        return this.kind[index];
    }
}
