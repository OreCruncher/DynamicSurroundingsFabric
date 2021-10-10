package org.orecruncher.dsurround.effects;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.math.BlockPos;

@Environment(EnvType.CLIENT)
public interface IBlockEffect {

    void tick();

    boolean isDone();

    void setDone();

    BlockPos getPos();
}
