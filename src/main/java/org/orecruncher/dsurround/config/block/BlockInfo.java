package org.orecruncher.dsurround.config.block;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Material;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.config.SoundLibrary;
import org.orecruncher.dsurround.config.biome.AcousticEntry;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.material.MaterialUtils;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class BlockInfo {

    private static final float DEFAULT_OPAQUE_OCCLUSION = 0.5F;
    private static final float DEFAULT_TRANSLUCENT_OCCLUSION = 0.15F;
    private static final float DEFAULT_REFLECTION = 0.4F;

    // Lazy init on add
    @Nullable
    protected ObjectArray<AcousticEntry> sounds;
    @Nullable
    protected ObjectArray<IBlockEffectProducer> blockEffects;
    @Nullable
    protected ObjectArray<IBlockEffectProducer> alwaysOnEffects;

    protected final int version;
    protected final Material material;

    protected Script soundChance = new Script("0.01");
    protected float soundReflectivity = DEFAULT_REFLECTION;
    protected float soundOcclusion = DEFAULT_OPAQUE_OCCLUSION;

    public BlockInfo(int version) {
        this.version = version;
        this.material = Material.AIR;
    }

    public BlockInfo(int version, BlockState state) {
        this.version = version;
        this.soundOcclusion = state.getMaterial().blocksLight() ? DEFAULT_OPAQUE_OCCLUSION : DEFAULT_TRANSLUCENT_OCCLUSION;
        this.material = state.getMaterial();
    }

    public boolean isDefault() {
        return this.sounds == null && this.blockEffects == null && this.alwaysOnEffects == null && this.soundReflectivity == DEFAULT_REFLECTION
                && (this.soundOcclusion == DEFAULT_OPAQUE_OCCLUSION || this.soundOcclusion == DEFAULT_TRANSLUCENT_OCCLUSION);
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

    private void addToSounds(AcousticEntry entry) {
        if (this.sounds == null)
            this.sounds = new ObjectArray<>(4);
        this.sounds.add(entry);
    }

    private void addToBlockEffects(IBlockEffectProducer effect) {
        if (this.blockEffects == null)
            this.blockEffects = new ObjectArray<>(2);
        this.blockEffects.add(effect);
    }

    private void addToAlwaysOnEffects(IBlockEffectProducer effect) {
        if (this.alwaysOnEffects == null)
            this.alwaysOnEffects = new ObjectArray<>(2);
        this.alwaysOnEffects.add(effect);
    }

    // TODO: Eliminate duplicates
    public void update(BlockConfigRule config) {
        // Reset of a block clears all registry
        if (config.clearSounds)
            this.clearSounds();

        config.soundChance.ifPresent(v -> this.soundChance = v);
        config.soundReflectivity.ifPresent(v ->this.soundReflectivity = v);
        config.soundOcclusion.ifPresent(v ->this.soundOcclusion = v);

        for (final AcousticConfig sr : config.acoustics) {
            if (sr.soundEventId != null) {
                final Identifier res = SoundLibrary.resolveIdentifier(Client.ModId, sr.soundEventId);
                final SoundEvent acoustic = SoundLibrary.getSound(res);
                final AcousticEntry acousticEntry = new AcousticEntry(acoustic, sr.conditions, sr.weight);
                this.addToSounds(acousticEntry);
            }
        }

        for (var e : config.effects) {
            var effect = e.effect.getInstance(e.spawnChance, e.conditions);
            effect.ifPresent(t -> {
                if (e.alwaysOn)
                    this.addToAlwaysOnEffects(t);
                else
                    this.addToBlockEffects(t);
            });
        }
    }

    private void clearSounds() {
        if (this.sounds != null)
            this.sounds.clear();
    }

    public boolean hasSoundsOrEffects() {
        return this.sounds != null || this.blockEffects != null;
    }

    public boolean hasAlwaysOnEffects() {
        return this.alwaysOnEffects != null;
    }

    public ISoundFactory getSoundToPlay(final Random random) {
        if (this.sounds != null) {
            var chance = ConditionEvaluator.INSTANCE.eval(this.soundChance);
            if (chance instanceof Double c && random.nextDouble() < c) {
                var candidates = this.sounds.stream().filter(AcousticEntry::matches);
                return WeightTable.makeSelection(candidates);
            }
        }
        return null;
    }

    public Collection<IBlockEffectProducer> getEffectProducers() {
        return this.blockEffects == null ? ImmutableList.of() : this.blockEffects;
    }

    public Collection<IBlockEffectProducer> getAlwaysOnEffectProducers() {
        return this.alwaysOnEffects == null ? ImmutableList.of() : this.alwaysOnEffects;
    }

    public void trim() {
        if (this.sounds != null) {
            if (sounds.size() == 0)
                this.sounds = null;
            else
                this.sounds.trim();
        }
        if (this.alwaysOnEffects != null) {
            if (alwaysOnEffects.size() == 0)
                this.alwaysOnEffects = null;
            else
                this.alwaysOnEffects.trim();
        }
        if (this.blockEffects != null) {
            if (blockEffects.size() == 0)
                this.blockEffects = null;
            else
                this.blockEffects.trim();
        }
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("material: ")
                .append(MaterialUtils.getMaterialName(this.material));

        builder.append("; reflectivity: ")
                .append(this.soundReflectivity)
                .append("; occlusion: ")
                .append(this.soundOcclusion)
                .append("\n");

        if (this.sounds != null) {
            builder.append("sound chance: ").append(this.soundChance);
            builder.append("; sounds [\n");
            builder.append(this.sounds.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]\n");
        }

        if (this.blockEffects != null) {
            builder.append("random effects [\n");
            builder.append(this.blockEffects.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]\n");
        }

        if (this.alwaysOnEffects != null) {
            builder.append("always on effects [\n");
            builder.append(this.alwaysOnEffects.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        return builder.toString();
    }
}