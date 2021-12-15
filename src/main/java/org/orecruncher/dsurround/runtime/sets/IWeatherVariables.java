package org.orecruncher.dsurround.runtime.sets;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;

@Environment(EnvType.CLIENT)
public interface IWeatherVariables {

    /**
     * Is it currently raining in the player world
     *
     * @return true if it is raining, false otherwise
     */
    boolean isRaining();

    /**
     * Is it currently thundering in the player world
     *
     * @return true if it is thundering, false otherwise
     */
    boolean isThundering();

    /**
     * Inverse of isRaining();
     *
     * @return true if it is not raining, false otherwise
     */
    default boolean isNotRaining() {
        return !isRaining();
    }

    /**
     * Inverse of isThundering()
     *
     * @return true if it is not thundering, false otherwise
     */
    default boolean isNotThundering() {
        return !isThundering();
    }

    /**
     * Get the current rain intensity
     *
     * @return 0 - 1
     */
    float getRainIntensity();

    /**
     * Get the current thunder intensity
     *
     * @return 0 - 1
     */
    float getThunderIntensity();

    /**
     * Gets the temperature at the current player location
     *
     * @return the temperature
     */
    float getTemperature();

    /**
     * Indicates if the temperature at the player location is cold enough to show frost breath, etc.
     *
     * @return true if the current temperature conditions are frosty, false otherwise
     */
    default boolean isFrosty() {
        return getTemperature() < 0.2;
    }

    /**
     * Indicaets if the temperature at the player location is cold enough for water to freeze.
     *
     * @return true if the current temperature allows water freezing, false otherwise.
     */
    default boolean canWaterFreeze() {
        return getTemperature() < 0.15;
    }
}
