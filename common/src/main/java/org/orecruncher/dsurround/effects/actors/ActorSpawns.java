// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.util.Mth;
import org.orecruncher.dsurround.config.ActorKind;
import org.orecruncher.dsurround.lib.random.Mixers;
import org.orecruncher.dsurround.lib.random.Randomizer;

/**
 * Where and when world actors may start, the same for every player in a world, with nothing from the server but its
 * hashed seed and the game time. The world is split into square cells and time into slots; in each slot every cell has
 * one chance at each kind of actor, at a time and place in it, both drawn from the seed, the cell, the slot and the
 * kind. Whether it does start there is the biome's to say (its chance and conditions, against the plan's roll) and the
 * actor's (what it needs underfoot), and both are the same for everyone.
 */
public final class ActorSpawns {

    // How big a cell is, in blocks, and how long a slot, in ticks: a biome's chance is for one cell over one slot
    public static final int CELL_BLOCKS = 128;
    public static final int SLOT_TICKS = 1200;

    private static final long CELL_X_SALT = 0x5DEECE66DL;
    private static final long CELL_Z_SALT = 0x2545F4914F6CDD1DL;
    private static final long SLOT_SALT = 0x632BE59BD9B4E019L;
    private static final long KIND_SALT = 0x9E6C63D0676A9A99L;

    /**
     * A chance for an actor to start
     *
     * @param kind      what kind of actor
     * @param key       the plan's own, to keep track of which have been looked at
     * @param startTick the game time it would start at
     * @param x         where, in blocks
     * @param z         where, in blocks
     * @param roll      0 to 1: it starts if this is under the biome's chance
     * @param seed      for everything else about the actor (its size, way and wander), so they are the same for everyone
     */
    public record Plan(ActorKind kind, long key, long startTick, double x, double z, float roll, long seed) {
    }

    private ActorSpawns() {
    }

    /**
     * The chance for a kind of actor in a cell in a slot
     *
     * @param worldSeed the world's hashed seed, as the client has it
     * @param slot      the game time over {@link #SLOT_TICKS}
     */
    public static Plan plan(long worldSeed, int cellX, int cellZ, long slot, ActorKind kind) {
        long key = key(cellX, cellZ, slot, kind);
        var random = Randomizer.create(Mixers.splitMix64(worldSeed ^ key));
        long start = slot * SLOT_TICKS + random.nextInt(SLOT_TICKS);
        double x = (double) cellX * CELL_BLOCKS + random.nextDouble() * CELL_BLOCKS;
        double z = (double) cellZ * CELL_BLOCKS + random.nextDouble() * CELL_BLOCKS;
        float roll = random.nextFloat();
        return new Plan(kind, key, start, x, z, roll, random.nextLong());
    }

    static long key(int cellX, int cellZ, long slot, ActorKind kind) {
        return Mixers.splitMix64(cellX * CELL_X_SALT ^ Mixers.splitMix64(cellZ * CELL_Z_SALT ^ Mixers.splitMix64(slot * SLOT_SALT ^ kind.ordinal() * KIND_SALT)));
    }

    /**
     * The cell a block coordinate is in
     */
    public static int cellOf(double coordinate) {
        return Math.floorDiv(Mth.floor(coordinate), CELL_BLOCKS);
    }

    /**
     * The slot a game time is in
     */
    public static long slotOf(long gameTime) {
        return Math.floorDiv(gameTime, SLOT_TICKS);
    }
}
