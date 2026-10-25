package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.MapColor;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.effects.precipitation.PrecipitationShaders;
import org.orecruncher.dsurround.lib.platform.FeatureConflict;

/**
 * Where storms bring dust: where nothing falls, in a biome that is a desert, badlands, dry and sandy, or a wasteland
 * with next to no downfall (Biomes O' Plenty's wasteland). The wasteland rule matches the desert fog color in
 * {@code biomes.json}, so dust storms come where the fog is already dusty. A storm there blows dust along the ground
 * and thickens the fog, in place of the rain other biomes get. Shared by the particles and the fog, so both agree.
 * <p>
 * "Nothing falls" is what falls there now, as the season provider says, not the biome's own setting: with Serene
 * Seasons a tropical biome gets no rain in the dry season even if it otherwise has rain (Biomes O' Plenty's lush
 * desert), and rain in the wet season even if it otherwise has none (the desert).
 */
public final class DustStorms {

    /**
     * The colors of the dust a biome is known for, as sand and red sand show on maps, 0xRRGGBB
     */
    public static final int SAND_COLOR = MapColor.SAND.col;
    public static final int RED_SAND_COLOR = MapColor.COLOR_ORANGE.col;

    private DustStorms() {
    }

    /**
     * Whether dust storms are on: they are part of particle rain and snow, so need it on too, and stand aside, as it
     * does, for a mod that draws the weather as particles of its own (Particle Rain, Simple Weather). They are off
     * whenever its drops aren't being drawn (the shader didn't load or failed, or a shader pack is in use): the dust's
     * fog, its tint on the fog and sky, and the harder wind go with the dust, rather than staying without it.
     */
    public static boolean enabled(Configuration.WeatherOptions options) {
        return enabled(options, FeatureConflict.PARTICLE_WEATHER.isBlocked()) && PrecipitationShaders.DROPS.isUsable();
    }

    static boolean enabled(Configuration.WeatherOptions options, boolean weatherModInstalled) {
        return options.enableParticlePrecipitation && options.enableDustStorms && !weatherModInstalled;
    }

    /**
     * Whether a storm brings dust to a place in the biome
     *
     * @param fallingNow what falls there now, as the season provider says
     */
    public static boolean isDusty(Biome.Precipitation fallingNow, Biome biome, IBiomeLibrary biomeLibrary) {
        if (fallingNow != Biome.Precipitation.NONE)
            return false;
        var info = biomeLibrary.getBiomeInfo(biome);
        return isDusty(info.getTraits(), info.getDownfall());
    }

    static boolean isDusty(Biome.Precipitation fallingNow, BiomeTraits traits, float downfall) {
        return fallingNow == Biome.Precipitation.NONE && isDusty(traits, downfall);
    }

    /**
     * The color of the dust a biome is known for: red sand's in badlands, sand's elsewhere
     */
    public static int dustColor(Biome biome, IBiomeLibrary biomeLibrary) {
        return dustColor(biomeLibrary.getBiomeInfo(biome).getTraits());
    }

    static int dustColor(BiomeTraits traits) {
        return traits.contains(BiomeTrait.BADLANDS) ? RED_SAND_COLOR : SAND_COLOR;
    }

    /**
     * The most a wasteland's downfall can be for it to be dusty. Biomes O' Plenty's wasteland has none; its volcanic
     * plains, with ash and basalt rather than loose dry ground, have 0.3.
     */
    static final float WASTELAND_MAX_DOWNFALL = 0.1F;

    static boolean isDusty(BiomeTraits traits, float downfall) {
        return traits.contains(BiomeTrait.DESERT)
                || traits.contains(BiomeTrait.BADLANDS)
                || (traits.contains(BiomeTrait.DRY) && traits.contains(BiomeTrait.SANDY))
                || isDustyWasteland(traits, downfall);
    }

    private static boolean isDustyWasteland(BiomeTraits traits, float downfall) {
        return traits.contains(BiomeTrait.WASTELAND)
                && downfall < WASTELAND_MAX_DOWNFALL
                && !traits.contains(BiomeTrait.NETHER)
                && !traits.contains(BiomeTrait.END)
                && !traits.contains(BiomeTrait.COLD)
                && !traits.contains(BiomeTrait.SNOWY)
                && !traits.contains(BiomeTrait.SWAMP);
    }
}
