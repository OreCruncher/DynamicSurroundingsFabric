// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import net.minecraft.core.BlockPos;
import org.jetbrains.annotations.Nullable;

import java.util.PriorityQueue;

/**
 * The way an apparition goes from where it is to its next haunt, around what is in the way: an A* search over the
 * places it can stand, one block apart, stepping up a block or down a few, cutting a corner only where both sides are
 * clear. It glides, but along the ground, as something that once walked there would. Searched once each time it sets
 * off, over a few thousand places at most, so a haunt walled off or out of reach is given up rather than searched for.
 * <p>
 * What it can stand on is asked of a {@link Footing}, so the search knows nothing of the world (and tests can give it
 * a made-up one).
 */
public final class ApparitionRoute {

    /**
     * Whether an apparition can stand with its feet in a block
     */
    @FunctionalInterface
    public interface Footing {
        boolean standable(int x, int y, int z);
    }

    // How far it may step up, and drop down, in blocks
    static final int CLIMB = 1;
    static final int DROP = 3;
    // The most places looked at before giving up
    static final int MAX_NODES = 3000;
    // How near to the goal is there, across and up or down, in blocks
    private static final int ARRIVE = 1;
    private static final int ARRIVE_Y = 2;
    private static final double DIAGONAL = Math.sqrt(2D);
    // Stepping up costs a little more than going on level, so a way around a hump is taken when it is no longer
    private static final double CLIMB_COST = 0.5D;
    private static final int[][] DIRECTIONS = {{1, 0}, {-1, 0}, {0, 1}, {0, -1}, {1, 1}, {1, -1}, {-1, 1}, {-1, -1}};

    private record Open(long pos, double f) {
    }

    private ApparitionRoute() {
    }

    /**
     * The places it passes through on its way, from the first step to the goal (or next to it), packed as
     * {@link BlockPos#asLong}; null if there is no way within the search, or it can't stand where it starts.
     *
     * @param from where its feet are
     * @param to   where it is going: its feet there
     */
    public static long @Nullable [] find(Footing footing, BlockPos from, BlockPos to) {
        return find(footing, from, to, MAX_NODES);
    }

    static long @Nullable [] find(Footing footing, BlockPos from, BlockPos to, int maxNodes) {
        var cache = new Long2ByteOpenHashMap();
        Footing known = (x, y, z) -> {
            long key = BlockPos.asLong(x, y, z);
            byte value = cache.getOrDefault(key, (byte) -1);
            if (value < 0) {
                value = (byte) (footing.standable(x, y, z) ? 1 : 0);
                cache.put(key, value);
            }
            return value == 1;
        };

        long start = from.asLong();
        if (arrived(from.getX(), from.getY(), from.getZ(), to))
            return new long[0];

        var cost = new Long2DoubleOpenHashMap();
        cost.defaultReturnValue(Double.MAX_VALUE);
        var cameFrom = new Long2LongOpenHashMap();
        var open = new PriorityQueue<Open>((a, b) -> Double.compare(a.f(), b.f()));
        cost.put(start, 0D);
        open.add(new Open(start, heuristic(from.getX(), from.getY(), from.getZ(), to)));

        int looked = 0;
        while (!open.isEmpty() && looked < maxNodes) {
            var node = open.poll();
            long pos = node.pos();
            int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
            double here = cost.get(pos);
            // An older, dearer copy of a place already reached more cheaply
            if (node.f() > here + heuristic(x, y, z, to) + 1e-9)
                continue;
            looked++;
            if (arrived(x, y, z, to))
                return route(cameFrom, start, pos);

            for (var d : DIRECTIONS) {
                int nx = x + d[0], nz = z + d[1];
                int ny = step(known, nx, y, nz);
                if (ny == Integer.MIN_VALUE)
                    continue;
                boolean diagonal = d[0] != 0 && d[1] != 0;
                // No cutting a corner past a wall or a tree
                if (diagonal && (step(known, x + d[0], y, z) == Integer.MIN_VALUE || step(known, x, y, z + d[1]) == Integer.MIN_VALUE))
                    continue;
                double next = here + (diagonal ? DIAGONAL : 1D) + (ny > y ? CLIMB_COST : 0D);
                long key = BlockPos.asLong(nx, ny, nz);
                if (next < cost.get(key)) {
                    cost.put(key, next);
                    cameFrom.put(key, pos);
                    open.add(new Open(key, next + heuristic(nx, ny, nz, to)));
                }
            }
        }
        return null;
    }

    /**
     * The height its feet are at stepping into a column from a height: on level, up a step, or down a drop, the
     * nearest first; MIN_VALUE if it can't
     */
    static int step(Footing footing, int x, int y, int z) {
        if (footing.standable(x, y, z))
            return y;
        for (int up = 1; up <= CLIMB; up++)
            if (footing.standable(x, y + up, z))
                return y + up;
        for (int down = 1; down <= DROP; down++)
            if (footing.standable(x, y - down, z))
                return y - down;
        return Integer.MIN_VALUE;
    }

    private static boolean arrived(int x, int y, int z, BlockPos to) {
        return Math.abs(x - to.getX()) <= ARRIVE && Math.abs(z - to.getZ()) <= ARRIVE && Math.abs(y - to.getY()) <= ARRIVE_Y;
    }

    // Octile distance across, and the height to make up: never more than the way costs
    private static double heuristic(int x, int y, int z, BlockPos to) {
        int dx = Math.abs(x - to.getX()), dz = Math.abs(z - to.getZ());
        return Math.max(dx, dz) + (DIAGONAL - 1D) * Math.min(dx, dz) + Math.abs(y - to.getY()) * 0.1D;
    }

    private static long[] route(Long2LongOpenHashMap cameFrom, long start, long end) {
        var steps = new LongArrayList();
        for (long pos = end; pos != start; pos = cameFrom.get(pos))
            steps.add(pos);
        var result = new long[steps.size()];
        for (int i = 0; i < result.length; i++)
            result[i] = steps.getLong(result.length - 1 - i);
        return result;
    }
}
