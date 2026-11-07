// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.entity;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ParticleStatus;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.orecruncher.dsurround.effects.WaterRippleHandler;
import org.orecruncher.dsurround.lib.GameUtils;

/**
 * Rings spreading on the water where a player or mob falls in: one as it strikes, more following, bigger for a bigger
 * body falling faster. Its speed is worked out from its position, as the client doesn't run the movement of anyone but
 * the player. Nothing small enough to be a chicken makes them.
 */
public class WaterSplashEffect extends EntityEffectBase {

    // How wide a body has to be, and how fast it has to be falling, in blocks a tick, to make rings
    static final float MIN_WIDTH = 0.5F;
    static final double MIN_FALL_SPEED = 0.15D;
    // The rings a fall makes, and the ticks between them
    static final int RINGS = 3;
    static final int RING_INTERVAL = 4;
    // How much smaller each ring is than the one before
    private static final float RING_FALLOFF = 0.8F;
    private static final float MAX_RING_SIZE = 6F;
    // How far above the entity to look for the surface, in blocks: a fast fall can be under it within a tick
    private static final int SURFACE_SEARCH = 4;

    private boolean wasInWater = true;
    private double lastFallSpeed;
    private int ringsLeft;
    private int untilRing;
    private double ringX, ringY, ringZ;
    private float ringSize;

    @Override
    public void activate(final EntityEffectInfo info) {
        // Not a fall: it was already there when it came into view
        this.wasInWater = true;
        this.ringsLeft = 0;
    }

    @Override
    public void tick(final EntityEffectInfo info) {
        var entity = info.getEntity();
        double fallSpeed = entity.yo - entity.getY();
        boolean inWater = entity.isInWater();

        // The water slows it the tick it strikes, so the tick before says how hard it did
        if (inWater && !this.wasInWater && makesRings(entity.getBbWidth(), Math.max(fallSpeed, this.lastFallSpeed)))
            this.strike(entity.level(), entity.getX(), entity.getY(), entity.getZ(), entity.getBbWidth(), Math.max(fallSpeed, this.lastFallSpeed));
        this.wasInWater = inWater;
        this.lastFallSpeed = fallSpeed;

        if (this.ringsLeft > 0 && --this.untilRing <= 0 && entity.level() instanceof ClientLevel level)
            this.ring(level);
    }

    private void strike(Level level, double x, double y, double z, float width, double fallSpeed) {
        if (GameUtils.getGameSettings().particles().get() == ParticleStatus.MINIMAL)
            return;
        var pos = BlockPos.containing(x, y, z).mutable();
        int searched = 0;
        while (searched++ < SURFACE_SEARCH && !level.getFluidState(pos.above()).isEmpty())
            pos.move(0, 1, 0);
        var fluid = level.getFluidState(pos);
        if (fluid.isEmpty())
            return;

        this.ringX = x;
        this.ringY = pos.getY() + fluid.getHeight(level, pos);
        this.ringZ = z;
        this.ringSize = ringSize(width, fallSpeed);
        this.ringsLeft = RINGS;
        this.untilRing = 0;
    }

    private void ring(ClientLevel level) {
        WaterRippleHandler.createRing(level, this.ringX, this.ringY, this.ringZ, this.ringSize).ifPresent(this::addParticle);
        this.ringSize *= RING_FALLOFF;
        this.ringsLeft--;
        this.untilRing = RING_INTERVAL;
    }

    /**
     * Whether a body this wide falling this fast makes rings
     */
    static boolean makesRings(float width, double fallSpeed) {
        return width >= MIN_WIDTH && fallSpeed >= MIN_FALL_SPEED;
    }

    /**
     * How fast the first ring spreads, next to a raindrop's
     */
    static float ringSize(float width, double fallSpeed) {
        return Mth.clamp(2F + width + (float) fallSpeed * 2F, 2F, MAX_RING_SIZE);
    }
}
