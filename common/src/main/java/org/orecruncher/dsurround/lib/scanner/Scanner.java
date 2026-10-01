package org.orecruncher.dsurround.lib.scanner;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

public abstract class Scanner {

    /**
     * How long scanning may run each tick. Scanning runs on the client thread, so this is time taken from the
     * frame; 1 ms is about 6% of a frame at 60 fps. This is the real limit; the block count below is a backstop.
     */
    protected final static long TIME_BUDGET_NANOS = 1_000_000L;
    /**
     * Upper bound on blocks read per tick regardless of time, in case the clock misbehaves.
     */
    protected final static int MAX_BLOCKS_TICK = 65_536;
    /**
     * How many blocks are read between checks of the clock. Reading the clock isn't free, and a block costs well
     * under a microsecond, so checking every block would be wasteful. The budget can be overrun by at most this
     * many blocks.
     */
    protected final static int CLOCK_CHECK_INTERVAL = 256;
    // After this many failures only a count is kept, so a broken handler can't flood the log
    private final static int MAX_LOGGED_ERRORS = 10;

    protected final String name;

    protected int xRange;
    protected int yRange;
    protected int zRange;

    protected int xSize;
    protected int ySize;
    protected int zSize;
    protected int volume;

    protected final ScanContext locus;

    protected final IRandomizer random = Randomizer.current();
    protected final BlockPos.MutableBlockPos workingPos = new BlockPos.MutableBlockPos();

    private int errorCount = 0;

    public Scanner(final ScanContext locus, final String name, final int range) {
        this(locus, name, range, range, range);
    }

    public Scanner(final ScanContext locus, final String name, final int xRange, final int yRange, final int zRange) {
        this.name = name;
        this.locus = locus;

        this.setRange(xRange, yRange, zRange);
    }

    protected void setRange(int range) {
        this.setRange(range, range, range);
    }

    protected void setRange(int xRange, int yRange, int zRange) {
        this.xRange = xRange;
        this.yRange = yRange;
        this.zRange = zRange;

        this.xSize = xRange * 2 + 1;
        this.ySize = yRange * 2 + 1;
        this.zSize = zRange * 2 + 1;
        this.volume = this.xSize * this.ySize * this.zSize;
    }

    /**
     * The volume of the scan area
     */
    public int getVolume() {
        return this.volume;
    }

    /**
     * Invoked when a block of interest is discovered. The BlockPos provided is not
     * safe to hold on to beyond the call, so if it needs to be kept, it needs to be
     * copied.
     */
    public abstract void blockScan(final Level world, final BlockState state, final BlockPos pos, final IRandomizer rand);

    /**
     * Does this tick's share of scanning, within {@link #TIME_BUDGET_NANOS} (and at most {@link #MAX_BLOCKS_TICK}
     * blocks).
     */
    public abstract void tick();

    /**
     * Passes a block to {@link #blockScan} unless it is one of {@link Constants#BLOCKS_TO_IGNORE}. Every scan path
     * goes through here, so they all skip the same blocks, and an exception from one block is logged instead of
     * abandoning the rest of the scan.
     */
    protected final void scanBlock(final Level world, final BlockState state, final BlockPos pos) {
        if (Constants.BLOCKS_TO_IGNORE.contains(state.getBlock()))
            return;
        try {
            this.blockScan(world, state, pos, this.random);
        } catch (Throwable t) {
            this.onBlockError(t, "blockScan", state, pos);
        }
    }

    /**
     * Logs a failure from a block handler. Errors the JVM can't recover from (out of memory, stack overflow) are
     * rethrown rather than swallowed.
     */
    protected final void onBlockError(final Throwable t, final String handler, final BlockState state, final BlockPos pos) {
        if (t instanceof VirtualMachineError fatal)
            throw fatal;

        this.errorCount++;
        if (this.errorCount <= MAX_LOGGED_ERRORS) {
            this.locus.getLogger().error(t, "[%s] %s failed at %s for %s", this.name, handler, pos.toShortString(), state);
            if (this.errorCount == MAX_LOGGED_ERRORS)
                this.locus.getLogger().warn("[%s] %d block errors; further errors will not be logged", this.name, MAX_LOGGED_ERRORS);
        }
    }
}
