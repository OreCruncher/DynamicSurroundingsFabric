// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.actors;

import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.function.IntPredicate;

/**
 * Where an actor's ground is under a roof, such as the Nether's. There the heightmap is the roof, and the first place
 * to stand looking down from it is on top of it, out of sight; a fixed height to look down from finds a ledge or a
 * pillar's top above the valley the player is in. So the ground is the height nearest a reference (the player's, for
 * one starting; its own last ground, for one already about) that suits it: a little above it or anything below, as a
 * valley's floor rises and falls, within the dimension's floor and roof.
 */
final class RoofedGround {

    // How far below and above the reference the ground is looked for, in blocks
    static final int BELOW = 48;
    static final int ABOVE = 12;

    private RoofedGround() {
    }

    /**
     * The height nearest {@code near} that {@code suits}, between the lowest and highest heights; null if none does
     */
    static @Nullable Integer nearest(int near, int lowest, int highest, IntPredicate suits) {
        for (int d = 0; d <= BELOW; d++) {
            int up = near + d, down = near - d;
            if (d <= ABOVE && up >= lowest && up <= highest && suits.test(up))
                return up;
            if (d > 0 && down >= lowest && down <= highest && suits.test(down))
                return down;
        }
        return null;
    }

    /**
     * The height nearest the player's (the sea level's with none) that suits, in a level, below its roof
     */
    static @Nullable Integer nearPlayer(Level level, IntPredicate suits) {
        return near(level, GameUtils.getPlayer().map(player -> (int) Math.floor(player.getY())).orElse(level.getSeaLevel()), suits);
    }

    /**
     * The height nearest a reference that suits, in a level, below its roof
     */
    static @Nullable Integer near(Level level, int near, IntPredicate suits) {
        return nearest(near, level.getMinY() + 1, level.getMinY() + level.dimensionType().logicalHeight() - 2, suits);
    }
}
