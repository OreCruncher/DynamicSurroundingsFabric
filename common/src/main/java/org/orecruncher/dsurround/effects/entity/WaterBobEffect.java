// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.world.entity.LivingEntity;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.OptionalDouble;

/**
 * Rings on the water from a player or mob at the surface. One each time it bobs: at the bottom of each bob, as it turns
 * to come back up. And, moving through the water, wading or swimming, one every so far just ahead of it: each spreads
 * from where it was left, so they open out behind it in a V, as a boat's wake does. Worked out from its position, as
 * the client doesn't run the movement of anyone but the player. Nothing small enough to be a chicken makes them, nor
 * anyone riding in a boat.
 */
public class WaterBobEffect extends EntityEffectBase {

    // How wide a body has to be, as for a splash, and how fast it has to have been sinking, in blocks a tick
    static final float MIN_WIDTH = WaterSplashEffect.MIN_WIDTH;
    static final double MIN_SINK_SPEED = 0.01D;
    // The fewest ticks between rings
    static final int MIN_INTERVAL = 12;
    // How fast it has to be moving through the water, in blocks a tick, and how far between the rings of its wake
    static final double MIN_WAKE_SPEED = 0.01D;
    static final double WAKE_STEP = 0.5D;
    private static final float MAX_WAKE_SIZE = 4.5F;
    // How much of its width a wake's ring starts at, as a half width
    private static final float WAKE_START = 0.6F;

    private double lastRise;
    private int cooldown;
    private double sinceWakeRing;

    @Override
    public void activate(final EntityEffectInfo info) {
        this.lastRise = 0D;
        this.cooldown = 0;
        this.sinceWakeRing = 0D;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var entity = info.getEntity();
        if (this.cooldown > 0)
            this.cooldown--;

        double rise = entity.getY() - entity.yo;
        boolean bobbed = turnedUp(this.lastRise, rise);
        this.lastRise = rise;
        double dx = entity.getX() - entity.xo, dz = entity.getZ() - entity.zo;
        double speed = Math.sqrt(dx * dx + dz * dz);

        if (entity.isPassenger() || !entity.isInWater() || entity.getBbWidth() < MIN_WIDTH
                || GameUtils.getGameSettings().particles().get() == ParticleStatus.MINIMAL
                || !(entity.level() instanceof ClientLevel level)) {
            this.sinceWakeRing = 0D;
            return;
        }

        if (speed < MIN_WAKE_SPEED)
            this.sinceWakeRing = 0D;
        else if ((this.sinceWakeRing += speed) >= WAKE_STEP) {
            this.sinceWakeRing = 0D;
            var surface = this.surfaceAt(level, entity);
            if (surface.isPresent())
                WaterRippleHandler.createWakeRing(level, entity.getX(), surface.getAsDouble(), entity.getZ(),
                                wakeSize(entity.getBbWidth(), speed), dx, dz, entity.getBbWidth() * WAKE_START)
                        .ifPresent(this::addParticle);
        } else if (bobbed && this.cooldown <= 0) {
            this.cooldown = MIN_INTERVAL;
            var surface = this.surfaceAt(level, entity);
            if (surface.isPresent())
                WaterRippleHandler.createRing(level, entity.getX(), surface.getAsDouble(), entity.getZ(),
                                ringSize(entity.getBbWidth()), WaterRippleHandler.FLOATING_RING_LIGHTEN)
                        .ifPresent(this::addParticle);
        }
    }

    /**
     * The water's surface where it is, if it is at the surface
     */
    private OptionalDouble surfaceAt(ClientLevel level, LivingEntity entity) {
        var surface = WaterRippleHandler.stillSurface(level, entity.getX(), entity.getY(), entity.getZ());
        return surface.isPresent() && floats(entity.getY(), entity.getBbHeight(), surface.getAsDouble()) ? surface : OptionalDouble.empty();
    }

    /**
     * Whether it has just stopped sinking and started to come back up
     */
    static boolean turnedUp(double lastRise, double rise) {
        return lastRise <= -MIN_SINK_SPEED && rise >= 0D;
    }

    /**
     * Whether the surface crosses a body this tall, with its feet here: it is floating, not swimming below or standing
     * in the shallows with its head out by more than a body's height
     */
    static boolean floats(double feet, float height, double surfaceY) {
        return surfaceY >= feet && surfaceY <= feet + height;
    }

    /**
     * How fast its ring spreads, next to a raindrop's
     */
    static float ringSize(float width) {
        return 1.5F + width;
    }

    /**
     * How fast a ring of its wake spreads: faster for a bigger body moving faster, as the angle of a wake comes from
     * how fast it spreads next to how fast what makes it moves
     */
    static float wakeSize(float width, double speed) {
        return Math.min(MAX_WAKE_SIZE, ringSize(width) + (float) speed * 11F);
    }
}
