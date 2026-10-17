package org.orecruncher.dsurround.gui.overlay.lightlevel;

import org.orecruncher.dsurround.config.LightLevelColors;
import org.orecruncher.dsurround.config.LightLevelMode;

/**
 * The light level overlay's arithmetic, apart from the world: how a block's entry is packed, what its light means
 * for spawning, which number it shows in which color, and which way its label turns.
 */
final class LightLevels {

    /**
     * How a block's light bears on monsters spawning on it
     */
    enum Kind {
        /**
         * Lit well enough that monsters can't spawn
         */
        SAFE,
        /**
         * Safe now, but not when the sky is at its darkest
         */
        CAUTION,
        /**
         * Dark enough for monsters to spawn now
         */
        HAZARD,
        /**
         * Monsters can't spawn there at all
         */
        NO_SPAWN
    }

    /**
     * How far the sky's light drops at its darkest (Level.updateSkyBrightness: a clear midnight)
     */
    static final int DARKEST_SKY = 11;
    /**
     * How far the sky's light drops in a thunderstorm, as Monster.isDarkEnoughToSpawn counts it
     */
    static final int THUNDER_SKY = 10;

    // An entry: the block within its section (x, z, y: 4 bits each), its block and sky light, whether a monster can
    // spawn there, and the height of the label above the block's bottom, in 16ths with a bias (-2 to almost 2 blocks)
    private static final int LIGHT_BLOCK_SHIFT = 12;
    private static final int LIGHT_SKY_SHIFT = 16;
    private static final int SPAWN_BIT = 1 << 20;
    private static final int HEIGHT_SHIFT = 21;
    private static final int HEIGHT_BIAS = 32;
    private static final int HEIGHT_MASK = 0x3F;
    private static final int LIGHT_MASK = ~((0xF << LIGHT_BLOCK_SHIFT) | (0xF << LIGHT_SKY_SHIFT));

    /**
     * The number of distinct light entries: spawnable or not, block light, sky light
     */
    static final int TABLE_SIZE = 2 * 16 * 16;

    private LightLevels() {
    }

    static int pack(int localX, int localY, int localZ, int blockLight, int skyLight, boolean spawnable, float height) {
        int sixteenths = Math.clamp(Math.round(height * 16F) + HEIGHT_BIAS, 0, HEIGHT_MASK);
        return (localX & 0xF) | (localZ & 0xF) << 4 | (localY & 0xF) << 8
                | (blockLight & 0xF) << LIGHT_BLOCK_SHIFT | (skyLight & 0xF) << LIGHT_SKY_SHIFT
                | (spawnable ? SPAWN_BIT : 0) | sixteenths << HEIGHT_SHIFT;
    }

    /**
     * The entry with its light replaced, everything else kept
     */
    static int withLight(int entry, int blockLight, int skyLight) {
        return entry & LIGHT_MASK | (blockLight & 0xF) << LIGHT_BLOCK_SHIFT | (skyLight & 0xF) << LIGHT_SKY_SHIFT;
    }

    static int localX(int entry) {
        return entry & 0xF;
    }

    static int localZ(int entry) {
        return (entry >> 4) & 0xF;
    }

    static int localY(int entry) {
        return (entry >> 8) & 0xF;
    }

    static int blockLight(int entry) {
        return (entry >> LIGHT_BLOCK_SHIFT) & 0xF;
    }

    static int skyLight(int entry) {
        return (entry >> LIGHT_SKY_SHIFT) & 0xF;
    }

    static boolean spawnable(int entry) {
        return (entry & SPAWN_BIT) != 0;
    }

    static float height(int entry) {
        return (((entry >> HEIGHT_SHIFT) & HEIGHT_MASK) - HEIGHT_BIAS) / 16F;
    }

    /**
     * The entry's place in the color and value tables: its spawn flag and its light, without its position
     */
    static int tableIndex(int entry) {
        return (entry >> LIGHT_BLOCK_SHIFT) & 0x1FF;
    }

