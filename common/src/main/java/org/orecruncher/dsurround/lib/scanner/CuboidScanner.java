package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.Collection;

public abstract class CuboidScanner extends Scanner {

    // Iteration variables
    protected boolean scanFinished = false;
    protected BlockBox activeCuboid;
    protected CuboidPointIterator fullRange;

    // State of last tick
    protected BlockPos lastPos = BlockPos.ZERO;
    protected ResourceLocation lastReference = ResourceLocation.parse("dsurround:aintnothin");

    protected CuboidScanner(final ScanContext locus, final String name, final int range) {
        super(locus, name, range);
    }

    protected BlockPos[] getMinMaxPointsForVolume(final BlockPos pos) {
        var mutable = new BlockPos.MutableBlockPos();

        mutable.setWithOffset(pos, -this.xRange, -this.yRange, -this.zRange);
        mutable.setY(this.locus.clampHeight(mutable.getY()));
        var min = mutable.immutable();

        mutable.setWithOffset(pos, this.xRange, this.yRange, this.zRange);
        mutable.setY(this.locus.clampHeight(mutable.getY()));
        var max = mutable.immutable();

        return new BlockPos[]{min, max};
    }

    protected BlockBox getVolumeFor(final BlockPos pos) {
        final BlockPos[] points = getMinMaxPointsForVolume(pos);
        return Cuboid.of(points);
    }

    @Override
    protected void setRange(int range) {
        // If there is a range change, we need to trigger a reset of the cuboid
        if (this.xRange != range || this.yRange != range || this.zRange != range) {
            super.setRange(range);
            this.resetFullScan();
        }
    }

    public void resetFullScan() {
        this.lastPos = this.locus.getScanCenter();
        this.lastReference = this.locus.getWorldReference();
        this.scanFinished = false;

        final BlockPos[] points = getMinMaxPointsForVolume(this.lastPos);
        this.activeCuboid = Cuboid.of(points);
        this.fullRange = new CuboidPointIterator(points);
    }

    @Override
    public void tick() {

        // If there is no player position, or it's bogus just return
        final BlockPos playerPos = this.locus.getScanCenter();
        if (this.locus.isOutOfHeightLimit(playerPos.getY())) {
            this.fullRange = null;
        } else if (this.fullRange == null || this.locus.getWorldReference() != this.lastReference) {
            // If the full range was reset, or the player dimension changed,
            // dump everything and restart.
            this.locus.getLogger().debug("[%s] full range reset", this.name);
            resetFullScan();
            super.tick();
        } else if (this.lastPos.equals(playerPos)) {
            // The player didn't move. If a scan is in progress
            // continue.
            if (!this.scanFinished)
                super.tick();
        } else {
            // The player moved.
            final BlockBox oldVolume = this.activeCuboid != null ? this.activeCuboid : getVolumeFor(this.lastPos);
            final BlockBox newVolume = getVolumeFor(playerPos);
            final BlockBox intersect = Cuboid.intersection(oldVolume, newVolume);

            // If there is no intersection, it means the player moved
            // enough of a distance in the last tick to make it a new
            // area, otherwise, if there is a large enough change to
            // the scan area dump and restart.
            if (intersect == null) {
                this.locus.getLogger().debug("[%s] no intersection: %s, %s", this.name, oldVolume.toString(), newVolume.toString());
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
                    // later tick will do a delta update to get
                    // the new blocks.
                    super.tick();
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
    public void blockUnscan(final Level world, final BlockState state, final BlockPos pos, final IRandomizer rand) {

    }

    protected void updateScan(final BlockBox newVolume, final BlockBox oldVolume,
                              final BlockBox intersect) {

        var provider = this.locus.getWorld();

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
    protected BlockPos nextPos(final BlockPos.MutableBlockPos workingPos, final IRandomizer rand) {

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

    public void onBlockUpdates(Collection<BlockPos> positions) {
        if (!positions.isEmpty() && this.activeCuboid != null) {
            var world = this.locus.getWorld();
            positions.stream()
                    .filter(p -> this.activeCuboid.contains(p))
                    .forEach(p -> {
                        var state = world.getBlockState(p);
                        if (!Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
                            try {
                                blockScan(world, state, p, this.random);
                            } catch (Throwable t) {
                                this.locus.getLogger().error(t, "onBlockUpdate() error %s for state %s", p.toString(), state.toString());
                            }
                    });
        }
    }
}