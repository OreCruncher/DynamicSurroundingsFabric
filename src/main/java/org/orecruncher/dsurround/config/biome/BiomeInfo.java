package org.orecruncher.dsurround.config.biome;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BiomeInfo implements Comparable<BiomeInfo>, IBiomeSoundProvider {

    public static final int DEFAULT_ADDITIONAL_SOUND_CHANCE = 1000 / 4;
    public static final Script DEFAULT_SOUND_CHANCE = new Script(String.valueOf(1D / DEFAULT_ADDITIONAL_SOUND_CHANCE));
    private static final IModLog LOGGER = ContainerManager.resolve(IModLog.class);
    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    private final int version;
    private final ResourceLocation biomeId;
    private final String biomeName;
    private final Optional<Biome> biome;
    private final ObjectArray<AcousticEntry> loopSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> moodSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> additionalSounds = new ObjectArray<>();
    private final ObjectArray<AcousticEntry> musicSounds = new ObjectArray<>();
    private final BiomeTraits traits;
    private final boolean isRiver;
    private final boolean isOcean;
    private final boolean isDeepOcean;
    private final IConditionEvaluator conditionEvaluator;
    private TextColor fogColor;
    private Script additionalSoundChance = DEFAULT_SOUND_CHANCE;
    private Script moodSoundChance = DEFAULT_SOUND_CHANCE;
    private ObjectArray<String> comments;

    public BiomeInfo(final int version, final ResourceLocation id, final String name, BiomeTraits traits) {
        this(version, id, name, traits, null);
    }

    public BiomeInfo(final int version, final ResourceLocation id, final String name, BiomeTraits traits, Biome biome) {
        this.version = version;
        this.biomeId = id;
        this.biomeName = name;
        this.biome = Optional.ofNullable(biome);

        this.traits = traits;
        this.isRiver = this.traits.contains("RIVER");
        this.isOcean = this.traits.contains("OCEAN");
        this.isDeepOcean = this.isOcean && this.traits.contains("DEEP");
        this.conditionEvaluator = ContainerManager.resolve(IConditionEvaluator.class);
    }

    public int getVersion() {
        return this.version;
    }

    public boolean isRiver() {
        return this.isRiver;
    }

    public boolean isOcean() {
        return this.isOcean;
    }

    public boolean isDeepOcean() {
        return this.isDeepOcean;
    }

    public ResourceLocation getBiomeId() {
        return this.biomeId;
    }

    void addComment(final String comment) {
        if (!StringUtils.isEmpty(comment)) {
            if (this.comments == null)
                this.comments = new ObjectArray<>();
            this.comments.add(comment);
        }
    }

    public String getBiomeName() {
        return this.biomeName;
    }

    public TextColor getFogColor() {
        return this.fogColor;
    }

    void setFogColor(final TextColor color) {
        this.fogColor = color;
    }

    public boolean getHasFog() {
        return this.fogColor != null;
    }

    void setAdditionalSoundChance(final Script chance) {
        this.additionalSoundChance = chance;
    }

    void setMoodSoundChance(final Script chance) {
        this.moodSoundChance = chance;
    }

    public BiomeTraits getTraits() {
        return this.traits;
    }

    public boolean hasTrait(BiomeTrait trait) {
        return this.traits.contains(trait);
    }

    public boolean hasTrait(String trait) {
        return this.traits.contains(trait);
    }

    @Override
    public Collection<ISoundFactory> findBiomeSoundMatches() {
        ObjectArray<ISoundFactory> results = new ObjectArray<>();
        for (final AcousticEntry sound : this.loopSounds) {
            if (sound.matches())
                results.add(sound.getAcoustic());
        }
        return results;
    }

    @Override
    public Optional<ISoundFactory> getExtraSound(final SoundEventType type, final IRandomizer random) {

        ObjectArray<AcousticEntry> sourceList = null;

        switch (type) {
            case ADDITION -> {
                var chance = this.conditionEvaluator.eval(this.additionalSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.additionalSounds : null;
                }
            }
            case MOOD -> {
                var chance = this.conditionEvaluator.eval(this.moodSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.moodSounds : null;
                }
            }
            case MUSIC -> sourceList = this.musicSounds;
            case LOOP -> sourceList = null;
        }

        if (sourceList == null || sourceList.isEmpty())
            return Optional.empty();

        var candidates = sourceList.stream().filter(AcousticEntry::matches);
        return WeightTable.makeSelection(candidates);
    }

    void clearSounds() {
        this.loopSounds.clear();
        this.additionalSounds.clear();
        this.musicSounds.clear();
        this.moodSounds.clear();
        this.moodSoundChance = DEFAULT_SOUND_CHANCE;
        this.additionalSoundChance = DEFAULT_SOUND_CHANCE;
    }

    public void update(final BiomeConfigRule entry) {

        entry.comment().ifPresent(this::addComment);
        entry.fogColor().ifPresent(this::setFogColor);
        entry.additionalSoundChance().ifPresent(this::setAdditionalSoundChance);
        entry.moodSoundChance().ifPresent(this::setMoodSoundChance);

        // Merge in any additional traits
        if (!entry.traits().isEmpty()) {
            this.traits.mergeTraits(entry.traits());
        }

        if (entry.clearSounds()) {
            addComment("> Sound Clear");
            clearSounds();
        }

        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        for (final AcousticConfig sr : entry.acoustics()) {
            final SoundEvent acoustic = soundLibrary.getSound(sr.soundEventId());
            var factory = SoundFactoryBuilder.create(acoustic)
                    .category(sr.category())
                    .volume(sr.minVolume(), sr.maxVolume())
                    .pitch(sr.minPitch(), sr.maxPitch())
                    .build();

            switch (sr.type()) {
                case LOOP -> {
                    final AcousticEntry acousticEntry = new AcousticEntry(factory, sr.conditions());
                    this.loopSounds.add(acousticEntry);
                }
                case MUSIC, MOOD, ADDITION -> {
                    final int weight = sr.weight();
                    final AcousticEntry acousticEntry = new AcousticEntry(factory, sr.conditions(), weight);

                    if (sr.type() == SoundEventType.ADDITION)
                        this.additionalSounds.add(acousticEntry);
                    else if (sr.type() == SoundEventType.MOOD)
                        this.moodSounds.add(acousticEntry);
                    else
                        this.musicSounds.add(acousticEntry);
                }
                default -> LOGGER.warn("Unknown SoundEventType %s", sr.type());
            }
        }
    }

    public void trim() {
        this.loopSounds.trim();
        this.additionalSounds.trim();
        this.moodSounds.trim();
        this.musicSounds.trim();
        if (this.comments != null)
            this.comments.trim();
    }

    @Override
    public String toString() {
        final String indent = "    ";

        var tags = this.biome.map(b -> {
                    var holder = RegistryUtils.getRegistryEntry(Registries.BIOME, b);
                    if (holder.isEmpty())
                        return "null";
                    return TAG_LIBRARY.asString(TAG_LIBRARY.streamTags(holder.get()));
                }).orElse("null");

        final StringBuilder builder = new StringBuilder();
        builder.append("Biome [").append(getBiomeName()).append('/').append(this.biomeId).append("]");
        builder.append("\nTags: ").append(tags);
        builder.append("\n").append(getTraits().toString());

        if (this.fogColor != null) {
            builder.append("\nfogColor: ").append(this.fogColor.formatValue());
        }

        if (!this.loopSounds.isEmpty()) {
            builder.append("\nLOOP sounds [\n");
            builder.append(this.loopSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
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
            builder.append("\nMOOD chance: ").append(this.additionalSoundChance);
            builder.append("\nMOOD sounds [\n");
            builder.append(this.moodSounds.stream().map(c -> indent + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        if (this.comments != null && !this.comments.isEmpty()) {
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