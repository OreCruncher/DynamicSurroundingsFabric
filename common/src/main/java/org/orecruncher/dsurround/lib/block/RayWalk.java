// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * A ray traced through the blocks as {@link BlockGetter#clip} traces it, block by block along it, with the same hit
 * at the end of it: the same blocks in the same order, each tested the same way. But where the ray is in a section
 * (16 by 16 by 16 blocks) that holds only air, nothing is read: air stops no ray, and reading every block of open
 * sky was most of a reverb ray's time, as each goes out a long way.
 */
public final class RayWalk {

    /**
     * Whether a section, by its section coordinates, holds only air
     */
    @FunctionalInterface
    public interface SectionTest {
        boolean holdsOnlyAir(int sectionX, int sectionY, int sectionZ);
    }

    private RayWalk() {
    }

    /**
     * The first block or fluid the ray hits, as {@code blocks.clip(context)} finds it; a miss at its end if none
     */
    public static BlockHitResult clip(BlockGetter blocks, ClipContext context, SectionTest air) {
        var from = context.getFrom();
        var to = context.getTo();
        if (from.equals(to))
            return miss(context);

        // Both ends pulled in a hair, as vanilla's: a ray starting on a block's face is in the block it faces
        double endX = Mth.lerp(-1.0E-7D, to.x, from.x);
        double endY = Mth.lerp(-1.0E-7D, to.y, from.y);
        double endZ = Mth.lerp(-1.0E-7D, to.z, from.z);
        double startX = Mth.lerp(-1.0E-7D, from.x, to.x);
        double startY = Mth.lerp(-1.0E-7D, from.y, to.y);
        double startZ = Mth.lerp(-1.0E-7D, from.z, to.z);
        int x = Mth.floor(startX);
        int y = Mth.floor(startY);
        int z = Mth.floor(startZ);
        // A position of its own: a hit holds on to it
        var pos = new BlockPos.MutableBlockPos(x, y, z);

        // The section in hand, and whether it holds only air
        int sectionX = SectionPos.blockToSectionCoord(x);
        int sectionY = SectionPos.blockToSectionCoord(y);
        int sectionZ = SectionPos.blockToSectionCoord(z);
        boolean empty = air.holdsOnlyAir(sectionX, sectionY, sectionZ);

        if (!empty) {
            var hit = test(blocks, context, pos);
            if (hit != null)
                return hit;
        }

        double dx = endX - startX;
        double dy = endY - startY;
        double dz = endZ - startZ;
        int stepX = Mth.sign(dx);
        int stepY = Mth.sign(dy);
        int stepZ = Mth.sign(dz);
        // How far along the ray, 0 to 1, a step of a block takes on each axis; and where the next block boundary is
        double deltaX = stepX == 0 ? Double.MAX_VALUE : stepX / dx;
        double deltaY = stepY == 0 ? Double.MAX_VALUE : stepY / dy;
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : stepZ / dz;
        double nextX = deltaX * (stepX > 0 ? 1.0D - Mth.frac(startX) : Mth.frac(startX));
        double nextY = deltaY * (stepY > 0 ? 1.0D - Mth.frac(startY) : Mth.frac(startY));
        double nextZ = deltaZ * (stepZ > 0 ? 1.0D - Mth.frac(startZ) : Mth.frac(startZ));

        while (nextX <= 1.0D || nextY <= 1.0D || nextZ <= 1.0D) {
            // Ties go as vanilla's do
            if (nextX < nextY) {
                if (nextX < nextZ) {
                    x += stepX;
                    nextX += deltaX;
                } else {
                    z += stepZ;
                    nextZ += deltaZ;
                }
            } else if (nextY < nextZ) {
                y += stepY;
                nextY += deltaY;
            } else {
                z += stepZ;
                nextZ += deltaZ;
            }

            int sx = SectionPos.blockToSectionCoord(x);
            int sy = SectionPos.blockToSectionCoord(y);
            int sz = SectionPos.blockToSectionCoord(z);
            if (sx != sectionX || sy != sectionY || sz != sectionZ) {
                sectionX = sx;
                sectionY = sy;
                sectionZ = sz;
                empty = air.holdsOnlyAir(sx, sy, sz);
            }
            if (empty)
                continue;

            var hit = test(blocks, context, pos.set(x, y, z));
            if (hit != null)
                return hit;
        }
        return miss(context);
    }

    /**
     * Whether the ray hits the block at {@code pos}, or its fluid, and where: the nearer of the two, as vanilla's
     */
    private static @Nullable BlockHitResult test(BlockGetter blocks, ClipContext context, BlockPos pos) {
        var state = blocks.getBlockState(pos);
        var fluid = blocks.getFluidState(pos);
        var from = context.getFrom();
        var to = context.getTo();
        var blockHit = blocks.clipWithInteractionOverride(from, to, pos, context.getBlockShape(state, blocks, pos), state);
        var fluidHit = context.getFluidShape(fluid, blocks, pos).clip(from, to, pos);
        double block = blockHit == null ? Double.MAX_VALUE : from.distanceToSqr(blockHit.getLocation());
        double water = fluidHit == null ? Double.MAX_VALUE : from.distanceToSqr(fluidHit.getLocation());
        return block <= water ? blockHit : fluidHit;
    }

    private static BlockHitResult miss(ClipContext context) {
        Vec3 back = context.getFrom().subtract(context.getTo());
        return BlockHitResult.miss(context.getTo(), Direction.getApproximateNearest(back.x, back.y, back.z), BlockPos.containing(context.getTo()));
    }
}
