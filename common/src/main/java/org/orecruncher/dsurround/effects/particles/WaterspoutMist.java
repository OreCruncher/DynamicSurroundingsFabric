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

    private final FunnelOrbit orbit;
    private final boolean spray;
    private final float rise;

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
        this.spray = spray;
        var angle = random.nextFloat() * Mth.TWO_PI;
        float shade, height, radius;
        if (spray) {
            height = random.nextFloat() * 0.3F;
            this.rise = SPRAY_RISE * (0.5F + random.nextFloat());
            radius = path.radius(0F);
            this.lifetime = 30 + random.nextInt(20);
            this.startSize = 0.5F + random.nextFloat() * 0.3F;
            this.peakAlpha = SPRAY_ALPHA * strength;
            shade = SPRAY_SHADE;
        } else {
            height = upShare * path.height();
            this.rise = FUNNEL_RISE * (0.5F + random.nextFloat());
            // Through the funnel, not only at its edge, so it reads as a solid column
            radius = path.radius(height) * (0.2F + random.nextFloat() * 0.8F);
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
        this.orbit = new FunnelOrbit(path, vortexAge, footY, height, angle, radius);
        this.setPos(this.orbit.x(0), this.orbit.y(), this.orbit.z(0));
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        // Where it will be at the end of this tick: around the axis at its height, wherever the waterspout has got to
        this.orbit.climb(this.rise);
        this.orbit.around(this.spray ? this.orbit.radius() + SPRAY_SPREAD : this.orbit.radius());
        this.xd = this.orbit.x(this.age + 1) - this.x;
        this.yd = this.orbit.y() - this.y;
        this.zd = this.orbit.z(this.age + 1) - this.z;
        super.tick();
    }
}
