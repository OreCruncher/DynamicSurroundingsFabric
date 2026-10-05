package org.orecruncher.dsurround.effects.systems;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for measuring a waterfall's strength: the height of the falling column above where it lands.
 */
public class WaterfallColumnTests {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    // Water block states: level 0 is a source, 1 to 7 flowing along the ground, 8 and up falling
    private static final BlockState SOURCE = water(0);
    private static final BlockState FLOWING = water(1);
    private static final BlockState FALLING = water(8);
    private static final BlockState STONE = Blocks.STONE.defaultBlockState();
    private static final BlockState AIR = Blocks.AIR.defaultBlockState();
    private static final BlockPos ONE_UP = new BlockPos(0, 1, 0);

    private static BlockState water(int level) {
        return Blocks.WATER.defaultBlockState().setValue(LiquidBlock.LEVEL, level);
    }

    /**
     * A column of blocks at x = z = 0, from y = 0 up; air everywhere else.
     */
    static final class Column implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        Column(BlockState... fromTheBottom) {
            for (int y = 0; y < fromTheBottom.length; y++)
                this.blocks.put(new BlockPos(0, y, 0), fromTheBottom[y]);
        }

        /**
         * Puts {@code state} on all four sides of the column at height {@code y}.
         */
        Column around(int y, BlockState state) {
            for (var side : List.of(new BlockPos(1, y, 0), new BlockPos(-1, y, 0), new BlockPos(0, y, 1), new BlockPos(0, y, -1)))
                this.blocks.put(side, state);
            return this;
        }

