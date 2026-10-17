package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Shelter;

/**
 * A puff of steam rising from heated water. A {@link SoftPuffParticle}, as waterfall mist is, with the mist's sprites,
 * but white and thicker: it rises steadily, wobbles, grows as it spreads, drifts with the wind out in the open, and
 * fades in quickly and out slowly.
 */
public class SteamPuff extends SoftPuffParticle {

    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT = 0.6F;
    // How much it grows over its life: 1.5 makes it 2.5 times its starting size by the end
    private static final float GROWTH = 1.5F;
    // Thicker than mist (0.2 to 0.4): this, and up to this much more
    private static final float MIN_ALPHA = 0.45F;
    private static final float ALPHA_RANGE = 0.2F;
    // Near white, a touch of grey so it reads against snow and sky
    private static final float SHADE = 0.95F;
    // Its rise, in blocks a tick: how fast it leaves the water, and the lift that keeps it rising against the drag
    private static final double RISE = 0.06D;
    private static final float LIFT = -0.01F;
    // The share of its speed it keeps each tick
    private static final float DRAG = 0.94F;
    // Turbulence: its own wobble, in blocks per tick per tick
    private static final double WOBBLE = 0.003D;

    // How much of the wind reaches it, 0 to 1, from where it began
    private final float windExposure;
    // Its wobble: a rate and a starting point in its cycle for each direction
    private final float wobbleRateX;
    private final float wobbleRateZ;
    private final float wobblePhaseX;
    private final float wobblePhaseZ;

    /**
     * A puff of steam.
     *
     * @param small from a small source (a water cauldron): a smaller, slower puff than from open water
     */
    public static Particle create(ClientLevel level, double x, double y, double z, boolean small) {
        return new SteamPuff(level, x, y, z, small);
    }

    protected SteamPuff(ClientLevel level, double x, double y, double z, boolean small) {
        super(level, x, y, z, DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()), FADE_IN, FADE_OUT, GROWTH);
        var random = level.getRandom();
        var scale = small ? 0.5F : 1F;

        this.xd = 0D;
        this.yd = RISE * scale;
        this.zd = 0D;
        this.lifetime = 40 + random.nextInt(30);
        // Half the puff's width, as quadSize is
        this.startSize = (0.25F + random.nextFloat() * 0.15F) * scale;
        this.quadSize = this.startSize;
        this.peakAlpha = MIN_ALPHA + random.nextFloat() * ALPHA_RANGE;
        this.rCol = this.gCol = this.bCol = SHADE;

        this.friction = DRAG;
        this.gravity = LIFT;
        this.windExposure = Shelter.exposure(level, BlockPos.containing(x, y, z));
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.1F;

        // Each a full cycle every 1.5 to 4 seconds, from anywhere in it
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
        // Ages it (removing it at the end of its life), moves it with drag and the lift, and turns it
        super.tick();
    }

}
