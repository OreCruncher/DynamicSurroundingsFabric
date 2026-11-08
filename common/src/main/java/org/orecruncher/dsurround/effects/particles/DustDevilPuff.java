// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * A puff of dust in a dust devil: a {@link SoftPuffParticle} with the dust storms' sprites, whirled around the devil's
 * axis as it rises up the funnel, following the devil as it travels. A skirt puff stays low instead, spreading out
 * around the foot.
 */
public class DustDevilPuff extends SoftPuffParticle {

    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.15F;
    private static final float FADE_OUT = 0.5F;
    // How much it grows over its life: up the funnel the dust thins out over a wider ring
    private static final float GROWTH = 2F;
    private static final float SKIRT_GROWTH = 1.5F;
    // How thick, at full strength, in the funnel and in the skirt
    private static final float FUNNEL_ALPHA = 0.3F;
    private static final float SKIRT_ALPHA = 0.25F;
    // The dust's color is the ground's, from the map: in the air it is darker, and greyer, as dust scatters the light
    private static final float SHADE = 0.72F;
    private static final float GREY = 0.35F;
    // How fast a skirt puff rises and spreads, in blocks a tick
    private static final float SKIRT_RISE = 0.03F;
    private static final float SKIRT_SPREAD = 0.05F;

    private final VortexPath path;
    // The devil's age when the puff was made, and the ground under it then
    private final int devilAge;
    private final double groundY;
    private final boolean skirt;
    private final float rise;
    private float height;
    private float angle;
    private float radius;

    /**
     * A puff in a dust devil
     *
     * @param devilAge the devil's age now, in ticks
     * @param groundY  the ground under the devil
     * @param color    the dust's color, as RGB
     * @param strength how strong the devil is, 0 to 1: a weak one is thinner
     * @param skirt    a puff of the skirt around the foot, rather than the funnel
     */
    public static Particle create(ClientLevel level, VortexPath path, int devilAge, double groundY, int color, float strength, boolean skirt) {
        return new DustDevilPuff(level, path, devilAge, groundY, color, strength, skirt);
    }

    protected DustDevilPuff(ClientLevel level, VortexPath path, int devilAge, double groundY, int color, float strength, boolean skirt) {
        super(level, path.x(devilAge), groundY, path.z(devilAge), DSurroundParticleSprites.DUST.get(level.getRandom()), FADE_IN, FADE_OUT, skirt ? SKIRT_GROWTH : GROWTH);
        var random = level.getRandom();
        this.path = path;
        this.devilAge = devilAge;
        this.groundY = groundY;
        this.skirt = skirt;
        this.angle = random.nextFloat() * Mth.TWO_PI;
        if (skirt) {
            this.rise = SKIRT_RISE * (0.5F + random.nextFloat());
            this.height = random.nextFloat() * 0.3F;
            this.radius = path.radius(0F);
            this.lifetime = 30 + random.nextInt(20);
            this.startSize = 0.45F + random.nextFloat() * 0.2F;
            this.peakAlpha = SKIRT_ALPHA * strength;
        } else {
            // Rises the funnel in two to four seconds, from near the ground
            this.height = random.nextFloat() * 0.5F;
            var climb = path.height() * (0.7F + random.nextFloat() * 0.3F) - this.height;
            this.lifetime = 40 + random.nextInt(40);
            this.rise = climb / this.lifetime;
            this.radius = path.radius(this.height);
            this.startSize = 0.3F + random.nextFloat() * 0.2F;
            this.peakAlpha = FUNNEL_ALPHA * strength;
        }
        this.quadSize = this.startSize;
        float r = ((color >> 16) & 0xFF) / 255F, g = ((color >> 8) & 0xFF) / 255F, b = (color & 0xFF) / 255F;
        float grey = (r + g + b) / 3F;
        this.rCol = Mth.lerp(GREY, r, grey) * SHADE;
        this.gCol = Mth.lerp(GREY, g, grey) * SHADE;
        this.bCol = Mth.lerp(GREY, b, grey) * SHADE;
        this.gravity = 0F;
        this.friction = 1F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.2F;
        this.setPos(this.targetX(this.devilAge), this.groundY + this.height, this.targetZ(this.devilAge));
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        // Where it will be at the end of this tick: up and around the funnel, wherever the devil has got to
        this.height += this.rise;
        this.radius = this.skirt ? this.radius + SKIRT_SPREAD : this.path.radius(this.height);
        this.angle += this.path.spin(this.radius);
        var devil = this.devilAge + this.age + 1;
        this.xd = this.targetX(devil) - this.x;
        this.yd = this.groundY + this.height - this.y;
        this.zd = this.targetZ(devil) - this.z;
        super.tick();
    }

    // Around the funnel's axis at its height, which bends and sways
    private double targetX(float devil) {
        return this.path.axisX(this.height, devil) + Mth.cos(this.angle) * this.radius;
    }

    private double targetZ(float devil) {
        return this.path.axisZ(this.height, devil) + Mth.sin(this.angle) * this.radius;
    }
}
