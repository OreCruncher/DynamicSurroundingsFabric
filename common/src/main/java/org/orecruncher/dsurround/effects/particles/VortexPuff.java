// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * A puff of dust or powder snow in a dust devil or a snowspout: a {@link SoftPuffParticle} with the dust storms'
 * sprites, whirled around the axis as it rises up the funnel, following the vortex as it travels. A skirt puff stays
 * low instead, spreading out around the foot.
 */
public class VortexPuff extends SoftPuffParticle {

    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.15F;
    private static final float FADE_OUT = 0.5F;
    // How much it grows over its life: up the funnel the dust thins out over a wider ring
    private static final float GROWTH = 2F;
    private static final float SKIRT_GROWTH = 1.5F;
    // How thick, at full strength, in the funnel and in the skirt
    private static final float FUNNEL_ALPHA = 0.3F;
    private static final float SKIRT_ALPHA = 0.25F;
    // How fast a skirt puff rises and spreads, in blocks a tick
    private static final float SKIRT_RISE = 0.03F;
    private static final float SKIRT_SPREAD = 0.05F;

    private final FunnelOrbit orbit;
    private final boolean skirt;
    private final float rise;

    /**
     * A puff in a vortex
     *
     * @param vortexAge the vortex's age now, in ticks
     * @param groundY   the ground under it
     * @param color    what it lifts' color, as RGB
     * @param shade    how much darker it is in the air, 0 to 1 (1 as it is)
     * @param grey     how much greyer, 0 to 1 (0 as it is)
     * @param strength how strong the vortex is, 0 to 1: a weak one is thinner
     * @param skirt    a puff of the skirt around the foot, rather than the funnel
     */
    public static Particle create(ClientLevel level, VortexPath path, int vortexAge, double groundY, int color, float shade, float grey, float strength, boolean skirt) {
        return new VortexPuff(level, path, vortexAge, groundY, color, shade, grey, strength, skirt);
    }

    protected VortexPuff(ClientLevel level, VortexPath path, int vortexAge, double groundY, int color, float shade, float grey, float strength, boolean skirt) {
        super(level, path.x(vortexAge), groundY, path.z(vortexAge), DSurroundParticleSprites.DUST.get(level.getRandom()), FADE_IN, FADE_OUT, skirt ? SKIRT_GROWTH : GROWTH);
        var random = level.getRandom();
        this.skirt = skirt;
        var angle = random.nextFloat() * Mth.TWO_PI;
        float height, radius;
        if (skirt) {
            this.rise = SKIRT_RISE * (0.5F + random.nextFloat());
            height = random.nextFloat() * 0.3F;
            radius = path.radius(0F);
            this.lifetime = 30 + random.nextInt(20);
            this.startSize = 0.45F + random.nextFloat() * 0.2F;
            this.peakAlpha = SKIRT_ALPHA * strength;
        } else {
            // Rises the funnel in two to four seconds, from near the ground
            height = random.nextFloat() * 0.5F;
            var climb = path.height() * (0.7F + random.nextFloat() * 0.3F) - height;
            this.lifetime = 40 + random.nextInt(40);
            this.rise = climb / this.lifetime;
            radius = path.radius(height);
            this.startSize = 0.3F + random.nextFloat() * 0.2F;
            this.peakAlpha = FUNNEL_ALPHA * strength;
        }
        this.quadSize = this.startSize;
        float r = ((color >> 16) & 0xFF) / 255F, g = ((color >> 8) & 0xFF) / 255F, b = (color & 0xFF) / 255F;
        float mean = (r + g + b) / 3F;
        this.rCol = Mth.lerp(grey, r, mean) * shade;
        this.gCol = Mth.lerp(grey, g, mean) * shade;
        this.bCol = Mth.lerp(grey, b, mean) * shade;
        this.gravity = 0F;
        this.friction = 1F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.2F;
        this.orbit = new FunnelOrbit(path, vortexAge, groundY, height, angle, radius);
        this.setPos(this.orbit.x(0), this.orbit.y(), this.orbit.z(0));
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        // Where it will be at the end of this tick: up and around the funnel, wherever the vortex has got to
        this.orbit.climb(this.rise);
        this.orbit.around(this.skirt ? this.orbit.radius() + SKIRT_SPREAD : this.orbit.path().radius(this.orbit.height()));
        this.xd = this.orbit.x(this.age + 1) - this.x;
        this.yd = this.orbit.y() - this.y;
        this.zd = this.orbit.z(this.age + 1) - this.z;
        super.tick();
    }
}
