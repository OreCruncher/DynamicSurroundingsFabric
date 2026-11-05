// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.bench;

import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/**
 * Keeps a scene's boats under way ({@code boatSpeed}), around a circle about the player, on the built-in server, so a
 * recording has boats moving at a steady speed and no one to steer them. Development only, as the rest of the bench.
 */
final class BenchBoats {

    // How far from the middle boats are held, and how hard they are pulled back to that, a block off
    private static final double RADIUS = 14D;
    private static final double PULL = 0.05D;
    private static final double REACH = 40D;

    private BenchBoats() {
    }

    /**
     * Sets the boats going for this tick, on the server's thread
     *
     * @param middle where they go around
     * @param speed  how fast, in blocks a tick
     */
    static void tick(MinecraftServer server, Vec3 middle, double speed) {
        server.execute(() -> {
            var players = server.getPlayerList().getPlayers();
            if (players.isEmpty())
                return;
            for (var boat : players.getFirst().level().getEntitiesOfClass(AbstractBoat.class, new AABB(middle, middle).inflate(REACH))) {
                double outX = boat.getX() - middle.x, outZ = boat.getZ() - middle.z;
                double distance = Math.sqrt(outX * outX + outZ * outZ);
                if (distance < 0.1D)
                    continue;
                outX /= distance;
                outZ /= distance;
                double pull = (RADIUS - distance) * PULL;
                boat.setDeltaMovement(-outZ * speed + outX * pull, boat.getDeltaMovement().y, outX * speed + outZ * pull);
            }
        });
    }
}
