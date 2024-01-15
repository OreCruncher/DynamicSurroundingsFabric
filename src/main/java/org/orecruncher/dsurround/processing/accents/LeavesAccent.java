package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.BlockEffectTags;

public class LeavesAccent implements IFootstepAccentProvider {

    private static final ISoundFactory LEAVES_FACTORY = SoundFactoryBuilder
            .create(SoundEvent.createVariableRangeEvent(new ResourceLocation("dsurround", "footsteps.leaves_through")))
            .category(SoundSource.PLAYERS).volume(1.0F).pitch(0.8F, 1.2F).build();

    private final Configuration config;

    LeavesAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public void collect(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics) {
        // Check for waterlogged.  Don't want to squeak if waterlogged.
        if (isWaterLogged)
            return;

        if (state.is(BlockEffectTags.LEAVES_STEP)) {
            acoustics.add(LEAVES_FACTORY);
        }
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableLeafAccents;
    }
}