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
 * Looking the columns up takes thousands of reads of the world (heights, biomes, temperatures, light), so the whole
 * area is looked up again only when the camera moves into another column, or when {@link #invalidate} says the last
 * look is out of date. Otherwise a few rows are looked at again each tick, in turn, so every column is within
 * {@link #REFRESH_TICKS} ticks, to see blocks placed or broken, light changing and the camera's height (which what
 * falls is taken at), without a tick that reads the whole area. Moving up or down alone doesn't look again: jumping and
 * climbing stairs would, a block at a time, for little.
 * <p>
 * The drops keep to a cylinder around the camera, so only the disc of columns they can reach is looked up, not the
 * square around it: about 15% fewer lookups (a little short of the disc's own share of the square, as the camera can be
 * anywhere in its column), and the shares of rain and dust are of the area the drops fall in.
 */
final class PrecipitationColumns {

    static final int REFRESH_TICKS = 10;

    private final int radius;
    private final int size;
    // Per row of the grid, how far the disc reaches either side of the camera's column, in columns
    private final int[] rowReach;
    // How many columns the disc has
    private final int columns;
    private final int[] ground;
    private final int[] light;
    private final int[] dustColor;
    private final float[] snowShare;
    private final PrecipitationKind[] kind;
    private int originX;
    private int originZ;
    private int regionalDustColor;
    // The share of the columns rain or snow falls in, and the share dust blows in, 0 to 1
    private float fallingShare;
    private float dustShare;
    // The camera's column when the columns were last looked up
    private int lastCameraX, lastCameraZ;
    // The row the next tick looks at again first
    private int refreshRow;
    private boolean stale = true;

    PrecipitationColumns(int radius) {
        this.radius = radius;
        this.size = radius * 2 + 1;
        this.ground = new int[this.size * this.size];
        this.light = new int[this.size * this.size];
        this.dustColor = new int[this.size * this.size];
        this.snowShare = new float[this.size * this.size];
        this.kind = new PrecipitationKind[this.size * this.size];
        Arrays.fill(this.kind, PrecipitationKind.NONE);
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
     * Looks the whole area up again if it is out of date: the camera is in another column, or the columns were
     * {@link #invalidate invalidated}. Otherwise looks the next few rows up again, so each is every
     * {@link #REFRESH_TICKS} ticks. Called once a tick.
     *
     * @return true if the whole area was looked up
     */
    boolean refresh(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        boolean moved = cameraX != this.lastCameraX || cameraZ != this.lastCameraZ;
        if (this.stale || moved) {
            this.update(source, cameraX, cameraY, cameraZ);
            return true;
        }
        int rows = rowsPerTick(this.size);
        for (int r = 0; r < rows; r++)
            this.lookUpRow(source, (this.refreshRow + r) % this.size, cameraY);
        this.refreshRow = (this.refreshRow + rows) % this.size;
        this.summarize(source, cameraX, cameraY, cameraZ);
        return false;
    }

    /**
     * How many rows of a grid {@code size} across are looked at again each tick, for each to be every
     * {@link #REFRESH_TICKS} ticks
     */
    static int rowsPerTick(int size) {
        return Math.max(1, Mth.ceil(size / (float) REFRESH_TICKS));
    }

    /**
     * Marks the last look as out of date, so the next {@link #refresh} looks again: for when ticks went by without one
     */
    void invalidate() {
        this.stale = true;
    }

    /**
     * Looks up the columns around the camera. What falls, and its light, are taken where the drops are seen: at the
     * camera's height, or the ground's if that is higher (so the temperature at altitude decides rain or snow).
     */
    void update(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        this.lastCameraX = cameraX;
        this.lastCameraZ = cameraZ;
        this.stale = false;
        this.refreshRow = 0;
        this.originX = cameraX - this.radius;
        this.originZ = cameraZ - this.radius;
        for (int dz = 0; dz < this.size; dz++)
            this.lookUpRow(source, dz, cameraY);
        this.summarize(source, cameraX, cameraY, cameraZ);
    }

    /**
     * Looks up the disc's columns in one row of the grid
     */
    private void lookUpRow(IColumnSource source, int dz, int cameraY) {
        int reach = this.rowReach[dz];
        int z = this.originZ + dz;
        for (int dx = this.radius - reach; dx <= this.radius + reach; dx++) {
            int x = this.originX + dx;
            int i = dz * this.size + dx;
            int g = source.groundHeight(x, z);
            int y = Math.max(g, cameraY);
            this.ground[i] = g;
            this.kind[i] = source.precipitation(x, y, z);
            this.light[i] = source.light(x, y, z);
            this.snowShare[i] = this.kind[i] == PrecipitationKind.RAIN ? source.snowShare(x, y, z) : 0F;
            // From the top block of the column, if dust is blown from it
            if (this.kind[i] == PrecipitationKind.DUST)
                this.dustColor[i] = source.dustColor(x, g - 1, z);
        }
    }

    /**
     * Works out what is true of the area as a whole from its columns as last looked up: the shares of rain and dust,
     * and the region's dust color
     */
    private void summarize(IColumnSource source, int cameraX, int cameraY, int cameraZ) {
        boolean anyDust = false;
        int falling = 0, dusty = 0;
        int sources = 0;
        long red = 0, green = 0, blue = 0;
        for (int dz = 0; dz < this.size; dz++) {
            int reach = this.rowReach[dz];
            for (int dx = this.radius - reach; dx <= this.radius + reach; dx++) {
                int i = dz * this.size + dx;
                var what = this.kind[i];
                if (what == PrecipitationKind.RAIN || what == PrecipitationKind.SNOW) {
                    falling++;
                } else if (what == PrecipitationKind.DUST) {
                    anyDust = true;
                    dusty++;
                    int color = this.dustColor[i];
                    if (color != IColumnSource.NOT_A_DUST_SOURCE) {
                        red += (color >> 16) & 0xFF;
                        green += (color >> 8) & 0xFF;
                        blue += color & 0xFF;
                        sources++;
                    }
                }
            }
        }

        this.fallingShare = falling / (float) this.columns;
        this.dustShare = dusty / (float) this.columns;

        if (sources > 0)
            this.regionalDustColor = (int) (red / sources) << 16 | (int) (green / sources) << 8 | (int) (blue / sources);
        else if (anyDust)
            this.regionalDustColor = source.fallbackDustColor(cameraX, cameraY, cameraZ);
    }

    /**
     * The column a position is in, or -1 if it is outside the area looked up
     */
    int index(double x, double z) {
        return this.indexOf(Mth.floor(x), Mth.floor(z));
    }

    /**
     * As {@link #index(double, double)}, for a drop's own position: floored as a float, without widening it first
     */
    int index(float x, float z) {
        return this.indexOf(Mth.floor(x), Mth.floor(z));
    }

    private int indexOf(int blockX, int blockZ) {
        int dx = blockX - this.originX;
        int dz = blockZ - this.originZ;
        if (dz < 0 || dz >= this.size || Math.abs(dx - this.radius) > this.rowReach[dz])
            return -1;
        return dz * this.size + dx;
    }

    /**
     * The share of the columns rain or snow falls in, 0 to 1
     */
    float fallingShare() {
        return this.fallingShare;
    }

    /**
     * The share of the columns dust blows in, 0 to 1
     */
    float dustShare() {
        return this.dustShare;
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
