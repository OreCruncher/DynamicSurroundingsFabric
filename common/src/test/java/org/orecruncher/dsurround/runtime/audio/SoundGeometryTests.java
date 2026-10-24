package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.math.ReusableRaycastContext;

import static org.junit.jupiter.api.Assertions.*;

public class SoundGeometryTests {

    private static final BlockPos POS = new BlockPos(0, 64, 0);

    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * One block at POS, air elsewhere.
     */
    private static BlockGetter worldWith(BlockState state) {
        return new BlockGetter() {
            @Override
            public BlockEntity getBlockEntity(BlockPos pos) {
                return null;
            }

            @Override
            public BlockState getBlockState(BlockPos pos) {
                return pos.equals(POS) ? state : Blocks.AIR.defaultBlockState();
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
            public int getMinY() {
                return -64;
            }
        };
    }

    private static boolean offsets(BlockState state) {
        return SoundGeometry.shouldOffset(worldWith(state), POS);
    }

    private static boolean offsets(Block block) {
        return offsets(block.defaultBlockState());
    }

    @Test
    void openBlocksDoNotMoveTheSound() {
        // Regression: anything but plain air counted, including cave air underground
        assertFalse(offsets(Blocks.AIR));
        assertFalse(offsets(Blocks.CAVE_AIR));
        assertFalse(offsets(Blocks.VOID_AIR));
        assertFalse(offsets(Blocks.SHORT_GRASS));
        assertFalse(offsets(Blocks.TORCH));
        assertFalse(offsets(Blocks.SNOW), "a single snow layer has no collision");
    }

    @Test
    void solidBlocksMoveTheSound() {
        assertTrue(offsets(Blocks.STONE));
        assertTrue(offsets(Blocks.OAK_SLAB));
        assertTrue(offsets(Blocks.GLASS));
    }

    @Test
    void fluidsMoveTheSound() {
        // Regression: after the change to solid blocks only, sounds in water (waterfalls) stayed inside it
        assertTrue(offsets(Blocks.WATER));
        assertTrue(offsets(Blocks.LAVA));
        assertTrue(offsets(Blocks.SEAGRASS), "not solid, but always under water");
        // Glow lichen has no collision, so only the water makes it count
        assertTrue(offsets(Blocks.GLOW_LICHEN.defaultBlockState().setValue(BlockStateProperties.WATERLOGGED, true)),
                "waterlogged");
        assertFalse(offsets(Blocks.GLOW_LICHEN.defaultBlockState()), "the same block, not waterlogged");
    }

    @Test
    void soundMovesTowardsTheTarget() {
        var origin = new Vec3(0.5, 64.5, 0.5);
        var target = new Vec3(10.5, 64.5, 0.5);

        var moved = SoundGeometry.offsetPositionIfNeeded(worldWith(Blocks.WATER.defaultBlockState()), origin, target);

        assertEquals(0.5 + SoundGeometry.OFFSET, moved.x, 1.0E-9);
        assertEquals(64.5, moved.y, 1.0E-9);
        assertEquals(0.5, moved.z, 1.0E-9);
    }

    @Test
    void soundInCaveAirStaysPut() {
        var origin = new Vec3(0.5, 64.5, 0.5);

        var result = SoundGeometry.offsetPositionIfNeeded(worldWith(Blocks.CAVE_AIR.defaultBlockState()), origin, new Vec3(10, 64, 0));

        assertSame(origin, result);
    }

    @Test
    void rayFromInsideWaterHitsAtItsStartButNotOnceMovedOut() {
        // Why fluids count: the sound's rays trace fluids, so one starting in water hits the water immediately
        var world = worldWith(Blocks.WATER.defaultBlockState());
        var context = new ReusableRaycastContext(world, ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY);
        var inWater = new Vec3(0.5, 64.5, 0.5);
        var player = new Vec3(10.5, 64.5, 0.5);

        var fromWater = context.trace(inWater, player);
        assertEquals(HitResult.Type.BLOCK, fromWater.getType());
        assertTrue(fromWater.isInside(), "hit at its own start");
        assertTrue(fromWater.getLocation().distanceTo(inWater) < 0.1);

        var moved = SoundGeometry.offsetPositionIfNeeded(world, inWater, player);
        assertEquals(HitResult.Type.MISS, context.trace(moved, player).getType(), "reaches the player once moved out");
    }
}
