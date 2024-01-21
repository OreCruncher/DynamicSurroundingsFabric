package org.orecruncher.dsurround.config.block;

import com.google.common.collect.ImmutableList;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.biome.AcousticEntry;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.WeightTable;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.OcclusionTags;
import org.orecruncher.dsurround.tags.ReflectanceTags;

import java.util.Collection;
import java.util.Optional;
import java.util.stream.Collectors;

public class BlockInfo {

    private static final IConditionEvaluator CONDITION_EVALUATOR = ContainerManager.resolve(IConditionEvaluator.class);

    private static class Occlusion {
        public static final float NONE = 0;
        public static final float VERY_LOW = 0.15F;
        public static final float LOW = 0.35F;
        public static final float MEDIUM = 0.5F;
        public static final float HIGH = 0.65F;
        public static final float VERY_HIGH = 0.8F;
        public static final float MAX = 1.0F;
        public static final float VIBRATION = HIGH;
        public static final float DEFAULT = MEDIUM;
        public static final float DEFAULT_TRANSLUCENT = VERY_LOW;
    }

    private static class Reflectance {
        public static final float NONE = 0;
        public static final float VERY_LOW = 0.15F;
        public static final float LOW = 0.35F;
        public static final float MEDIUM = 0.5F;
        public static final float HIGH = 0.65F;
        public static final float VERY_HIGH = 0.8F;
        public static final float MAX = 1.0F;
        public static final float VIBRATION = LOW;
        public static final float DEFAULT = LOW;
    }

    private static final ISoundLibrary SOUND_LIBRARY = ContainerManager.resolve(ISoundLibrary.class);
    private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

    protected final int version;
    protected Collection<AcousticEntry> sounds = new ObjectArray<>();
    protected Collection<IBlockEffectProducer> blockEffects = new ObjectArray<>();

    protected Script soundChance = new Script("0.01");
    protected float soundReflectivity = Reflectance.DEFAULT;
    protected float soundOcclusion = Occlusion.DEFAULT;

    public BlockInfo(int version) {
        this.version = version;
    }

    public BlockInfo(int version, BlockState state) {
        this.version = version;
        this.soundOcclusion = getSoundOcclusionSetting(state);
        this.soundReflectivity = getSoundReflectionSetting(state);
    }

    public boolean isDefault() {
        return this.sounds == null && this.blockEffects == null
                && this.soundReflectivity == Reflectance.DEFAULT
                && (this.soundOcclusion == Occlusion.DEFAULT
                        || this.soundOcclusion == Occlusion.DEFAULT_TRANSLUCENT);
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
        this.sounds.add(entry);
    }

    private void addToBlockEffects(IBlockEffectProducer effect) {
        this.blockEffects.add(effect);
    }

    // TODO: Eliminate duplicates
    public void update(BlockConfigRule config) {
        // Reset of a block clears all registries
        if (config.clearSounds())
            this.clearSounds();

        config.soundChance().ifPresent(v -> this.soundChance = v);

        for (final AcousticConfig sr : config.acoustics()) {
            var factory = SOUND_LIBRARY.getSoundFactoryOrDefault(sr.factory());
            final AcousticEntry acousticEntry = new AcousticEntry(factory, sr.conditions(), sr.weight());
            this.addToSounds(acousticEntry);
        }

        for (var e : config.effects()) {
            var effect = e.effect().createInstance(e.spawnChance(), e.conditions());
            effect.ifPresent(this::addToBlockEffects);
        }
    }

    private void clearSounds() {
        if (this.sounds != null)
            this.sounds.clear();
    }

    public boolean hasSoundsOrEffects() {
        return this.sounds != null || this.blockEffects != null;
    }

    public Optional<ISoundFactory> getSoundToPlay(final IRandomizer random) {
        if (this.sounds != null) {
            var chance = CONDITION_EVALUATOR.eval(this.soundChance);
            if (chance instanceof Double c && random.nextDouble() < c) {
                var candidates = this.sounds.stream().filter(AcousticEntry::matches);
                return WeightTable.makeSelection(candidates);
            }
        }
        return Optional.empty();
    }

    public Collection<IBlockEffectProducer> getEffectProducers() {
        return this.blockEffects;
    }

    public void trim() {
        if (this.sounds.isEmpty()) {
            this.sounds = ImmutableList.of();
        }
        if (this.blockEffects.isEmpty()) {
            this.blockEffects = ImmutableList.of();
        }
    }

    private static float getSoundReflectionSetting(BlockState state) {
        if (TAG_LIBRARY.is(ReflectanceTags.NONE, state))
            return Reflectance.NONE;
        if (TAG_LIBRARY.is(ReflectanceTags.VERY_LOW, state))
            return Reflectance.VERY_LOW;
        if (TAG_LIBRARY.is(ReflectanceTags.LOW, state))
            return Reflectance.LOW;
        if (TAG_LIBRARY.is(ReflectanceTags.MEDIUM, state))
            return Reflectance.MEDIUM;
        if (TAG_LIBRARY.is(ReflectanceTags.HIGH, state))
            return Reflectance.HIGH;
        if (TAG_LIBRARY.is(ReflectanceTags.VERY_HIGH, state))
            return Reflectance.VERY_HIGH;
        if (TAG_LIBRARY.is(ReflectanceTags.MAX, state))
            return Reflectance.MAX;

        return estimateReflectance(state);
    }

