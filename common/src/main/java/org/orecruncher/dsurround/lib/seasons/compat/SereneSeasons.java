package org.orecruncher.dsurround.lib.seasons.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.BiomeCompat;

import java.util.Optional;

/**
 * Temporary 26.2-compatible placeholder for the Serene Seasons integration.
 *
 * <p>The Serene Seasons API artifacts used by the 1.21.1 build were not
 * available for Minecraft 26.2 when this port was prepared. Keeping this class
 * free of Serene Seasons imports lets the base mod compile and run on 26.2.
 * Reintroduce the API-backed implementation once the upstream 26.2 dependency
 * is published.</p>
 */
public class SereneSeasons extends AbstractSeasonProvider {

    public SereneSeasons() {
        super("Serene Seasons (disabled in 26.2 port)");
    }

    @Override
    public Optional<Component> getCurrentSeason() {
        return Optional.empty();
    }

    @Override
    public Optional<Component> getCurrentSeasonTranslated() {
        return Optional.empty();
    }

    @Override
    public float getTemperature(BlockPos blockPos) {
        var biome = this.level().getBiome(blockPos).value();
        return BiomeCompat.getTemperature(biome, blockPos);
    }
}