        Column with(BlockPos pos, BlockState state) {
            this.blocks.put(pos, state);
            return this;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    @Test
    void smallestWaterfallHasStrengthOne() {
        // Regression: the landing block and the lip were counted too, so this was 3 and nothing was ever below that.
        // Water pours off a ledge (flowing, at the lip), falls one block, and lands on the ground (falling).
        var world = new Column(FALLING, FALLING, FLOWING);

        assertEquals(1, WaterfallColumn.strength(world, BlockPos.ZERO));
    }

    @Test
    void strengthIsTheDropIntoAPool() {
        // Landing in a pool (a source), three blocks of falling water above, pouring from a source at the top
        var world = new Column(SOURCE, FALLING, FALLING, FALLING, SOURCE);

        assertEquals(3, WaterfallColumn.strength(world, BlockPos.ZERO));
    }

    @Test
    void stillWaterAboveTheLipIsNotCounted() {
        // Pouring from the bottom of a deep pool: the pool's water isn't falling
        var world = new Column(SOURCE, FALLING, FALLING, SOURCE, SOURCE, SOURCE);

        assertEquals(2, WaterfallColumn.strength(world, BlockPos.ZERO));
    }

    @Test
    void strengthIsCapped() {
        var column = new BlockState[16];
        Arrays.fill(column, FALLING);
        column[0] = SOURCE;

        assertEquals(10, WaterfallColumn.strength(new Column(column), BlockPos.ZERO));
    }

    @Test
    void nothingFallingIsZero() {
        assertEquals(0, WaterfallColumn.strength(new Column(SOURCE, SOURCE), BlockPos.ZERO));
        assertEquals(0, WaterfallColumn.strength(new Column(SOURCE), BlockPos.ZERO));
    }

    @Test
    void onlyFallingFluidIsFalling() {
        assertTrue(WaterfallColumn.isFalling(FALLING.getFluidState()));
        assertTrue(WaterfallColumn.isFalling(Blocks.LAVA.defaultBlockState().setValue(LiquidBlock.LEVEL, 8).getFluidState()));
        assertFalse(WaterfallColumn.isFalling(SOURCE.getFluidState()));
        assertFalse(WaterfallColumn.isFalling(FLOWING.getFluidState()));
        assertFalse(WaterfallColumn.isFalling(Blocks.AIR.defaultBlockState().getFluidState()));
    }

    // ---- Steps -------------------------------------------------------------------------------------------------

    @Test
    void waterDroppingOneBlockOntoGroundIsAStep() {
        // Flowing water spills over the edge (y = 2), falls one block (y = 1) and lands on the ground
        var world = new Column(STONE, FALLING, FLOWING);

        assertTrue(WaterfallColumn.isStep(world, ONE_UP));
    }

    @Test
    void waterDroppingOneBlockIntoAPoolIsAStep() {
        assertTrue(WaterfallColumn.isStep(new Column(SOURCE, FALLING, FLOWING), ONE_UP));
        assertTrue(WaterfallColumn.isStep(new Column(SOURCE, FALLING, SOURCE), ONE_UP), "spilling from a source");
    }

    @Test
    void aDropOfTwoOrMoreIsAWaterfallNotAStep() {
        // Where it lands has falling water above (a waterfall's landing), and the falling block above that has
        // nothing to land on
        var world = new Column(STONE, FALLING, FALLING, FLOWING);

        assertFalse(WaterfallColumn.isStep(world, ONE_UP));
        assertFalse(WaterfallColumn.isStep(world, new BlockPos(0, 2, 0)));
    }

    @Test
    void notAStepWithoutWaterAboveOrGroundBelow() {
        assertFalse(WaterfallColumn.isStep(new Column(STONE, FALLING, AIR), ONE_UP), "nothing above");
        assertFalse(WaterfallColumn.isStep(new Column(AIR, FALLING, FLOWING), ONE_UP), "nothing to land on");
        assertFalse(WaterfallColumn.isStep(new Column(FLOWING, FALLING, FLOWING), ONE_UP), "lands on more moving water");
        assertFalse(WaterfallColumn.isStep(new Column(STONE, FLOWING, FLOWING), ONE_UP), "not falling");
        assertFalse(WaterfallColumn.isStep(new Column(STONE, SOURCE, SOURCE), ONE_UP), "still water");
    }

    @Test
    void landsOnGroundOrAPool() {
        assertTrue(WaterfallColumn.landsOn(new Column(STONE, FALLING), ONE_UP));
        assertTrue(WaterfallColumn.landsOn(new Column(SOURCE, FALLING), ONE_UP));
        assertFalse(WaterfallColumn.landsOn(new Column(FLOWING, FALLING), ONE_UP));
        assertFalse(WaterfallColumn.landsOn(new Column(AIR, FALLING), ONE_UP));
    }

    // ---- Where waterfalls land

    @Test
    void waterCanSpreadWhereASideIsOpenOrPartlyFilled() {
        var hemmedIn = new Column(STONE, SOURCE).around(1, STONE);
        assertFalse(WaterfallColumn.canSpread(hemmedIn, ONE_UP), "walls on every side");

        var fullPool = new Column(STONE, SOURCE).around(1, SOURCE);
        assertFalse(WaterfallColumn.canSpread(fullPool, ONE_UP), "a full pool all round");

        assertTrue(WaterfallColumn.canSpread(new Column(STONE, SOURCE).around(1, STONE).with(new BlockPos(1, 1, 0), AIR), ONE_UP), "one side open");
        assertTrue(WaterfallColumn.canSpread(new Column(STONE, SOURCE).around(1, STONE).with(new BlockPos(0, 1, -1), FLOWING), ONE_UP), "one side partly filled");
    }

    @Test
    void aWaterfallLandsWhereFallingWaterHitsSomethingAndCanSpread() {
        // Falling water above, open sides (air by default), and ground below
        assertTrue(WaterfallColumn.isLandingSite(new Column(STONE, FALLING, FALLING), ONE_UP));
        // ... or a pool below
        assertTrue(WaterfallColumn.isLandingSite(new Column(SOURCE, FALLING, FALLING), ONE_UP));
    }

    @Test
    void noWaterfallWithoutFallingWaterAboveSomethingToLandOnOrRoomToSpread() {
        assertFalse(WaterfallColumn.isLandingSite(new Column(STONE, FALLING, FLOWING), ONE_UP), "not falling above");
        assertFalse(WaterfallColumn.isLandingSite(new Column(STONE, SOURCE, SOURCE), ONE_UP), "still water above");
        assertFalse(WaterfallColumn.isLandingSite(new Column(AIR, FALLING, FALLING), ONE_UP), "nothing to land on");
        assertFalse(WaterfallColumn.isLandingSite(new Column(STONE, FALLING, FALLING).around(1, STONE), ONE_UP), "walled in");
    }
}
