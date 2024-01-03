package org.orecruncher.dsurround.lib.scanner;

import it.unimi.dsi.fastutil.objects.ReferenceArraySet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.Random;
import java.util.Set;

public abstract class Scanner {

    protected static final Set<BlockState> BLOCKSTATES_TO_IGNORE = new ReferenceArraySet<>(3);
    private final static int MAX_BLOCKS_TICK = 6000;

    static {
        BLOCKSTATES_TO_IGNORE.add(Blocks.VOID_AIR.defaultBlockState());
        BLOCKSTATES_TO_IGNORE.add(Blocks.CAVE_AIR.defaultBlockState());
        BLOCKSTATES_TO_IGNORE.add(Blocks.AIR.defaultBlockState());
    }

    protected final String name;

    protected int xRange;
    protected int yRange;
    protected int zRange;

    protected int xSize;
    protected int ySize;
    protected int zSize;
    protected int blocksPerTick;
    protected int volume;

    protected final ScanContext locus;

    protected final Random random = new Randomizer();
    protected final BlockPos.MutableBlockPos workingPos = new BlockPos.MutableBlockPos();

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
        this.blocksPerTick = Math.min(this.volume / 20, MAX_BLOCKS_TICK);
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
    public abstract void blockScan(final Level world, final BlockState state, final BlockPos pos, final Random rand);

    public void tick() {
        var world = this.locus.getWorld();
        for (int count = 0; count < this.blocksPerTick; count++) {
            final BlockPos pos = nextPos(this.workingPos, this.random);
            if (pos == null)
                break;
            final BlockState state = world.getBlockState(pos);
            if (BLOCKSTATES_TO_IGNORE.contains(state))
                continue;
            blockScan(world, state, pos, this.random);
        }
    }

    /**
     * Provide the next block position to be processed. For memory efficiency the
     * provided mutable should be used to store the coordinate information and
     * returned from the function call.
     */
    @Nullable
    protected abstract BlockPos nextPos(final BlockPos.MutableBlockPos pos, final Random rand);

}