// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.core.BlockPos;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for apparitions: the way they find around what is in the way, their seeded life, and how they fade.
 */
public class ApparitionTests {

    // A flat floor at y 64, feet at 65, with walls (two blocks high) where listed
    private static ApparitionRoute.Footing flat(Set<Long> walls) {
        return (x, y, z) -> y == 65 && !walls.contains(BlockPos.asLong(x, 0, z));
    }

    private static Set<Long> wall(int x, int fromZ, int toZ) {
        var walls = new HashSet<Long>();
        for (int z = fromZ; z <= toZ; z++)
            walls.add(BlockPos.asLong(x, 0, z));
        return walls;
    }

    private static void assertStepsAreNeighbors(BlockPos from, long[] route) {
        long last = from.asLong();
        for (long step : route) {
            assertTrue(Math.abs(BlockPos.getX(step) - BlockPos.getX(last)) <= 1, "one block at a time across");
            assertTrue(Math.abs(BlockPos.getZ(step) - BlockPos.getZ(last)) <= 1, "one block at a time across");
            int dy = BlockPos.getY(step) - BlockPos.getY(last);
            assertTrue(dy <= ApparitionRoute.CLIMB && dy >= -ApparitionRoute.DROP, "a step up or a drop it can take: " + dy);
            last = step;
        }
    }

    @Test
    void goesStraightOnOpenGround() {
        var from = new BlockPos(0, 65, 0);
        var route = ApparitionRoute.find(flat(Set.of()), from, new BlockPos(10, 65, 0));
        assertNotNull(route);
        assertTrue(route.length >= 9 && route.length <= 10, "about the distance: " + route.length);
        assertStepsAreNeighbors(from, route);
    }

    @Test
    void goesAroundAWall() {
        // A wall across the way, from z -5 to 5, with the goal behind it
        var walls = wall(5, -5, 5);
        var from = new BlockPos(0, 65, 0);
        var route = ApparitionRoute.find(flat(walls), from, new BlockPos(10, 65, 0));
        assertNotNull(route);
        assertStepsAreNeighbors(from, route);
        for (long step : route)
            assertFalse(walls.contains(BlockPos.asLong(BlockPos.getX(step), 0, BlockPos.getZ(step))), "never through the wall");
        assertTrue(route.length > 10, "longer than straight through");
    }

    @Test
    void doesNotCutACornerPastAWall() {
        // A corner: the diagonal step from (0,0) to (1,1) would slip between (1,0) and (0,1)
        var walls = new HashSet<Long>();
        walls.add(BlockPos.asLong(1, 0, 0));
        var from = new BlockPos(0, 65, 0);
        var route = ApparitionRoute.find(flat(walls), from, new BlockPos(3, 65, 3));
        assertNotNull(route);
        assertNotEquals(BlockPos.asLong(1, 65, 1), route[0], "not diagonally past the wall's corner");
    }

    @Test
    void climbsAStepAndTakesADropButNotACliff() {
        // Ground at 65 to x 4, then 66 (a step up) to x 8, then 62 (a drop of four) beyond
        ApparitionRoute.Footing terrain = (x, y, z) -> y == (x <= 4 ? 65 : x <= 8 ? 66 : 62);
        var from = new BlockPos(0, 65, 0);
        var up = ApparitionRoute.find(terrain, from, new BlockPos(7, 66, 0));
        assertNotNull(up, "a step up is taken");
        assertStepsAreNeighbors(from, up);
        assertNull(ApparitionRoute.find(terrain, from, new BlockPos(12, 62, 0)), "a drop of four isn't");
        // Three is: from 66 down to 63
        ApparitionRoute.Footing gentler = (x, y, z) -> y == (x <= 4 ? 66 : 63);
        assertNotNull(ApparitionRoute.find(gentler, new BlockPos(0, 66, 0), new BlockPos(8, 63, 0)));
    }

    @Test
    void givesUpOnAPlaceWalledIn() {
        // A ring of wall around the goal
        var walls = new HashSet<Long>();
        for (int i = -3; i <= 3; i++) {
            walls.add(BlockPos.asLong(17, 0, i));
            walls.add(BlockPos.asLong(23, 0, i));
            walls.add(BlockPos.asLong(17 + 3 + i, 0, -3));
            walls.add(BlockPos.asLong(17 + 3 + i, 0, 3));
        }
        assertNull(ApparitionRoute.find(flat(walls), new BlockPos(0, 65, 0), new BlockPos(20, 65, 0)));
    }

    @Test
    void searchesNoMoreThanItsLimit() {
        int[] asked = {0};
        ApparitionRoute.Footing counting = (x, y, z) -> {
            asked[0]++;
            return y == 65;
        };
        // Unreachable: the goal floats far above the ground
        assertNull(ApparitionRoute.find(counting, new BlockPos(0, 65, 0), new BlockPos(500, 200, 0), 200));
        assertTrue(asked[0] < 200 * 8 * 6, "bounded by the places looked at: " + asked[0]);
    }

