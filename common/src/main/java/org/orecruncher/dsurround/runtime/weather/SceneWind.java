package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

/**
 * The wind around the player, for effects to lean with: waterfall mist, steam, breath. Set each tick by the
 * {@link WindService}; read by particles, which are made far from anything the service could be handed to. Before the
 * first tick, and out of the game, it is still air.
 * <p>
 * Effects lean only as far as they are out under the open sky: a cave or a building keeps the wind off, trees some of
 * it. Client
 * thread only, which in Minecraft is also the render thread.
 */
public final class SceneWind {

    /**
     * How much of the wind's speed something light, like mist or steam, is carried at, once it has caught it
     */
    public static final float CARRIED_SHARE = 0.5F;
    // The share of the gap to the wind a drift closes each tick: what the mod's smoke loses to its drag (0.93)
    static final float CATCH_RATE = 0.07F;
    // Skylight below which there is no wind, and the range over which it builds to full, as for the weather's fog
    static final int MIN_SKY_LIGHT = 8;
    static final float SKY_LIGHT_RANGE = 7F;
    // The share of the wind under trees, and how deep a canopy can be before it counts as a roof
    static final float CANOPY_SHARE = 0.6F;
    static final int MAX_CANOPY_DEPTH = 32;
    // Under a roof: the share of the wind a block from the open sky, halving a block further in, as far as it reaches
    static final float ROOF_SHARE = 0.5F;
    static final int ROOF_REACH = 2;

    private static Wind wind;
    private static double originX;
    private static double originZ;

    private SceneWind() {
    }

    /**
     * The wind now, and where the player is: a gust passes across the area from upwind of them
     */
    static void update(Wind current, double cameraX, double cameraZ) {
        wind = current;
        originX = cameraX;
        originZ = cameraZ;
    }

    static void clear() {
        wind = null;
    }

    /**
     * The wind at a place, east-west and north-south, in blocks a tick: the steady wind, stronger where a gust is
     * passing
     */
    public static float x(double x, double z) {
        var current = wind;
        return current == null ? 0F : current.x() * gustAt(current, x, z);
    }

    public static float z(double x, double z) {
        var current = wind;
        return current == null ? 0F : current.z() * gustAt(current, x, z);
    }

    private static float gustAt(Wind current, double x, double z) {
        var along = (float) ((x - originX) * current.directionX() + (z - originZ) * current.directionZ());
        return current.gustFactor(along, 0F);
    }

    /**
     * How much of the wind reaches a place, 0 to 1: all of it out under the open sky, none in a cave or a building
     */
    public static float exposure(Level level, BlockPos pos) {
        var open = exposure(level.getBrightness(LightLayer.SKY, pos));
        if (open == 0F)
            return 0F;
        return open * shelter(new Columns() {
            @Override
            public int top(int x, int z) {
                return level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            }

            @Override
            public BlockState state(BlockPos at) {
                return level.getBlockState(at);
            }

            @Override
            public boolean isLeaves(BlockState state) {
                return state.is(BlockTags.LEAVES);
            }
        }, pos);
    }

    static float exposure(int skyLight) {
        return Mth.clamp((skyLight - MIN_SKY_LIGHT) / SKY_LIGHT_RANGE, 0F, 1F);
    }

    /**
     * What a place's cover leaves of the wind, 0 to 1. Sky light alone lets too much in: it fades only a level a block
     * sideways, so a room with a window or a door is lit nearly as the outside is, and glass doesn't dim it at all. So
     * what is overhead counts too, from the client's {@code MOTION_BLOCKING} heightmap (glass is in it, so a greenhouse
     * roof is a roof):
     * <ul>
     *     <li>nothing: all of it</li>
     *     <li>only leaves, with air between: some of it, through the trees</li>
     *     <li>a roof: only what comes in sideways, under an overhang or through a doorway to the open sky a block or
     *     two away</li>
     * </ul>
     */
    static float shelter(Columns columns, BlockPos pos) {
        int top = columns.top(pos.getX(), pos.getZ());
        if (pos.getY() >= top)
            return 1F;
        if (onlyLeavesAbove(columns, pos, top))
            return CANOPY_SHARE;
        float share = 0F;
        var at = new BlockPos.MutableBlockPos();
        for (var direction : Direction.Plane.HORIZONTAL) {
            for (int step = 1; step <= ROOF_REACH; step++) {
                at.setWithOffset(pos, direction.getStepX() * step, 0, direction.getStepZ() * step);
                if (columns.state(at).blocksMotion())
                    break;
                if (at.getY() >= columns.top(at.getX(), at.getZ())) {
                    share = Math.max(share, ROOF_SHARE / step);
                    break;
                }
            }
        }
        return share;
    }

    // Leaves (and the air between them) from the top of the column down to just above the place
    private static boolean onlyLeavesAbove(Columns columns, BlockPos pos, int top) {
        if (top - pos.getY() > MAX_CANOPY_DEPTH)
            return false;
        var at = new BlockPos.MutableBlockPos();
        for (int y = top - 1; y > pos.getY(); y--) {
            var state = columns.state(at.set(pos.getX(), y, pos.getZ()));
            if (!state.isAir() && !columns.isLeaves(state))
                return false;
        }
        return true;
    }

    /**
     * The world, as far as {@link #shelter} looks at it
     */
    interface Columns {
        /**
         * The lowest y with nothing above it that blocks motion
         */
        int top(int x, int z);

        BlockState state(BlockPos pos);

        boolean isLeaves(BlockState state);
    }

    /**
     * The push a tick that carries something with the given drag at {@link #CARRIED_SHARE} of the wind's speed, once
     * it has caught it: against drag that keeps {@code friction} of its speed each tick, it settles where the push makes
     * up what the drag takes
     *
     * @param wind     the wind's speed this way, from {@link #x} or {@link #z}
     * @param exposure from {@link #exposure}
     * @param friction the share of its speed it keeps each tick, as a particle's friction
     */
    public static float push(float wind, float exposure, float friction) {
        return wind * CARRIED_SHARE * exposure * (1F - friction);
    }

    /**
     * The drift a tick, after this one, of something blown whose drag can't be relied on (vanilla's and other mods'
     * particles: campfire smoke has none, so a {@link #push} would speed it up for ever). Added to how far it moves,
     * not to its speed, it closes {@link #CATCH_RATE} of the gap to {@link #CARRIED_SHARE} of the wind each tick: a new
     * particle catches the wind, and a gust eases in and out, about as the mod's own smoke does against its drag.
     *
     * @param current  the drift last tick, 0 for a new particle
     * @param wind     the wind's speed this way, from {@link #x} or {@link #z}
     * @param exposure from {@link #exposure}
     */
    public static float drift(float current, float wind, float exposure) {
        return current + (wind * CARRIED_SHARE * exposure - current) * CATCH_RATE;
    }
}
