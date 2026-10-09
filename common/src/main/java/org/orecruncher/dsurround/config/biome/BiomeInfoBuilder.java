package org.orecruncher.dsurround.config.biome;

import com.google.common.base.Preconditions;
import dev.architectury.hooks.level.biome.BiomeHooks;
import dev.architectury.hooks.level.biome.BiomeProperties;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntry;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.processing.fog.FogDensity;

/**
 * Applies the configuration rules for a biome and then builds its {@link BiomeInfo}. Trait rules come first, as the
 * selectors of the other rules can depend on traits. Used once: after {@link #build()} it can't be changed.
 */
public final class BiomeInfoBuilder implements IBiomeIdentity {

    public static final Script DEFAULT_SOUND_CHANCE = new Script("0.008");

    final int version;
    final ConfigServices services;
    final Identifier biomeId;
    final String biomeName;
    @Nullable
    final Biome biome;
    @Nullable
    final BiomeProperties properties;
    final BiomeTraits traits;
    final AcousticEntryCollection loopSounds = new AcousticEntryCollection();
    final AcousticEntryCollection moodSounds = new AcousticEntryCollection();
    final AcousticEntryCollection additionalSounds = new AcousticEntryCollection();
    final AcousticEntryCollection musicSounds = new AcousticEntryCollection();
    final ObjectArray<String> comments = new ObjectArray<>();
    @Nullable
    TextColor fogColor;
    FogDensity fogDensity = FogDensity.NONE;
    Script additionalSoundChance = DEFAULT_SOUND_CHANCE;
    Script moodSoundChance = DEFAULT_SOUND_CHANCE;
    private boolean built;

    /**
     * For a synthetic biome, which has no game biome behind it
     */
    public BiomeInfoBuilder(final int version, final Identifier id, final String name, BiomeTraits traits, ConfigServices services) {
        this(version, id, name, traits, null, services);
    }

    /**
     * @param services what the info uses, from the library that builds it
     */
    public BiomeInfoBuilder(final int version, final Identifier id, final String name, BiomeTraits traits, @Nullable Biome biome, ConfigServices services) {
        this.version = version;
        this.services = services;
        this.biomeId = id;
        this.biomeName = name;
        this.biome = biome;
        this.traits = traits;
        this.properties = biome != null ? BiomeHooks.getBiomeProperties(biome) : null;
    }

    @Override
    public Identifier getBiomeId() {
        return this.biomeId;
    }

    @Override
    public String getBiomeName() {
        return this.biomeName;
    }

    @Override
    public float getDownfall() {
        return downfall(this.properties);
    }

    static float downfall(@Nullable BiomeProperties properties) {
        return properties != null ? properties.getClimateProperties().getDownfall() : 0.0F;
    }

    @Override
    public BiomeTraits getTraits() {
        return this.traits;
    }

    public void mergeTraits(BiomeConfigRule configRule) {
        this.checkNotBuilt();
        if (configRule.clearTraits())
            this.traits.clear();
        this.traits.merge(configRule.traits());
        configRule.comment().ifPresent(this::addComment);
    }

    public void apply(final BiomeConfigRule entry) {
        this.checkNotBuilt();

        // If configured, reset the fog color. This will only reset the
        // Dynamic Surrounding fog color - the underlying fog color from
        // data packs will still apply.
        if (entry.resetFogColor()) {
            this.addComment("> Reset Fog");
            this.fogColor = null;
        }

        entry.comment().ifPresent(this::addComment);
        entry.fogColor().ifPresent(color -> this.fogColor = color);
        entry.fogDensity().ifPresent(density -> this.fogDensity = density);
        entry.additionalSoundChance().ifPresent(chance -> this.additionalSoundChance = chance);
        entry.moodSoundChance().ifPresent(chance -> this.moodSoundChance = chance);

        // NOTE: Traits are not merged here - that has already been done by mergeTraits()

        if (entry.clearSounds()) {
            this.addComment("> Sound Clear");
            this.clearSounds();
        }

        for (final AcousticConfig sr : entry.acoustics()) {
            var factory = this.services.soundLibrary().getSoundFactoryOrDefault(sr.factory());
            var evaluator = this.services.conditionEvaluator();

            AcousticEntryCollection targetCollection;
            AcousticEntry acousticEntry;

            switch (sr.type()) {
                case LOOP -> {
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), evaluator);
                    targetCollection = this.loopSounds;
                }
                case MUSIC -> {
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), sr.weight(), evaluator);
                    targetCollection = this.musicSounds;
                }
                case MOOD -> {
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), sr.weight(), evaluator);
                    targetCollection = this.moodSounds;
                }
                case ADDITION -> {
                    acousticEntry = new AcousticEntry(factory, sr.conditions(), sr.weight(), evaluator);
                    targetCollection = this.additionalSounds;
                }
                default -> {
                    this.services.logger().warn("[%s] Unknown SoundEventType %s", this.biomeName, sr.type());
                    continue;
                }
            }

            if (!targetCollection.add(acousticEntry))
                this.services.logger().warn("[%s] Duplicate acoustic entry: %s", this.biomeName, sr.toString());
        }
    }

    /**
     * The finished info. The builder can't be used after this.
     */
    public BiomeInfo build() {
        this.checkNotBuilt();
        this.built = true;

        // Reduce memory consumption as much as possible
        this.loopSounds.trim();
        this.moodSounds.trim();
        this.additionalSounds.trim();
        this.musicSounds.trim();
        this.comments.trim();

        return new BiomeInfo(this);
    }

    private void addComment(final String comment) {
        if (!StringUtils.isEmpty(comment))
            this.comments.add(comment);
    }

    private void clearSounds() {
        this.loopSounds.clear();
        this.additionalSounds.clear();
        this.musicSounds.clear();
        this.moodSounds.clear();
        this.moodSoundChance = DEFAULT_SOUND_CHANCE;
        this.additionalSoundChance = DEFAULT_SOUND_CHANCE;
    }

    private void checkNotBuilt() {
        Preconditions.checkState(!this.built, "BiomeInfo for %s has already been built", this.biomeId);
    }
}
