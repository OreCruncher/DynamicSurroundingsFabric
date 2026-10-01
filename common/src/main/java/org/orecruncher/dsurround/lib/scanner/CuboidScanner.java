package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.random.IRandomizer;

import java.util.ArrayDeque;
import java.util.Collection;

/**
 * Scans the cuboid around the scan center, reporting blocks as they come into range ({@link #blockScan}) and, if
 * {@link #doBlockUnscan()} is true, as they leave it ({@link #blockUnscan}).
 * <p>
 * All work goes through one queue of {@link ScanJob}s processed in order, at most about {@link #blocksPerTick}
 * blocks per tick:
 * <ul>
 *   <li>a full scan of the volume when scanning starts, the world changes, or the player jumps somewhere new</li>
 *   <li>when the player moves, an unscan job for each slab that left range and a scan job for each slab that
 *       entered it</li>
 * </ul>
 * Fast movement therefore spreads its cost over several ticks instead of landing in one. Jobs walk the world one
 * chunk section at a time and skip sections that are unloaded or contain only air.
 */
public abstract class CuboidScanner extends Scanner {

    private final ArrayDeque<ScanJob> jobs = new ArrayDeque<>();

    // The volume currently in range. Null until the first scan, and while the scan center is outside the world.
    protected @Nullable BlockBox activeCuboid;
    // The client creates a new Level on every dimension change, so comparing the instance detects those (and a
    // reconnect) without relying on the dimension id
    protected @Nullable Level lastWorld = null;

    protected CuboidScanner(final ScanContext locus, final String name, final int range) {
        super(locus, name, range);
    }

    /**
     * The volume centered on {@code pos}. The height is clamped to the world's build limits, so every position in
     * the volume is a valid block position and nothing downstream needs to check.
     */
    protected BlockBox getVolumeFor(final BlockPos pos) {
        int minY = this.locus.clampHeight(pos.getY() - this.yRange);
        int maxY = this.locus.clampHeight(pos.getY() + this.yRange);
        return new BlockBox(
                new BlockPos(pos.getX() - this.xRange, minY, pos.getZ() - this.zRange),
                new BlockPos(pos.getX() + this.xRange, maxY, pos.getZ() + this.zRange));
    }

    @Override
    protected void setRange(int range) {
        // If there is a range change, we need to trigger a reset of the cuboid
        if (this.xRange != range || this.yRange != range || this.zRange != range) {
            super.setRange(range);
            this.resetFullScan();
        }
    }

    /**
     * Drops all queued work and starts a full scan of the volume around the scan center.
     */
    public void resetFullScan() {
        this.lastWorld = this.locus.getWorld();
        this.activeCuboid = this.getVolumeFor(this.locus.getScanCenter());
        this.jobs.clear();
        this.jobs.add(new ScanJob(false, this.activeCuboid));
    }

    /**
     * Blocks queued but not yet processed. Approximate: a partly processed section counts in full.
     */
    public long getPendingBlocks() {
        long pending = 0;
        for (var job : this.jobs)
            pending += job.remaining();
        return pending;
    }

    @Override
    public void tick() {
        final BlockPos center = this.locus.getScanCenter();
        if (this.locus.isOutOfHeightLimit(center.getY())) {
            // Nothing sensible to scan from outside the world; start over once back inside
            this.activeCuboid = null;
            this.jobs.clear();
            return;
        }

        var world = this.locus.getWorld();
        if (this.activeCuboid == null || world != this.lastWorld) {
            this.locus.getLogger().debug("[%s] full range reset", this.name);
            this.resetFullScan();
        } else {
            var newVolume = this.getVolumeFor(center);
            if (!newVolume.equals(this.activeCuboid))
                this.moveTo(newVolume);
        }

        this.processJobs(world);
    }

