package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundSource;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.BlockEffectTags;

class FloorSqueakAccent  implements IFootstepAccentProvider {

    private static final ISoundFactory floorSqueakFactory = SoundFactoryBuilder
            .create(SoundEvent.createVariableRangeEvent(new ResourceLocation("dsurround", "footsteps.floor_squeak")))
            .category(SoundSource.PLAYERS).volume(0.3F).pitchRange(0.8F, 1.2F).build();

    private final Configuration config;

    FloorSqueakAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public void provide(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics) {
        // Check for waterlogged.  Don't want to squeak if waterlogged.
        if (isWaterLogged)
            return;

        if (state.is(BlockEffectTags.FLOOR_SQUEAKS)) {
            // 1 in 10 chance of a squeak
            if (Randomizer.current().nextInt(10) == 0)
                acoustics.add(floorSqueakFactory);
        }
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableFloorSqueaks;
    }
}
