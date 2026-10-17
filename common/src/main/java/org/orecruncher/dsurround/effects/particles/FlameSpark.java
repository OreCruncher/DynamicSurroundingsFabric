package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.runtime.weather.SceneWind;
import org.orecruncher.dsurround.runtime.weather.Shelter;

/**
 * A spark thrown up from a fire. Shot up fast, it slows and arcs over as its weight tells, carries on the wind out in
 * the open, and twinkles as it cools from white-yellow to a dull red and goes out. Drawn as a short streak along the
 * way it is going, adding its light as the flames do (see {@link SoftParticles#flameLayer}),
 * and lit by itself.
 */
public class FlameSpark extends SingleQuadParticle {

    // Its colour as it cools: white-yellow, then orange, then a dull red
    private static final float[] HOT = {1F, 0.9F, 0.55F};
    private static final float[] WARM = {1F, 0.5F, 0.1F};
    private static final float[] COOL = {0.6F, 0.12F, 0.03F};
    private static final float WARM_AT = 0.35F;
    // Its weight, as a particle's gravity: enough to arc it over in its short life
    private static final float WEIGHT = 0.35F;
    private static final float DRAG = 0.97F;
    // Half the streak's width, and its length as a multiple of how far it moves in a tick
    private static final float HALF_WIDTH = 0.02F;
    private static final float STREAK = 2.5F;
    // How much it twinkles in brightness, and how fast
    private static final float TWINKLE = 0.35F;
    private static final float TWINKLE_RATE = 2.1F;
    // Fades out over the last share of its life
    private static final float FADE_OUT = 0.4F;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final float twinklePhase;
    private final float windExposure;

    /**
     * A spark.
     *
     * @param xd how fast it is thrown, in blocks a tick
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        return new FlameSpark(level, x, y, z, xd, yd, zd);
    }

    protected FlameSpark(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        // The constructor without velocity: the one with it adds a random velocity of its own
        super(level, x, y, z, DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()));
        var random = level.getRandom();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 15 + random.nextInt(16);
        this.friction = DRAG;
        this.gravity = WEIGHT;
        // Flies through the air without stopping at blocks: the soft shader fades it where it meets them
        this.hasPhysics = false;
        this.twinklePhase = random.nextFloat() * Mth.TWO_PI;
        this.windExposure = Shelter.exposure(level, BlockPos.containing(x, y, z));
        this.quadSize = HALF_WIDTH * 1.5F;
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return SoftParticles.flameLayer();
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void tick() {
        // Carried downwind, as far as it is out in the open
        this.xd += SceneWind.push(SceneWind.x(this.x, this.z), this.windExposure, this.friction);
        this.zd += SceneWind.push(SceneWind.z(this.x, this.z), this.windExposure, this.friction);
        super.tick();
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTick) {
        float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0F, 1F);
        float twinkle = 1F - TWINKLE * (0.5F + 0.5F * Mth.sin((this.age + partialTick) * TWINKLE_RATE + this.twinklePhase));
        float alpha = brightness(life) * twinkle;
        if (alpha <= 0F)
            return;
        Streaks.add(camera, partialTick, this.xo, this.yo, this.zo, this.x, this.y, this.z, STREAK, HALF_WIDTH,
                colorAt(life), alpha, this.getLightCoords(partialTick), this.getU0(), this.getU1(), this.getV0(), this.getV1());
        // Its own quad as well, clear: the particle engine draws the flames' group only if something in it has a quad,
        // and the streaks are drawn as that group is (see Streaks)
        this.setAlpha(0F);
        super.extract(state, camera, partialTick);
    }

    /**
     * How bright it is at a point in its life, 0 to 1: bright from the start, going out over the last
     * {@link #FADE_OUT} of it
     */
    static float brightness(float life) {
        return Mth.clamp((1F - life) / FADE_OUT, 0F, 1F);
    }

    // Reused for the colour as it is drawn: particles are drawn on the render thread only
    private static final float[] COLOR = new float[3];

    /**
     * Its colour at a point in its life, 0 to 1: from white-yellow through orange to a dull red. The array is reused.
     */
    static float[] colorAt(float life) {
        float[] from, to;
        float t;
        if (life < WARM_AT) {
            from = HOT;
            to = WARM;
            t = life / WARM_AT;
        } else {
            from = WARM;
            to = COOL;
            t = (life - WARM_AT) / (1F - WARM_AT);
        }
        t = Mth.clamp(t, 0F, 1F);
        for (int i = 0; i < 3; i++)
            COLOR[i] = Mth.lerp(t, from[i], to[i]);
        return COLOR;
    }
}
