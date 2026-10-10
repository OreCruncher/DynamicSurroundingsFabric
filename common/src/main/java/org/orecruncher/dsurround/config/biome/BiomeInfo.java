package org.orecruncher.dsurround.config.biome;

import dev.architectury.hooks.level.biome.BiomeProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntry;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.processing.fog.FogDensity;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A biome as the configuration describes it, built by {@link BiomeInfoBuilder}. Doesn't change once built; a reload
 * builds new ones.
 */
public final class BiomeInfo implements Comparable<BiomeInfo>, IBiomeIdentity, IBiomeSoundProvider {

    private final int version;
    private final ConfigServices services;
    private final Identifier biomeId;
    private final String biomeName;
    @Nullable
    private final Biome biome;
    @Nullable
    private final BiomeProperties properties;
    private final BiomeTraits traits;
    private final AcousticEntryCollection loopSounds;
    private final AcousticEntryCollection moodSounds;
    private final AcousticEntryCollection additionalSounds;
    private final AcousticEntryCollection musicSounds;
    private final ObjectArray<String> comments;
    @Nullable
    private final TextColor fogColor;
    private final FogDensity fogDensity;
    private final Script additionalSoundChance;
    private final Script moodSoundChance;
    // Built from the configured music and the game's track for the biome, which doesn't change
    @Nullable
    private AcousticEntryCollection musicChoices;
    @Nullable
    private Music musicChoicesVanilla;

    BiomeInfo(BiomeInfoBuilder builder) {
        this.version = builder.version;
        this.services = builder.services;
        this.biomeId = builder.biomeId;
        this.biomeName = builder.biomeName;
        this.biome = builder.biome;
        this.properties = builder.properties;
        this.traits = builder.traits;
        this.loopSounds = builder.loopSounds;
        this.moodSounds = builder.moodSounds;
        this.additionalSounds = builder.additionalSounds;
        this.musicSounds = builder.musicSounds;
        this.comments = builder.comments;
        this.fogColor = builder.fogColor;
        this.fogDensity = builder.fogDensity;
        this.additionalSoundChance = builder.additionalSoundChance;
        this.moodSoundChance = builder.moodSoundChance;
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isRiver() {
        return this.hasTrait(BiomeTrait.RIVER);
    }

    public boolean isOcean() {
        return this.hasTrait(BiomeTrait.OCEAN);
    }

    public boolean isDeepOcean() {
        return this.hasTrait(BiomeTrait.DEEP_OCEAN);
    }

    public boolean isCave() {
        return this.hasTrait(BiomeTrait.CAVE);
    }

    @Override
    public Identifier getBiomeId() {
        return this.biomeId;
    }

    @Override
    public String getBiomeName() {
        return this.biomeName;
    }

    public @Nullable TextColor getFogColor() {
        return this.fogColor;
    }

    public FogDensity getFogDensity() {
        return this.fogDensity;
    }

    @Override
    public float getDownfall() {
        return BiomeInfoBuilder.downfall(this.properties);
    }

    @Override
    public BiomeTraits getTraits() {
        return this.traits;
    }

    @Override
    public Collection<ISoundFactory> findBiomeSoundMatches() {
        return this.loopSounds
                .findMatches()
                .map(AcousticEntry::getAcoustic)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<ISoundFactory> getExtraSound(final SoundEventType type, final IRandomizer random) {

        @Nullable
        AcousticEntryCollection sourceList = null;

        // An empty list can't produce a sound, so the chance script isn't run for it
        switch (type) {
            case ADDITION -> {
                if (!this.additionalSounds.isEmpty()) {
                    var chance = this.services.conditionEvaluator().eval(this.additionalSoundChance);
                    if (chance instanceof Double c) {
                        sourceList = random.nextDouble() < c ? this.additionalSounds : null;
                    }
                }
            }
            case MOOD -> {
                if (!this.moodSounds.isEmpty()) {
                    var chance = this.services.conditionEvaluator().eval(this.moodSoundChance);
                    if (chance instanceof Double c) {
                        sourceList = random.nextDouble() < c ? this.moodSounds : null;
                    }
                }
            }
            case MUSIC -> sourceList = this.musicSounds;
        }

        return sourceList == null ? Optional.empty() : sourceList.makeSelection(random);
    }

    @Override
    public AcousticEntryCollection getMusicChoices(Optional<Music> vanilla) {
        var offered = vanilla.orElse(null);
        if (this.musicChoices == null || this.musicChoicesVanilla != offered) {
            var choices = new AcousticEntryCollection();
            if (offered != null) {
                var factory = this.services.soundLibrary().getSoundFactoryForMusic(offered);
                choices.add(new AcousticEntry(factory, null, this.services.conditionEvaluator()));
            }
            choices.addAll(this.musicSounds);
            choices.trim();
            this.musicChoices = choices;
            this.musicChoicesVanilla = offered;
        }
        return this.musicChoices;
    }

    /**
     * Gets the list of sounds for the specified type. API for diagnostics purposes only.
     */
    public Collection<AcousticEntry> getSounds(SoundEventType type) {
        return switch (type) {
            case LOOP -> this.loopSounds;
            case MUSIC -> this.musicSounds;
            case MOOD -> this.moodSounds;
            case ADDITION -> this.additionalSounds;
        };
    }

    public Collection<String> getComments() {
        return this.comments;
    }

    @Override
    public String toString() {
        final String indent = "    ";

        var tags = Optional.ofNullable(this.biome).map(b -> {
                    var holder = RegistryUtils.getRegistryEntry(Registries.BIOME, b);
                    if (holder.isEmpty())
                        return "null";
                    return this.services.tagLibrary().asString(this.services.tagLibrary().streamTags(holder.get()));
                }).orElse("null");

        final StringBuilder builder = new StringBuilder();
        builder.append("Biome [").append(getBiomeName()).append('/').append(this.biomeId).append("]");
        builder.append("\nTags: ").append(tags);
        builder.append("\n").append(getTraits().toString());

        builder.append("\nfogDensity: ").append(this.fogDensity.getName());

        if (this.fogColor != null) {
            builder.append(", fogColor: ").append(this.fogColor.formatValue());
        }

        if (!this.loopSounds.isEmpty()) {
            builder.append("\nLOOP sounds [\n");
            builder.append(this.loopSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        } else {
            builder.append("\nLOOP sounds [NONE]");
        }

        if (!this.musicSounds.isEmpty()) {
            builder.append("\nMUSIC sounds [\n");
            builder.append(this.musicSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (!this.additionalSounds.isEmpty()) {
            builder.append("\nADDITIONAL chance: ").append(this.additionalSoundChance);
            builder.append("\nADDITIONAL sounds [\n");
            builder.append(
                    this.additionalSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (!this.moodSounds.isEmpty()) {
            builder.append("\nMOOD chance: ").append(this.moodSoundChance);
            builder.append("\nMOOD sounds [\n");
            builder.append(this.moodSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (!this.comments.isEmpty()) {
            builder.append("\ncomments:\n");
            builder.append(this.comments.stream().map(c -> indent + c).collect(Collectors.joining("\n")));
            builder.append('\n');
        }

        builder.append("\n");

        return builder.toString();
    }

    @Override
    public int compareTo(final BiomeInfo o) {
        return getBiomeId().compareTo(o.getBiomeId());
    }
}
