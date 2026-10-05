package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.gui.ColorPalette;

/**
 * A puff of mist thrown up where a waterfall lands. Many of them overlapping make the mist, so each is small and
 * simple: it is thrown out from the impact, slowed by drag while slowly rising (it is lighter than air), grows as it
 * spreads, turns, and fades in quickly and out slowly. Drawn soft (see {@link SoftParticles}), so it fades into the
 * water and rock instead of cutting through them.
 * <p>
 * What makes it churn: the roll of air a waterfall drives where it lands. The falling water drags air down; it spreads
 * out along the surface, rises further out, and is drawn back in toward the falling water above, and down again.
 * Puffs circulate around a ring encircling the impact the same way, and wobble a little each in its own rhythm so no
 * two follow the same path.
 * <p>
 * Uses the waterfall_mist sprites (textures/particle/waterfall_mist_0 to 5), one picked at random and kept for the
 * puff's life rather than animated.
 */
public class WaterfallMist extends SingleQuadParticle {

    // Fractions of its life spent fading in at the start and out at the end
    private static final float FADE_IN = 0.15F;
    private static final float FADE_OUT = 0.5F;
    // How much it grows over its life: 1.5 makes it 2.5 times its starting size by the end
    private static final float GROWTH = 1.5F;
    // How far toward white the biome's water color is shifted
    private static final float WHITENESS = 0.65F;

    // The roll: how hard puffs are pushed around the ring, in blocks per tick per tick, at its core. Against the drag
    // that settles at about 0.1 blocks a tick, so a puff makes part of a turn in its life.
    private static final double CHURN = 0.008D;
    // The ring around the impact: its radius and height above the water, for the smallest waterfall and how much
    // more for each block of drop
    private static final double RING_RADIUS = 0.8D;
    private static final double RING_RADIUS_PER_STRENGTH = 0.12D;
    private static final double RING_HEIGHT = 0.3D;
    private static final double RING_HEIGHT_PER_STRENGTH = 0.08D;
    // How quickly the roll weakens away from the ring's core, in blocks: it halves this far out
    private static final double CHURN_FALLOFF = 1.0D;

    // Turbulence: each puff's own wobble, in blocks per tick per tick
    private static final double WOBBLE = 0.003D;

    private final float startSize;
    private final float peakAlpha;
    private final float rollSpeed;

    // Where the waterfall lands (the center of the impact, on the water's surface), and its ring
    private final double centerX;
    private final double centerZ;
    private final double surfaceY;
    private final double ringRadius;
    private final double ringHeight;
    private final double churn;

    // This puff's wobble: a rate and a starting point in its cycle for each direction
    private final float wobbleRateX;
    private final float wobbleRateY;
    private final float wobbleRateZ;
    private final float wobblePhaseX;
    private final float wobblePhaseY;
    private final float wobblePhaseZ;

