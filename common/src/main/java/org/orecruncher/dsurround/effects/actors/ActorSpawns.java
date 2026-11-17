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
 * a chance at each kind of actor, at a time and place in it, both drawn from the seed, the cell, the slot and the
 * kind; a kind that comes in company (apparitions) has more chances there, each drawn as well from which it is.
 * Whether it does start there is the biome's to say (its chance and conditions, against the plan's roll) and the
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
    private static final long INDEX_SALT = 0xD1B54A32D192ED03L;

    /**
     * A chance for an actor to start
     *
     * @param kind      what kind of actor
     * @param index     which of the kind's chances in the cell and slot it is, from 0
     * @param key       the plan's own, to keep track of which have been looked at
     * @param startTick the game time it would start at
     * @param x         where, in blocks
     * @param z         where, in blocks
     * @param roll      0 to 1: it starts if this is under the biome's chance
     * @param seed      for everything else about the actor (its size, way and wander), so they are the same for everyone
     */
    public record Plan(ActorKind kind, int index, long key, long startTick, double x, double z, float roll, long seed) {
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
        return plan(worldSeed, cellX, cellZ, slot, kind, 0);
    }

    /**
     * One of the chances for a kind of actor in a cell in a slot: the first is {@link #plan(long, int, int, long,
     * ActorKind)}'s, the others each their own time, place and roll
     *
     * @param index which, from 0
     */
    public static Plan plan(long worldSeed, int cellX, int cellZ, long slot, ActorKind kind, int index) {
        long key = key(cellX, cellZ, slot, kind, index);
        var random = Randomizer.create(Mixers.splitMix64(worldSeed ^ key));
        long start = slot * SLOT_TICKS + random.nextInt(SLOT_TICKS);
        double x = (double) cellX * CELL_BLOCKS + random.nextDouble() * CELL_BLOCKS;
        double z = (double) cellZ * CELL_BLOCKS + random.nextDouble() * CELL_BLOCKS;
        float roll = random.nextFloat();
        return new Plan(kind, index, key, start, x, z, roll, random.nextLong());
    }

    static long key(int cellX, int cellZ, long slot, ActorKind kind, int index) {
        long key = key(cellX, cellZ, slot, kind);
        // The first keeps the key it had before there were more
        return index == 0 ? key : Mixers.splitMix64(key ^ index * INDEX_SALT);
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
