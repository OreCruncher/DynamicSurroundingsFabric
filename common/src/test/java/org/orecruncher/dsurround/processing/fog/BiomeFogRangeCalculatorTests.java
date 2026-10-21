package org.orecruncher.dsurround.processing.fog;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how the biome fog averages the biomes around the player.
 */
public class BiomeFogRangeCalculatorTests {

    // A different value in each biome cell
    private static float cellValue(int qx, int qy, int qz) {
        int h = qx * 73856093 ^ qy * 19349663 ^ qz * 83492791;
        return (h & 0xFFFF) / 65535F;
    }

    // What sampling every block did: each block's cell's value, averaged
    private static float everyBlock(BlockPos pos, int range) {
        float sum = 0F;
        int count = 0;
        for (int y = pos.getY() - range; y <= pos.getY() + range; y++)
            for (int z = pos.getZ() - range; z <= pos.getZ() + range; z++)
                for (int x = pos.getX() - range; x <= pos.getX() + range; x++) {
                    sum += cellValue(x >> 2, y >> 2, z >> 2);
                    count++;
                }
        return sum / count;
    }

    @Test
    void theSameAsAveragingEveryBlock() {
        // Regression: every block's biome was read each time the player moved a block, thousands, a few percent of
        // the render thread. Each cell is now read once, counted by its blocks
        int[][] places = {{0, 64, 0}, {1, 70, -1}, {-7, -30, 13}, {2, 63, 3}, {-1000, 200, 3333}};
        for (var p : places) {
            var pos = new BlockPos(p[0], p[1], p[2]);
            for (int range : new int[]{0, 1, 3, 6}) {
                int[] reads = {0};
                float average = BiomeFogRangeCalculator.averageOverCells(pos, range, (qx, qy, qz) -> {
                    reads[0]++;
                    return cellValue(qx, qy, qz);
                });
                assertEquals(everyBlock(pos, range), average, 1e-5F, pos + " within " + range);
                assertTrue(reads[0] <= 64, reads[0] + " reads");
            }
        }
    }

    @Test
    void oneBiomeEverywhereIsItsValue() {
        assertEquals(0.4F, BiomeFogRangeCalculator.averageOverCells(new BlockPos(5, 64, -9), 6, (qx, qy, qz) -> 0.4F), 1e-6F);
    }
}
