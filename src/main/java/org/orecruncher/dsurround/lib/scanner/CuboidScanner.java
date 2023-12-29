package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.block.BlockState;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.jetbrains.annotations.Nullable;

import java.util.Random;

public abstract class CuboidScanner extends Scanner {

    // Iteration variables
    protected boolean scanFinished = false;
    protected Cuboid activeCuboid;
    protected CuboidPointIterator fullRange;

    // State of last tick
    protected BlockPos lastPos;
    protected Identifier lastReference = new Identifier("dsurround:aintnothin");

    protected CuboidScanner(final ScanContext locus, final String name, final int range,
                            final int blocksPerTick) {
        super(locus, name, range, blocksPerTick);
    }

    protected CuboidScanner(final ScanContext locus, final String name, final int xRange,
                            final int yRange, final int zRange) {
        super(locus, name, xRange, yRange, zRange);
    }

    protected CuboidScanner(final ScanContext locus, final String name, final int xSize,
                            final int ySize, final int zSize, final int blocksPerTick) {
        super(locus, name, xSize, ySize, zSize, blocksPerTick);
    }

    public boolean isScanFinished() {
        return this.scanFinished;
    }

    protected BlockPos[] getMinMaxPointsForVolume(final BlockPos pos) {
        var mutable = new BlockPos.Mutable();

        mutable.set(pos, -this.xRange, -this.yRange, -this.zRange);
        mutable.setY(this.locus.clampHeight(mutable.getY()));
        var min = mutable.toImmutable();

        mutable.set(pos, this.xRange, this.yRange, this.zRange);
        mutable.setY(this.locus.clampHeight(mutable.getY()));
        var max = mutable.toImmutable();

        return new BlockPos[]{min, max};
    }

    protected Cuboid getVolumeFor(final BlockPos pos) {
        final BlockPos[] points = getMinMaxPointsForVolume(pos);
        return new Cuboid(points);
    }

    public void resetFullScan() {
        this.lastPos = this.locus.scanCenter().get();
        this.lastReference = this.locus.worldReference().get();
        this.scanFinished = false;

        final BlockPos[] points = getMinMaxPointsForVolume(this.lastPos);
        this.activeCuboid = new Cuboid(points);
        this.fullRange = new CuboidPointIterator(points);
    }

    @Override
    public void tick() {

        // If there is no player position, or it's bogus just return
        final BlockPos playerPos = this.locus.scanCenter().get();
        if (this.locus.isOutOfHeightLimit(playerPos.getY())) {
            this.fullRange = null;
        } else {
            // If the full range was reset, or the player dimension changed,
            // dump everything and restart.
            if (this.fullRange == null || this.locus.worldReference().get() != this.lastReference) {
                this.locus.logger().debug("[%s] full range reset", this.name);
                resetFullScan();
                super.tick();
            } else if (this.lastPos.equals(playerPos)) {
                // The player didn't move. If a scan is in progress
                // continue.
                if (!this.scanFinished)
                    super.tick();
            } else {
                // The player moved.
                final Cuboid oldVolume = this.activeCuboid != null ? this.activeCuboid : getVolumeFor(this.lastPos);
                final Cuboid newVolume = getVolumeFor(playerPos);
                final Cuboid intersect = oldVolume.intersection(newVolume);

                // If there is no intersection, it means the player moved
                // enough of a distance in the last tick to make it a new
                // area. Otherwise, if there is a sufficiently large
                // change to the scan area dump and restart.
                if (intersect == null) {
                    this.locus.logger().debug("[%s] no intersection: %s, %s", this.name, oldVolume.toString(), newVolume.toString() );
                    resetFullScan();
                    super.tick();
                } else {

                    // Looks to be a small update, like a player walking around.
                    // If the scan has already completed, we do an update.
                    if (this.scanFinished) {
                        this.lastPos = playerPos;
                        this.activeCuboid = newVolume;
                        updateScan(newVolume, oldVolume, intersect);
                    } else {
                        // The existing scan hasn't completed, but now we
                        // have a delta set. Finish out scanning the
                        // old volume, and once that is locked, then a
                        // subsequent tick will do a delta update to get
                        // the new blocks.
                        super.tick();
                    }
                }
            }
        }
    }

    /**
     * Override to have unscan notifications invoked when processing a block.
     */
    public boolean doBlockUnscan() {
        return false;
    }

    /**
     * This is the hook that gets called when a block goes out of scope because the
     * player moved or something.
     */
    public void blockUnscan(final World world, final BlockState state, final BlockPos pos, final Random rand) {

    }

    protected void updateScan(final Cuboid newVolume, final Cuboid oldVolume,
                              final Cuboid intersect) {

        final World provider = this.locus.world().get();

        if (doBlockUnscan()) {
            final ComplementsPointIterator newOutOfRange = new ComplementsPointIterator(oldVolume, intersect);
            // Notify on the blocks going out of range
            for (BlockPos point = newOutOfRange.next(); point != null; point = newOutOfRange.next()) {
                if (!this.locus.isOutOfHeightLimit(point.getY())) {
                    final BlockState state = provider.getBlockState(point);
                    blockUnscan(provider, state, point, this.random);
                }
            }
        }

        // Notify on blocks coming into range
        final ComplementsPointIterator newInRange = new ComplementsPointIterator(newVolume, intersect);
        for (BlockPos point = newInRange.next(); point != null; point = newInRange.next()) {
            if (!this.locus.isOutOfHeightLimit(point.getY())) {
                final BlockState state = provider.getBlockState(point);
                blockScan(provider, state, point, this.random);
            }
        }

        this.scanFinished = true;
    }

    @Override
    @Nullable
    protected BlockPos nextPos(final BlockPos.Mutable workingPos, final Random rand) {

        if (this.scanFinished)
            return null;

        int checked = 0;

        BlockPos point;
        while ((point = this.fullRange.peek()) != null) {

            // Consume the point
            this.fullRange.next();

            // Has to be in valid space for it to
            // be returned.
            if (!this.locus.isOutOfHeightLimit(point.getY())) {
                return point;
            }

            // Advance our check counter and loop back
            // to examine the next point.
            if (++checked >= this.blocksPerTick)
                return null;
        }

        this.scanFinished = true;
        return null;
    }

    public void onBlockUpdate(final BlockPos pos) {
        try {
            if (this.activeCuboid != null && this.activeCuboid.contains(pos)) {
                var world = this.locus.world().get();
                final BlockState state = world.getBlockState(pos);
                blockScan(world, state, pos, this.random);
            }
        } catch (final Throwable t) {
            this.locus.logger().error(t, "onBlockUpdate() error");
        }
    }
}