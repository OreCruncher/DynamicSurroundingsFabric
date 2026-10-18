package org.orecruncher.dsurround.config.biome.biometraits;

import dev.architectury.hooks.level.biome.BiomeHooks;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

/**
 * What can be known of a biome on the client, to work out its traits from. A server sends the client each biome's
 * climate and effects (colors, sounds, music), though not what generates in it; and only the tags it has, which a
 * vanilla server keeps to Minecraft's own.
 *
 * @param id          the biome's id
 * @param biome       the biome, for looking up its tags; null where there is none (in tests)
 * @param music       the music the biome plays, if any
 * @param ambientLoop the ambient sound looping in the biome, if any
 */
public record BiomeEvidence(ResourceLocation id, @Nullable Biome biome,
                            boolean hasPrecipitation, float temperature, boolean frozen, float downfall,
                            BiomeSpecialEffects.GrassColorModifier grassColorModifier,
                            Optional<ResourceLocation> music, Optional<ResourceLocation> ambientLoop) {

    /**
     * The evidence a game biome gives
     */
    public static BiomeEvidence of(ResourceLocation id, Biome biome) {
        // Initialization of BiomeInfo depends on this routine, so the biome's properties are read directly
        var properties = BiomeHooks.getBiomeProperties(biome);
        var climate = properties.getClimateProperties();
        var effects = properties.getEffectsProperties();
        return new BiomeEvidence(id, biome,
                climate.hasPrecipitation(), climate.getTemperature(),
                climate.getTemperatureModifier() == Biome.TemperatureModifier.FROZEN, climate.getDownfall(),
                effects.getGrassColorModifier(),
                effects.getBackgroundMusic().map(music -> soundId(music.getEvent())),
                effects.getAmbientLoopSound().map(BiomeEvidence::soundId));
    }

    private static ResourceLocation soundId(Holder<SoundEvent> sound) {
        return sound.value().getLocation();
    }
}
