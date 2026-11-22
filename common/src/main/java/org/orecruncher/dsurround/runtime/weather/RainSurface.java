// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.tags.BlockEffectTags;

import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * What the rain is falling on where a rain sound plays, and how that changes the sound. Vanilla's rain tick plays each
 * rain sound on the block the rain lands on in one column picked at random near the player (the top of the
 * {@code MOTION_BLOCKING} heightmap, which has water and leaves in it), and the sounds overlap, so tweaking each for
 * its own block gives a mix that follows what is around: half lake and half meadow sounds half and half.
 * <p>
 * Looked up in order: something hot or water, then the {@code rain_on_*} tags (for packs, and blocks whose sound type
 * says the wrong thing), then {@code rain_absorbing}, then the block's sound type, so a mod's blocks mostly sort
 * themselves. Anything else is hard ground: stone, brick, concrete.
 * <p>
 * Two ways to sound like the surface. With enhanced sound processing, each sound gets a filter of the surface's
 * ({@link FixedFilter}: soft ground takes the treble away, metal and glass the bass) and a little volume and pitch;
 * filters change the tone, where pitch changes the sound itself (rain sped up), and a filter can still dull a roof's
 * sound, whose pitch, 0.5, is already the lowest the game plays. Without it (Enhanced Sounds off), volume and pitch
 * alone, set by ear on the rain-surfaces bench (2026-11-21): milder ones were too close to tell apart, and leaves
 * underfoot needed more than the rest. A volume over 1 still makes the rain louder, as vanilla plays it at 0.2 at most
 * (0.1 on a roof), well under the sound engine's limit of 1. Leaves, metal, glass and water also have rain of their own
 * ({@link #layer}), recordings played in place of most of the rain there, as neither a filter nor pitch can add a ring,
 * a tick or a patter. A prototype: the filters' numbers are first guesses.
 * <p>
 * {@code /dsdump blocks} gives each block's surface and what sorted it ({@link #describe}), for fitting a pack's tags.
 */
public enum RainSurface {
    // Stone, brick, concrete: the rain sounds as it always has
    HARD(1F, 1F, null, 1F, 1F),
    // Grass, dirt, sand, snow, wool: duller and quieter, the ground takes the drops in
    SOFT(0.4F, 0.75F, FixedFilter.lowPass(0.25F), 0.6F, 0.9F),
    // A canopy or leaf litter: a busier, lighter patter
    LEAVES(1.6F, 1.3F, FixedFilter.highPass(0.5F), 1.5F, 1.15F),
    // Planks, logs, a wooden roof: boxy, a little lower
    WOOD(1F, 0.8F, FixedFilter.bandPass(0.6F, 0.5F), 1.1F, 0.9F),
    // Iron, copper, an anvil: thin and ringing, louder
    METAL(1.6F, 1.5F, FixedFilter.highPass(0.3F), 1.6F, 1.25F),
    // Glass, ice, amethyst: bright ticks
    GLASS(1.3F, 1.6F, FixedFilter.highPass(0.15F), 1.5F, 1.3F),
    // Open water: a softer hiss
    WATER(0.7F, 1.3F, FixedFilter.lowPass(0.6F), 0.8F, 1.1F),
    // Lava, magma, a lit campfire: a thin sizzle
    HOT(0.6F, 1.7F, FixedFilter.highPass(0.3F), 0.8F, 1.4F);

    private static final Set<SoundType> METAL_SOUNDS = Set.of(SoundType.METAL, SoundType.IRON, SoundType.ANVIL,
            SoundType.LANTERN, SoundType.NETHERITE_BLOCK, SoundType.CHAIN, SoundType.COPPER, SoundType.COPPER_BULB,
            SoundType.COPPER_GRATE, SoundType.COPPER_GOLEM_STATUE, SoundType.HEAVY_CORE, SoundType.VAULT,
            SoundType.TRIAL_SPAWNER, SoundType.SPAWNER);
    // Checked against /dsdump blocks (2026-11-21): what fell to HARD and shouldn't have (hanging signs, nether stems,
    // amethyst buds, azaleas, nylium...). Plants with no collision don't matter: the rain lands on what is under them.
    private static final Set<SoundType> GLASS_SOUNDS = Set.of(SoundType.GLASS, SoundType.AMETHYST,
            SoundType.AMETHYST_CLUSTER, SoundType.LARGE_AMETHYST_BUD, SoundType.MEDIUM_AMETHYST_BUD,
            SoundType.SMALL_AMETHYST_BUD);
    private static final Set<SoundType> WOOD_SOUNDS = Set.of(SoundType.WOOD, SoundType.NETHER_WOOD,
            SoundType.BAMBOO_WOOD, SoundType.CHERRY_WOOD, SoundType.LADDER, SoundType.SCAFFOLDING, SoundType.SHELF,
            SoundType.CHISELED_BOOKSHELF, SoundType.STEM, SoundType.HANGING_SIGN, SoundType.NETHER_WOOD_HANGING_SIGN,
            SoundType.CHERRY_WOOD_HANGING_SIGN, SoundType.BAMBOO_WOOD_HANGING_SIGN);
    private static final Set<SoundType> LEAVES_SOUNDS = Set.of(SoundType.AZALEA_LEAVES, SoundType.CHERRY_LEAVES,
            SoundType.POPLAR_LEAVES, SoundType.LEAF_LITTER, SoundType.BIG_DRIPLEAF, SoundType.LILY_PAD,
            SoundType.AZALEA, SoundType.FLOWERING_AZALEA);
    private static final Set<SoundType> SOFT_SOUNDS = Set.of(SoundType.GRASS, SoundType.WET_GRASS, SoundType.WOOL,
            SoundType.SNOW, SoundType.POWDER_SNOW, SoundType.SAND, SoundType.SUSPICIOUS_SAND, SoundType.GRAVEL,
            SoundType.SUSPICIOUS_GRAVEL, SoundType.MOSS, SoundType.MOSS_CARPET, SoundType.MUD, SoundType.ROOTED_DIRT,
            SoundType.SOUL_SAND, SoundType.SOUL_SOIL, SoundType.SPONGE, SoundType.WET_SPONGE, SoundType.STRAW_BED,
            SoundType.NYLIUM, SoundType.WART_BLOCK, SoundType.SCULK, SoundType.SLIME_BLOCK, SoundType.HONEY_BLOCK);

    private final float volume;
    private final float pitch;
    @Nullable
    private final FixedFilter filter;
    private final float filteredVolume;
    private final float filteredPitch;

    RainSurface(float volume, float pitch, @Nullable FixedFilter filter, float filteredVolume, float filteredPitch) {
        this.volume = volume;
        this.pitch = pitch;
        this.filter = filter;
        this.filteredVolume = filteredVolume;
        this.filteredPitch = filteredPitch;
    }

    /**
     * The rain sound's volume on this surface, as a share of what it would be, given its filter or not
     */
    public float volume(boolean filtered) {
        return filtered ? this.filteredVolume : this.volume;
    }

    /**
     * The rain sound's pitch on this surface, as a share of what it would be, given its filter or not
     */
    public float pitch(boolean filtered) {
        return filtered ? this.filteredPitch : this.pitch;
    }

    /**
     * The surface's own rain, played in place of most of the rain where a rain sound lands on it, or null for none: what
     * a filter can't give (a tin roof's ring, drops ticking on glass, pattering on leaves, plinking on water)
     */
    public @Nullable Identifier layer() {
        return switch (this) {
            case LEAVES -> StormSounds.RAIN_LEAVES;
            case METAL -> StormSounds.RAIN_METAL;
            case GLASS -> StormSounds.RAIN_GLASS;
            case WATER -> StormSounds.RAIN_WATER;
            default -> null;
        };
    }

    /**
     * The filter the rain sounds through on this surface, or null for none
     */
    public @Nullable FixedFilter filter() {
        return this.filter;
    }

    /**
     * What the rain falls on at a block: the one vanilla plays its rain sound on
     */
    public static RainSurface of(BlockGetter level, BlockPos pos, ITagLibrary tags) {
        var state = level.getBlockState(pos);
        if (isHot(state, tags))
            return HOT;
        if (isWater(state, tags, level, pos))
            return WATER;
        return of(state, tags);
    }

    /**
     * What a block is to the rain, past heat and water
     */
    static RainSurface of(BlockState state, ITagLibrary tags) {
        return sort(state, tags).surface();
    }

    /**
     * What a block state is to the rain and why, for dumps: "metal (sound type IRON)", "leaves (tag
     * dsurround:effects/rain_on_leaves)". Water is judged as the state stands alone, with nothing around it.
     */
    public static String describe(BlockState state, ITagLibrary tags) {
        if (isHot(state, tags))
            return "hot (lava, magma or a lit campfire)";
        if (isWater(state, tags, EmptyBlockGetter.INSTANCE, BlockPos.ZERO))
            return "water (water with nothing to stand on)";
        var sorted = sort(state, tags);
        var name = sorted.surface().name().toLowerCase(Locale.ROOT);
        if (sorted.tag() != null)
            return name + " (tag " + sorted.tag().location() + ")";
        if (sorted.surface() != HARD)
            return name + " (sound type " + soundTypeName(state.getSoundType()) + ")";
        return name + " (no tag or sound type says otherwise)";
    }

    /**
     * A sound type's name as the game's field has it, or for a mod's own, its step sound
     */
    public static String soundTypeName(SoundType sound) {
        var name = GameSoundTypes.NAMES.get(sound);
        return name != null ? name : "mod's own, steps " + sound.getStepSound().location();
    }

    private static boolean isHot(BlockState state, ITagLibrary tags) {
        return tags.is(FluidTags.LAVA, state.getFluidState()) || state.is(Blocks.MAGMA_BLOCK)
                || (state.getBlock() instanceof CampfireBlock && state.getValue(CampfireBlock.LIT));
    }

    // Water, and what grows in it with nothing above the surface (seagrass, kelp); a waterlogged slab or stair is what
    // it is made of
    private static boolean isWater(BlockState state, ITagLibrary tags, BlockGetter level, BlockPos pos) {
        return tags.is(FluidTags.WATER, state.getFluidState()) && state.getCollisionShape(level, pos).isEmpty();
    }

    private static Sorted sort(BlockState state, ITagLibrary tags) {
        for (var rule : TAGS)
            if (tags.is(rule.tag(), state))
                return rule;
        return bySound(state.getSoundType());
    }

    /**
     * What a sound type is to the rain, for a block no tag sorts
     */
    static RainSurface of(SoundType sound) {
        return bySound(sound).surface();
    }

    private static Sorted bySound(SoundType sound) {
        var sorted = bySoundType(sound);
        if (sorted != BY_DEFAULT || GameSoundTypes.NAMES.containsKey(sound))
            return sorted;
        // A mod's own sound type that steps as one of the game's does is what that one is to the rain (Mini Mob
        // Trophies' horses step as wool): seen in The Cozy Artificer's dump, 2026-11-22
        return GameSoundTypes.BY_STEP.getOrDefault(sound.getStepSound().location(), BY_DEFAULT);
    }

    private static Sorted bySoundType(SoundType sound) {
        if (METAL_SOUNDS.contains(sound))
            return BY_SOUND_METAL;
        if (GLASS_SOUNDS.contains(sound))
            return BY_SOUND_GLASS;
        if (LEAVES_SOUNDS.contains(sound))
            return BY_SOUND_LEAVES;
        if (WOOD_SOUNDS.contains(sound))
            return BY_SOUND_WOOD;
        if (SOFT_SOUNDS.contains(sound))
            return BY_SOUND_SOFT;
        return BY_DEFAULT;
    }

    /**
     * A surface and the tag that gave it, null when the sound type (or nothing) did
     */
    private record Sorted(RainSurface surface, @Nullable TagKey<Block> tag) {
    }

    // In the order they are looked up: a tag wins over the sound type
    private static final List<Sorted> TAGS = List.of(
            new Sorted(METAL, BlockEffectTags.RAIN_ON_METAL),
            new Sorted(GLASS, BlockEffectTags.RAIN_ON_GLASS),
            new Sorted(LEAVES, BlockEffectTags.RAIN_ON_LEAVES),
            new Sorted(WOOD, BlockEffectTags.RAIN_ON_WOOD),
            new Sorted(SOFT, BlockEffectTags.RAIN_ABSORBING));
    private static final Sorted BY_SOUND_METAL = new Sorted(METAL, null);
    private static final Sorted BY_SOUND_GLASS = new Sorted(GLASS, null);
    private static final Sorted BY_SOUND_LEAVES = new Sorted(LEAVES, null);
    private static final Sorted BY_SOUND_WOOD = new Sorted(WOOD, null);
    private static final Sorted BY_SOUND_SOFT = new Sorted(SOFT, null);
    private static final Sorted BY_DEFAULT = new Sorted(HARD, null);

    // The game's sound types, made on first use (the dump, or a mod's own sound type rained on): each one's field name,
    // and what each step sound is to the rain where every listed game sound type stepping with it agrees
    private static final class GameSoundTypes {
        static final Map<SoundType, String> NAMES = new IdentityHashMap<>();
        static final Map<Identifier, Sorted> BY_STEP = new HashMap<>();

        static {
            var disagreeing = new HashSet<Identifier>();
            for (var field : SoundType.class.getFields()) {
                if (field.getType() != SoundType.class || !Modifier.isStatic(field.getModifiers()))
                    continue;
                try {
                    var sound = (SoundType) field.get(null);
                    NAMES.putIfAbsent(sound, field.getName());
                    // Only the listed ones: those left out are hard ground, or plants the rain falls through (crops
                    // and berry bushes step as grass, cocoa as wood), which say nothing of what a step sound is
                    var sorted = bySoundType(sound);
                    if (sorted == BY_DEFAULT)
                        continue;
                    var step = sound.getStepSound().location();
                    var before = BY_STEP.putIfAbsent(step, sorted);
                    if (before != null && before.surface() != sorted.surface())
                        disagreeing.add(step);
                } catch (IllegalAccessException ignored) {
                    // Public fields only; none is refused
                }
            }
            disagreeing.forEach(BY_STEP::remove);
        }
    }
}
