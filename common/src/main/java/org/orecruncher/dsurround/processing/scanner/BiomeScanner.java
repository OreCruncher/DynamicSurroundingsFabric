package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionLibrary;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.dimension.DimensionInfo;
import org.orecruncher.dsurround.lib.GameUtils;

public final class BiomeScanner extends AbstractScanner {

    public static final int SCAN_INTERVAL = 4;
    private static final int UNDERGROUND_THRESHOLD_OFFSET = 8;
    private static final int SURVEY_HORIZONTAL_DIMENSION = 18;
    private static final int SURVEY_HORIZONTAL_OFFSET = SURVEY_HORIZONTAL_DIMENSION / 2 - 1;
    private static final int SURVEY_VERTICAL_DIMENSION = 16;
    private static final int SURVEY_VERTICAL_OFFSET = SURVEY_VERTICAL_DIMENSION / 4 - 1;
    private static final int MAX_SURVEY_VOLUME = SURVEY_HORIZONTAL_DIMENSION * SURVEY_HORIZONTAL_DIMENSION * SURVEY_VERTICAL_DIMENSION;

    private DimensionInfo surveyedDimension;
    private boolean isUnderWater;
    private BiomeInfo logicalBiomeInfo;

    private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    private int biomeArea;
    private Reference2IntOpenHashMap<BiomeInfo> weights = new Reference2IntOpenHashMap<>(8);
    private Biome surveyedBiome = null;
    private BlockPos surveyedPosition = BlockPos.ZERO;
    private final IBiomeLibrary biomeLibrary;
    private final IDimensionLibrary dimensionLibrary;
    private final CeilingScanner ceilingScanner;

    public BiomeScanner(IBiomeLibrary biomeLibrary, IDimensionLibrary dimensionLibrary, CeilingScanner ceilingScanner) {
        this.biomeLibrary = biomeLibrary;
        this.dimensionLibrary = dimensionLibrary;
        this.ceilingScanner = ceilingScanner;
    }

    public BiomeInfo playerLogicBiomeInfo() {
        return this.logicalBiomeInfo;
    }

    public DimensionInfo getDimInfo() {
        return this.surveyedDimension;
    }

    public boolean isUnderWater() {
        return this.isUnderWater;
    }

    public void tick(long tickCount) {

        if (tickCount % SCAN_INTERVAL != 0)
            return;

        var player = GameUtils.getPlayer().orElseThrow();
        var world = player.level();
        var position = player.blockPosition();

        var dimensionInfo = this.dimensionLibrary.getData(world);
        var biomes = world.getBiomeManager();
        var playerBiome = biomes.getBiome(position);

        if (this.surveyedBiome != playerBiome.value()
                || !this.surveyedDimension.equals(dimensionInfo)
                || !this.surveyedPosition.equals(position)) {

            this.surveyedBiome = playerBiome.value();
            this.surveyedPosition = position;
            this.surveyedDimension = dimensionInfo;

            this.weights = new Reference2IntOpenHashMap<>(8);

            // If the player is underwater, underwater effects will rule over everything else
            this.isUnderWater = player.isEyeInFluid(FluidTags.WATER);
            if (this.isUnderWater) {
                SyntheticBiome internalBiome;
                var playerBiomeInfo = this.biomeLibrary.getBiomeInfo(playerBiome.value());
                if (playerBiomeInfo.isRiver())
                    internalBiome = SyntheticBiome.UNDER_RIVER;
                else if (playerBiomeInfo.isDeepOcean())
                    internalBiome = SyntheticBiome.UNDER_DEEP_OCEAN;
                else if (playerBiomeInfo.isOcean())
                    internalBiome = SyntheticBiome.UNDER_OCEAN;
                else
                    internalBiome = SyntheticBiome.UNDER_WATER;

                this.logicalBiomeInfo = this.biomeLibrary.getBiomeInfo(internalBiome);
                this.biomeArea = 1;
                this.weights.addTo(this.logicalBiomeInfo, 1);
                return;
            }

            this.logicalBiomeInfo = this.resolveBiome(dimensionInfo, biomes, position);

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

    private BiomeInfo resolveBiome(DimensionInfo dimInfo, BiomeManager access, BlockPos pos) {

        // Get the biome at the specified position
        var biome = access.getBiome(pos).value();
        var biomeInfo = this.biomeLibrary.getBiomeInfo(biome);

        // Check if the user is in a cave. This overrides the check for being underground.
        if (biomeInfo.isCave()) {
            return biomeInfo;
        }

        // Are we simulating underground? This is done by checking the Y level.
        var y = pos.getY();
        if (y < (dimInfo.getSeaLevel() - UNDERGROUND_THRESHOLD_OFFSET)) {
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.UNDERGROUND);
        }

        // Inside check overrides everything else. Some dimensions are always out side
        // so those are excluded (like the nether).
        if (!dimInfo.alwaysOutside() && this.ceilingScanner.isReallyInside()) {
            // If it's not underground, and we are inside, return INSIDE
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.INSIDE);
        }

        // Are we really up in the sky
        if (y >= dimInfo.getSpaceHeight()) {
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.SPACE);
        }

        // Up in the clouds
        if (y >= dimInfo.getCloudHeight()) {
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.CLOUDS);
        }

        // Nothing special
        return biomeInfo;
    }

    public int getBiomeArea() {
        return this.biomeArea;
    }

    public Reference2IntOpenHashMap<BiomeInfo> getBiomes() {
        return this.weights;
    }
}