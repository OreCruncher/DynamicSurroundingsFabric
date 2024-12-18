package org.orecruncher.dsurround.processing.accents;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.tags.BlockEffectTags;

public class LeavesAccent implements IFootstepAccentProvider {

    private static final ResourceLocation LEAVES_FACTORY = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "footstep/leaves");

    private final Configuration config;

    LeavesAccent(Configuration config) {
        this.config = config;
    }

    @Override
    public void collect(LivingEntity entity, BlockPos pos, BlockState state, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics) {
        // Check for waterlogged.  Don't want to squeak if waterlogged.
        if (isWaterLogged)
            return;

        if (FootstepAccents.TAG_LIBRARY.is(BlockEffectTags.LEAVES_STEP, state)) {
            SOUND_LIBRARY.getSoundFactory(LEAVES_FACTORY)
                            .ifPresent(acoustics::add);
        }
    }

    @Override
    public boolean isEnabled() {
        return this.config.footstepAccents.enableLeafAccents;
    }
}