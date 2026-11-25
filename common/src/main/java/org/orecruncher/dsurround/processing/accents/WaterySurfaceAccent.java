// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.runtime.audio.EnhancedSoundProcessor;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;
import org.orecruncher.dsurround.runtime.weather.GroundWetness;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.runtime.weather.RainSurface;
import org.orecruncher.dsurround.tags.BlockEffectTags;

/**
 * What a step on something wet adds to it, by what is underfoot. Water-logged blocks and watery ones (a lily pad, the
 * {@code watery_step} tag) splash at full volume. Ground in the rain answers as it is soaked
 * ({@link GroundWetness}, so not under a roof or an overhang): ground that turns to mud (grass, dirt: what leaves mud
 * prints) squelches once it is muddy; hard ground (stone, wood, metal, glass) gives a light wet slap, the splash
 * thinned by a filter; sand, gravel, snow and leaves take the rain in and add nothing. Every step sounds, louder the
 * wetter the ground, so a drizzle doesn't slosh like a downpour. (Until the 2026-11 rework every ground splashed as if
 * wading, and a quiet wade still sounds like one, so light rain skipped steps too; a squelch or a slap reads as the
 * ground at any volume, and skipping steps only made the walk sound uneven.)
 */
class WaterySurfaceAccent implements IFootstepAccentProvider {

    private static final Identifier WETSURFACE_FACTORY = Constants.asId("footstep/wetsurface");
    private static final Identifier MUD_FACTORY = Constants.asId("footstep/mud");

    /**
     * What a step on ground in the rain adds
     */
    enum WetGround {
        // Grass, dirt: a squelch once muddy
        MUD,
        // Stone, wood, metal, glass: a light slap
        SLAP,
        // Sand, gravel, snow, leaves, and anything else: nothing
        NONE
    }

    /**
     * Below this wetness, ground in the rain is too lightly wet to sound
     */
    static final float DRY_BELOW = 0.15F;

    /**
     * A slap on hard ground, from just wet enough to soaked: its volume, as a share of the splash's. Well under a splash
     * even when soaked: a film of water, not a puddle.
     */
    static final float SLAP_QUIETEST = 0.15F;
    static final float SLAP_LOUDEST = 0.45F;
    /**
     * The splash thinned to a slap: the low slosh taken away with enhanced sound processing, a little higher too.
     * Without it (Enhanced Sounds off), higher and quieter instead, as rain on surfaces does.
     */
    static final FixedFilter SLAP_FILTER = FixedFilter.highPass(0.2F);
    static final float SLAP_PITCH = 1.1F;
    static final float SLAP_PITCH_UNFILTERED = 1.3F;
    static final float SLAP_VOLUME_UNFILTERED = 0.8F;

    /**
     * A squelch on muddy ground, from just muddy ({@link GroundWetness#MUDDY}) to soaked: its volume, as a share of its
     * own
     */
    static final float MUD_QUIETEST = 0.35F;

    private final Configuration config;
    private final ISoundLibrary soundLibrary;
    private final ITagLibrary tagLibrary;
    private final IBlockLibrary blockLibrary;
    private final IWeatherSimulation weatherSimulation;

    WaterySurfaceAccent(Configuration config, ISoundLibrary soundLibrary, ITagLibrary tagLibrary, IBlockLibrary blockLibrary,
                        IWeatherSimulation weatherSimulation) {
        this.config = config;
        this.soundLibrary = soundLibrary;
        this.tagLibrary = tagLibrary;
        this.blockLibrary = blockLibrary;
        this.weatherSimulation = weatherSimulation;
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableWetSurfaceAccents;
    }

    @Override
    public void collect(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, StepAccents acoustics) {
        var level = entity.level();
        var up = pos.above();

        // Water underfoot, or something watery on top of the block (a lily pad): a splash
        if (isWaterLogged || this.tagLibrary.is(BlockEffectTags.WATERY_STEP, state)
                || this.tagLibrary.is(BlockEffectTags.WATERY_STEP, level.getBlockState(up))) {
            this.soundLibrary.getSoundFactory(WETSURFACE_FACTORY).ifPresent(acoustics::add);
            return;
        }

        float wetness = GroundWetness.at(this.weatherSimulation, level, up);
        if (wetness < DRY_BELOW)
            return;

        switch (wetGround(RainSurface.of(level, pos, this.tagLibrary), this.blockLibrary.getBlockInfo(state).tracksMudWhenWet())) {
            case MUD -> {
                float volume = mudVolume(wetness);
                if (volume > 0F)
                    this.soundLibrary.getSoundFactory(MUD_FACTORY).ifPresent(factory -> acoustics.add(factory, volume));
            }
            case SLAP -> {
                boolean filtered = EnhancedSoundProcessor.isAvailable();
                float volume = slapVolume(wetness) * (filtered ? 1F : SLAP_VOLUME_UNFILTERED);
                float pitch = filtered ? SLAP_PITCH : SLAP_PITCH_UNFILTERED;
                this.soundLibrary.getSoundFactory(WETSURFACE_FACTORY)
                        .ifPresent(factory -> acoustics.add(factory, volume, pitch, SLAP_FILTER));
            }
            case NONE -> {
            }
        }
    }

    /**
     * What a step on ground in the rain adds, by what the rain is to the block and whether it turns to mud: ground
     * that turns to mud squelches whatever its sound type, hard surfaces slap, the rest take the rain in
     */
    static WetGround wetGround(RainSurface surface, boolean turnsToMud) {
        if (turnsToMud)
            return WetGround.MUD;
        return switch (surface) {
            case HARD, WOOD, METAL, GLASS -> WetGround.SLAP;
            default -> WetGround.NONE;
        };
    }

    /**
     * How loud a slap on hard ground is, as a share of the splash's volume: nothing barely wet, then rising
     */
    static float slapVolume(float wetness) {
        return ramp(wetness, DRY_BELOW, SLAP_QUIETEST, SLAP_LOUDEST);
    }

    /**
     * How loud a squelch on muddy ground is, as a share of its own volume: nothing until muddy, then rising to all
     */
    static float mudVolume(float wetness) {
        return ramp(wetness, GroundWetness.MUDDY, MUD_QUIETEST, 1F);
    }

    /**
     * Nothing below {@code from}; from there {@code low}, rising to {@code high} when soaked (1)
     */
    private static float ramp(float wetness, float from, float low, float high) {
        if (wetness < from)
            return 0F;
        float t = Math.min((wetness - from) / (1F - from), 1F);
        return low + (high - low) * t;
    }
}
