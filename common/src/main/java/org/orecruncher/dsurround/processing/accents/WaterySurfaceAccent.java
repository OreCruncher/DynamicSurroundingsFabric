package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.tags.BlockEffectTags;

/**
 * The splash of a step on something wet: water-logged blocks and watery surfaces at full volume, and ground in the
 * rain as loud as the storm is strong, so a drizzle doesn't splash like a downpour.
 */
class WaterySurfaceAccent implements IFootstepAccentProvider {

    private static final ResourceLocation WETSURFACE_FACTORY = Constants.asId("footstep/wetsurface");
    /**
     * Below this storm strength, ground in the rain is too lightly wet to splash
     */
    static final float DRY_BELOW = 0.15F;
    /**
     * Where the rain is just strong enough: the splash's volume, as a share of its own, and the share of steps that
     * splash at all. Both rise to all at full strength. A splash sounds like one at any volume, so in light rain it is
     * fewer splashes as well as quieter ones that make the step lighter.
     */
    static final float QUIETEST = 0.25F;
    static final float FEWEST = 0.3F;

    private final Configuration config;
    private final ISoundLibrary soundLibrary;
    private final ITagLibrary tagLibrary;
    private final IWeatherSimulation weatherSimulation;

    WaterySurfaceAccent(Configuration config, ISoundLibrary soundLibrary, ITagLibrary tagLibrary, IWeatherSimulation weatherSimulation) {
        this.config = config;
        this.weatherSimulation = weatherSimulation;
        this.soundLibrary = soundLibrary;
        this.tagLibrary = tagLibrary;
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableWetSurfaceAccents;
    }

    @Override
    public void collect(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, StepAccents acoustics) {

        boolean addAcoustic = isWaterLogged;
        float volumeScale = 1F;

        if (!addAcoustic)
            addAcoustic = this.tagLibrary.is(BlockEffectTags.WATERY_STEP, state);

        // Check the block above because it may be flagged as having a wet effect, like a lily pad.
        if (!addAcoustic) {
            var world = entity.level();
            var up = pos.above();
            addAcoustic = this.tagLibrary.is(BlockEffectTags.WATERY_STEP, world.getBlockState(up));

            if (!addAcoustic && world.isRainingAt(up)) {
                // Get the precipitation type at the location
                var precipitation = world.getBiome(up).value().getPrecipitationAt(up);
                if (precipitation == Biome.Precipitation.RAIN) {
                    var rainLevel = this.weatherSimulation.rainLevel(world.getRainLevel(1F));
                    volumeScale = rainVolume(rainLevel);
                    addAcoustic = Randomizer.current().nextFloat() < splashChance(rainLevel);
                }
            }
        }

        if (addAcoustic) {
            var factory = this.soundLibrary.getSoundFactory(WETSURFACE_FACTORY);
            if (factory.isPresent())
                acoustics.add(factory.get(), volumeScale);
        }
    }

    /**
     * How loud a step splashes on ground in the rain, at a storm's strength, as a share of the splash's own volume:
     * nothing in the lightest drizzle, then rising with the storm
     */
    static float rainVolume(float rainLevel) {
        return rainLevel < DRY_BELOW ? 0F : QUIETEST + (1F - QUIETEST) * wetness(rainLevel);
    }

    /**
     * The chance a step on ground in the rain splashes, at a storm's strength: none in the lightest drizzle, then
     * rising to every step at full strength
     */
    static float splashChance(float rainLevel) {
        return rainLevel < DRY_BELOW ? 0F : FEWEST + (1F - FEWEST) * wetness(rainLevel);
    }

    /**
     * How far a storm is from just wet enough to splash, 0, to full strength, 1
     */
    private static float wetness(float rainLevel) {
        return Math.min((rainLevel - DRY_BELOW) / (1F - DRY_BELOW), 1F);
    }
}