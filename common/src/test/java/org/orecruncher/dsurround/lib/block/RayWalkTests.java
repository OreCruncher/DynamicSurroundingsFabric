// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.SlabType;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The ray walk finds the same hit as the level's own trace ({@link BlockGetter#clip}), skipping sections of air.
 */
public class RayWalkTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    /**
     * Blocks by position, air elsewhere; and which sections hold anything
     */
    private static class Blocks3 implements BlockGetter {
        final Map<Long, BlockState> blocks = new HashMap<>();
        final Set<Long> sections = new HashSet<>();
        int sectionTests;

        void put(int x, int y, int z, BlockState state) {
            this.blocks.put(BlockPos.asLong(x, y, z), state);
            this.sections.add(SectionPos.asLong(SectionPos.blockToSectionCoord(x), SectionPos.blockToSectionCoord(y), SectionPos.blockToSectionCoord(z)));
        }

        boolean holdsOnlyAir(int sx, int sy, int sz) {
            this.sectionTests++;
            return !this.sections.contains(SectionPos.asLong(sx, sy, sz));
        }

        @Override
        public @Nullable BlockEntity getBlockEntity(BlockPos pos) {
            return null;
        }

        @Override
        public BlockState getBlockState(BlockPos pos) {
            return this.blocks.getOrDefault(pos.asLong(), Blocks.AIR.defaultBlockState());
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
    }

    private static final BlockState[] KINDS = {
            Blocks.STONE.defaultBlockState(),
            Blocks.GLASS.defaultBlockState(),
            Blocks.OAK_LEAVES.defaultBlockState(),
            Blocks.WATER.defaultBlockState(),
            Blocks.OAK_FENCE.defaultBlockState(),
            Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.BOTTOM),
            Blocks.STONE_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP).setValue(BlockStateProperties.WATERLOGGED, true),
            Blocks.OAK_STAIRS.defaultBlockState().setValue(StairBlock.HALF, net.minecraft.world.level.block.state.properties.Half.TOP),
            Blocks.TORCH.defaultBlockState(),
            Blocks.SHORT_GRASS.defaultBlockState(),
    };

    // A cave-like region near the origin, with sections of open air above it and around it
    private static Blocks3 world(IRandomizer random) {
        var world = new Blocks3();
        for (int x = -24; x < 24; x++)
            for (int z = -24; z < 24; z++)
                for (int y = -8; y < 12; y++) {
                    // A floor, scattered blocks, and a hollow middle
                    boolean floor = y < -4;
                    boolean scattered = random.nextInt(9) == 0;
                    if ((floor && random.nextInt(6) != 0) || scattered)
                        world.put(x, y, z, KINDS[random.nextInt(KINDS.length)]);
                }
        // A few blocks far up, alone in their sections
        world.put(40, 60, -35, Blocks.STONE.defaultBlockState());
        world.put(-50, 75, 20, Blocks.GLASS.defaultBlockState());
        return world;
    }

    private static Vec3 point(IRandomizer random, double range) {
        return new Vec3(random.nextDouble() * 2 * range - range, random.nextDouble() * 100 - 20, random.nextDouble() * 2 * range - range);
    }

    private static void assertSame(BlockHitResult expected, BlockHitResult actual, String what) {
        assertEquals(expected.getType(), actual.getType(), what);
        assertEquals(expected.getLocation(), actual.getLocation(), what);
        assertEquals(expected.getDirection(), actual.getDirection(), what);
        assertEquals(expected.getBlockPos().immutable(), actual.getBlockPos().immutable(), what);
        assertEquals(expected.isInside(), actual.isInside(), what);
    }

    // How many of the rays checked hit something, so a test can tell it isn't comparing misses alone
    private static int hits;

    private static void check(Blocks3 world, Vec3 from, Vec3 to) {
        for (var shape : new ClipContext.Block[]{ClipContext.Block.COLLIDER, ClipContext.Block.VISUAL})
            for (var fluid : new ClipContext.Fluid[]{ClipContext.Fluid.ANY, ClipContext.Fluid.NONE}) {
                var context = new ClipContext(from, to, shape, fluid, CollisionContext.empty());
                var expected = world.clip(context);
                assertSame(expected, RayWalk.clip(world, context, world::holdsOnlyAir), from + " to " + to + " " + shape + " " + fluid);
                if (expected.getType() != net.minecraft.world.phys.HitResult.Type.MISS)
                    hits++;
            }
    }

    @Test
    void randomRaysHitWhatTheLevelsTraceHits() {
        var random = Randomizer.create(20261107);
        var world = world(random);
        hits = 0;
        for (int i = 0; i < 4000; i++)
            check(world, point(random, 60), point(random, 60));
        // Most go through open air: some 2500 hit (seed 20261107)
        assertTrue(hits > 2000, "rays that hit something: " + hits + " of 16000");
    }

    @Test
    void awkwardRaysHitWhatTheLevelsTraceHits() {
        var random = Randomizer.create(7);
        var world = world(random);
        for (int i = 0; i < 1500; i++) {
            // On whole and half block coordinates: along edges, through corners, starting on faces
            var from = new Vec3(Math.round(random.nextDouble() * 80 - 40) / 2.0, Math.round(random.nextDouble() * 60 - 20) / 2.0, Math.round(random.nextDouble() * 80 - 40) / 2.0);
            var to = new Vec3(Math.round(random.nextDouble() * 80 - 40) / 2.0, Math.round(random.nextDouble() * 60 - 20) / 2.0, Math.round(random.nextDouble() * 80 - 40) / 2.0);
            check(world, from, to);
            // Along an axis
            check(world, from, new Vec3(to.x, from.y, from.z));
            check(world, from, new Vec3(from.x, to.y, from.z));
            check(world, from, new Vec3(from.x, from.y, to.z));
        }
        // No length at all
        check(world, new Vec3(1.5, 2.5, 3.5), new Vec3(1.5, 2.5, 3.5));
    }

    @Test
    void sectionsOfAirAreNotRead() {
        var world = new Blocks3();
        world.put(100, 5, 0, Blocks.STONE.defaultBlockState());
        var reads = new int[1];
        var counting = new Blocks3() {
            @Override
            public BlockState getBlockState(BlockPos pos) {
                reads[0]++;
                return world.getBlockState(pos);
            }
        };
        counting.sections.addAll(world.sections);
        var context = new ClipContext(new Vec3(0.5, 5.5, 0.5), new Vec3(120.5, 5.5, 0.5), ClipContext.Block.COLLIDER, ClipContext.Fluid.ANY, CollisionContext.empty());

        var hit = RayWalk.clip(counting, context, counting::holdsOnlyAir);
        assertEquals(new BlockPos(100, 5, 0), hit.getBlockPos().immutable());
        // Only the blocks of the one section with anything in it, up to the stone: none of the 96 before it
        assertTrue(reads[0] <= 16, "blocks read: " + reads[0]);
    }
}
