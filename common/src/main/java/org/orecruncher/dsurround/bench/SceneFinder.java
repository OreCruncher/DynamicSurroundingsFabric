package org.orecruncher.dsurround.bench;

import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * Finds a place for a scene in a biome, on the server's thread. The nearest of the biome, as /locate biome finds it, is
 * often a sliver at its edge (a strip along a coast), so the biome is sampled on the ground around it, coarsely over a
 * wide area for where the most of it is, then finely there for the place with the most of it within
 * {@link #FINE_RADIUS}: on dry ground of it, looking toward the most of it. The ground's height comes from the
 * generated chunk, so the player stands on it: not inside a mountain, not falling.
 */
final class SceneFinder {

    // As /locate biome searches
    private static final int SEARCH_RADIUS = 6400;
    private static final int SEARCH_HORIZONTAL_STEP = 32;
    private static final int SEARCH_VERTICAL_STEP = 64;
    // The coarse pass: every COARSE_STEP blocks out to COARSE_SPAN from the find, counting the biome within COARSE_RADIUS
    private static final int COARSE_SPAN = 2048;
    private static final int COARSE_STEP = 64;
    private static final int COARSE_RADIUS = 128;
    // The fine pass, around the coarse pass's best
    private static final int FINE_SPAN = 256;
    private static final int FINE_STEP = 16;
    private static final int FINE_RADIUS = 64;
    // How far to look for dry ground of the biome to stand on, and in what steps
    private static final int DRY_RADIUS = 32;
    private static final int DRY_STEP = 4;
    // Looking a little down at the scene
    private static final float PITCH = 5F;

    private SceneFinder() {
    }

    /**
     * A place in the biome, from {@code origin}
     *
     * @throws IllegalArgumentException if this world has no such biome, or none within the search's reach
     */
    static SceneLocations.Location find(ServerLevel level, String biome, BlockPos origin) {
        var key = ResourceKey.create(Registries.BIOME, Identifier.parse(biome));
        if (level.registryAccess().lookupOrThrow(Registries.BIOME).get(key).isEmpty())
            throw new IllegalArgumentException("this world has no biome " + biome);
        var found = level.findClosestBiome3d(holder -> holder.is(key), origin, SEARCH_RADIUS, SEARCH_HORIZONTAL_STEP, SEARCH_VERTICAL_STEP);
        if (found == null)
            throw new IllegalArgumentException("no " + biome + " within " + SEARCH_RADIUS + " blocks");

        var coarse = best(level, key, found.getFirst().getX(), found.getFirst().getZ(), COARSE_SPAN, COARSE_STEP, COARSE_RADIUS);
        var fine = best(level, key, coarse.x(), coarse.z(), FINE_SPAN, FINE_STEP, FINE_RADIUS);

        // Generates the chunk if it must: the height is then the real ground's
        var dry = dryGround(level, key, fine.x(), fine.z());
        int x = dry[0], z = dry[1];
        int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        // Minecraft's yaw: 0 looks south (+z), 90 west (-x)
        float yaw = fine.lookX() == 0 && fine.lookZ() == 0 ? 0F : (float) Math.toDegrees(Math.atan2(-fine.lookX(), fine.lookZ()));
        return new SceneLocations.Location(biome, level.dimension().identifier().toString(), x + 0.5, y, z + 0.5,
                yaw, PITCH, fine.share(), false);
    }

    /**
     * Whether a remembered place is still in its biome
     */
    static boolean stillThere(ServerLevel level, SceneLocations.Location location) {
        var pos = BlockPos.containing(location.x(), location.y(), location.z());
        return level.getBiome(pos).unwrapKey().map(k -> k.identifier().toString().equals(location.biome())).orElse(false);
    }

    /**
     * The best place in an area: of the biome, with the most of it within {@code radius}, the nearest the middle of
     * those; how much of the ground there is the biome, and which way the most of it lies
     */
    private record Best(int x, int z, double share, double lookX, double lookZ) {
    }

    private static Best best(ServerLevel level, ResourceKey<Biome> key, int cx, int cz, int span, int step, int radius) {
        // The biome on the ground, sampled over the area; then the sum of each corner's rectangle, to count any square
        // of it at once
        int cells = span / step;
        int size = 2 * cells + 1;
        var generator = level.getChunkSource().getGenerator();
        var random = level.getChunkSource().randomState();
        var is = new boolean[size][size];
        var sums = new int[size + 1][size + 1];
        for (int i = 0; i < size; i++) {
            for (int j = 0; j < size; j++) {
                int px = cx + (i - cells) * step, pz = cz + (j - cells) * step;
                // The ground's height from the generator's noise: needs no chunk generated
                int py = generator.getBaseHeight(px, pz, Heightmap.Types.WORLD_SURFACE_WG, level, random);
                is[i][j] = level.getBiome(new BlockPos(px, py, pz)).is(key);
                sums[i + 1][j + 1] = sums[i][j + 1] + sums[i + 1][j] - sums[i][j] + (is[i][j] ? 1 : 0);
            }
        }

        int w = radius / step;
        int bestI = cells, bestJ = cells, bestCount = -1, bestDistance = Integer.MAX_VALUE;
        for (int i = w; i < size - w; i++) {
            for (int j = w; j < size - w; j++) {
                if (!is[i][j])
                    continue;
                int count = sums[i + w + 1][j + w + 1] - sums[i - w][j + w + 1] - sums[i + w + 1][j - w] + sums[i - w][j - w];
                int distance = (i - cells) * (i - cells) + (j - cells) * (j - cells);
                if (count > bestCount || count == bestCount && distance < bestDistance) {
                    bestI = i;
                    bestJ = j;
                    bestCount = count;
                    bestDistance = distance;
                }
            }
        }

        double lookX = 0D, lookZ = 0D;
        for (int di = -w; di <= w; di++) {
            for (int dj = -w; dj <= w; dj++) {
                int i = bestI + di, j = bestJ + dj;
                if ((di != 0 || dj != 0) && i >= 0 && j >= 0 && i < size && j < size && is[i][j]) {
                    var length = Mth.length(di, dj);
                    lookX += di / length;
                    lookZ += dj / length;
                }
            }
        }
        double share = Math.max(0, bestCount) / (double) ((2 * w + 1) * (2 * w + 1));
        return new Best(cx + (bestI - cells) * step, cz + (bestJ - cells) * step, share, lookX, lookZ);
    }

    /**
     * The nearest place of the biome to stand on that isn't water or lava, within {@link #DRY_RADIUS}; the place
     * itself if there is none (the player then swims). The biome is read where the player's feet would be, as the
     * client reads it for the weather.
     */
    private static int[] dryGround(ServerLevel level, ResourceKey<Biome> key, int x, int z) {
        for (int r = 0; r <= DRY_RADIUS; r += DRY_STEP) {
            for (int dx = -r; dx <= r; dx += DRY_STEP) {
                for (int dz = -r; dz <= r; dz += DRY_STEP) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r)
                        continue;
                    int px = x + dx, pz = z + dz;
                    level.getChunk(px >> 4, pz >> 4);
                    int top = level.getHeight(Heightmap.Types.MOTION_BLOCKING, px, pz);
                    var feet = new BlockPos(px, top, pz);
                    if (level.getFluidState(feet.below()).isEmpty() && level.getBiome(feet).is(key))
                        return new int[]{px, pz};
                }
            }
        }
        return new int[]{x, z};
    }
}
