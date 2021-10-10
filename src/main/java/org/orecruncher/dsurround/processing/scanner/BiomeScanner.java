package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.tag.FluidTags;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeAccess;
import org.orecruncher.dsurround.config.BiomeLibrary;
import org.orecruncher.dsurround.config.DimensionLibrary;
import org.orecruncher.dsurround.config.InternalBiomes;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.GameUtils;

@Environment(EnvType.CLIENT)
public final class BiomeScanner {

    public static final int SCAN_INTERVAL = 4;
    private static final int BIOME_SURVEY_RANGE = 18;
    private static final int MAX_BIOME_AREA = (int) Math.pow(BIOME_SURVEY_RANGE * 2 + 1, 2);
    private static DimensionInfo surveyedDimension;
    private static boolean isUnderground;
    private static boolean isInOuterspace;
    private static boolean isInClouds;
    private static boolean isUnderWater;
    private static BiomeInfo logicalBiomeInfo;
    private final BlockPos.Mutable mutable = new BlockPos.Mutable();
    private int biomeArea;
    private Reference2IntOpenHashMap<BiomeInfo> weights = new Reference2IntOpenHashMap<>(8);
    // "Fingerprint" of the last area survey.
    private Biome surveyedBiome = null;
    private BlockPos surveyedPosition = BlockPos.ORIGIN;

    public static BiomeInfo playerLogicBiomeInfo() {
        return logicalBiomeInfo;
    }

    public static DimensionInfo getDimInfo() {
        return surveyedDimension;
    }

    public static boolean isUnderground() {
        return isUnderground;
    }

    public static boolean isUnderWater() {
        return isUnderWater;
    }

    public static boolean isInOuterspace() {
        return isInOuterspace;
    }

    public static boolean isInClouds() {
        return isInClouds;
    }

    public void tick(long tickCount) {

        if (tickCount % SCAN_INTERVAL != 0)
            return;

        final PlayerEntity player = GameUtils.getPlayer();
        assert player != null;

        final World world = player.getEntityWorld();
        final BlockPos position = player.getBlockPos();

        var dimensionInfo = DimensionLibrary.getData(world);
        final BiomeAccess biomes = world.getBiomeAccess();
        final Biome playerBiome = biomes.getBiome(position);

        if (this.surveyedBiome != playerBiome
                || !surveyedDimension.equals(dimensionInfo)
                || this.surveyedPosition.compareTo(position) != 0) {

            this.surveyedBiome = playerBiome;
            this.surveyedPosition = position;
            surveyedDimension = dimensionInfo;

            var playerBiomeInfo = BiomeLibrary.getBiomeInfo(playerBiome);

            isUnderground = isInOuterspace = isInClouds = isUnderWater = false;
            InternalBiomes internalBiome = InternalBiomes.NONE;

            if (player.isSubmergedIn(FluidTags.WATER)) {
                isUnderWater = true;
                if (playerBiomeInfo.isRiver())
                    internalBiome = InternalBiomes.UNDER_RIVER;
                else if (playerBiomeInfo.isDeepOcean())
                    internalBiome = InternalBiomes.UNDER_DEEP_OCEAN;
                else if (playerBiomeInfo.isOcean())
                    internalBiome = InternalBiomes.UNDER_OCEAN;
                else
                    internalBiome = InternalBiomes.UNDER_WATER;
            } else {
                final int theY = position.getY();
                if (theY < (dimensionInfo.getSeaLevel() - 4)) {
                    internalBiome = InternalBiomes.UNDERGROUND;
                    isUnderground = true;
                } else if (theY >= dimensionInfo.getSpaceHeight()) {
                    internalBiome = InternalBiomes.SPACE;
                    isInOuterspace = true;
                } else if (theY >= dimensionInfo.getCloudHeight()) {
                    internalBiome = InternalBiomes.CLOUDS;
                    isInClouds = true;
                }
            }

            this.biomeArea = 0;
            this.weights = new Reference2IntOpenHashMap<>(8);

            if (internalBiome != InternalBiomes.NONE) {
                logicalBiomeInfo = BiomeLibrary.getBiomeInfo(internalBiome);
                this.biomeArea = 1;
                this.weights.addTo(logicalBiomeInfo, 1);
            } else {
                logicalBiomeInfo = playerBiomeInfo;
                for (int dZ = -BIOME_SURVEY_RANGE; dZ <= BIOME_SURVEY_RANGE; dZ++) {
                    for (int dX = -BIOME_SURVEY_RANGE; dX <= BIOME_SURVEY_RANGE; dX++) {
                        this.mutable.set(this.surveyedPosition.getX() + dX, 0, this.surveyedPosition.getZ() + dZ);
                        final Biome biome = biomes.getBiome(this.mutable);
                        if (biome == null) {
                            continue;
                        }
                        this.weights.addTo(BiomeLibrary.getBiomeInfo(biome), 1);
                    }
                }
                this.biomeArea = MAX_BIOME_AREA;
            }
        }
    }

    public int getBiomeArea() {
        return this.biomeArea;
    }

    public Reference2IntOpenHashMap<BiomeInfo> getBiomes() {
        return this.weights;
    }

}