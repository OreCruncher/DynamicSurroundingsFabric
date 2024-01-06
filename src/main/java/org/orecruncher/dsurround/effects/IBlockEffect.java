package org.orecruncher.dsurround.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;

public interface IBlockEffect {

    void tick();

    boolean isDone();

    void remove();

    BlockPos getPos();

    default Vec3 getPosition() {
        return Vec3.atCenterOf(this.getPos());
    }

    default long getPosIndex() {
        return this.getPos().asLong();
    }
}
