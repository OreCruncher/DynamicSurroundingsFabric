package org.orecruncher.dsurround.config.biome;

import dev.architectury.hooks.level.biome.BiomeHooks;
import dev.architectury.hooks.level.biome.BiomeProperties;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntry;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.SoundEventType;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.weighted.WeightValue;
import org.orecruncher.dsurround.processing.fog.FogDensity;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public final class BiomeInfo implements Comparable<BiomeInfo>, IBiomeSoundProvider {

    public static final Script DEFAULT_SOUND_CHANCE = new Script("0.008");

    private final int version;
    private final ConfigServices services;
    private final Identifier biomeId;
    private final String biomeName;
    @Nullable
    private final Biome biome;
    @Nullable
    private final BiomeProperties properties;
    private final BiomeTraits traits;
    private final boolean isRiver;
    private final boolean isOcean;
    private final boolean isDeepOcean;
    private final boolean isCave;
    private final AcousticEntryCollection loopSounds = new AcousticEntryCollection();
    private final AcousticEntryCollection moodSounds = new AcousticEntryCollection();
    private final AcousticEntryCollection additionalSounds = new AcousticEntryCollection();
    private final AcousticEntryCollection musicSounds = new AcousticEntryCollection();
    private final ObjectArray<String> comments = new ObjectArray<>();
    private TextColor fogColor;
    private FogDensity fogDensity;
    private Script additionalSoundChance = DEFAULT_SOUND_CHANCE;
    private Script moodSoundChance = DEFAULT_SOUND_CHANCE;
    @Nullable
    private AcousticEntryCollection musicChoices;
    @Nullable
    private Music musicChoicesVanilla;
    @Nullable
    private Music chosenMusic;
    @Nullable
    private Music chosenMusicVanilla;

    public BiomeInfo(final int version, final Identifier id, final String name, BiomeTraits traits, ConfigServices services) {
        this(version, id, name, traits, null, services);
    }

    /**
     * @param services what it uses, from the library that builds it
     */
    public BiomeInfo(final int version, final Identifier id, final String name, BiomeTraits traits, @Nullable Biome biome, ConfigServices services) {
        this.version = version;
        this.services = services;
        this.biomeId = id;
        this.biomeName = name;
        this.biome = biome;

        this.traits = traits;
        this.isRiver = this.traits.contains(BiomeTrait.RIVER);
        this.isOcean = this.traits.contains(BiomeTrait.OCEAN);
        this.isDeepOcean = this.traits.contains(BiomeTrait.DEEP_OCEAN);
        this.isCave = this.traits.contains(BiomeTrait.CAVE);

        this.fogDensity = FogDensity.NONE;

        // Fetch biome properties if a Biome is provided, and perform
        // other necessary dependent work.
        this.properties = this.biome != null ? BiomeHooks.getBiomeProperties(this.biome) : null;
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

    public boolean isCave() {
        return this.isCave;
    }

    public Identifier getBiomeId() {
        return this.biomeId;
    }

    void addComment(final String comment) {
        if (!StringUtils.isEmpty(comment)) {
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

    public FogDensity getFogDensity() {
        return this.fogDensity;
    }

    public void setFogDensity(final FogDensity density) {
        this.fogDensity = density;
    }

    public float getDownfall() {
        return this.properties != null ? this.properties.getClimateProperties().getDownfall() : 0.0F;
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

    public void mergeTraits(BiomeConfigRule configRule) {
        if (configRule.clearTraits())
            this.traits.clear();
        this.traits.merge(configRule.traits());
        configRule.comment().ifPresent(this::addComment);
    }

    public boolean hasTrait(String trait) {
        return this.traits.contains(trait);
    }

    public boolean hasTrait(BiomeTrait trait) {
        return this.traits.contains(trait);
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

        switch (type) {
            case ADDITION -> {
                var chance = this.services.conditionEvaluator().eval(this.additionalSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.additionalSounds : null;
                }
            }
            case MOOD -> {
                var chance = this.services.conditionEvaluator().eval(this.moodSoundChance);
                if (chance instanceof Double c) {
                    sourceList = random.nextDouble() < c ? this.moodSounds : null;
                }
            }
            case MUSIC -> sourceList = this.musicSounds;
        }

        return sourceList == null ? Optional.empty() : sourceList.makeSelection(random);
    }

    @Override
    public Optional<Music> getBackgroundMusic(Optional<Music> vanilla, IRandomizer random, boolean chooseAgain) {
        var offered = vanilla.orElse(null);
        if (chooseAgain || this.chosenMusic == null || this.chosenMusicVanilla != offered) {
            var choices = offered != null ? this.musicChoicesWith(offered) : this.musicSounds;
            this.chosenMusic = choices.makeSelection(random).map(ISoundFactory::createAsMusic).orElse(null);
            this.chosenMusicVanilla = offered;
        }
        return Optional.ofNullable(this.chosenMusic);
    }

    /**
     * The configured music plus the game's track for the biome, built when first asked for and kept while the game
     * offers the same track.
     */
    private AcousticEntryCollection musicChoicesWith(Music vanilla) {
        if (this.musicChoices == null || this.musicChoicesVanilla != vanilla) {
            var choices = new AcousticEntryCollection();
            var factory = this.services.soundLibrary().getSoundFactoryForMusic(vanilla);
            choices.add(new AcousticEntry(factory, null, this.services.conditionEvaluator()));
            for (var entry : this.musicSounds)
                choices.add(entry);
            choices.trim();
            this.musicChoices = choices;
            this.musicChoicesVanilla = vanilla;
        }
        return this.musicChoices;
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

        // The music may change, so the choices with the game's track are built again when next asked for
        this.musicChoices = null;
        this.chosenMusic = null;

        // If configured, reset the fog color. This will only reset the
        // Dynamic Surrounding fog color - the underlying fog color from
        // data packs will still apply.
        if (entry.resetFogColor()) {
            addComment("> Reset Fog");
            this.setFogColor(null);
        }

        entry.comment().ifPresent(this::addComment);
        entry.fogColor().ifPresent(this::setFogColor);
        entry.fogDensity().ifPresent(this::setFogDensity);
        entry.additionalSoundChance().ifPresent(this::setAdditionalSoundChance);
        entry.moodSoundChance().ifPresent(this::setMoodSoundChance);

        // NOTE: We do not merge in traits here - it has already
        // been done prior to this point.

        if (entry.clearSounds()) {
            addComment("> Sound Clear");
            clearSounds();
        }

        for (final AcousticConfig sr : entry.acoustics()) {
            var factory = this.services.soundLibrary().getSoundFactoryOrDefault(sr.factory());

            Collection<AcousticEntry> targetCollection = null;
            AcousticEntry acousticEntry = null;

            switch (sr.type()) {
                case LOOP -> {
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), this.services.conditionEvaluator());
                    targetCollection = this.loopSounds;
                }
                case MUSIC, MOOD, ADDITION -> {
                    final WeightValue weight = sr.weight();
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), weight, this.services.conditionEvaluator());

                    if (sr.type() == SoundEventType.ADDITION)
                        targetCollection = this.additionalSounds;
                    else if (sr.type() == SoundEventType.MOOD)
                        targetCollection = this.moodSounds;
                    else
                        targetCollection = this.musicSounds;
                }
                default -> this.services.logger().warn("[%s] Unknown SoundEventType %s", this.getBiomeName(), sr.type());
            }

            // Add if we have a target collection and it is not present
            if (targetCollection != null) {
                if (!targetCollection.add(acousticEntry))
                    this.services.logger().warn("[%s] Duplicate acoustic entry: %s", this.getBiomeName(), sr.toString());
            }
        }
    }

    public void trim() {
        this.loopSounds.trim();
        this.moodSounds.trim();
        this.additionalSounds.trim();
        this.musicSounds.trim();
        this.comments.trim();
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
            builder.append("\nMOOD chance: ").append(this.additionalSoundChance);
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