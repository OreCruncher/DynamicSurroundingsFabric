package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.BlockEffectTags;

class WaterySurfaceAccent implements IFootstepAccentProvider {

    private static final ResourceLocation WETSURFACE_FACTORY = new ResourceLocation(Constants.MOD_ID, "footstep/wetsurface");

    private final Configuration config;

    WaterySurfaceAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableWetSurfaceAccents;
    }

    @Override
    public void collect(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics) {

        boolean addAcoustic = isWaterLogged;

        if (!addAcoustic)
            addAcoustic = state.is(BlockEffectTags.WATERY_STEP);

        // Check the block above because it may be flagged as having a wet effect, like a lily pad.
        if (!addAcoustic) {
            var world = entity.level();
            var up = pos.above();
            addAcoustic = world.getBlockState(up).is(BlockEffectTags.WATERY_STEP);

            if (!addAcoustic && world.isRainingAt(up)) {
                // Get the precipitation type at the location
                var precipitation = world.getBiome(up).value().getPrecipitationAt(up);
                addAcoustic = precipitation == Biome.Precipitation.RAIN;
            }
        }

        if (addAcoustic)
            SOUND_LIBRARY.getSoundFactory(WETSURFACE_FACTORY)
                    .ifPresent(acoustics::add);
    }
}