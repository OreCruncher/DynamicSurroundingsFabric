// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;

/**
 * How wet the ground is from the rain falling on it: what footprints and footsteps share, so a step that squelches
 * leaves a mud print. Read from the rain falling now: dry the moment it stops.
 */
public final class GroundWetness {

    /**
     * From this wetness, ground that turns to mud (grass, dirt) is muddy: mud prints, mud carried onto paths, a
     * squelching step
     */
    public static final float MUDDY = 0.25F;

    private GroundWetness() {
    }

    /**
     * How open a place is to rain: 0 under cover or where it doesn't rain (snow, a dry biome), to 1 in the open
     */
    public static float openToRain(Level level, BlockPos pos) {
        boolean rains = level.getBiome(pos).value().getPrecipitationAt(pos, level.getSeaLevel()) == Biome.Precipitation.RAIN;
        return rains ? Shelter.exposure(level, pos) : 0F;
    }

    /**
     * How soaked the ground under a place is by the rain falling on it now, 0 to 1: how much falls (the storm's
     * strength, under a season's lighter rain), as far as the place is open to it
     *
     * @param pos the place above the ground: where feet are, or where a print lies
     */
    public static float at(IWeatherSimulation weather, Level level, BlockPos pos) {
        float falling = weather.precipitationLevel(level.getRainLevel(1F));
        return falling <= 0F ? 0F : falling * openToRain(level, pos);
    }
}
