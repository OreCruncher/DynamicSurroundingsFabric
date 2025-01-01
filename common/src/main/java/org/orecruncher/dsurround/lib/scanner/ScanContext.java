package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Supplier;

public final class ScanContext {

    private final Supplier<Level> world;
    private final Supplier<BlockPos> scanCenter;
    private final IModLog logger;

    public ScanContext(
            Supplier<Level> world,
            Supplier<BlockPos> scanCenter,
            IModLog logger) {

        this.world = world;
        this.scanCenter = scanCenter;
        this.logger = logger;
    }

    public Level getWorld() {
        return this.world.get();
    }

    public BlockPos getScanCenter() {
        return this.scanCenter.get();
    }

    public IModLog getLogger() {
        return this.logger;
    }

    public ResourceLocation getWorldReference() {
        return this.getWorld().dimension().registry();
    }

    public boolean isOutOfHeightLimit(int y) {
        return this.getWorld().isOutsideBuildHeight(y);
    }

    public int clampHeight(int y) {
        var world = this.getWorld();
        return Mth.clamp(y, world.getMinY(), world.getMaxY());
    }
}