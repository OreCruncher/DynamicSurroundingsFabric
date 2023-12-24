package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.util.math.BlockPos;
import org.orecruncher.dsurround.lib.random.LCGRandom;

import java.util.Random;

public abstract class RandomScanner extends Scanner {

    private final LCGRandom lcg = new LCGRandom();

    private int playerX;
    private int playerY;
    private int playerZ;

    public RandomScanner(final ScanContext locus, final String name, final int range,
                         final int blocksPerTick) {
        super(locus, name, range, blocksPerTick);
    }

    private int randomRange(final int range) {
        return this.lcg.nextInt(range) - this.lcg.nextInt(range);
    }

    @Override
    public void preScan() {
        final BlockPos pos = this.locus.getCenter();
        this.playerX = pos.getX();
        this.playerY = pos.getY();
        this.playerZ = pos.getZ();
    }

    @Override
    protected BlockPos nextPos(final BlockPos.Mutable workingPos, final Random rand) {
        return workingPos.set(this.playerX + randomRange(this.xRange), this.playerY + randomRange(this.yRange),
                this.playerZ + randomRange(this.zRange));
    }

}