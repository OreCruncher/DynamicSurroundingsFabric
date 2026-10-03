package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the biome scanner's survey: sampling every other block gives the same total area and nearly the same
 * shares as looking at every block.
 */
public class BiomeSurveyTests {

    private static final String PLAINS = "plains";
    private static final String FOREST = "forest";
    private static final String RIVER = "river";

    private static final BlockPos CENTER = new BlockPos(100, 64, -40);

    private static Reference2IntOpenHashMap<String> survey(int stride, Function<BlockPos, String> lookup) {
        var weights = new Reference2IntOpenHashMap<String>();
        int total = BiomeScanner.survey(CENTER, stride, new BlockPos.MutableBlockPos(), weights, lookup);
        assertEquals(BiomeScanner.MAX_SURVEY_VOLUME, total, "the total area doesn't depend on the stride");
        int sum = 0;
        for (var value : weights.values())
            sum += value;
        assertEquals(total, sum);
        return weights;
    }

    private static float share(Reference2IntOpenHashMap<String> weights, String biome) {
        return weights.getInt(biome) / (float) BiomeScanner.MAX_SURVEY_VOLUME;
    }

    @Test
    void oneBiomeEverywhere() {
        var weights = survey(BiomeScanner.SURVEY_STRIDE, pos -> PLAINS);

        assertEquals(1, weights.size());
        assertEquals(BiomeScanner.MAX_SURVEY_VOLUME, weights.getInt(PLAINS));
    }

    @Test
    void sampledLookupsAreAnEighthOfTheVolume() {
        int[] lookups = {0};

        survey(BiomeScanner.SURVEY_STRIDE, pos -> {
            lookups[0]++;
            return PLAINS;
        });

        assertEquals(BiomeScanner.MAX_SURVEY_VOLUME / 8, lookups[0]);
    }

    @Test
    void edgesOnTheBiomeGridGiveTheSameShares() {
        // Biomes are stored on a 4 block grid; a boundary on that grid lands between samples
        Function<BlockPos, String> lookup = pos -> Math.floorMod(pos.getX(), 8) < 4 ? PLAINS : FOREST;

        var full = survey(1, lookup);
        var sampled = survey(BiomeScanner.SURVEY_STRIDE, lookup);

        assertEquals(full, sampled);
    }

    @Test
    void edgesOffTheGridGiveNearlyTheSameShares() {
        // Vanilla blends the grid at the edges, so a boundary can fall on any block; a sample then stands for one
        // block of the wrong biome. With 9 samples across, a share is off by at most a ninth of a column per edge.
        Function<BlockPos, String> lookup = pos -> {
            if (pos.getX() < CENTER.getX() - 3)
                return RIVER;
            return pos.getZ() + pos.getY() / 5 < CENTER.getZ() + 2 ? PLAINS : FOREST;
        };

        var full = survey(1, lookup);
        var sampled = survey(BiomeScanner.SURVEY_STRIDE, lookup);

        for (var biome : new String[]{PLAINS, FOREST, RIVER})
            assertEquals(share(full, biome), share(sampled, biome), 0.12F, biome);
    }

    @Test
    void surveyIsCenteredOnThePosition() {
        // 18 blocks across starting 8 before the center; 16 high starting 3 below
        int[] minX = {Integer.MAX_VALUE}, maxX = {Integer.MIN_VALUE}, minY = {Integer.MAX_VALUE}, maxY = {Integer.MIN_VALUE};

        survey(1, pos -> {
            minX[0] = Math.min(minX[0], pos.getX());
            maxX[0] = Math.max(maxX[0], pos.getX());
            minY[0] = Math.min(minY[0], pos.getY());
            maxY[0] = Math.max(maxY[0], pos.getY());
            return PLAINS;
        });

        assertEquals(CENTER.getX() - 8, minX[0]);
        assertEquals(CENTER.getX() + 9, maxX[0]);
        assertEquals(CENTER.getY() - 3, minY[0]);
        assertEquals(CENTER.getY() + 12, maxY[0]);
    }
}
