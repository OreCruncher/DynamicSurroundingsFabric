package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Supplier;

public final class ScanContext {

    private final Supplier<World> world;
    private final Supplier<BlockPos> scanCenter;
    private final IModLog logger;

    public ScanContext(
            Supplier<World> world,
            Supplier<BlockPos> scanCenter,
            IModLog logger) {

        this.world = world;
        this.scanCenter = scanCenter;
        this.logger = logger;
    }

    public World getWorld() {
        return this.world.get();
    }

    public BlockPos getScanCenter() {
        return this.scanCenter.get();
    }

    public IModLog getLogger() {
        return this.logger;
    }

    public Identifier getWorldReference() {
        return this.getWorld().getRegistryKey().getValue();
    }

    public boolean isOutOfHeightLimit(int y) {
        return this.getWorld().isOutOfHeightLimit(y);
    }

    public int clampHeight(int y) {
        var world = this.getWorld();
        return MathHelper.clamp(y, world.getBottomY(), world.getTopY());
    }
}