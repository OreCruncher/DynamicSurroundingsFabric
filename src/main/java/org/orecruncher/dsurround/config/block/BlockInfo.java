package org.orecruncher.dsurround.config.block;

import com.google.common.collect.ImmutableList;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.AcousticConfig;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.biome.AcousticEntry;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.OcclusionTags;
import org.orecruncher.dsurround.tags.ReflectanceTags;

import java.util.Collection;
import java.util.Random;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class BlockInfo {

    private static final float DEFAULT_OPAQUE_OCCLUSION = 0.5F;
    private static final float DEFAULT_TRANSLUCENT_OCCLUSION = 0.15F;
    private static final float DEFAULT_REFLECTION = 0.0F; //0.4F;

    // Lazy init on add
    @Nullable
    protected ObjectArray<AcousticEntry> sounds;
    @Nullable
    protected ObjectArray<IBlockEffectProducer> blockEffects;
    @Nullable
    protected ObjectArray<IBlockEffectProducer> alwaysOnEffects;

    protected final int version;
    protected final IConditionEvaluator conditionEvaluator;

    protected Script soundChance = new Script("0.01");
    protected float soundReflectivity = DEFAULT_REFLECTION;
    protected float soundOcclusion = DEFAULT_OPAQUE_OCCLUSION;

    public BlockInfo(int version) {
        this.version = version;
        this.conditionEvaluator = null;
    }

    public BlockInfo(int version, BlockState state, IConditionEvaluator conditionEvaluator) {
        this.version = version;
        this.conditionEvaluator = conditionEvaluator;
        this.soundOcclusion = getSoundOcclusionSetting(state);
        this.soundReflectivity = getSoundReflectionSetting(state);
    }

    public boolean isDefault() {
        return this.sounds == null && this.blockEffects == null && this.alwaysOnEffects == null
                && this.soundReflectivity == DEFAULT_REFLECTION
                && (this.soundOcclusion == DEFAULT_OPAQUE_OCCLUSION
                        || this.soundOcclusion == DEFAULT_TRANSLUCENT_OCCLUSION);
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
        if (config.clearSounds())
            this.clearSounds();

        config.soundChance().ifPresent(v -> this.soundChance = v);

        var soundLibrary = ContainerManager.resolve(ISoundLibrary.class);

        for (final AcousticConfig sr : config.acoustics()) {
            if (sr.soundEventId != null) {
                final Identifier res = IdentityUtils.resolveIdentifier(Client.ModId, sr.soundEventId);
                final SoundEvent acoustic = soundLibrary.getSound(res);
                var factory = SoundFactoryBuilder.create(acoustic)
                        .category(sr.category)
                        .volumeRange(sr.minVolume, sr.maxVolume)
                        .pitchRange(sr.minPitch, sr.maxPitch)
                        .build();
                final AcousticEntry acousticEntry = new AcousticEntry(factory, sr.conditions, sr.weight);
                this.addToSounds(acousticEntry);
            }
        }

        for (var e : config.effects()) {
            var effect = e.effect().createInstance(e.spawnChance(), e.conditions());
            effect.ifPresent(t -> {
                if (e.alwaysOn())
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
            var chance = this.conditionEvaluator.eval(this.soundChance);
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
            if (this.sounds.isEmpty())
                this.sounds = null;
            else
                this.sounds.trim();
        }
        if (this.alwaysOnEffects != null) {
            if (alwaysOnEffects.isEmpty())
                this.alwaysOnEffects = null;
            else
                this.alwaysOnEffects.trim();
        }
        if (this.blockEffects != null) {
            if (blockEffects.isEmpty())
                this.blockEffects = null;
            else
                this.blockEffects.trim();
        }
    }

    private static float getSoundReflectionSetting(BlockState state) {
        if (state.isIn(ReflectanceTags.NONE))
            return 0;
        if (state.isIn(ReflectanceTags.VERY_LOW))
            return 0.15F;
        if (state.isIn(ReflectanceTags.LOW))
            return 0.35F;
        if (state.isIn(ReflectanceTags.MEDIUM))
            return 0.5F;
        if (state.isIn(ReflectanceTags.HIGH))
            return 0.65F;
        if (state.isIn(ReflectanceTags.VERY_HIGH))
            return 0.8F;
        if (state.isIn(ReflectanceTags.MAX))
            return 1.0F;
        return DEFAULT_REFLECTION;
    }

    private static float getSoundOcclusionSetting(BlockState state) {
        if (state.isIn(OcclusionTags.NONE))
            return 0;
        if (state.isIn(OcclusionTags.VERY_LOW))
            return 0.15F;
        if (state.isIn(OcclusionTags.LOW))
            return 0.35F;
        if (state.isIn(OcclusionTags.MEDIUM))
            return 0.5F;
        if (state.isIn(OcclusionTags.HIGH))
            return 0.65F;
        if (state.isIn(OcclusionTags.VERY_HIGH))
            return 0.8F;
        if (state.isIn(OcclusionTags.MAX))
            return 1.0F;
        return state.isOpaque() ? DEFAULT_OPAQUE_OCCLUSION : DEFAULT_TRANSLUCENT_OCCLUSION;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("reflectivity: ")
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
            builder.append(
                    this.blockEffects.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]\n");
        }

        if (this.alwaysOnEffects != null) {
            builder.append("always on effects [\n");
            builder.append(
                    this.alwaysOnEffects.stream().map(c -> "    " + c.toString()).collect(Collectors.joining("\n")));
            builder.append("\n]");
        }

        return builder.toString();
    }
}