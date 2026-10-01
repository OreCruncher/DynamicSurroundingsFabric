package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;
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

    /**
     * The lowest y that holds blocks.
     */
    public int getMinY() {
        return this.getWorld().getMinBuildHeight();
    }

    /**
     * The highest y that holds blocks. getMaxBuildHeight() is exclusive, so this is one less.
     */
    public int getMaxY() {
        return this.getWorld().getMaxBuildHeight() - 1;
    }
}