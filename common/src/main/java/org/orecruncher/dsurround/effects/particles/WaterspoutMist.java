// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * A puff of a waterspout: a {@link SoftPuffParticle} with the waterfall mist's sprites. In the funnel, cloud made where
 * the air is drawn in, appearing anywhere up its height and turning around the axis there as it slowly rises, the
 * funnel bending and swaying under it; in the spray, sea water flung out around the foot, spreading and rising a little
 * before it falls back.
 */
public class WaterspoutMist extends SoftPuffParticle {

    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.25F;
    private static final float FADE_OUT = 0.5F;
    // How much it grows over its life
    private static final float FUNNEL_GROWTH = 0.6F;
    private static final float SPRAY_GROWTH = 2F;
    // How thick, at full strength, in the funnel and in the spray
    private static final float FUNNEL_ALPHA = 0.5F;
    private static final float SPRAY_ALPHA = 0.4F;
    // The funnel's light grey, a touch blue, against the spray's white: it is cloud, seen against the sky
    private static final float FUNNEL_SHADE = 0.92F;
    private static final float FUNNEL_BLUE = 0.97F;
    private static final float SPRAY_SHADE = 1F;
    // How fast funnel cloud climbs, in blocks a tick
    private static final float FUNNEL_RISE = 0.08F;
    // How fast spray rises and spreads, in blocks a tick
    private static final float SPRAY_RISE = 0.06F;
    private static final float SPRAY_SPREAD = 0.09F;

    private final VortexPath path;
    private final int vortexAge;
    private final double footY;
    private final boolean spray;
    private final float rise;
    private float height;
    private float angle;
    private float radius;

    /**
     * A puff of a waterspout
     *
     * @param vortexAge the waterspout's age now, in ticks
     * @param footY     the water's surface under it
     * @param strength  how strong it is, 0 to 1: a weak one is thinner
     * @param spray     spray at the foot, rather than the funnel's cloud
     * @param upShare   for the funnel, how far up it the puff appears, 0 to 1
     */
    public static Particle create(ClientLevel level, VortexPath path, int vortexAge, double footY, float strength, boolean spray, float upShare) {
        return new WaterspoutMist(level, path, vortexAge, footY, strength, spray, upShare);
    }

    protected WaterspoutMist(ClientLevel level, VortexPath path, int vortexAge, double footY, float strength, boolean spray, float upShare) {
        super(level, path.x(vortexAge), footY, path.z(vortexAge), DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()),
                FADE_IN, FADE_OUT, spray ? SPRAY_GROWTH : FUNNEL_GROWTH);
        var random = level.getRandom();
        this.path = path;
        this.vortexAge = vortexAge;
        this.footY = footY;
        this.spray = spray;
        this.angle = random.nextFloat() * Mth.TWO_PI;
        float shade;
        if (spray) {
            this.height = random.nextFloat() * 0.3F;
            this.rise = SPRAY_RISE * (0.5F + random.nextFloat());
            this.radius = path.radius(0F);
            this.lifetime = 30 + random.nextInt(20);
            this.startSize = 0.5F + random.nextFloat() * 0.3F;
            this.peakAlpha = SPRAY_ALPHA * strength;
            shade = SPRAY_SHADE;
        } else {
            this.height = upShare * path.height();
            this.rise = FUNNEL_RISE * (0.5F + random.nextFloat());
            // Through the funnel, not only at its edge, so it reads as a solid column
            this.radius = path.radius(this.height) * (0.2F + random.nextFloat() * 0.8F);
            this.lifetime = 40 + random.nextInt(20);
            // Bigger up high, where the funnel is wide and seen from far below
            this.startSize = (1F + random.nextFloat() * 0.5F) * (1F + 2F * upShare);
            this.peakAlpha = FUNNEL_ALPHA * strength;
            shade = FUNNEL_SHADE;
        }
        this.quadSize = this.startSize;
        this.rCol = this.gCol = shade;
        this.bCol = spray ? shade : FUNNEL_BLUE;
        this.gravity = 0F;
        this.friction = 1F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.15F;
        this.setPos(this.targetX(this.vortexAge), this.footY + this.height, this.targetZ(this.vortexAge));
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        // Where it will be at the end of this tick: around the axis at its height, wherever the waterspout has got to
        this.height += this.rise;
        if (this.spray)
            this.radius += SPRAY_SPREAD;
        this.angle += this.path.spin(this.radius);
        var vortex = this.vortexAge + this.age + 1;
        this.xd = this.targetX(vortex) - this.x;
        this.yd = this.footY + this.height - this.y;
        this.zd = this.targetZ(vortex) - this.z;
        super.tick();
    }

    private double targetX(float vortex) {
        return this.path.axisX(this.height, vortex) + Mth.cos(this.angle) * this.radius;
    }

    private double targetZ(float vortex) {
        return this.path.axisZ(this.height, vortex) + Mth.sin(this.angle) * this.radius;
    }
}
