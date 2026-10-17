package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for where a snowflake blown in from the side ends up: followed from where it was, the way it was moving, to
 * the first thing whose shape is in its way. A small world of real blocks stands in for the level.
 */
public class SnowStickingTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final int GROUND = 63;

    /**
     * Stone ground at y 63, and whatever else is placed; air elsewhere
     */
    private static final class World implements BlockGetter {
        private final Map<BlockPos, BlockState> blocks = new HashMap<>();

        World set(int x, int y, int z, BlockState state) {
            this.blocks.put(new BlockPos(x, y, z), state);
            return this;
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            if (pos.getY() <= GROUND)
                return Blocks.STONE.defaultBlockState();
            return this.blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @Override
        public FluidState getFluidState(BlockPos pos) {
            return this.getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 384;
        }

        @Override
        public int getMinBuildHeight() {
            return -64;
        }
    }

    /**
     * A tree at x 0, z 0: a log trunk from the ground up to 69, under a slab of leaves at 70 reaching two blocks
     * each way
     */
    private static World tree() {
        var world = new World();
        for (int y = GROUND + 1; y < 70; y++)
            world.set(0, y, 0, Blocks.OAK_LOG.defaultBlockState());
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 2; z++)
                world.set(x, 70, z, Blocks.OAK_LEAVES.defaultBlockState());
        return world;
    }

    @Test
    void blownInUnderACanopyItSticksToTheTrunk() {
        // Regression: the trunk is under the canopy, where no flake falls; only one blown in under it reaches it
        var hit = Landings.trace(tree(), new Vec3(-4.5, 68.5, 0.5), 0.3F, -0.05F, 0F);
        assertNotNull(hit);
        assertEquals(Direction.WEST, hit.getDirection());
        assertEquals(Blocks.OAK_LOG, tree().getBlockState(hit.getBlockPos()).getBlock());
        assertEquals(0D, hit.getLocation().x, 1e-6, "on the trunk's west face");
        assertTrue(hit.getLocation().y < 68.5D, "lower than it started, still falling");
    }

    @Test
    void inLightWindItLandsOnTheGroundUnderTheCanopyEdge() {
        var hit = Landings.trace(tree(), new Vec3(-4.5, 68.5, 0.5), 0.02F, -0.08F, 0F);
        assertNotNull(hit);
        assertEquals(Direction.UP, hit.getDirection());
        assertEquals(GROUND + 1D, hit.getLocation().y, 1e-6);
        assertTrue(hit.getLocation().x < -2D, "short of the trunk");
    }

    @Test
    void itSticksToTheSideOfLeaves() {
        // Regression: leaves hold nothing up, so their faces aren't sturdy, which turned flakes away from them
        var hit = Landings.trace(tree(), new Vec3(-3.5, 70.5, 0.5), 0.3F, -0.05F, 0F);
        assertNotNull(hit);
        assertEquals(Direction.WEST, hit.getDirection());
        assertEquals(Blocks.OAK_LEAVES, tree().getBlockState(hit.getBlockPos()).getBlock());
    }

    @Test
    void itPassesAFencePostByAndGoesOn() {
        // A post stands in the middle of its block: a flake beside it passes, and reaches the trunk beyond
        var world = tree().set(-1, GROUND + 1, 0, Blocks.OAK_FENCE.defaultBlockState());
        var hit = Landings.trace(world, new Vec3(-4.5, GROUND + 1.5, 0.1), 0.3F, -0.01F, 0F);
        assertNotNull(hit);
        assertEquals(Blocks.OAK_LOG, world.getBlockState(hit.getBlockPos()).getBlock());

        var through = Landings.trace(world, new Vec3(-4.5, GROUND + 1.5, 0.5), 0.3F, -0.01F, 0F);
        assertNotNull(through);
        assertEquals(Blocks.OAK_FENCE, world.getBlockState(through.getBlockPos()).getBlock(), "straight at the post, it hits it");
    }

    @Test
    void nothingWithinReachOrNoMotionIsNothing() {
        // Level with the ground far off: nothing within reach
        assertNull(Landings.trace(tree(), new Vec3(-30.5, 66.5, 0.5), 0.3F, 0F, 0F));
        assertNull(Landings.trace(tree(), new Vec3(-4.5, 68.5, 0.5), 0F, 0F, 0F));
    }
}