    /**
     * What a block's light means for spawning, following Monster.isDarkEnoughToSpawn: block light over the
     * dimension's limit keeps monsters away whatever the sky; otherwise they spawn where the brighter of block light
     * and the sky's (less how far it has dropped) is within the dimension's light test. Caution marks blocks safe
     * only for as long as the sky stays bright.
     *
     * @param skyDarken      how far the sky's light has dropped now (0 at noon)
     * @param blockLimit     DimensionType.monsterSpawnBlockLightLimit (15 means block light alone never prevents it)
     * @param spawnLightMax  the highest light DimensionType.monsterSpawnLightTest allows
     */
    static Kind classify(boolean spawnable, int blockLight, int skyLight, int skyDarken, int blockLimit, int spawnLightMax) {
        if (!spawnable)
            return Kind.NO_SPAWN;
        if (blockLimit < 15 && blockLight > blockLimit)
            return Kind.SAFE;
        if (Math.max(blockLight, skyLight - skyDarken) <= spawnLightMax)
            return Kind.HAZARD;
        if (Math.max(blockLight, skyLight - Math.max(skyDarken, DARKEST_SKY)) <= spawnLightMax)
            return Kind.CAUTION;
        return Kind.SAFE;
    }

    /**
     * The number a block shows
     */
    static int displayValue(LightLevelMode mode, int blockLight, int skyLight, int skyDarken) {
        int sky = Math.max(0, skyLight - skyDarken);
        return switch (mode) {
            case BLOCK -> blockLight;
            case SKY -> sky;
            case BLOCK_SKY -> Math.max(blockLight, sky);
        };
    }

    /**
     * The color for a kind of block, opaque, or 0 when it is hidden
     */
    static int color(Kind kind, LightLevelColors colors, boolean hideSafe) {
        int rgb = switch (kind) {
            case SAFE -> hideSafe ? -1 : colors.safe();
            case CAUTION -> colors.caution();
            case HAZARD -> colors.hazard();
            case NO_SPAWN -> hideSafe ? -1 : colors.noSpawn();
        };
        return rgb == -1 ? 0 : 0xFF000000 | rgb;
    }

    /**
     * What the legend lists, in order: the kinds whose numbers are shown, most dangerous first
     */
    static Kind[] legendKinds(boolean hideSafe) {
        return hideSafe ? new Kind[]{Kind.HAZARD, Kind.CAUTION} : new Kind[]{Kind.HAZARD, Kind.CAUTION, Kind.SAFE, Kind.NO_SPAWN};
    }

    /**
     * Fills the tables the renderer looks entries up in (by {@link #tableIndex}): the color, 0 for hidden, and the
     * number shown. 512 entries, so building them each tick costs less than looking light up per block would.
     */
    static void fillTables(int[] colors, byte[] values, LightLevelMode mode, LightLevelColors palette, boolean hideSafe,
                           int skyDarken, int blockLimit, int spawnLightMax) {
        for (int i = 0; i < TABLE_SIZE; i++) {
            boolean spawnable = (i & 0x100) != 0;
            int block = i & 0xF;
            int sky = (i >> 4) & 0xF;
            var kind = classify(spawnable, block, sky, skyDarken, blockLimit, spawnLightMax);
            colors[i] = color(kind, palette, hideSafe);
            values[i] = (byte) displayValue(mode, block, sky, skyDarken);
        }
    }

    /**
     * How many quarter turns clockwise (seen from above) a label is turned so that it reads upright for a viewer
     * facing a direction: 0 north, 1 east, 2 south, 3 west (Direction.get2DDataValue: 0 south, 1 west, 2 north,
     * 3 east)
     */
    static int quarterTurns(int facing2D) {
        return (facing2D + 2) & 3;
    }

    /**
     * Which corner of a number's cell (0 top left, 1 top right, 2 bottom right, 3 bottom left) a label corner gets
     * (0 north west, 1 north east, 2 south east, 3 south west), turned so the top of the number is away from the
     * viewer
     */
    static int cellCorner(int labelCorner, int quarterTurns) {
        return (labelCorner - quarterTurns) & 3;
    }
}
