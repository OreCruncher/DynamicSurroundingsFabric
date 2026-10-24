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
 * A tongue of flame in a flame jet. Many small ones overlapping make the fire: each is a soft glowing puff, drawn
 * adding its light to what is behind it (see {@link SoftParticles#flameLayer}), so where they
 * bunch the fire burns brighter. It burns white-yellow as it leaves the source, then orange, then red as it cools, and
 * shrinks; hot gas is light, so it speeds up as it rises; it flickers, and leans with the wind out in the open.
 * Lit by itself, not the world: fire is as bright at night as by day.
 */
public class FlameTongue extends SoftPuffParticle {

    // Fractions of its life spent flaring in at the start and dying out at the end
    private static final float FADE_IN = 0.1F;
    private static final float FADE_OUT = 0.5F;
    // It shrinks as it burns: to 40% of its starting size by the end
    private static final float GROWTH = -0.6F;
    // Its colour as it burns: hottest when new, orange through the middle of its life, red as it dies
    private static final float[] HOT = {1F, 0.95F, 0.7F};
    private static final float[] WARM = {1F, 0.55F, 0.12F};
    private static final float[] COOL = {0.75F, 0.15F, 0.04F};
    // How far through its life it is when orange
    private static final float WARM_AT = 0.4F;
    // Buoyancy: the lift a tick, as a particle's gravity (negative rises), and the drag
    private static final float BUOYANCY = -0.4F;
    private static final float DRAG = 0.96F;
    // How much it flickers, in size, and how fast, in radians a tick
    private static final float FLICKER = 0.18F;
    private static final float FLICKER_RATE = 1.3F;
    // Full brightness, block and sky: it lights itself
    private static final int FULL_BRIGHT = 0xF000F0;
    // The light a tongue carrying the jet's light casts at its brightest, 0 to 1, and how far, in blocks, for a full
    // jet's tongue: a handful alive at once make the jet's light, rising and fading with them, so it wavers
    static final float LIGHT_STRENGTH = 0.2F;
    static final float LIGHT_RADIUS = 3.5F;

    private final float flickerPhase;
    private final float windExposure;
    // Whether it lights its surroundings, and how far
    private final boolean carriesLight;
    private final float lightRadius;

    /**
     * A tongue of flame.
     *
     * @param xd    starting speed, in blocks a tick; it rises faster as it goes
     * @param scale        how big, 1 for a full jet's tongue
     * @param carriesLight whether it lights its surroundings: only some of a jet's tongues do
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                  float scale, boolean carriesLight) {
        return new FlameTongue(level, x, y, z, xd, yd, zd, scale, carriesLight);
    }

    protected FlameTongue(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, float scale,
                          boolean carriesLight) {
        super(level, x, y, z, DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()), FADE_IN, FADE_OUT, GROWTH);
        var random = level.getRandom();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 10 + random.nextInt(9);
        this.startSize = (0.12F + random.nextFloat() * 0.08F) * scale;
        this.quadSize = this.startSize;
        this.peakAlpha = 0.6F + random.nextFloat() * 0.3F;
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.2F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        this.friction = DRAG;
        this.gravity = BUOYANCY;
        this.flickerPhase = random.nextFloat() * Mth.TWO_PI;
        this.windExposure = Shelter.exposure(level, BlockPos.containing(x, y, z));
        this.carriesLight = carriesLight;
        this.lightRadius = LIGHT_RADIUS * scale;
        this.setColor(HOT);
    }

    private void setColor(float[] color) {
        this.rCol = color[0];
        this.gCol = color[1];
        this.bCol = color[2];
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return SoftParticles.flameLayer();
    }

    @Override
    public void tick() {
        // Leans downwind, as far as it is out in the open
        this.xd += SceneWind.push(SceneWind.x(this.x, this.z), this.windExposure, this.friction);
        this.zd += SceneWind.push(SceneWind.z(this.x, this.z), this.windExposure, this.friction);
        super.tick();
    }

    @Override
    public float getQuadSize(float partialTick) {
        float flicker = 1F + FLICKER * Mth.sin((this.age + partialTick) * FLICKER_RATE + this.flickerPhase);
        return super.getQuadSize(partialTick) * flicker;
    }

    @Override
    protected int getLightCoords(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTick) {
        var color = colorAt(this.lifeFraction(partialTick));
        this.rCol = color[0];
        this.gCol = color[1];
        this.bCol = color[2];
        super.extract(state, camera, partialTick);

        // Its light, as bright as it is now: fading in and out with it, and flickering with it
        if (this.carriesLight && this.peakAlpha > 0F && PointLights.flameLightsOn()) {
            var size = this.getQuadSize(partialTick) / Math.max(super.getQuadSize(partialTick), 1.0E-4F);
            var strength = LIGHT_STRENGTH * (this.alpha / this.peakAlpha) * size;
            PointLights.add(
                    Mth.lerp(partialTick, this.xo, this.x),
                    Mth.lerp(partialTick, this.yo, this.y),
                    Mth.lerp(partialTick, this.zo, this.z),
                    this.rCol, this.gCol, this.bCol, strength, this.lightRadius);
        }
    }

    // Reused for the colour as it is drawn: particles are drawn on the render thread only
    private static final float[] COLOR = new float[3];

    /**
     * Its colour at a point in its life, 0 to 1: from white-yellow through orange to red. The array is reused.
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
