// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.effects.actors.VortexPath;

/**
 * A tongue of flame in a firespout: a flame jet's {@link FlameTongue}, burning white-yellow, orange, then red, glowing
 * and adding its light, but whirled up the funnel around its axis, as the dust in a dust devil is, rather than rising
 * freely. It cools as it climbs, so the column burns hottest at its foot.
 */
public class FirespoutFlame extends FlameTongue {

    // How big, against a flame jet's tongue
    private static final float MIN_SCALE = 3F;
    private static final float MAX_SCALE = 5F;
    // The share of the funnel it climbs before it has burned out, at most
    private static final float MAX_CLIMB = 0.75F;
    // Its light, against a flame jet tongue's: not as bright, and reaching a set distance rather than one growing with
    // its size, or a few big flames light the whole cavern
    private static final float LIGHT_BRIGHTNESS = 0.6F;
    private static final float LIGHT_RADIUS = 8F;

    private final FunnelOrbit orbit;
    private final float rise;

    /**
     * A tongue of flame in a firespout
     *
     * @param vortexAge    the firespout's age now, in ticks
     * @param footY        the lava's surface under it
     * @param carriesLight whether it lights its surroundings: only some of them do
     */
    public static Particle create(ClientLevel level, VortexPath path, int vortexAge, double footY, boolean carriesLight) {
        return new FirespoutFlame(level, path, vortexAge, footY, carriesLight);
    }

    protected FirespoutFlame(ClientLevel level, VortexPath path, int vortexAge, double footY, boolean carriesLight) {
        super(level, path.x(vortexAge), footY, path.z(vortexAge), 0D, 0D, 0D,
                Mth.lerp(level.getRandom().nextFloat(), MIN_SCALE, MAX_SCALE), carriesLight, FIRE);
        var random = level.getRandom();
        var angle = random.nextFloat() * Mth.TWO_PI;
        var height = random.nextFloat() * 0.5F;
        this.lifetime = 20 + random.nextInt(16);
        this.rise = (path.height() * MAX_CLIMB * (0.4F + random.nextFloat() * 0.6F) - height) / this.lifetime;
        this.orbit = new FunnelOrbit(path, vortexAge, footY, height, angle, path.radius(height) * (0.5F + random.nextFloat() * 0.5F));
        // Held on the funnel: no lift or drag of its own
        this.gravity = 0F;
        this.friction = 1F;
        this.setPos(this.orbit.x(0), this.orbit.y(), this.orbit.z(0));
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
    }

    @Override
    public void tick() {
        // Where it will be at the end of this tick: up and around the funnel, wherever the firespout has got to
        this.orbit.climb(this.rise);
        this.orbit.around(Math.max(this.orbit.radius(), this.orbit.path().radius(this.orbit.height()) * 0.5F));
        this.xd = this.orbit.x(this.age + 1) - this.x;
        this.yd = this.orbit.y() - this.y;
        this.zd = this.orbit.z(this.age + 1) - this.z;
        super.tick();
    }

    @Override
    protected void castLight(float partialTick) {
        PointLights.add(
                Mth.lerp(partialTick, this.xo, this.x),
                Mth.lerp(partialTick, this.yo, this.y),
                Mth.lerp(partialTick, this.zo, this.z),
                this.rCol, this.gCol, this.bCol, LIGHT_STRENGTH * LIGHT_BRIGHTNESS * this.brightness(partialTick), LIGHT_RADIUS);
    }
}
