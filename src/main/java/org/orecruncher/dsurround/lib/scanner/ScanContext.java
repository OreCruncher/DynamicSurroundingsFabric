package org.orecruncher.dsurround.lib.scanner;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.function.Supplier;

@Environment(EnvType.CLIENT)
public class ScanContext {

    private final Supplier<World> worldReader;
    private final Supplier<BlockPos> scanCenter;
    private final Supplier<Identifier> worldReference;
    private final Supplier<IModLog> logger;

    public ScanContext(
            final Supplier<World> worldReader,
            final Supplier<BlockPos> scanCenter,
            final Supplier<IModLog> logger,
            final Supplier<Identifier> worldReference
    ) {
        this.worldReader = worldReader;
        this.scanCenter = scanCenter;
        this.worldReference = worldReference;
        this.logger = logger;
    }

    public World getWorld() {
        return this.worldReader.get();
    }

    public BlockPos getCenter() {
        return this.scanCenter.get();
    }

    public IModLog getLogger() {
        return this.logger.get();
    }

    public Identifier getReference() {
        return this.worldReference.get();
    }

    public boolean isOutOfHeightLimit(int y) {
        return this.worldReader.get().isOutOfHeightLimit(y);
    }

    public int clampHeight(int y) {
        var world = this.worldReader.get();
        return MathHelper.clamp(y, world.getBottomY(), world.getTopY());
    }
}