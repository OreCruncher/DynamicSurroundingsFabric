// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.processing;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.AbstractBoat;
import net.minecraft.world.phys.AABB;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.orecruncher.dsurround.effects.particles.WaterFoam;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;

/**
 * Rings spreading from boats. Boats aren't living entities, so they get none of the entity effects. One under way
 * leaves a wake, as anything moving through the water does ({@code WaterBobEffect}): rings from its bow, closer in time
 * the faster it goes, that open out behind it, and foam shed from either side of its stern, which coasts out across the
 * water, slowing, and fades. One sitting in the water doesn't bob in the game, so it simply makes a ring every couple of
 * seconds, in turns so that a row of them doesn't keep time.
 */
public class BoatRingHandler extends AbstractClientHandler {

    // The ticks between the rings of a boat sitting still, and how fast one has to go to leave a wake instead
    static final int STILL_RING_TICKS = 40;
    static final double MIN_WAKE_SPEED = 0.03D;
    // How far a boat under way goes between the rings of its wake, in blocks, and how far ahead of its middle they start
    static final double WAKE_STEP = 1.2D;
    private static final double BOW = 0.7D;
    private static final float RING_SIZE = 3F;
    private static final float MAX_WAKE_SIZE = 5.5F;
    // How big its wake's rings start, as a half width in blocks
    private static final float WAKE_START = 0.8F;
    // How fast a boat has to go to shed foam, and how much bigger its patches are than usual
    static final double MIN_FOAM_SPEED = 0.1D;
    private static final float FOAM_SCALE = 1.7F;
    // Where it starts: how far behind a boat's middle, and how far to either side
    private static final double STERN = 0.9D;
    private static final double BEAM = 0.5D;
    // How fast foam is thrown out from the side, at the least and how much more, and what share of the boat's speed it
    // keeps as it is left behind
    private static final double FOAM_MIN_SPEED = 0.03D;
    private static final double FOAM_MORE_SPEED = 0.05D;
    private static final double FOAM_FOLLOW = 0.3D;

    public BoatRingHandler(Configuration config, IModLog logger) {
        super("Boat Rings", config, logger);
    }

    @Override
    public void process(final Player player) {
        if (!this.config.entityEffects.enableBobbingRings || !WaterRippleHandler.doRings()
                || GameUtils.getGameSettings().particles().get() == ParticleStatus.MINIMAL)
            return;
        if (!(player.level() instanceof ClientLevel level))
            return;

        var area = AABB.unitCubeFromLowerCorner(player.position()).inflate(this.config.entityEffects.entityEffectRange);
        for (var boat : level.getEntitiesOfClass(AbstractBoat.class, area)) {
            double dx = boat.getX() - boat.xo, dz = boat.getZ() - boat.zo;
            double speed = Math.sqrt(dx * dx + dz * dz);
            boolean underWay = speed >= MIN_WAKE_SPEED;
            if (speed >= MIN_FOAM_SPEED) {
                addFoam(level, boat.getX(), boat.getY(), boat.getZ(), dx / speed, dz / speed, speed, 1D);
                addFoam(level, boat.getX(), boat.getY(), boat.getZ(), dx / speed, dz / speed, speed, -1D);
            }
            if (!ringDue(player.tickCount, boat.getId(), underWay ? wakeTicks(speed) : STILL_RING_TICKS))
                continue;

            double ahead = underWay ? BOW / speed : 0D;
            double x = boat.getX() + dx * ahead, z = boat.getZ() + dz * ahead;
            var surface = WaterRippleHandler.stillSurface(level, x, boat.getY(), z);
            if (surface.isEmpty())
                continue;
            if (underWay)
                WaterRippleHandler.createWakeRing(level, x, surface.getAsDouble(), z, wakeSize(speed), dx, dz, WAKE_START)
                        .ifPresent(GameUtils.getParticleManager()::add);
            else
                WaterRippleHandler.createRing(level, x, surface.getAsDouble(), z, RING_SIZE, WaterRippleHandler.FLOATING_RING_LIGHTEN)
                        .ifPresent(GameUtils.getParticleManager()::add);
        }
    }

    /**
     * Sheds a patch of foam from one side of a boat's stern, thrown out to that side, somewhere along what it covered
     * this tick so that patches don't land in rows.
     *
     * @param dirX the way the boat is going, along X, as part of a unit vector
     * @param dirZ along Z
     * @param side which side: 1 to the right of the way it is going, -1 to the left
     */
    private static void addFoam(ClientLevel level, double x, double y, double z, double dirX, double dirZ, double speed, double side) {
        double outX = -dirZ * side, outZ = dirX * side;
        double behind = STERN + RANDOM.nextDouble() * speed;
        double footX = x - dirX * behind + outX * BEAM * RANDOM.nextDouble();
        double footZ = z - dirZ * behind + outZ * BEAM * RANDOM.nextDouble();
        var surface = WaterRippleHandler.stillSurface(level, footX, y, footZ);
        if (surface.isEmpty())
            return;
        double thrown = FOAM_MIN_SPEED + RANDOM.nextDouble() * FOAM_MORE_SPEED;
        double follow = speed * FOAM_FOLLOW;
        GameUtils.getParticleManager().add(WaterFoam.create(level, footX, surface.getAsDouble(), footZ,
                outX * thrown + dirX * follow, outZ * thrown + dirZ * follow, FOAM_SCALE));
    }

    /**
     * Whether a boat makes a ring this tick, making one every so many ticks
     */
    static boolean ringDue(int tick, int boatId, int ticks) {
        return Math.floorMod(tick + boatId, ticks) == 0;
    }

    /**
     * The ticks between the rings of a boat's wake at a speed, for them to be {@link #WAKE_STEP} apart
     */
    static int wakeTicks(double speed) {
        return Math.max(1, (int) Math.round(WAKE_STEP / speed));
    }

    /**
     * How fast a ring of its wake spreads: faster for a faster boat
     */
    static float wakeSize(double speed) {
        return Math.min(MAX_WAKE_SIZE, RING_SIZE + (float) speed * 6F);
    }
}
