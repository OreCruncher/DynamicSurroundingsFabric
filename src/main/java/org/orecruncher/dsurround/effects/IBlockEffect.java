package org.orecruncher.dsurround.effects;

import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;

public interface IBlockEffect {

    void tick();

    boolean isDone();

    void setDone();

    BlockPos getPos();

    default Vec3d getPosition() {
        return Vec3d.ofCenter(this.getPos());
    }

    default long getPosIndex() {
        return this.getPos().asLong();
    }
}
