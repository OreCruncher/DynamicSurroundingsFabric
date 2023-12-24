package org.orecruncher.dsurround.processing.accents;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.block.BlockState;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.sound.ISoundFactory;

@Environment(EnvType.CLIENT)
public interface IFootstepAccentProvider {
    void provide(LivingEntity entity, BlockPos pos, BlockState posState, ObjectArray<ISoundFactory> acoustics);

    boolean isEnabled();
}
