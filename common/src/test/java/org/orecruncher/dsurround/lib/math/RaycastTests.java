package org.orecruncher.dsurround.lib.math;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for ray tracing through a small fake world of stone blocks. Uses the real vanilla clip logic.
 */
public class RaycastTests {

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Stone at the given positions, air everywhere else.
     */
    static final class FakeWorld implements BlockGetter {
        private final Set<BlockPos> stone = new HashSet<>();

        FakeWorld stone(int x, int y, int z) {
            this.stone.add(new BlockPos(x, y, z));
            return this;
        }

        @Override
        public BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.stone.contains(pos) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
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

    private static List<BlockHitResult> hits(FakeWorld world, Vec3 start, Vec3 end) {
        var context = new ReusableRaycastContext(world, start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var iterator = new ReusableRaycastIterator(context);
        var result = new ArrayList<BlockHitResult>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
            assertTrue(result.size() < 100, "runaway iteration");
        }
        return result;
    }

    private static List<BlockPos> positions(List<BlockHitResult> hits) {
        return hits.stream().map(BlockHitResult::getBlockPos).toList();
    }

    // ---- Context ---------------------------------------------------------------------------------------------

    @Test
    void contextTracesWithoutAPlayer() {
        // Regression: the constructors needed the player, and threw when there wasn't one
        var world = new FakeWorld().stone(5, 0, 0);
        var context = new ReusableRaycastContext(world, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);

        var hit = context.trace(new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5));

        assertEquals(HitResult.Type.BLOCK, hit.getType());
        assertEquals(new BlockPos(5, 0, 0), hit.getBlockPos());
        assertEquals(5.0, hit.getLocation().x, 1.0E-9);
    }

