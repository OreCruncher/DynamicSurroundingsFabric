package org.orecruncher.dsurround.processing.scanner;

import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeManager;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.IDimensionInformation;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Objects;
import java.util.function.Function;

public final class BiomeScanner extends AbstractScanner {

    public static final int SCAN_INTERVAL = 4;
    private static final int UNDERGROUND_THRESHOLD_OFFSET = 8;
    private static final int SURVEY_HORIZONTAL_DIMENSION = 18;
    private static final int SURVEY_HORIZONTAL_OFFSET = SURVEY_HORIZONTAL_DIMENSION / 2 - 1;
    private static final int SURVEY_VERTICAL_DIMENSION = 16;
    private static final int SURVEY_VERTICAL_OFFSET = SURVEY_VERTICAL_DIMENSION / 4 - 1;
    static final int MAX_SURVEY_VOLUME = SURVEY_HORIZONTAL_DIMENSION * SURVEY_HORIZONTAL_DIMENSION * SURVEY_VERTICAL_DIMENSION;
    // Sample every other block on each axis: 648 lookups rather than 5,184. Biomes change on a 4 block grid, with
    // some blending at the edges, so the shares come out nearly the same.
    static final int SURVEY_STRIDE = 2;

    private ResourceLocation surveyedDimension;
    private boolean isUnderWater;
    private BiomeInfo logicalBiomeInfo;

    private final BlockPos.MutableBlockPos mutable = new BlockPos.MutableBlockPos();

    private int biomeArea;
    private final Reference2IntOpenHashMap<BiomeInfo> weights = new Reference2IntOpenHashMap<>(8);
    private Biome surveyedBiome = null;
    private BlockPos surveyedPosition = BlockPos.ZERO;
    private final IBiomeLibrary biomeLibrary;
    private final IDimensionInformation dimensionInformation;
    private final CeilingScanner ceilingScanner;

    public BiomeScanner(IBiomeLibrary biomeLibrary, IDimensionInformation dimensionInformation, CeilingScanner ceilingScanner) {
        this.biomeLibrary = biomeLibrary;
        this.dimensionInformation = dimensionInformation;
        this.ceilingScanner = ceilingScanner;
    }

    public BiomeInfo playerLogicBiomeInfo() {
        return this.logicalBiomeInfo;
    }

    public ResourceLocation getDimInfo() {
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

        var biomes = world.getBiomeManager();
        var playerBiome = biomes.getBiome(position);

        var dimension = this.dimensionInformation.name();
        if (this.surveyedBiome != playerBiome.value()
                || !Objects.equals(this.surveyedDimension, dimension)
                || !this.surveyedPosition.equals(position)) {

            this.surveyedBiome = playerBiome.value();
            this.surveyedPosition = position;
            this.surveyedDimension = dimension;

            this.weights.clear();

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

            this.logicalBiomeInfo = this.resolveBiome(biomes, position);
            this.biomeArea = survey(position, SURVEY_STRIDE, this.mutable, this.weights, pos -> this.resolveBiome(biomes, pos));
        }
    }

    /**
     * Counts what {@code lookup} reports across the survey volume around {@code center}, into {@code weights}.
     * Every {@code stride}th block along each axis is sampled, and each sample counts for the
     * {@code stride}<sup>3</sup> blocks it stands for, so the total is the same whatever the stride.
     *
     * @return the total weight added
     */
    static <T> int survey(BlockPos center, int stride, BlockPos.MutableBlockPos mutable, Reference2IntOpenHashMap<T> weights, Function<BlockPos, T> lookup) {
        final int weight = stride * stride * stride;
        int total = 0;
        for (int z = 0; z < SURVEY_HORIZONTAL_DIMENSION; z += stride) {
            mutable.setZ(z - SURVEY_HORIZONTAL_OFFSET + center.getZ());
            for (int x = 0; x < SURVEY_HORIZONTAL_DIMENSION; x += stride) {
                mutable.setX(x - SURVEY_HORIZONTAL_OFFSET + center.getX());
                for (int y = 0; y < SURVEY_VERTICAL_DIMENSION; y += stride) {
                    mutable.setY(y - SURVEY_VERTICAL_OFFSET + center.getY());
                    weights.addTo(lookup.apply(mutable), weight);
                    total += weight;
                }
            }
        }
        return total;
    }

    private BiomeInfo resolveBiome(BiomeManager access, BlockPos pos) {

        // Get the biome at the specified position
        var biome = access.getBiome(pos).value();
        var biomeInfo = this.biomeLibrary.getBiomeInfo(biome);

        // Check if the user is in a cave. This overrides the check for being underground.
        if (biomeInfo.isCave()) {
            return biomeInfo;
        }

        // Are we simulating underground? This is done by checking the Y level.
        var y = pos.getY();
        if (y < (this.dimensionInformation.seaLevel() - UNDERGROUND_THRESHOLD_OFFSET)) {
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.UNDERGROUND);
        }

        // Inside check overrides everything else. Some dimensions are always outside,
        // so those are excluded (like the nether).
        if (!this.dimensionInformation.alwaysOutside() && this.ceilingScanner.isReallyInside()) {
            // If it's not underground, and we are inside, return INSIDE
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.INSIDE);
        }

        // Are we really up in the sky
        if (y >= this.dimensionInformation.getSpaceHeight()) {
            return this.biomeLibrary.getBiomeInfo(SyntheticBiome.SPACE);
        }

        // Up in the clouds
        if (y >= this.dimensionInformation.getCloudHeight()) {
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