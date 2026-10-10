package org.orecruncher.dsurround.config.block;

import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * A block state as the configuration describes it, built by {@link BlockInfoBuilder}. Doesn't change once built; a
 * reload builds new ones.
 */
public final class BlockInfo {

    private final int version;
    // What it uses, from the library that built it: kept as one reference, as there is an info for every block state
    private final ConfigServices services;
    @Nullable
    private final ResourceLocation stepSound;
    private final AcousticEntryCollection sounds;
    private final Collection<IBlockEffectProducer> blockEffects;
    private final Script soundChance;
    private final float soundReflectivity;
    private final float soundOcclusion;

    BlockInfo(BlockInfoBuilder builder, Collection<IBlockEffectProducer> blockEffects) {
        this.version = builder.version;
        this.services = builder.services;
        this.stepSound = builder.stepSound;
        this.sounds = builder.sounds;
        this.blockEffects = blockEffects;
        this.soundChance = builder.soundChance;
        this.soundReflectivity = builder.soundReflectivity;
        this.soundOcclusion = builder.soundOcclusion;
    }

    public int getVersion() {
        return this.version;
    }

    public float getSoundReflectivity() {
        return this.soundReflectivity;
    }

    public float getSoundOcclusion() {
        return this.soundOcclusion;
    }

    public boolean hasSoundsOrEffects() {
        return !this.sounds.isEmpty() || !this.blockEffects.isEmpty();
    }

    public Optional<ISoundFactory> getSoundToPlay(final IRandomizer random) {
        // Checked before evaluating the chance script, which would otherwise run for blocks with no sounds
        if (!this.sounds.isEmpty()) {
            var chance = this.services.conditionEvaluator().eval(this.soundChance);
            if (chance instanceof Double c && random.nextDouble() < c) {
                return this.sounds.makeSelection(random);
            }
        }
        return Optional.empty();
    }

    public Collection<IBlockEffectProducer> getEffectProducers() {
        return this.blockEffects;
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("reflectivity: ")
                .append(this.soundReflectivity)
                .append("; occlusion: ")
                .append(this.soundOcclusion)
                .append("\n");

        if (this.stepSound != null) {
            builder.append("step sound: ").append(this.stepSound).append("\n");
        }

        if (!this.sounds.isEmpty()) {
            builder.append("sound chance: ").append(this.soundChance);
            builder.append("; sounds [\n");
            builder.append(this.sounds.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]\n");
        }

        if (!this.blockEffects.isEmpty()) {
            builder.append("random effects [\n");
            builder.append(
                    this.blockEffects.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]\n");
        }

        return builder.toString();
    }
}