package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleRenderType;
import org.jetbrains.annotations.NotNull;

/**
 * A little flame licking up out of a skull's eye socket: a {@link FlameTongue} a fraction of a jet's size that lives
 * a short while and rises only a little, so a few overlapping keep the eye lit and flickering. One now and then lights
 * the skull's surroundings, from a point out in front of the face rather than where the flame is: a light right
 * against the face would wash it out.
 */
public class EyeFlame extends FlameTongue {

    // Its size next to a full jet's tongue
    static final float SCALE = 0.3F;
    // A share of a jet tongue's lift: it licks a quarter of a block or so up the face, not off into the air
    static final float LIFT = 0.25F;
    // How long it burns, in ticks: this many, and up to EXTRA_LIFE more
    static final int LIFE = 6;
    static final int EXTRA_LIFE = 5;
    // The light a skull's flame carrying it casts at its brightest, 0 to 1, and how far, in blocks: two or three alive
    // at once make the skull's light, about a quarter of a flame jet's
    static final float LIGHT_STRENGTH = 0.08F;
    static final float LIGHT_RADIUS = 1.5F;

    private final double lightX;
    private final double lightY;
    private final double lightZ;

    /**
     * A flame from an eye socket, starting at (xd, yd, zd) blocks a tick.
     *
     * @param carriesLight whether it lights the surroundings, from (lightX, lightY, lightZ)
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                  Palette palette, boolean carriesLight, double lightX, double lightY, double lightZ) {
        return new EyeFlame(level, x, y, z, xd, yd, zd, palette, carriesLight, lightX, lightY, lightZ);
    }

    protected EyeFlame(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                       Palette palette, boolean carriesLight, double lightX, double lightY, double lightZ) {
        super(level, x, y, z, xd, yd, zd, SCALE, carriesLight, palette);
        this.lifetime = LIFE + level.random.nextInt(EXTRA_LIFE);
        this.gravity = BUOYANCY * LIFT;
        this.lightX = lightX;
        this.lightY = lightY;
        this.lightZ = lightZ;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return DSurroundParticleRenderType.PARTICLE_SHEET_EYE_FLAME;
    }

    @Override
    protected void castLight(float partialTick) {
        PointLights.add(this.lightX, this.lightY, this.lightZ,
                this.rCol, this.gCol, this.bCol, LIGHT_STRENGTH * this.brightness(partialTick), LIGHT_RADIUS);
    }
}
