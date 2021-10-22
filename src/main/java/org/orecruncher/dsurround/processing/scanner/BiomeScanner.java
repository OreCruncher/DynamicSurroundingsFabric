package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.DimensionLibrary;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.processing.Scanners;

@Environment(EnvType.CLIENT)
public final class BiomeScanner {

    public static final int SCAN_INTERVAL = 4;
    private static final int UNDERGROUND_THRESHOLD_OFFSET = 4;
    private static final int SURVEY_HORIZONTAL_DIMENSION = 18;
    private static final int SURVEY_HORIZONTAL_OFFSET = SURVEY_HORIZONTAL_DIMENSION / 2 - 1;
    private static final int SURVEY_VERTICAL_DIMENSION = 16;
    private static final int SURVEY_VERTICAL_OFFSET = SURVEY_VERTICAL_DIMENSION / 4 - 1;
    private static final int MAX_SURVEY_VOLUME = SURVEY_HORIZONTAL_DIMENSION * SURVEY_HORIZONTAL_DIMENSION * SURVEY_VERTICAL_DIMENSION;

    private static DimensionInfo surveyedDimension;
    private static boolean isUnderWater;
    private static BiomeInfo logicalBiomeInfo;

    private final BlockPos.Mutable mutable = new BlockPos.Mutable();

    private int biomeArea;
    private Reference2IntOpenHashMap<BiomeInfo> weights = new Reference2IntOpenHashMap<>(8);
    private Biome surveyedBiome = null;
    private BlockPos surveyedPosition = BlockPos.ORIGIN;

    public static BiomeInfo playerLogicBiomeInfo() {
        return logicalBiomeInfo;
    }

    public static DimensionInfo getDimInfo() {
        return surveyedDimension;
    }

    public static boolean isUnderWater() {
        return isUnderWater;
    }

    public void tick(long tickCount) {

        if (tickCount % SCAN_INTERVAL != 0)
            return;

        var player = GameUtils.getPlayer();
        assert player != null;

        var world = player.getEntityWorld();
        var position = player.getBlockPos();

        var dimensionInfo = DimensionLibrary.getData(world);
        var biomes = world.getBiomeAccess();
        var playerBiome = biomes.getBiome(position);

        if (this.surveyedBiome != playerBiome
                || !surveyedDimension.equals(dimensionInfo)
                || !this.surveyedPosition.equals(position)) {

            this.surveyedBiome = playerBiome;
            this.surveyedPosition = position;
            surveyedDimension = dimensionInfo;

            // If the player is underwater, underwater effects will rule over everything else
            isUnderWater = player.isSubmergedIn(FluidTags.WATER);
            if (isUnderWater) {
                InternalBiomes internalBiome;
                var playerBiomeInfo = BiomeLibrary.getBiomeInfo(playerBiome);
                if (playerBiomeInfo.isRiver())
                    internalBiome = InternalBiomes.UNDER_RIVER;
                else if (playerBiomeInfo.isDeepOcean())
                    internalBiome = InternalBiomes.UNDER_DEEP_OCEAN;
                else if (playerBiomeInfo.isOcean())
                    internalBiome = InternalBiomes.UNDER_OCEAN;
                else
                    internalBiome = InternalBiomes.UNDER_WATER;

                logicalBiomeInfo = BiomeLibrary.getBiomeInfo(internalBiome);
                this.biomeArea = 1;
                this.weights.addTo(logicalBiomeInfo, 1);
                return;
            }

            logicalBiomeInfo = this.resolveBiome(dimensionInfo, biomes, position);

            this.weights = new Reference2IntOpenHashMap<>(8);

            for (int z = 0; z < SURVEY_HORIZONTAL_DIMENSION; z++) {
                var dZ = z - SURVEY_HORIZONTAL_OFFSET + this.surveyedPosition.getZ();
                this.mutable.setZ(dZ);
                for (int x = 0; x < SURVEY_HORIZONTAL_DIMENSION; x++) {
                    var dX = x - SURVEY_HORIZONTAL_OFFSET + this.surveyedPosition.getX();
                    this.mutable.setX(dX);
                    for (int y = 0; y < SURVEY_VERTICAL_DIMENSION; y++) {
                        var dY = y - SURVEY_VERTICAL_OFFSET + this.surveyedPosition.getY();
                        this.mutable.setY(dY);
                        var info = this.resolveBiome(dimensionInfo, biomes, this.mutable);
                        this.weights.addTo(info, 1);
                    }
                }
            }
            this.biomeArea = MAX_SURVEY_VOLUME;
        }
    }

    private BiomeInfo resolveBiome(DimensionInfo dimInfo, BiomeAccess access, BlockPos pos) {
        var biome = access.getBiome(pos);

        // If it is not an underground biome see if we need to simulate one of the other internal biomes
        if (biome.getCategory() != Biome.Category.UNDERGROUND) {
            var y = pos.getY();
            if (y < (dimInfo.getSeaLevel() - UNDERGROUND_THRESHOLD_OFFSET)) {
                return BiomeLibrary.getBiomeInfo(InternalBiomes.UNDERGROUND);
            } else if (!dimInfo.alwaysOutside() && Scanners.isInside()) {
                // If it's not underground, and we are inside, return INSIDE
                return BiomeLibrary.getBiomeInfo(InternalBiomes.INSIDE);
            } else if (y >= dimInfo.getSpaceHeight()) {
                return BiomeLibrary.getBiomeInfo(InternalBiomes.SPACE);
            } else if (y >= dimInfo.getCloudHeight()) {
                return BiomeLibrary.getBiomeInfo(InternalBiomes.CLOUDS);
            }
        }

        return BiomeLibrary.getBiomeInfo(biome);
    }

    public int getBiomeArea() {
        return this.biomeArea;
    }

    public Reference2IntOpenHashMap<BiomeInfo> getBiomes() {
        return this.weights;
    }

}