package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;

/**
 * How open to the weather a place is, 0 to 1: all of it out under the open sky, none in a cave or a building. The
 * wind reaches only as far (particles drifting, the wind's sounds), and so does the weather's fog and the ground fog.
 * <p>
 * Sky light alone lets too much in: it fades only a level a block sideways, so a room with a window or a door is lit
 * nearly as the outside is, and glass doesn't dim it at all. So what is overhead counts too: the blocks above the
 * place, from the top of the client's {@code MOTION_BLOCKING} heightmap down (glass is in it, so a greenhouse roof is
 * a roof). What is a roof is as {@link Roofs} says, for the ceiling scanner too. Under a roof, only what comes in
 * sideways reaches it: under an overhang, or through a doorway to the open sky a block or two away. Leaves aren't a
 * roof: under trees the sky light, which each layer of leaves dims, says how much gets through. Nor is a lantern or a
 * chain hanging overhead, a fence or a wall, a fluid (water falling past), or the place's own block (smoke starts
 * inside a campfire, mist inside the water).
 * <p>
 * Client thread only, which in Minecraft is also the render thread.
 */
public final class Shelter {

    // Sky light below which nothing reaches, and the range over which it builds to all of it
    static final int MIN_SKY_LIGHT = 8;
    static final float SKY_LIGHT_RANGE = 7F;
    // How deep cover is looked through for a roof; deeper is taken as one
    static final int MAX_COVER_DEPTH = 32;
    // Under a roof: the share that comes in a block from the open sky, falling off further in, as far as it reaches
    static final float ROOF_SHARE = 0.5F;
    static final int ROOF_REACH = 2;

    private static final Direction[] SIDES = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
    // Reused for every look at the world: client thread only
    private static final LevelColumns LEVEL_COLUMNS = new LevelColumns();
    private static final BlockPos.MutableBlockPos SCRATCH = new BlockPos.MutableBlockPos();

    private Shelter() {
    }

    /**
     * How open to the weather a place is, 0 to 1
     */
    public static float exposure(Level level, BlockPos pos) {
        var open = exposure(level.getBrightness(LightLayer.SKY, pos));
        if (open == 0F)
            return 0F;
        LEVEL_COLUMNS.level = level;
        try {
            return open * roofShare(LEVEL_COLUMNS, pos);
        } finally {
            LEVEL_COLUMNS.level = null;
        }
    }

    /**
     * How open a place with the given sky light is, before what is overhead is looked at
     */
    static float exposure(int skyLight) {
        return Mth.clamp((skyLight - MIN_SKY_LIGHT) / SKY_LIGHT_RANGE, 0F, 1F);
    }

    /**
     * What a roof over a place leaves, 0 to 1: all of it with none, otherwise what comes in sideways
     */
    static float roofShare(Columns columns, BlockPos pos) {
        return isRoofed(columns, pos) ? sideways(columns, pos) : 1F;
    }

    // A roof above the place, below the top of its column
    private static boolean isRoofed(Columns columns, BlockPos pos) {
        int top = columns.top(pos.getX(), pos.getZ());
        if (top - 1 - pos.getY() > MAX_COVER_DEPTH)
            return true;
        for (int y = top - 1; y > pos.getY(); y--) {
            SCRATCH.set(pos.getX(), y, pos.getZ());
            if (columns.isRoof(SCRATCH, columns.state(SCRATCH)))
                return true;
        }
        return false;
    }

    // The most that reaches in from the open sky beside a roofed place, through anything that doesn't block motion
    private static float sideways(Columns columns, BlockPos pos) {
        float share = 0F;
        for (var side : SIDES) {
            for (int step = 1; step <= ROOF_REACH; step++) {
                SCRATCH.setWithOffset(pos, side.getStepX() * step, 0, side.getStepZ() * step);
                if (columns.state(SCRATCH).blocksMotion())
                    break;
                // Its own block doesn't block motion, so at most a fluid's surface there
                if (SCRATCH.getY() >= columns.top(SCRATCH.getX(), SCRATCH.getZ()) - 1) {
                    share = Math.max(share, ROOF_SHARE / step);
                    break;
                }
            }
        }
        return share;
    }

    /**
     * The world, as far as {@link #roofShare} looks at it
     */
    interface Columns {
        /**
         * The lowest y with nothing above it that blocks motion or holds a fluid
         */
        int top(int x, int z);

        BlockState state(BlockPos pos);

        /**
         * Whether a block is a roof (see {@link Roofs})
         */
        boolean isRoof(BlockPos pos, BlockState state);
    }

    private static final class LevelColumns implements Columns {
        private Level level;

        @Override
        public int top(int x, int z) {
            return this.level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
        }

        @Override
        public BlockState state(BlockPos pos) {
            return this.level.getBlockState(pos);
        }

        @Override
        public boolean isRoof(BlockPos pos, BlockState state) {
            return Roofs.isRoof(this.level, pos, state, TagLibraryHolder.INSTANCE);
        }
    }

    // Resolved on first use: nothing can be handed to a static helper
    private static final class TagLibraryHolder {
        static final ITagLibrary INSTANCE = ContainerManager.resolve(ITagLibrary.class);
    }
}