    private static float estimateReflectance(BlockState state) {

        Float result = null;

        if (TAG_LIBRARY.is(BlockTags.FLOWERS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.FENCES, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.FENCE_GATES, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.BEDS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.TRAPDOORS, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.BANNERS, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.LEAVES, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.WOOL, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.WOOL_CARPETS, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.BUTTONS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.DOORS, state))
            result = Reflectance.LOW;
        else if (TAG_LIBRARY.is(BlockTags.LOGS, state))
            result = Reflectance.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.TERRACOTTA, state))
            result = Reflectance.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.ICE, state))
            result = Reflectance.LOW;
        else if (TAG_LIBRARY.is(BlockTags.SIGNS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.CROPS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.CAULDRONS, state))
            result = Reflectance.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.SAPLINGS, state))
            result = Reflectance.NONE;
        else if (TAG_LIBRARY.is(BlockTags.DAMPENS_VIBRATIONS, state))
            result = Reflectance.VIBRATION;
        else if (TAG_LIBRARY.is(BlockTags.SWORD_EFFICIENT, state))
            result = Reflectance.NONE;

        if (result == null) {
            var pathString = state.getBlockHolder().unwrapKey().map(k -> k.location().getPath()).orElse(null);
            if (pathString != null) {
                if (pathString.contains("panes") || pathString.contains("wall"))
                    result = Reflectance.LOW;
                else if (pathString.contains("glass") || pathString.contains("dripstone"))
                    result = Reflectance.MEDIUM;
                else if (pathString.contains("cobble") || pathString.contains("deepslate"))
                    result = Reflectance.HIGH;
                else if (pathString.contains("stone") || pathString.contains("infested"))
                    result = Reflectance.MAX;
            }
        }

        if (result == null)
            result = Reflectance.DEFAULT;

        return result;
    }

    private static float getSoundOcclusionSetting(BlockState state) {
        if (TAG_LIBRARY.is(OcclusionTags.NONE, state))
            return Occlusion.NONE;
        if (TAG_LIBRARY.is(OcclusionTags.VERY_LOW, state))
            return Occlusion.VERY_LOW;
        if (TAG_LIBRARY.is(OcclusionTags.LOW, state))
            return Occlusion.LOW;
        if (TAG_LIBRARY.is(OcclusionTags.MEDIUM, state))
            return Occlusion.MEDIUM;
        if (TAG_LIBRARY.is(OcclusionTags.HIGH, state))
            return Occlusion.HIGH;
        if (TAG_LIBRARY.is(OcclusionTags.VERY_HIGH, state))
            return Occlusion.VERY_HIGH;
        if (TAG_LIBRARY.is(OcclusionTags.MAX, state))
            return Occlusion.MAX;

        return estimateOcclusion(state);
    }

    private static float estimateOcclusion(BlockState state) {

        Float result = null;

        if (TAG_LIBRARY.is(BlockTags.FLOWERS, state))
            result = Occlusion.NONE;
        else if (TAG_LIBRARY.is(BlockTags.FENCES, state))
            result = Occlusion.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.FENCE_GATES, state))
            result = Occlusion.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.BEDS, state))
            result = Occlusion.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.TRAPDOORS, state))
            result = Occlusion.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.BANNERS, state))
            result = Occlusion.VERY_LOW;
        else if (TAG_LIBRARY.is(BlockTags.LEAVES, state))
            result = Occlusion.LOW;
        else if (TAG_LIBRARY.is(BlockTags.WOOL, state))
            result = Occlusion.MAX;
        else if (TAG_LIBRARY.is(BlockTags.WOOL_CARPETS, state))
            result = Occlusion.HIGH;
        else if (TAG_LIBRARY.is(BlockTags.BUTTONS, state))
            result = Occlusion.NONE;
        else if (TAG_LIBRARY.is(BlockTags.DOORS, state))
            result = Occlusion.LOW;
        else if (TAG_LIBRARY.is(BlockTags.LOGS, state))
            result = Occlusion.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.TERRACOTTA, state))
            result = Occlusion.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.ICE, state))
            result = Occlusion.LOW;
        else if (TAG_LIBRARY.is(BlockTags.SIGNS, state))
            result = Occlusion.NONE;
        else if (TAG_LIBRARY.is(BlockTags.CROPS, state))
            result = Occlusion.NONE;
        else if (TAG_LIBRARY.is(BlockTags.CAULDRONS, state))
            result = Occlusion.MEDIUM;
        else if (TAG_LIBRARY.is(BlockTags.SAPLINGS, state))
            result = Occlusion.NONE;
        else if (TAG_LIBRARY.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS, state))
            result = Occlusion.VIBRATION;
        else if (TAG_LIBRARY.is(BlockTags.SWORD_EFFICIENT, state))
            result = Occlusion.NONE;

        if (result == null) {
            var pathString = state.getBlockHolder().unwrapKey().map(k -> k.location().getPath()).orElse(null);
            if (pathString != null) {
                if (pathString.contains("chest") || pathString.contains("glass"))
                    result = Occlusion.LOW;
                else if (pathString.contains("stone"))
                    result = Occlusion.HIGH;
            }
        }

        if (result == null)
            result = state.canOcclude() ? Occlusion.DEFAULT : Occlusion.DEFAULT_TRANSLUCENT;

        return result;
    }

    public String toString() {
        final StringBuilder builder = new StringBuilder();

        builder.append("reflectivity: ")
                .append(this.soundReflectivity)
                .append("; occlusion: ")
                .append(this.soundOcclusion)
                .append("\n");

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