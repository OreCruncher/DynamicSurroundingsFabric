package org.orecruncher.dsurround.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.dimension.DimensionType;
import org.orecruncher.dsurround.lib.GameUtils;

@Environment(EnvType.CLIENT)
public final class BiomeScanner {

    public static final int SCAN_INTERVAL = 4;
    private static final int BIOME_SURVEY_RANGE = 18;
    private static final int MAX_BIOME_AREA = (int) Math.pow(BIOME_SURVEY_RANGE * 2 + 1, 2);

    private final BlockPos.Mutable mutable = new BlockPos.Mutable();

    private int biomeArea;
    private Reference2IntOpenHashMap<Biome> weights = new Reference2IntOpenHashMap<>(8);

    // "Fingerprint" of the last area survey.
    private Biome surveyedBiome = null;
    private DimensionType surveyedDimension;
    private BlockPos surveyedPosition = BlockPos.ORIGIN;

    public void tick(long tickCount) {

        if (tickCount % SCAN_INTERVAL != 0)
            return;

        final PlayerEntity player = GameUtils.getPlayer();
        assert player != null;

        final World world = player.getEntityWorld();
        final BlockPos position = player.getBlockPos();

        final DimensionType dimension = world.getDimension();
        final BiomeAccess biomes = world.getBiomeAccess();
        final Biome playerBiome = biomes.getBiome(position);

        if (this.surveyedBiome != playerBiome
                || !this.surveyedDimension.equals(dimension)
                || this.surveyedPosition.compareTo(position) != 0) {

            this.surveyedBiome = playerBiome;
            this.surveyedDimension = dimension;
            this.surveyedPosition = position;

            this.biomeArea = 0;
            this.weights = new Reference2IntOpenHashMap<>(8);

            for (int dZ = -BIOME_SURVEY_RANGE; dZ <= BIOME_SURVEY_RANGE; dZ++) {
                for (int dX = -BIOME_SURVEY_RANGE; dX <= BIOME_SURVEY_RANGE; dX++) {
                    this.mutable.set(this.surveyedPosition.getX() + dX, 0, this.surveyedPosition.getZ() + dZ);
                    final Biome biome = biomes.getBiome(this.mutable);
                    if (biome == null) {
                        continue;
                    }
                    this.weights.addTo(biome, 1);
                }
            }
            this.biomeArea = MAX_BIOME_AREA;
        }
    }

    public int getBiomeArea() {
        return this.biomeArea;
    }

    public Reference2IntOpenHashMap<Biome> getBiomes() {
        return this.weights;
    }
}