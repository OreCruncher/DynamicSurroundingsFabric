package org.orecruncher.dsurround.runtime.oracle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.lib.DayCycle;

import java.util.List;
import java.util.function.Predicate;

public interface ILevelOracle {

    /**
     * Gets the current ClientLevel
     */
    ClientLevel level();
    /**
     * Gets the current world time
     */
    long worldTime();
    /**
     * The sea level of this level
     */
    int seaLevel();
    /**
     * Returns the biome holder for the specified position
     */
    Holder<Biome> biomeHolder(BlockPos blockPos);
    /**
     * Returns the Biome for the specified position
     */
    default Biome biome(BlockPos pos) {
        return this.biomeHolder(pos).value();
    }
    /**
     * Returns the precipitation at the specified position
     */
    Biome.Precipitation precipitationAt(BlockPos pos);
    /**
     * Gets the temperature at the specified block position
     */
    float temperatureAt(BlockPos pos);
    /**
     * Gets the current rain level
     */
    float getRainLevel();
    /**
     * Gets the current thunder level
     */
    float getThunderLevel();
    /**
     * Indicates if it is currently raining
     */
    boolean isRaining();
    /**
     * Indicates if it is current thundering
     */
    boolean isThundering();
    /**
     * Whether the sky can be seen at the current position
     */
    boolean canSeeSky(BlockPos pos);

    BlockPos getTopSolidOrLiquidBlock(BlockPos pos);
    /**
     * Determines if the level is natural (eg, Overworld like) vs. not (eg, Nether)
     */
    boolean natural();
    /**
     * Whether the level is super flat or not
     */
    boolean isSuperFlat();
    /**
     * Name of the dimension
     */
    String dimensionName();
    /**
     * Identifier of the dimension
     */
    Identifier dimensionIdentifier();
    /**
     * Indicates if the dimension has skylight
     */
    boolean hasSkyLight();
    /**
     * Gets the current diurnal state of the day
     */
    DayCycle currentDiurnalState();
    /**
     * Gets the current moon size
     */
    float currentMoonSize();
    /**
     * Gets the current celestial angle
     */
    float currentCelestialAngle();

    <T extends Entity> List<T> getEntitiesOfClass(final Class<T> baseClass, final AABB bb);

    boolean doesBlockEntityExist(final Predicate<BlockEntity> predicate);
}
