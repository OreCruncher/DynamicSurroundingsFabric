package org.orecruncher.dsurround.config.block;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.AcousticEntry;
import org.orecruncher.dsurround.config.AcousticEntryCollection;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.data.AcousticConfig;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.effects.IBlockEffectProducer;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.tags.OcclusionTags;
import org.orecruncher.dsurround.tags.ReflectanceTags;

/**
 * Works out a block state's acoustics, applies the configuration rules that match it, and then builds its
 * {@link BlockInfo}. Used once: after {@link #build()} it can't be changed.
 */
public final class BlockInfoBuilder {

    public static final Script DEFAULT_SOUND_CHANCE = new Script("0.01");

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

    final int version;
    final ConfigServices services;
    @Nullable
    final ResourceLocation stepSound;
    final AcousticEntryCollection sounds = new AcousticEntryCollection();
    final ObjectArray<IBlockEffectProducer> blockEffects = new ObjectArray<>();
    Script soundChance = DEFAULT_SOUND_CHANCE;
    final float soundReflectivity;
    final float soundOcclusion;
    private boolean built;

    /**
     * For the info shared by blocks that need nothing special
     */
    public BlockInfoBuilder(int version, ConfigServices services) {
        this.version = version;
        this.services = services;
        this.stepSound = null;
        this.soundReflectivity = Reflectance.DEFAULT;
        this.soundOcclusion = Occlusion.DEFAULT;
    }

    /**
     * @param services what the info uses, from the library that builds it
     */
    public BlockInfoBuilder(int version, BlockState state, ConfigServices services) {
        this.version = version;
        this.services = services;
        this.soundOcclusion = getSoundOcclusionSetting(state, services.tagLibrary());
        this.soundReflectivity = getSoundReflectionSetting(state, services.tagLibrary());
        this.stepSound = state.getSoundType().getStepSound().getLocation();
    }

    /**
     * True if this block needs nothing beyond the shared default info: no sounds, no effects, and the same acoustic
     * properties as the default. BlockLibrary then uses the shared instance instead of building one.
     * <p>
     * The acoustics must match the default exactly. Translucent blocks (occlusion DEFAULT_TRANSLUCENT) don't
     * qualify: the shared instance has ordinary occlusion, so collapsing them into it would change how much sound
     * they block.
     */
    public boolean isDefault() {
        return this.sounds.isEmpty() && this.blockEffects.isEmpty()
                && this.soundReflectivity == Reflectance.DEFAULT
                && this.soundOcclusion == Occlusion.DEFAULT;
    }

    public void apply(BlockConfigRule config) {
        this.checkNotBuilt();

        // Reset of a block clears all registries
        if (config.clearSounds())
            this.sounds.clear();

        config.soundChance().ifPresent(v -> this.soundChance = v);

        for (final AcousticConfig sr : config.acoustics()) {
            var factory = this.services.soundLibrary().getSoundFactoryOrDefault(sr.factory());
            final AcousticEntry acousticEntry = new AcousticEntry(factory, sr.conditions(), sr.weight(), this.services.conditionEvaluator());
            if (!this.sounds.add(acousticEntry))
                this.services.logger().warn("[BlockInfo] Duplicate acoustic entry: %s", sr.toString());
        }

        for (var e : config.effects()) {
            var effect = e.effect().createInstance(e.spawnChance(), e.conditions());
            effect.ifPresent(this.blockEffects::add);
        }
    }

    /**
     * The finished info. The builder can't be used after this.
     */
    public BlockInfo build() {
        this.checkNotBuilt();
        this.built = true;

        // Reduce memory consumption as much as possible
        this.sounds.trim();
        this.blockEffects.trim();
        return new BlockInfo(this, this.blockEffects.isEmpty() ? ImmutableList.of() : this.blockEffects);
    }

    private void checkNotBuilt() {
        Preconditions.checkState(!this.built, "BlockInfo has already been built");
    }

    private static float getSoundReflectionSetting(BlockState state, ITagLibrary tags) {
        if (tags.is(ReflectanceTags.NONE, state))
            return Reflectance.NONE;
        if (tags.is(ReflectanceTags.VERY_LOW, state))
            return Reflectance.VERY_LOW;
        if (tags.is(ReflectanceTags.LOW, state))
            return Reflectance.LOW;
        if (tags.is(ReflectanceTags.MEDIUM, state))
            return Reflectance.MEDIUM;
        if (tags.is(ReflectanceTags.HIGH, state))
            return Reflectance.HIGH;
        if (tags.is(ReflectanceTags.VERY_HIGH, state))
            return Reflectance.VERY_HIGH;
        if (tags.is(ReflectanceTags.MAX, state))
            return Reflectance.MAX;

        return estimateReflectance(state, tags);
    }