    /**
     * A mist puff, or null if its sprites aren't available.
     *
     * @param xd       starting velocity, in blocks per tick; drag slows it
     * @param centerX  the center of the impact, where the waterfall lands
     * @param surfaceY the height of the water's surface there
     * @param strength the waterfall's strength, the height of its drop: a bigger waterfall makes bigger puffs and a
     *                 bigger, stronger roll
     */
    @Nullable
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                                  double centerX, double surfaceY, double centerZ, int strength) {
        var sprites = ParticleUtils.getSpriteProvider(DSurroundParticleTypes.WATERFALL_MIST);
        if (sprites == null)
            return null;
        return new WaterfallMist(level, x, y, z, xd, yd, zd, centerX, surfaceY, centerZ, sprites, strength);
    }

    protected WaterfallMist(ClientLevel level, double x, double y, double z, double xd, double yd, double zd,
                            double centerX, double surfaceY, double centerZ, SpriteSet sprites, int strength) {
        // The constructor without velocity: the one with it adds a random velocity of its own
        super(level, x, y, z, sprites.get(level.getRandom()));
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;

        this.centerX = centerX;
        this.centerZ = centerZ;
        this.surfaceY = surfaceY;
        this.ringRadius = RING_RADIUS + strength * RING_RADIUS_PER_STRENGTH;
        this.ringHeight = RING_HEIGHT + strength * RING_HEIGHT_PER_STRENGTH;
        this.churn = CHURN * (1D + strength * 0.05D);

        var random = level.getRandom();
        this.lifetime = 20 + random.nextInt(40);
        // Half the puff's width, as quadSize is; 0.2 to 0.4 blocks, 15% more for each block of drop. Every waterfall
        // makes the same number of puffs, so a bigger one fills its bigger cloud with bigger puffs: its ring (see
        // RING_RADIUS) covers about 4.7 times the area at strength 10 as at 1, and this makes its puffs about 2.2
        // times as wide, to cover it about as densely.
        this.startSize = (0.2F + random.nextFloat() * 0.2F) * (1F + strength * 0.15F);
        this.quadSize = this.startSize;
        this.peakAlpha = 0.2F + random.nextFloat() * 0.2F;
        this.alpha = 0F;

        // Drifts through blocks rather than stopping at them: the soft shader fades it where it meets them
        this.hasPhysics = false;
        this.friction = 0.92F;
        // Negative gravity: it rises, slowly
        this.gravity = -0.015F;
        this.roll = this.oRoll = random.nextFloat() * Mth.TWO_PI;
        // Up to about 3.4 degrees a tick, either way
        this.rollSpeed = (random.nextFloat() - 0.5F) * 0.12F;

        // Each a full cycle every 1.5 to 4 seconds, from anywhere in it
        this.wobbleRateX = 0.08F + random.nextFloat() * 0.13F;
        this.wobbleRateY = 0.08F + random.nextFloat() * 0.13F;
        this.wobbleRateZ = 0.08F + random.nextFloat() * 0.13F;
        this.wobblePhaseX = random.nextFloat() * Mth.TWO_PI;
        this.wobblePhaseY = random.nextFloat() * Mth.TWO_PI;
        this.wobblePhaseZ = random.nextFloat() * Mth.TWO_PI;


        // The biome's water color, shifted toward white
        var biomeColor = level.getBiome(BlockPos.containing(x, y, z)).value().getWaterColor();
        var colorRgb = ARGB.srgbLerp(WHITENESS, biomeColor, ColorPalette.MC_WHITE.getValue());
        this.rCol = ColorPalette.getRed(colorRgb) / 255F;
        this.gCol = ColorPalette.getGreen(colorRgb) / 255F;
        this.bCol = ColorPalette.getBlue(colorRgb) / 255F;
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        // Drawn soft (see SoftParticles) if it can be
        return SoftParticles.mistLayer();
    }

    @Override
    public void tick() {
        this.churn();
        this.wobble();
        // Ages it (removing it at the end of its life) and moves it, with drag and the slow rise
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.rollSpeed;
    }

    /**
     * Pushes the puff around the ring encircling the impact, in the upright plane through the impact's center: out
     * below the ring, up outside it, in above it, and down inside it, as the air rolls where a waterfall lands.
     */
    private void churn() {
        double dx = this.x - this.centerX;
        double dz = this.z - this.centerZ;
        double distance = Math.sqrt(dx * dx + dz * dz);
        if (distance < 1.0E-4D)
            return; // Right at the center there is no "outward"; a tick of drift moves it off

        // Where it is relative to the ring's core: out (+) or in (-) from it, and above (+) or below (-) it
        double out = distance - this.ringRadius;
        double up = (this.y - this.surfaceY) - this.ringHeight;
        double fromCore = Math.sqrt(out * out + up * up);
        if (fromCore < 1.0E-4D)
            return;

        // Around the core: below it that is outward, outside it upward, above it inward, inside it downward
        double push = this.churn / (1D + fromCore / CHURN_FALLOFF);
        double outward = -up / fromCore * push;
        double upward = out / fromCore * push;

        // The downward pull stops at the water: air isn't drawn down into it. Without this, puffs starting under the
        // surface inside the ring were held there, and about a quarter never came up into view.
        if (upward < 0D && this.y < this.surfaceY)
            upward = 0D;

        this.xd += dx / distance * outward;
        this.zd += dz / distance * outward;
        this.yd += upward;
    }

    /**
     * A small, smooth push that varies over the puff's life, each puff in its own rhythm.
     */
    private void wobble() {
        this.xd += Mth.sin(this.age * this.wobbleRateX + this.wobblePhaseX) * WOBBLE;
        this.yd += Mth.sin(this.age * this.wobbleRateY + this.wobblePhaseY) * WOBBLE * 0.5D;
        this.zd += Mth.sin(this.age * this.wobbleRateZ + this.wobblePhaseZ) * WOBBLE;
    }

    /**
     * How far through its life it is, 0 to 1, between ticks.
     */
    private float lifeFraction(float partialTick) {
        return Mth.clamp((this.age + partialTick) / this.lifetime, 0F, 1F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.startSize * (1F + GROWTH * this.lifeFraction(partialTick));
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTick) {
        // Set each frame, so the fades are smooth rather than stepping once a tick
        float life = this.lifeFraction(partialTick);
        float fade = Math.min(life / FADE_IN, (1F - life) / FADE_OUT);
        this.alpha = this.peakAlpha * Mth.clamp(fade, 0F, 1F);
        super.extract(state, camera, partialTick);
    }
}
