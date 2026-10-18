package org.orecruncher.dsurround.config.biome.biometraits;

import dev.architectury.hooks.level.biome.BiomeHooks;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.attribute.AmbientSounds;
import net.minecraft.world.attribute.BackgroundMusic;
import net.minecraft.world.attribute.EnvironmentAttributes;
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
 * @param definition  what its definition file says it grows, if a mod's jar has one
 */
public record BiomeEvidence(Identifier id, @Nullable Biome biome,
                            boolean hasPrecipitation, float temperature, boolean frozen, float downfall,
                            BiomeSpecialEffects.GrassColorModifier grassColorModifier,
                            Optional<Identifier> music, Optional<Identifier> ambientLoop,
                            Optional<BiomeDefinition> definition) {

    /**
     * The evidence a game biome gives
     */
    public static BiomeEvidence of(Identifier id, Biome biome, Optional<BiomeDefinition> definition) {
        // Initialization of BiomeInfo depends on this routine, so the biome's properties are read directly
        var properties = BiomeHooks.getBiomeProperties(biome);
        var climate = properties.getClimateProperties();
        var effects = properties.getEffectsProperties();
        // Music and ambient sounds are environment attributes: only those the biome sets itself, not the dimension's
        var attributes = biome.getAttributes();
        var music = attributes.contains(EnvironmentAttributes.BACKGROUND_MUSIC)
                ? attributes.applyModifier(EnvironmentAttributes.BACKGROUND_MUSIC, BackgroundMusic.EMPTY).defaultMusic()
                : Optional.<Music>empty();
        var ambient = attributes.contains(EnvironmentAttributes.AMBIENT_SOUNDS)
                ? attributes.applyModifier(EnvironmentAttributes.AMBIENT_SOUNDS, AmbientSounds.EMPTY).loop()
                : Optional.<Holder<SoundEvent>>empty();
        return new BiomeEvidence(id, biome,
                climate.hasPrecipitation(), climate.getTemperature(),
                climate.getTemperatureModifier() == Biome.TemperatureModifier.FROZEN, climate.getDownfall(),
                effects.getGrassColorModifier(),
                music.map(m -> soundId(m.sound())),
                ambient.map(BiomeEvidence::soundId),
                definition);
    }

    private static Identifier soundId(Holder<SoundEvent> sound) {
        return sound.value().location();
    }
}