    private static float estimateReflectance(BlockState state, ITagLibrary tags) {

        Float result = null;

        if (tags.is(BlockTags.FLOWERS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.FENCES, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.FENCE_GATES, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.BEDS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.TRAPDOORS, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.BANNERS, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.LEAVES, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.WOOL, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.WOOL_CARPETS, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.BUTTONS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.DOORS, state))
            result = Reflectance.LOW;
        else if (tags.is(BlockTags.LOGS, state))
            result = Reflectance.VERY_LOW;
        else if (tags.is(BlockTags.TERRACOTTA, state))
            result = Reflectance.MEDIUM;
        else if (tags.is(BlockTags.ICE, state))
            result = Reflectance.LOW;
        else if (tags.is(BlockTags.SIGNS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.CROPS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.CAULDRONS, state))
            result = Reflectance.MEDIUM;
        else if (tags.is(BlockTags.SAPLINGS, state))
            result = Reflectance.NONE;
        else if (tags.is(BlockTags.STONE_ORE_REPLACEABLES, state))
            // Assume stone equivalent
            result = Reflectance.MAX;
        else if (tags.is(BlockTags.DAMPENS_VIBRATIONS, state))
            result = Reflectance.VIBRATION;
        else if (tags.is(BlockTags.SWORD_EFFICIENT, state))
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

    private static float getSoundOcclusionSetting(BlockState state, ITagLibrary tags) {
        if (tags.is(OcclusionTags.NONE, state))
            return Occlusion.NONE;
        if (tags.is(OcclusionTags.VERY_LOW, state))
            return Occlusion.VERY_LOW;
        if (tags.is(OcclusionTags.LOW, state))
            return Occlusion.LOW;
        if (tags.is(OcclusionTags.MEDIUM, state))
            return Occlusion.MEDIUM;
        if (tags.is(OcclusionTags.HIGH, state))
            return Occlusion.HIGH;
        if (tags.is(OcclusionTags.VERY_HIGH, state))
            return Occlusion.VERY_HIGH;
        if (tags.is(OcclusionTags.MAX, state))
            return Occlusion.MAX;

        return estimateOcclusion(state, tags);
    }

    private static float estimateOcclusion(BlockState state, ITagLibrary tags) {

        Float result = null;

        if (tags.is(BlockTags.FLOWERS, state))
            result = Occlusion.NONE;
        else if (tags.is(BlockTags.FENCES, state))
            result = Occlusion.VERY_LOW;
        else if (tags.is(BlockTags.FENCE_GATES, state))
            result = Occlusion.VERY_LOW;
        else if (tags.is(BlockTags.BEDS, state))
            result = Occlusion.MEDIUM;
        else if (tags.is(BlockTags.TRAPDOORS, state))
            result = Occlusion.VERY_LOW;
        else if (tags.is(BlockTags.BANNERS, state))
            result = Occlusion.VERY_LOW;
        else if (tags.is(BlockTags.LEAVES, state))
            result = Occlusion.LOW;
        else if (tags.is(BlockTags.WOOL, state))
            result = Occlusion.MAX;
        else if (tags.is(BlockTags.WOOL_CARPETS, state))
            result = Occlusion.HIGH;
        else if (tags.is(BlockTags.BUTTONS, state))
            result = Occlusion.NONE;
        else if (tags.is(BlockTags.DOORS, state))
            result = Occlusion.LOW;
        else if (tags.is(BlockTags.LOGS, state))
            result = Occlusion.MEDIUM;
        else if (tags.is(BlockTags.TERRACOTTA, state))
            result = Occlusion.MEDIUM;
        else if (tags.is(BlockTags.ICE, state))
            result = Occlusion.LOW;
        else if (tags.is(BlockTags.SIGNS, state))
            result = Occlusion.NONE;
        else if (tags.is(BlockTags.CROPS, state))
            result = Occlusion.NONE;
        else if (tags.is(BlockTags.CAULDRONS, state))
            result = Occlusion.MEDIUM;
        else if (tags.is(BlockTags.SAPLINGS, state))
            result = Occlusion.NONE;
        else if (tags.is(BlockTags.STONE_ORE_REPLACEABLES, state))
            // Assume stone equivalent
            result = Occlusion.HIGH;
        else if (tags.is(BlockTags.OCCLUDES_VIBRATION_SIGNALS, state))
            result = Occlusion.VIBRATION;
        else if (tags.is(BlockTags.SWORD_EFFICIENT, state))
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
}