    @Test
    void contextCanBeReused() {
        var world = new FakeWorld().stone(5, 0, 0).stone(0, 0, 5);
        var context = new ReusableRaycastContext(world, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);

        assertEquals(new BlockPos(5, 0, 0), context.trace(new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5)).getBlockPos());
        assertEquals(new BlockPos(0, 0, 5), context.trace(new Vec3(0.5, 0.5, 0.5), new Vec3(0.5, 0.5, 10.5)).getBlockPos());
        assertEquals(HitResult.Type.MISS, context.trace(new Vec3(0.5, 0.5, 0.5), new Vec3(0.5, 10.5, 0.5)).getType());
    }

    @Test
    void contextCanMoveBetweenWorlds() {
        // How the sound processor keeps one per thread: attached to the world while tracing, then let go of
        var first = new FakeWorld().stone(5, 0, 0);
        var second = new FakeWorld().stone(3, 0, 0);
        var context = new ReusableRaycastContext(null, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var start = new Vec3(0.5, 0.5, 0.5);
        var end = new Vec3(10.5, 0.5, 0.5);

        context.setWorld(first);
        assertEquals(new BlockPos(5, 0, 0), context.trace(start, end).getBlockPos());
        context.setWorld(second);
        assertEquals(new BlockPos(3, 0, 0), context.trace(start, end).getBlockPos());
        context.setWorld(null);
        assertThrows(NullPointerException.class, () -> context.trace(start, end), "no world is kept once let go of");
    }

    private static List<BlockHitResult> drain(ReusableRaycastIterator iterator) {
        var result = new ArrayList<BlockHitResult>();
        while (iterator.hasNext()) {
            result.add(iterator.next());
            assertTrue(result.size() < 100, "runaway iteration");
        }
        return result;
    }

    @Test
    void unstartedIteratorHasNoHitsAndNeedsNoWorld() {
        // How the sound processor keeps one per thread, before any world is attached
        var context = new ReusableRaycastContext(null, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var iterator = ReusableRaycastIterator.unstarted(context);

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void restartedIteratorMatchesANewOneEachTime() {
        // The occlusion calculation keeps one iterator and restarts it for every ray
        var world = new FakeWorld().stone(2, 0, 0).stone(5, 0, 0).stone(8, 0, 0).stone(0, 0, 4);
        var context = new ReusableRaycastContext(world, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var iterator = ReusableRaycastIterator.unstarted(context);
        var origin = new Vec3(0.5, 0.5, 0.5);
        var east = new Vec3(10.5, 0.5, 0.5);
        var south = new Vec3(0.5, 0.5, 10.5);
        var up = new Vec3(0.5, 10.5, 0.5);

        assertEquals(positions(hits(world, origin, east)), positions(drain(iterator.restart(origin, east))));
        assertEquals(positions(hits(world, origin, south)), positions(drain(iterator.restart(origin, south))));
        assertEquals(List.of(), drain(iterator.restart(origin, up)), "a miss");

        // Restarted part way through a ray: the rest of the old one is forgotten
        iterator.restart(origin, east).next();
        assertEquals(positions(hits(world, origin, south)), positions(drain(iterator.restart(origin, south))));
    }

    // ---- Iterator --------------------------------------------------------------------------------------------

    @Test
    void reportsEachBlockAlongTheRayInOrder() {
        var world = new FakeWorld().stone(2, 0, 0).stone(5, 0, 0).stone(8, 0, 0);

        var hits = hits(world, new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5));

        assertEquals(List.of(new BlockPos(2, 0, 0), new BlockPos(5, 0, 0), new BlockPos(8, 0, 0)), positions(hits));
        assertEquals(2.0, hits.get(0).getLocation().x, 1.0E-9);
        assertEquals(5.0, hits.get(1).getLocation().x, 1.0E-9);
        assertEquals(8.0, hits.get(2).getLocation().x, 1.0E-9);
    }

    @Test
    void adjacentBlocksAreEachReported() {
        // Stepping one block past the entry point lands on the next block's face
        var world = new FakeWorld().stone(2, 0, 0).stone(3, 0, 0).stone(4, 0, 0);

        var hits = hits(world, new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5));

        assertEquals(List.of(new BlockPos(2, 0, 0), new BlockPos(3, 0, 0), new BlockPos(4, 0, 0)), positions(hits));
    }

    @Test
    void stopsAtTheTargetBlock() {
        var world = new FakeWorld().stone(2, 0, 0).stone(6, 0, 0).stone(9, 0, 0);

        // The end is inside the block at x=6; the block at x=9 is beyond it
        var hits = hits(world, new Vec3(0.5, 0.5, 0.5), new Vec3(6.5, 0.5, 0.5));

        assertEquals(List.of(new BlockPos(2, 0, 0), new BlockPos(6, 0, 0)), positions(hits));
    }

    @Test
    void blocksBeyondTheEndAreNotReported() {
        var world = new FakeWorld().stone(2, 0, 0).stone(9, 0, 0);

        var hits = hits(world, new Vec3(0.5, 0.5, 0.5), new Vec3(5.5, 0.5, 0.5));

        assertEquals(List.of(new BlockPos(2, 0, 0)), positions(hits));
    }

    @Test
    void clearPathHasNoHits() {
        var context = new ReusableRaycastContext(new FakeWorld(), new Vec3(0.5, 0.5, 0.5), new Vec3(10.5, 0.5, 0.5),
                ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var iterator = new ReusableRaycastIterator(context);

        assertFalse(iterator.hasNext());
        assertThrows(NoSuchElementException.class, iterator::next);
    }

    @Test
    void diagonalRayReportsALongCrossingAgainFromInside() {
        // The ray y = x + 0.2 enters this block's side at (3, 3.2) and leaves its top at (3.8, 4): about 1.13 blocks
        // inside it. So it is reported at its entry point and again from inside, a block-length further on.
        // (The ray avoids exact block corners, where vanilla's clip is imprecise.)
        var world = new FakeWorld().stone(3, 3, 0);
        var start = new Vec3(0.5, 0.7, 0.5);
        var end = new Vec3(8.5, 8.7, 0.5);

        var hits = hits(world, start, end);

        assertEquals(List.of(new BlockPos(3, 3, 0), new BlockPos(3, 3, 0)), positions(hits));
        assertEquals(0.0, hits.get(0).getLocation().distanceTo(new Vec3(3, 3.2, 0.5)), 1.0E-6, "entry point");
        assertTrue(hits.get(1).isInside(), "second hit is from inside the block");

        // The ray restarts one block-length past the entry point. Vanilla reports a hit from inside a block at
        // 0.1% of the way from the start to the end, so the second hit is that little bit further on.
        var restart = hits.get(0).getLocation().add(start.vectorTo(end).normalize());
        var expected = 1.0 + 0.001 * restart.distanceTo(end);
        assertEquals(expected, hits.get(0).getLocation().distanceTo(hits.get(1).getLocation()), 1.0E-6);
    }

    @Test
    @Timeout(value = 5, unit = TimeUnit.SECONDS)
    void stopsInsteadOfTracingBackwardsPastTheEnd() {
        // Regression: the ray y = x - 0.1 enters stone at (0,0), crosses the corner of air block (1,0) where it
        // ends, and would continue into stone at (1,1). One block-length past the second hit is beyond the end; the
        // old iterator traced back from there, hit (1,1) from behind, stepped forward to the same place, and
        // repeated forever.
        var world = new FakeWorld().stone(0, 0, 0).stone(1, 1, 0);
        var start = new Vec3(-1.9, -2.0, 0.5);
        var end = new Vec3(1.05, 0.95, 0.5);

        var hits = hits(world, start, end);

        assertFalse(hits.isEmpty());
        assertTrue(positions(hits).stream().allMatch(p -> p.equals(new BlockPos(0, 0, 0))),
                "only the block before the end: " + positions(hits));
    }
}
