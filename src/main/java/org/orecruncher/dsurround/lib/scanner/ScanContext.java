package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Supplier;

public record ScanContext(
        Supplier<World> world,
        Supplier<BlockPos> scanCenter,
        IModLog logger,
        Supplier<Identifier> worldReference
    ) {

    public boolean isOutOfHeightLimit(int y) {
        return this.world.get().isOutOfHeightLimit(y);
    }

    public int clampHeight(int y) {
        return MathHelper.clamp(y, this.world.get().getBottomY(), this.world.get().getTopY());
    }
}