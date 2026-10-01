package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockBox;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.ChunkPos;
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
 * All work goes through one queue of {@link ScanJob}s processed in order, for up to {@link #TIME_BUDGET_NANOS}
 * each tick:
 * <ul>
 *   <li>a full scan of the volume when scanning starts, the world changes, or the player jumps somewhere new</li>
 *   <li>when the player moves, a scan job for each slab that entered range (and, if {@link #doBlockUnscan()},
 *       an unscan job for each slab that left it)</li>
 *   <li>when a chunk finishes loading, a scan job for the part of the volume it holds</li>
 * </ul>
 * Fast movement therefore spreads its cost over several ticks instead of landing in one. Jobs walk the world one
 * chunk section at a time and skip sections that are unloaded or contain only air.
 */
public abstract class CuboidScanner extends Scanner {

    private final ArrayDeque<ScanJob> jobs = new ArrayDeque<>();
    private final ScanStats stats = new ScanStats();

    // This tick's budget and counts, set up by processJobs()
    private long deadline;
    private int blocksLeft;
    private int untilClockCheck;
    private int blocksRead;
    private int sectionsSkipped;

    // The volume currently in range. Null until the first scan, and while the volume lies entirely outside the
    // world (for example flying far above the build limit).
    protected @Nullable BlockBox activeCuboid;
    // The client creates a new Level on every dimension change, so comparing the instance detects those (and a
    // reconnect) without relying on the dimension id
    protected @Nullable Level lastWorld = null;

    protected CuboidScanner(final ScanContext locus, final String name, final int range) {
        super(locus, name, range);
    }

    /**
     * The volume centered on {@code pos}, trimmed to the world's height so every position in it is a valid block
     * position and nothing downstream needs to check. Null if none of it is inside the world.
     */
    @Nullable
    protected BlockBox getVolumeFor(final BlockPos pos) {
        int minY = Math.max(pos.getY() - this.yRange, this.locus.getMinY());
        int maxY = Math.min(pos.getY() + this.yRange, this.locus.getMaxY());
        if (minY > maxY)
            return null;
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
     * Starts over: drops all queued work and starts a full scan of the volume around the scan center. Subclasses
     * override this to also discard what they built from earlier scans. Used when the world or the range changes,
     * where earlier results are no longer valid.
     */
    public void resetFullScan() {
        this.stats.countReset();
        this.startFullScan();
    }

    /**
     * Drops all queued work and starts a full scan, without telling subclasses to discard anything. Used when
     * earlier results are still valid but the queue is no longer the best way to bring them up to date: a jump to a
     * new place in the same world, or a backlog bigger than a full scan. Blocks already reported may be reported
     * again, so {@link #blockScan} must tolerate repeats.
     */
    protected final void rescan() {
        this.stats.countRescan();
        this.startFullScan();
    }

    private void startFullScan() {
        this.lastWorld = this.locus.getWorld();
        this.activeCuboid = this.getVolumeFor(this.locus.getScanCenter());
        this.jobs.clear();
        if (this.activeCuboid != null)
            this.jobs.add(new ScanJob(false, this.activeCuboid));
    }

    /**
     * Timing and throughput, for diagnostics.
     */
    public ScanStats getStats() {
        return this.stats;
    }

    /**
     * Number of queued jobs, for diagnostics.
     */
    public int getPendingJobs() {
        return this.jobs.size();
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
        final BlockBox newVolume = this.getVolumeFor(this.locus.getScanCenter());
        if (newVolume == null) {
            // The whole volume is above or below the world; start over once some of it is back inside
            this.activeCuboid = null;
            this.jobs.clear();
            return;
        }

        var world = this.locus.getWorld();
        if (this.activeCuboid == null || world != this.lastWorld) {
            this.locus.getLogger().debug("[%s] full range reset", this.name);
            this.resetFullScan();
        } else if (!newVolume.equals(this.activeCuboid)) {
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

        // No overlap means the player jumped somewhere new in the same world (teleport, respawn), so scan the new
        // volume from scratch. Nothing from the old volume is in range any more.
        if (intersect == null) {
            this.locus.getLogger().debug("[%s] no intersection: %s, %s", this.name, oldVolume, newVolume);
            this.rescan();
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
        // What was found so far is still valid, so this is a rescan rather than a reset.
        if (this.getPendingBlocks() > 2L * this.volume) {
            this.locus.getLogger().debug("[%s] scan backlog exceeds volume; rescanning", this.name);
            this.rescan();
        }
    }

    private void processJobs(final Level world) {
        final long start = System.nanoTime();
        this.deadline = start + TIME_BUDGET_NANOS;
        this.blocksLeft = MAX_BLOCKS_TICK;
        this.untilClockCheck = CLOCK_CHECK_INTERVAL;
        this.blocksRead = 0;
        this.sectionsSkipped = 0;

        while (this.blocksLeft > 0 && !this.jobs.isEmpty()) {
            var job = this.jobs.peekFirst();
            this.runJob(job, world);
            if (job.isDone())
                this.jobs.pollFirst();
        }

        this.stats.record(System.nanoTime() - start, this.blocksRead, this.sectionsSkipped);
    }

    /**
     * Uses one unit of this tick's budget. Every {@link #CLOCK_CHECK_INTERVAL} units the clock is checked, and once
     * the time is up the budget is emptied.
     */
    private void spend() {
        this.blocksLeft--;
        if (--this.untilClockCheck == 0) {
            this.untilClockCheck = CLOCK_CHECK_INTERVAL;
            if (System.nanoTime() >= this.deadline)
                this.blocksLeft = 0;
        }
    }

    /**
     * Works on {@code job} until it is done or this tick's budget is spent. Each block read costs one unit;
     * skipping a whole section costs one.
     */
    private void runJob(final ScanJob job, final Level world) {
        final var pos = this.workingPos;
        while (this.blocksLeft > 0 && !job.isDone()) {
            final BlockBox box = job.currentBox();
            final LevelChunkSection section = getSection(world, box.min());
            if (section == null || section.hasOnlyAir()) {
                // Unloaded, or nothing but air: no block in it can be of interest
                job.finishBox();
                this.spend();
                this.sectionsSkipped++;
                continue;
            }

            final CuboidPointIterator points = job.points();
            while (this.blocksLeft > 0) {
                if (!points.next(pos)) {
                    job.finishBox();
                    break;
                }
                this.spend();
                this.blocksRead++;

                // A block that has left range since this job was queued is skipped. If unscans are on, the
                // unscan job for its leaving was queued after this one, so the end result is the same with less
                // work; if they're off, nothing needs doing for a block out of range.
                if (!job.isUnscan() && !this.activeCuboid.contains(pos))
                    continue;

                final BlockState state = section.getBlockState(pos.getX() & 15, pos.getY() & 15, pos.getZ() & 15);
                if (job.isUnscan())
                    this.unscanBlock(world, state, pos);
                else
                    this.scanBlock(world, state, pos);
            }
        }
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
     * Queues a scan of the part of the volume in a chunk that has just loaded. Chunks keep arriving after a scan
     * starts (joining, respawning, a scan range beyond the render distance), and a section that wasn't loaded when
     * the scan reached it was skipped.
     */
    public void onChunkLoaded(final Level world, final ChunkPos chunkPos) {
        if (this.activeCuboid == null || world != this.lastWorld)
            return;

        var column = new BlockBox(
                new BlockPos(chunkPos.getMinBlockX(), this.activeCuboid.min().getY(), chunkPos.getMinBlockZ()),
                new BlockPos(chunkPos.getMaxBlockX(), this.activeCuboid.max().getY(), chunkPos.getMaxBlockZ()));
        var inRange = Cuboid.intersection(column, this.activeCuboid);
        if (inRange != null)
            this.jobs.add(new ScanJob(false, inRange));
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
