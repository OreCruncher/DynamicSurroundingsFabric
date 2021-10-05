package org.orecruncher.dsurround.config;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.biome.Biome;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.gui.Color;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
@Environment(EnvType.CLIENT)
public final class BiomeInfo implements Comparable<BiomeInfo> {

    private static final IModLog LOGGER = Client.LOGGER.createChild(BiomeInfo.class);

    private final static float DEFAULT_VISIBILITY = 1F;

    public final static int DEFAULT_ADDITIONAL_SOUND_CHANCE = 1000 / 4;

    private final Biome biome;
    private final Identifier biomeId;
    private final String biomeName;

    private Color fogColor;
    private float visibility = DEFAULT_VISIBILITY;

    private int additionalSoundChance = DEFAULT_ADDITIONAL_SOUND_CHANCE;

    private final ObjectArray<AcousticEntry> biomeSounds = new ObjectArray<>();
    private final ObjectArray<WeightedAcousticEntry> additionalSounds = new ObjectArray<>();
    private final ObjectArray<WeightedAcousticEntry> moodSounds = new ObjectArray<>();
    private final ObjectArray<String> traits = new ObjectArray<>();

    private ObjectArray<String> comments;

    private final boolean isRiver;
    private final boolean isOcean;
    private final boolean isDeepOcean;

    public BiomeInfo(final Biome biome) {
        this.biome = biome;
        this.biomeId = BiomeLibrary.getBiomeId(biome);
        this.biomeName = BiomeLibrary.getBiomeName(biome);

        this.traits.addAll(BiomeLibrary.getBiomeTraits(biome));
        this.isRiver = this.biome.getCategory() == Biome.Category.RIVER;
        this.isOcean = this.biome.getCategory() == Biome.Category.OCEAN;
        this.isDeepOcean = this.isOcean && this.traits.contains("deep");
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

    public Identifier getKey() {
        return this.biomeId;
    }

    public Biome getBiome() {
        return this.biome;
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

    public Biome.Precipitation getPrecipitationType() {
        return this.biome.getPrecipitation();
    }

    public Color getFogColor() {
        return this.fogColor;
    }

    void setFogColor(final Color color) {
        this.fogColor = color;
    }

    public boolean getHasFog() {
        return this.fogColor != null;
    }

    public float getVisibility() {
        return this.visibility;
    }

    void setVisibility(final float density) {
        this.visibility = MathHelper.clamp(density, 0, 1F);
    }

    void setAdditionalSoundChance(final int chance) {
        this.additionalSoundChance = chance;
    }

    public float getFloatTemperature(final BlockPos pos) {
        return this.biome.getTemperature(pos);
    }

    public float getTemperature() {
        return this.biome.getTemperature();
    }

    public boolean isHighHumidity() {
        return this.biome.hasHighHumidity();
    }

    public float getRainfall() {
        return this.biome.getDownfall();
    }

    public Collection<String> getTraits() {
        return this.traits;
    }

    public Collection<SoundEvent> findBiomeSoundMatches() {
        return findBiomeSoundMatches(new ObjectArray<>());
    }

    public Collection<SoundEvent> findBiomeSoundMatches(final Collection<SoundEvent> results) {
        for (final AcousticEntry sound : this.biomeSounds) {
            if (sound.matches())
                results.add(sound.getAcoustic());
        }
        return results;
    }

    @Nullable
    public SoundEvent getAdditionalSound(final Random random) {
        if (this.additionalSounds.size() == 0 || random.nextInt(this.additionalSoundChance) != 0)
            return null;
        var candidates = this.additionalSounds.stream().filter(AcousticEntry::matches).collect(Collectors.toList());
        return new WeightTable<>(candidates).next();
    }

    void clearSounds() {
        this.biomeSounds.clear();
        this.additionalSounds.clear();
        this.additionalSoundChance = DEFAULT_ADDITIONAL_SOUND_CHANCE;
    }

    public void update(final BiomeConfigRule entry) {
        addComment(entry.comment);

        if (entry.visibility != null)
            setVisibility(entry.visibility);

        if (entry.fogColor != null) {
            setFogColor(Color.parse(entry.fogColor));
        }

        if (entry.clearSounds) {
            addComment("> Sound Clear");
            clearSounds();
        }

        if (entry.additionalSoundChance != null)
            setAdditionalSoundChance(entry.additionalSoundChance);

        for (final AcousticConfig sr : entry.acoustics) {
            final Identifier res = SoundLibrary.resolveIdentifier(Client.ModId, sr.soundEventId);
            final SoundEvent acoustic = SoundLibrary.getSound(res);

            switch (sr.type) {
                case LOOP: {
                    final AcousticEntry acousticEntry = new AcousticEntry(acoustic, sr.conditions);
                    this.biomeSounds.add(acousticEntry);
                }
                break;
                case MOOD:
                case ADDITION: {
                    final int weight = sr.weight;
                    final WeightedAcousticEntry acousticEntry = new WeightedAcousticEntry(acoustic, sr.conditions, weight);
                    if (sr.type == SoundEventType.ADDITION)
                        this.additionalSounds.add(acousticEntry);
                    else
                        this.moodSounds.add(acousticEntry);
                }
                break;
                case MUSIC:
                    break;

                default:
                    LOGGER.warn("Unknown SoundEventType %s", sr.type);
            }
        }
    }

    public void trim() {
        this.biomeSounds.trim();
        this.additionalSounds.trim();
        this.moodSounds.trim();
        this.comments = null;
    }

    @Override

    public String toString() {
        final Identifier rl = this.biomeId;
        final String registryName = rl == null ? ("UNKNOWN") : rl.toString();

        final StringBuilder builder = new StringBuilder();
        builder.append("Biome [").append(getBiomeName()).append('/').append(registryName).append("]");
        builder.append("+ temp: ").append(getTemperature());
        builder.append(" rain: ").append(getRainfall());

        if (this.fogColor != null) {
            builder.append(" fogColor:").append(this.fogColor);
        }

        builder.append(" visibility:").append(this.visibility);

        if (this.biomeSounds.size() > 0) {
            builder.append("\n+ biome sounds [\n");
            builder.append(this.biomeSounds.stream().map(c -> "+   " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n+ ]");
        }

        if (this.additionalSounds.size() > 0) {
            builder.append("\n+ additional sound chance:").append(this.additionalSoundChance);
            builder.append("\n+ additional sounds [\n");
            builder.append(this.additionalSounds.stream().map(c -> "+   " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n+ ]");
        }

        if (this.moodSounds.size() > 0) {
            builder.append("\n+ mood sounds [\n");
            builder.append(this.moodSounds.stream().map(c -> "+   " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n+ ]");
        }

        if (this.comments != null && this.comments.size() > 0) {
            builder.append("\n+ comments:\n");
            builder.append(this.comments.stream().map(c -> "+   " + c).collect(Collectors.joining("\n")));
            builder.append('\n');
        }

        return builder.toString();
    }

    @Override
    public int compareTo(final BiomeInfo o) {
        return getBiomeName().compareTo(o.getBiomeName());
    }
}