package org.orecruncher.dsurround.processing.accents;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.block.Waterloggable;
import net.minecraft.entity.LivingEntity;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.config.Configuration;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

@Environment(EnvType.CLIENT)
class WaterySurfaceAccent implements IFootstepAccentProvider {

    private static final ISoundFactory wetSurfaceFactory = SoundFactoryBuilder
            .create(SoundEvent.of(new Identifier("dsurround", "footsteps.water_through")))
            .category(SoundCategory.PLAYERS).volume(0.8F).pitchRange(0.8F, 1.2F).build();

    private final Configuration config;

    WaterySurfaceAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableWetSurfaceAccents;
    }

    @Override
    public void provide(LivingEntity entity, BlockPos pos, BlockState posState, ObjectArray<ISoundFactory> acoustics) {

        boolean addAcoustic = false;

        if (posState.getBlock() instanceof Waterloggable) {
            addAcoustic = !posState.getFluidState().isEmpty();
        }

        if (!addAcoustic) {
            // Get the precipitation type at the location
            final World world = entity.getEntityWorld();
            var up = pos.up();
            if (world.hasRain(up)) {
                var precipitation = world.getBiome(up).value().getPrecipitation(up);
                addAcoustic = precipitation == Biome.Precipitation.RAIN;
            }
        }

        if (addAcoustic)
            acoustics.add(wetSurfaceFactory);
    }
}