package org.orecruncher.dsurround.gui.overlay.lightlevel;

import net.minecraft.SharedConstants;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.LightLevelColors;
import org.orecruncher.dsurround.config.LightLevelMode;

import static org.junit.jupiter.api.Assertions.*;
import static org.orecruncher.dsurround.gui.overlay.lightlevel.LightLevels.Kind.*;

/**
 * Tests for the light level overlay apart from drawing: how entries are packed, what light means for spawning,
 * which way labels turn, the atlas layout, and which sections are kept and looked at again.
 */
public class LightLevelTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // The Overworld's: block light over 0 stops spawning; otherwise up to light 7
    private static final int OVERWORLD_LIMIT = 0;
    // The Nether's: block light alone never stops it
    private static final int NETHER_LIMIT = 15;
    private static final int SPAWN_MAX = 7;

    // ---- Entries ----------------------------------------------------------------------------------------------

    @Test
    void entryRoundTrips() {
        int entry = LightLevels.pack(3, 15, 9, 12, 5, true, 0.5F);
        assertEquals(3, LightLevels.localX(entry));
        assertEquals(15, LightLevels.localY(entry));
        assertEquals(9, LightLevels.localZ(entry));
        assertEquals(12, LightLevels.blockLight(entry));
        assertEquals(5, LightLevels.skyLight(entry));
        assertTrue(LightLevels.spawnable(entry));
        assertEquals(0.5F, LightLevels.height(entry));
    }

    @Test
    void heightBelowTheBlockRoundTrips() {
        // A label on a bottom slab, half a block down
        assertEquals(-0.5F, LightLevels.height(LightLevels.pack(0, 0, 0, 0, 0, false, -0.5F)));
        assertEquals(0.125F, LightLevels.height(LightLevels.pack(0, 0, 0, 0, 0, false, 0.125F)), "one snow layer");
        assertEquals(1F, LightLevels.height(LightLevels.pack(0, 0, 0, 0, 0, false, 1F)), "full snow");
    }

    @Test
    void newLightKeepsTheRest() {
        int entry = LightLevels.pack(7, 2, 11, 3, 14, true, -0.25F);
        int relit = LightLevels.withLight(entry, 9, 0);
        assertEquals(9, LightLevels.blockLight(relit));
        assertEquals(0, LightLevels.skyLight(relit));
        assertEquals(7, LightLevels.localX(relit));
        assertEquals(2, LightLevels.localY(relit));
        assertEquals(11, LightLevels.localZ(relit));
        assertTrue(LightLevels.spawnable(relit));
        assertEquals(-0.25F, LightLevels.height(relit));
    }

    @Test
    void tableIndexMatchesTheTables() {
        int[] colors = new int[LightLevels.TABLE_SIZE];
        byte[] values = new byte[LightLevels.TABLE_SIZE];
        LightLevels.fillTables(colors, values, LightLevelMode.BLOCK, LightLevelColors.BRIGHT, false, 0, OVERWORLD_LIMIT, SPAWN_MAX);

        for (int block = 0; block < 16; block++)
            for (int sky = 0; sky < 16; sky++)
                for (boolean spawnable : new boolean[]{false, true}) {
                    int index = LightLevels.tableIndex(LightLevels.pack(5, 5, 5, block, sky, spawnable, 0F));
                    assertEquals(block, values[index], "the number shown is the block light");
                    var kind = LightLevels.classify(spawnable, block, sky, 0, OVERWORLD_LIMIT, SPAWN_MAX);
                    assertEquals(LightLevels.color(kind, LightLevelColors.BRIGHT, false), colors[index]);
                }
    }

    // ---- Spawning ---------------------------------------------------------------------------------------------

    @Test
    void anyBlockLightIsSafeInTheOverworld() {
        assertEquals(SAFE, LightLevels.classify(true, 1, 0, 11, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void darkCaveIsHazard() {
        assertEquals(HAZARD, LightLevels.classify(true, 0, 0, 0, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void openGroundByDayIsCaution() {
        // Unlit grass at noon: safe while the sun is up, not at night
        assertEquals(CAUTION, LightLevels.classify(true, 0, 15, 0, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void openGroundAtNightIsHazard() {
        assertEquals(HAZARD, LightLevels.classify(true, 0, 15, LightLevels.DARKEST_SKY, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void thunderDarkensTheSkyEnough() {
        assertEquals(HAZARD, LightLevels.classify(true, 0, 15, LightLevels.THUNDER_SKY, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void netherGoesByBlockLightAgainstTheLightTest() {
        assertEquals(HAZARD, LightLevels.classify(true, 7, 0, 0, NETHER_LIMIT, SPAWN_MAX));
        assertEquals(SAFE, LightLevels.classify(true, 8, 0, 0, NETHER_LIMIT, SPAWN_MAX));
    }

    @Test
    void notSpawnableWinsOverLight() {
        assertEquals(NO_SPAWN, LightLevels.classify(false, 0, 0, 0, OVERWORLD_LIMIT, SPAWN_MAX));
        assertEquals(NO_SPAWN, LightLevels.classify(false, 15, 15, 0, OVERWORLD_LIMIT, SPAWN_MAX));
    }

    @Test
    void numbersFollowTheMode() {
        assertEquals(3, LightLevels.displayValue(LightLevelMode.BLOCK, 3, 15, 11));
        assertEquals(4, LightLevels.displayValue(LightLevelMode.SKY, 3, 15, 11));
        assertEquals(4, LightLevels.displayValue(LightLevelMode.BLOCK_SKY, 3, 15, 11));
        assertEquals(15, LightLevels.displayValue(LightLevelMode.BLOCK_SKY, 3, 15, 0));
        assertEquals(0, LightLevels.displayValue(LightLevelMode.SKY, 0, 5, 11), "never below 0");
    }

    @Test
    void hideSafeHidesSafeAndNoSpawn() {
        assertEquals(0, LightLevels.color(SAFE, LightLevelColors.BRIGHT, true));
        assertEquals(0, LightLevels.color(NO_SPAWN, LightLevelColors.BRIGHT, true));
        assertEquals(0xFF000000 | LightLevelColors.BRIGHT.caution(), LightLevels.color(CAUTION, LightLevelColors.BRIGHT, true));
        assertEquals(0xFF000000 | LightLevelColors.BRIGHT.hazard(), LightLevels.color(HAZARD, LightLevelColors.BRIGHT, true));
    }

    @Test
    void shownColorsAreOpaque() {
        for (var kind : LightLevels.Kind.values())
            for (var palette : LightLevelColors.values())
                assertEquals(0xFF, LightLevels.color(kind, palette, false) >>> 24, kind + " " + palette);
    }

    // ---- Legend -----------------------------------------------------------------------------------------------

    @Test
    void legendListsEveryKindMostDangerousFirst() {
        assertArrayEquals(new LightLevels.Kind[]{HAZARD, CAUTION, SAFE, NO_SPAWN}, LightLevels.legendKinds(false));
    }

    @Test
    void legendLeavesOutWhatHideSafeHides() {
        var kinds = LightLevels.legendKinds(true);
        assertArrayEquals(new LightLevels.Kind[]{HAZARD, CAUTION}, kinds);
        for (var kind : kinds)
            assertNotEquals(0, LightLevels.color(kind, LightLevelColors.BRIGHT, true), kind + " is listed, so it must be shown");
    }

    @Test
    void legendTextIsTranslated() throws Exception {
        try (var stream = LightLevelTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json")) {
            assertNotNull(stream);
            var lang = com.google.gson.JsonParser.parseReader(new java.io.InputStreamReader(stream, java.nio.charset.StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(lang.has("dsurround.text.lightLevel.legend.title"));
            for (var mode : LightLevelMode.values())
                assertTrue(lang.has("dsurround.text.lightLevel.legend.mode." + mode.name()), mode.name());
            for (var kind : LightLevels.Kind.values())
                assertTrue(lang.has("dsurround.text.lightLevel.legend." + kind.name()), kind.name());
        }
    }

    // ---- Turning ----------------------------------------------------------------------------------------------

    // Label corners: 0 north west, 1 north east, 2 south east, 3 south west. Cell corners: 0 top left, 1 top right,
    // 2 bottom right, 3 bottom left. Whichever way the viewer faces, the cell's top left is at their far left.

    private static int topLeftCornerFacing(Direction facing) {
        int turns = LightLevels.quarterTurns(facing.get2DDataValue());
        for (int corner = 0; corner < 4; corner++)
            if (LightLevels.cellCorner(corner, turns) == 0)
                return corner;
        throw new AssertionError("no corner gets the top left");
    }

    @Test
    void numbersReadUprightFacingEachWay() {
        assertEquals(0, topLeftCornerFacing(Direction.NORTH), "facing north: far left is north west");
        assertEquals(1, topLeftCornerFacing(Direction.EAST), "facing east: far left is north east");
        assertEquals(2, topLeftCornerFacing(Direction.SOUTH), "facing south: far left is south east");
        assertEquals(3, topLeftCornerFacing(Direction.WEST), "facing west: far left is south west");
    }

    @Test
    void cornersStayInOrderWhenTurned() {
        // Clockwise around the label is clockwise around the cell, so the number isn't mirrored
        for (int turns = 0; turns < 4; turns++)
            for (int corner = 0; corner < 4; corner++)
                assertEquals((LightLevels.cellCorner(corner, turns) + 1) & 3, LightLevels.cellCorner((corner + 1) & 3, turns));
    }

    // ---- Atlas ------------------------------------------------------------------------------------------------

    @Test
    void cellsAreAFourByFourGrid() {
        assertEquals(0F, LightLevelAtlas.cellU(0));
        assertEquals(0F, LightLevelAtlas.cellV(0));
        assertEquals(0.75F, LightLevelAtlas.cellU(7));
        assertEquals(0.25F, LightLevelAtlas.cellV(7));
        assertEquals(0.75F, LightLevelAtlas.cellU(15));
        assertEquals(0.75F, LightLevelAtlas.cellV(15));
        assertEquals(0.5F, LightLevelAtlas.cellU(10));
        assertEquals(0.5F, LightLevelAtlas.cellV(10));
    }

    @Test
    void vanillaFontScalesToFitACell() {
        // "15" is 12 wide in the vanilla font, its lines 9 high
        int scale = LightLevelAtlas.fitScale(12, 9);
        assertEquals(4, scale);
        assertTrue((12 + 2) * scale <= LightLevelAtlas.CELL_SIZE);
    }

    @Test
    void wideFontStillFits() {
        int scale = LightLevelAtlas.fitScale(40, 12);
        assertEquals(1, scale);
        assertEquals(1, LightLevelAtlas.fitScale(200, 9), "never below 1, even if it can't fit");
    }

    // ---- Sections ---------------------------------------------------------------------------------------------

    private static LightLevelField fieldAround(int x, int y, int z, int range) {
        var field = new LightLevelField();
        field.setRange(-4, 19, x, y, z, range);
        field.forEachSection(s -> s.dirty = false);
        return field;
    }

    @Test
    void rangeCoversTheSectionsAround() {
        var field = new LightLevelField();
        // From x and z 8 - 16 to 8 + 16 (sections -1 to 1), y 64 - 16 to 64 + 3 (sections 3 to 4)
        field.setRange(-4, 19, 8, 64, 8, 16);
        assertEquals(3 * 3 * 2, field.sectionCount());
        assertTrue(field.isDirty(0, 4, 0), "new sections are to be scanned");
        assertTrue(field.isDirty(-1, 3, 1));
        assertFalse(field.isDirty(2, 4, 0), "out of range");
    }

    @Test
    void rangeStopsAtTheWorldsEdge() {
        var field = new LightLevelField();
        field.setRange(-4, 19, 8, -60, 8, 16);
        // y -76 to -57 would be sections -5 to -4; the world starts at -4
        assertEquals(3 * 3, field.sectionCount());
    }

    @Test
    void movingKeepsWhatIsStillInRange() {
        var field = fieldAround(8, 64, 8, 16);
        // A section east: x 9 - 16 to 9 + 16 is sections -1 to 1 still
        field.setRange(-4, 19, 9, 64, 8, 16);
        assertFalse(field.isDirty(0, 4, 0), "nothing new within the same sections");

        // Into the next section: x 24 - 16 to 24 + 16 is sections 0 to 2
        field.setRange(-4, 19, 24, 64, 8, 16);
        assertEquals(3 * 3 * 2, field.sectionCount());
        assertFalse(field.isDirty(1, 4, 0), "kept");
        assertTrue(field.isDirty(2, 4, 0), "come into range");
        assertFalse(field.isDirty(-1, 4, 0), "dropped");
    }

    @Test
    void blockChangeMarksItsSection() {
        var field = fieldAround(8, 64, 8, 16);
        field.blockChanged(5, 56, 5);
        assertTrue(field.isDirty(0, 3, 0));
        assertFalse(field.isDirty(0, 4, 0));
        assertFalse(field.isDirty(0, 2, 0));
    }

    @Test
    void blockChangeAtTheTopMarksTheSectionAbove() {
        // What stands on the block is in the section above
        var field = fieldAround(8, 64, 8, 16);
        field.blockChanged(5, 63, 5);
        assertTrue(field.isDirty(0, 3, 0));
        assertTrue(field.isDirty(0, 4, 0));
    }

    @Test
    void blockChangeAtTheBottomMarksTheSectionBelow() {
        // A monster needs the block over it clear
        var field = fieldAround(8, 64, 8, 16);
        field.blockChanged(5, 64, 5);
        assertTrue(field.isDirty(0, 4, 0));
        assertTrue(field.isDirty(0, 3, 0));
    }

    @Test
    void blockChangeAtNegativeCoordinatesMarksTheRightSection() {
        var field = fieldAround(8, 64, 8, 16);
        field.blockChanged(-1, 56, -16);
        assertTrue(field.isDirty(-1, 3, -1));
        assertFalse(field.isDirty(0, 3, 0));
    }

    @Test
    void chunkLoadMarksItsColumn() {
        var field = fieldAround(8, 64, 8, 16);
        field.chunkChanged(1, -1);
        assertTrue(field.isDirty(1, 3, -1));
        assertTrue(field.isDirty(1, 4, -1));
        assertFalse(field.isDirty(0, 4, 0));
    }

    @Test
    void lightChangeOnlyRereadsLight() {
        var field = fieldAround(8, 64, 8, 16);
        field.lightChanged(0, 4, 0);
        assertFalse(field.isDirty(0, 4, 0), "no full scan");
        boolean[] lightDirty = {false};
        field.forEachSection(s -> {
            if (s.x == 0 && s.y == 4 && s.z == 0)
                lightDirty[0] = s.lightDirty;
        });
        assertTrue(lightDirty[0]);
    }

    @Test
    void clearForgetsTheRange() {
        var field = fieldAround(8, 64, 8, 16);
        field.clear();
        assertEquals(0, field.sectionCount());
        field.setRange(-4, 19, 8, 64, 8, 16);
        assertTrue(field.isDirty(0, 4, 0), "the same range again is all new");
    }
}
