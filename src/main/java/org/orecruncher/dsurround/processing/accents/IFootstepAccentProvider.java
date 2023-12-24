package org.orecruncher.dsurround.processing.accents;

import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

public interface IFootstepAccentProvider {
    void provide(LivingEntity entity, BlockPos pos, BlockState posState, boolean isWaterLogged, ObjectArray<ISoundFactory> acoustics);

    boolean isEnabled();
}