    @Test
    void alreadyThereIsNoWay() {
        var route = ApparitionRoute.find(flat(Set.of()), new BlockPos(0, 65, 0), new BlockPos(1, 65, 0));
        assertNotNull(route);
        assertEquals(0, route.length);
    }

    @Test
    void theSameSeedIsTheSameLife() {
        var a = ApparitionPath.of(100.5, -40.5, 1234L);
        var b = ApparitionPath.of(100.5, -40.5, 1234L);
        assertEquals(a.lifetime(), b.lifetime());
        for (int age = 0; age < a.lifetime(); age += 97) {
            assertEquals(a.hauntAt(age), b.hauntAt(age));
            assertEquals(a.x(age), b.x(age));
            assertEquals(a.bob(age), b.bob(age));
        }
        assertEquals(100.5, a.hauntX(0));
        assertEquals(-40.5, a.hauntZ(0));
    }

    @Test
    void itsHauntsAreNearWhereItStarted() {
        for (long seed = 0; seed < 50; seed++) {
            var path = ApparitionPath.of(0, 0, seed);
            assertTrue(path.lifetime() >= ApparitionPath.MIN_LIFETIME && path.lifetime() <= ApparitionPath.MAX_LIFETIME);
            for (int h = 1; h < ApparitionPath.HAUNTS; h++) {
                double distance = Math.hypot(path.hauntX(h), path.hauntZ(h));
                assertTrue(distance >= 7.9 && distance <= 18.1, "haunt " + h + " at " + distance);
            }
        }
    }

    @Test
    void itShowsAndFadesWithItsLife() {
        var path = ApparitionPath.of(0, 0, 7L);
        assertEquals(0F, path.presence(0F));
        assertEquals(1F, path.presence(path.lifetime() / 2F));
        assertEquals(0F, path.presence(path.lifetime()));
        assertTrue(path.presence(ApparitionPath.FADE_TICKS / 2F) > 0F && path.presence(ApparitionPath.FADE_TICKS / 2F) < 1F);
        for (int age = 0; age < 400; age += 7)
            assertTrue(path.bob(age) >= 0F && path.bob(age) <= 2F * ApparitionPath.BOB + 1e-6F, "it floats, never sinks into the ground");
    }

    @Test
    void itFadesOutTowardItsLegs() {
        float height = Apparition.HEIGHT;
        assertEquals(0F, Apparition.fade(0F), "nothing at its feet");
        assertEquals(0F, Apparition.fade(height * Apparition.FADE_FROM), "nothing at its knees");
        assertEquals(1F, Apparition.fade(height * Apparition.FADE_TO), "all of it at its waist");
        assertEquals(1F, Apparition.fade(height), "and its head");
        float last = 0F;
        for (float h = 0F; h <= height; h += 0.05F) {
            float f = Apparition.fade(h);
            assertTrue(f >= last - 1e-6F, "never less further up");
            last = f;
        }
    }

    @Test
    void facesTheWayItGoes() {
        // The game's yaw: 0 faces south (+z), 90 west (-x), -90 east (+x), 180 north
        assertEquals(0F, Apparition.yawOf(0, 1), 1e-4F);
        assertEquals(-90F, Apparition.yawOf(1, 0), 1e-4F);
        assertEquals(90F, Apparition.yawOf(-1, 0), 1e-4F);
        assertEquals(10F, Apparition.turnToward(0F, 90F, 10F));
        assertEquals(-10F, Apparition.turnToward(0F, 270F, 10F), "the short way round");
    }

    @Test
    void twoWithRoomAreLeftAlone() {
        assertNull(Apparition.separation(Apparition.ROOM, 0D, 1L));
        assertNull(Apparition.separation(3D, 4D, 1L));
    }

    @Test
    void twoTooNearArePushedApart() {
        var push = Apparition.separation(0.3D, 0D, 1L);
        assertNotNull(push);
        assertTrue(push[0] > 0D, "the second away from the first");
        assertEquals(0D, push[1], 1e-9D, "along the line between them");
        assertEquals(Apparition.NUDGE, push[0], 1e-9D, "a little at a time");

        var close = Apparition.separation(0.78D, 0D, 1L);
        assertNotNull(close);
        assertEquals(0.01D, close[0], 1e-9D, "only as far as gives them room, each going half");
    }

    @Test
    void twoInTheSameSpotGoApartTheSameWayForEveryone() {
        var a = Apparition.separation(0D, 0D, 42L);
        var b = Apparition.separation(0D, 0D, 42L);
        assertNotNull(a);
        assertArrayEquals(a, b);
        assertEquals(Apparition.NUDGE, Math.hypot(a[0], a[1]), 1e-9D);
    }
}
