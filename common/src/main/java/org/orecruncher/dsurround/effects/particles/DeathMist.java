package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Shelter;

/**
 * A puff of dark mist where a mob's body vanishes, in place of vanilla's white poof. A {@link SoftPuffParticle}, as
 * steam is, with the mist's sprites, but dark gray and lit by the world: it drifts out from the body, slows, billows
 * as it spreads, barely rises, and drifts with the wind out in the open.
 */
public class DeathMist extends SoftPuffParticle {

    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT = 0.6F;
    // Billows out: two and a half times its starting size by the end
    private static final float GROWTH = 1.5F;
    // Dark gray, a little varied
    private static final float MIN_SHADE = 0.18F;
    private static final float SHADE_RANGE = 0.12F;
    private static final float MIN_ALPHA = 0.35F;
    private static final float ALPHA_RANGE = 0.15F;
    // A slight lift, as a particle's gravity (negative rises), and a strong drag: it settles where it was thrown
    private static final float LIFT = -0.02F;
    private static final float DRAG = 0.88F;
    // Turbulence: its own wobble, in blocks per tick per tick
    private static final double WOBBLE = 0.002D;

    private final float windExposure;
    private final float wobbleRateX;
    private final float wobbleRateZ;
    private final float wobblePhaseX;
    private final float wobblePhaseZ;

    /**
     * A puff of death mist.
     *
     * @param xd    starting speed, in blocks a tick
     * @param scale how big, 1 for a zombie's
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float scale) {
        return new DeathMist(level, x, y, z, xd, yd, zd, scale);
    }

    protected DeathMist(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float scale) {
        super(level, x, y, z, FADE_IN, FADE_OUT, GROWTH);
        var random = level.random;
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 25 + random.nextInt(20);
        // Half the puff's width, as quadSize is
        this.startSize = (0.22F + random.nextFloat() * 0.12F) * scale;
        this.quadSize = this.startSize;
        this.peakAlpha = MIN_ALPHA + random.nextFloat() * ALPHA_RANGE;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.1F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.friction = DRAG;
        this.gravity = LIFT;
        var shade = MIN_SHADE + random.nextFloat() * SHADE_RANGE;
        this.rCol = this.gCol = this.bCol = shade;
        this.windExposure = Shelter.exposure(level, BlockPos.containing(x, y, z));
        this.wobbleRateX = 0.08F + random.nextFloat() * 0.13F;
        this.wobbleRateZ = 0.08F + random.nextFloat() * 0.13F;
        this.wobblePhaseX = random.nextFloat() * Mth.TWO_PI;
        this.wobblePhaseZ = random.nextFloat() * Mth.TWO_PI;
        this.setSprite(DSurroundParticleSprites.WATERFALL_MIST.get(random));
    }

    @Override
    public void tick() {
        this.xd += Mth.sin(this.age * this.wobbleRateX + this.wobblePhaseX) * WOBBLE;
        this.zd += Mth.sin(this.age * this.wobbleRateZ + this.wobblePhaseZ) * WOBBLE;
        // Drifts downwind, as far as it is out in the open
        this.xd += SceneWind.push(SceneWind.x(this.x, this.z), this.windExposure, this.friction);
        this.zd += SceneWind.push(SceneWind.z(this.x, this.z), this.windExposure, this.friction);
        super.tick();
    }
}
