package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.Mth;
import net.minecraft.util.ParticleUtils;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Water dripping from what the rain soaked, for a while after it stops: from the lowest leaves of a tree, and from the
 * underside of an overhanging edge (eaves, a ledge). Vanilla drips from leaves only while it rains; this carries that
 * on as the trees dry, and adds the edges, which drip while it rains too. The drips are vanilla's own dripping water,
 * which falls and splashes as from a dripping ceiling.
 * <p>
 * How hard it drips follows how hard it rained, and eases off over {@link #DRY_TICKS}. Each tick a few columns near the
 * camera are looked at, more the wetter it is, and a drip is hung where one fits. Only where it rains: not where it
 * snows, nor in a desert. Client thread only.
 */
public final class AfterRainDrips {

    /**
     * Ticks after the rain stops that drips carry on, fewer and fewer
     */
    static final int DRY_TICKS = 1200;
    // How far from the camera, horizontally and up or down, drips are hung
    static final int RADIUS = 16;
    static final int HEIGHT_RANGE = 16;
    // Columns looked at a tick, when as wet as can be
    static final float TRIES_AT_FULL = 3F;
    // How far down through a tree's leaves to look for the lowest
    static final int MAX_LEAF_DEPTH = 8;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
    private final BlockPos.MutableBlockPos below = new BlockPos.MutableBlockPos();
    // How hard it rained, at its hardest, since things were last dry, 0 to 1; and ticks since it stopped
    private float wetness;
    private int dryTicks = DRY_TICKS;

    /**
     * Forgets how wet it is, as on leaving a world
     */
    public void clear() {
        this.wetness = 0F;
        this.dryTicks = DRY_TICKS;
    }

    /**
     * How hard it drips now, 0 to 1
     */
    public float strength() {
        return dripStrength(this.wetness, this.dryTicks);
    }

    /**
     * Follows the rain a tick, and hangs drips where they fit
     *
     * @param rainLevel the storm's strength, 0 to 1
     */
    public void tick(ClientLevel level, IColumnSource columns, double cameraX, double cameraY, double cameraZ, float rainLevel, RandomSource random) {
        boolean raining = rainLevel > 0F;
        this.soak(rainLevel);
        int tries = tries(TRIES_AT_FULL * this.strength(), random.nextFloat());
        for (int i = 0; i < tries; i++) {
            int x = Mth.floor(cameraX) + random.nextInt(RADIUS * 2 + 1) - RADIUS;
            int z = Mth.floor(cameraZ) + random.nextInt(RADIUS * 2 + 1) - RADIUS;
            this.tryDrip(level, columns, x, z, Mth.floor(cameraY), raining, random);
        }
    }

    /**
     * Notes the rain a tick: while it rains, things get as wet as the hardest rain; once it stops, they start drying
     */
    void soak(float rainLevel) {
        if (rainLevel > 0F) {
            this.wetness = Math.clamp(rainLevel, this.wetness, 1F);
            this.dryTicks = 0;
        } else if (this.dryTicks < DRY_TICKS) {
            if (++this.dryTicks == DRY_TICKS)
                this.wetness = 0F;
        }
    }

    private void tryDrip(ClientLevel level, IColumnSource columns, int x, int z, int cameraY, boolean raining, RandomSource random) {
        // The top block of the column: what the rain fell on
        int top = columns.groundHeight(x, z) - 1;
        if (Math.abs(top - cameraY) > HEIGHT_RANGE)
            return;
        if (columns.precipitation(x, top + 1, z) != PrecipitationKind.RAIN)
            return;
        var state = level.getBlockState(this.pos.set(x, top, z));
        if (!state.getFluidState().isEmpty())
            return;

        if (state.is(BlockTags.LEAVES)) {
            // While it rains, vanilla drips from leaves itself
            if (raining)
                return;
            // Down through the leaves to the lowest: the drips fall from the bottom of the tree
            for (int depth = 0; depth < MAX_LEAF_DEPTH && level.getBlockState(this.below.setWithOffset(this.pos, Direction.DOWN)).is(BlockTags.LEAVES); depth++)
                this.pos.set(this.below);
            var under = level.getBlockState(this.below.setWithOffset(this.pos, Direction.DOWN));
            if (under.is(BlockTags.LEAVES) || isSolidBelow(level, this.below, under))
                return;
        } else {
            // An edge with open air under it: eaves, a ledge. Not a roof's middle, which would drip indoors.
            var under = level.getBlockState(this.below.setWithOffset(this.pos, Direction.DOWN));
            if (!under.getCollisionShape(level, this.below).isEmpty())
                return;
            if (!isEdge(top, columns.groundHeight(x + 1, z), columns.groundHeight(x - 1, z),
                    columns.groundHeight(x, z + 1), columns.groundHeight(x, z - 1)))
                return;
        }
        ParticleUtils.spawnParticleBelow(level, this.pos, random, ParticleTypes.DRIPPING_WATER);
    }

    /**
     * Whether what is under a block stops a drip, as vanilla's leaves decide: a solid block whose top is whole
     */
    private static boolean isSolidBelow(ClientLevel level, BlockPos pos, BlockState state) {
        return state.canOcclude() && state.isFaceSturdy(level, pos, Direction.UP);
    }

    /**
     * How hard it drips, 0 to 1: as hard as it rained while it rains, then less and less, quickly at first, until
     * {@link #DRY_TICKS} after it stopped
     *
     * @param wetness  how hard it rained, at its hardest, 0 to 1
     * @param dryTicks ticks since the rain stopped; 0 while it rains
     */
    static float dripStrength(float wetness, int dryTicks) {
        if (dryTicks >= DRY_TICKS)
            return 0F;
        float remaining = 1F - dryTicks / (float) DRY_TICKS;
        return wetness * remaining * remaining;
    }

    /**
     * How many columns to look at this tick for a number wanted on average: its whole part, and one more as often as
     * its fraction says
     *
     * @param chance a random number, 0 to 1
     */
    static int tries(float wanted, float chance) {
        int whole = (int) wanted;
        return whole + (chance < wanted - whole ? 1 : 0);
    }

    /**
     * Whether the top block of a column is at an edge: the top of a column beside it is lower, so its side is in the
     * open. A roof's middle, with roof all around, is not.
     *
     * @param top    the height of the column's top block
     * @param others the ground heights (one above their top blocks) of the columns on each side
     */
    static boolean isEdge(int top, int... others) {
        for (int ground : others)
            if (ground <= top)
                return true;
        return false;
    }
}