    /**
     * Queues the work for the volume moving from {@link #activeCuboid} to {@code newVolume}.
     */
    private void moveTo(final BlockBox newVolume) {
        final BlockBox oldVolume = this.activeCuboid;
        final BlockBox intersect = Cuboid.intersection(oldVolume, newVolume);

        // No overlap means the player jumped somewhere new (teleport, respawn), so start over
        if (intersect == null) {
            this.locus.getLogger().debug("[%s] no intersection: %s, %s", this.name, oldVolume, newVolume);
            this.resetFullScan();
            return;
        }

        if (this.doBlockUnscan()) {
            for (var slab : Cuboid.complement(oldVolume, intersect))
                this.jobs.add(new ScanJob(true, slab));
        }
        for (var slab : Cuboid.complement(newVolume, intersect))
            this.jobs.add(new ScanJob(false, slab));

        this.activeCuboid = newVolume;

        // Moving faster than the budget can keep up with (flying, riding) would let the queue grow without limit.
        // The queue can legitimately hold a full scan (up to one volume) plus movement work. Once it holds more
        // than two volumes, the movement work alone exceeds a full scan, so starting over is the cheaper way to
        // catch up.
        if (this.getPendingBlocks() > 2L * this.volume) {
            this.locus.getLogger().debug("[%s] scan backlog exceeds volume; full range reset", this.name);
            this.resetFullScan();
        }
    }

    private void processJobs(final Level world) {
        int budget = this.blocksPerTick;
        while (budget > 0 && !this.jobs.isEmpty()) {
            var job = this.jobs.peekFirst();
            budget = this.runJob(job, world, budget);
            if (job.isDone())
                this.jobs.pollFirst();
        }
    }

    /**
     * Works on {@code job} until it is done or the budget is spent, and returns what is left of the budget. Each
     * block read costs one; skipping a whole section costs one.
     */
    private int runJob(final ScanJob job, final Level world, int budget) {
        final var pos = this.workingPos;
        while (budget > 0 && !job.isDone()) {
            final BlockBox box = job.currentBox();
            final LevelChunkSection section = getSection(world, box.min());
            if (section == null || section.hasOnlyAir()) {
                // Unloaded, or nothing but air: no block in it can be of interest
                job.finishBox();
                budget--;
                continue;
            }

            final CuboidPointIterator points = job.points();
            while (budget > 0) {
                if (!points.next(pos)) {
                    job.finishBox();
                    break;
                }
                budget--;

                // A block that has left range since this job was queued is skipped. The unscan job for its
                // leaving was queued after this one, so the end result is the same, with less work.
                if (!job.isUnscan() && !this.activeCuboid.contains(pos))
                    continue;

                final BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                if (job.isUnscan())
                    this.unscanBlock(world, state, pos);
                else
                    this.scanBlock(world, state, pos);
            }
        }
        return budget;
    }

    /**
     * The chunk section containing {@code pos}, or null if its chunk isn't loaded.
     */
    @Nullable
    private static LevelChunkSection getSection(final Level world, final BlockPos pos) {
        var chunk = world.getChunkSource().getChunkNow(
                SectionPos.blockToSectionCoord(pos.getX()),
                SectionPos.blockToSectionCoord(pos.getZ()));
        if (chunk == null)
            return null;
        int index = world.getSectionIndex(pos.getY());
        var sections = chunk.getSections();
        return index >= 0 && index < sections.length ? sections[index] : null;
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

    /**
     * The {@link #blockUnscan} counterpart of {@link #scanBlock}: skips ignored blocks and logs, rather than
     * propagates, an exception.
     */
    protected final void unscanBlock(final Level world, final BlockState state, final BlockPos pos) {
        if (Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
            return;
        try {
            this.blockUnscan(world, state, pos, this.random);
        } catch (Throwable t) {
            this.onBlockError(t, "blockUnscan", state, pos);
        }
    }

    /**
     * Rescans blocks the client was told changed, if they are in range. Not queued: block updates are few and
     * should take effect immediately.
     */
    public void onBlockUpdates(Collection<BlockPos> positions) {
        if (positions.isEmpty() || this.activeCuboid == null)
            return;

        var world = this.locus.getWorld();
        for (var pos : positions) {
            if (this.activeCuboid.contains(pos))
                this.scanBlock(world, world.getBlockState(pos), pos);
        }
    }
}
