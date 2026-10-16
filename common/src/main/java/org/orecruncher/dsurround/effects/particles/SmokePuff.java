package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Shelter;

/**
 * A puff of smoke rising from a fire. A {@link SoftPuffParticle}, as steam is, with the mist's sprites, but dark and
 * lit by the world: it rises, billows as it spreads, drifts with the wind out in the open, and thins as it goes.
 */
public class SmokePuff extends SoftPuffParticle {

    private static final float FADE_IN = 0.2F;
    private static final float FADE_OUT = 0.6F;
    // Billows out: three times its starting size by the end
    private static final float GROWTH = 2F;
    // Dark grey, a little varied
    private static final float MIN_SHADE = 0.15F;
    private static final float SHADE_RANGE = 0.12F;
    // The lift that keeps it rising against the drag, as a particle's gravity (negative rises), and the drag
    private static final float LIFT = -0.25F;
    private static final float DRAG = 0.93F;
    // Turbulence: its own wobble, in blocks per tick per tick
    private static final double WOBBLE = 0.003D;

    private final float windExposure;
    private final float wobbleRateX;
    private final float wobbleRateZ;
    private final float wobblePhaseX;
    private final float wobblePhaseZ;

    /**
     * A puff of smoke.
     *
     * @param yd    how fast it leaves the fire, in blocks a tick
     * @param scale how big, 1 for a full jet's smoke
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double yd, float scale) {
        return new SmokePuff(level, x, y, z, yd, scale);
    }

    protected SmokePuff(ClientLevel level, double x, double y, double z, double yd, float scale) {
        super(level, x, y, z, DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()), FADE_IN, FADE_OUT, GROWTH);
        var random = level.getRandom();
        this.xd = 0D;
        this.yd = yd;
        this.zd = 0D;
        this.lifetime = 50 + random.nextInt(30);
        this.startSize = (0.18F + random.nextFloat() * 0.12F) * scale;
        this.quadSize = this.startSize;
        this.peakAlpha = 0.25F + random.nextFloat() * 0.15F;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.08F;
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
