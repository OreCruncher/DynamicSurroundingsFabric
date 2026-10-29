package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.QuadParticleRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * A drop of molten rock thrown up by a lava jet. It glows, arcs up and falls back, cooling from yellow-orange to a dull
 * red as it goes. Falling back into lava, it is gone; landing on something solid, it gives a wisp of smoke and goes
 * out. Drawn as a short, thick streak, as a spark is, adding its light.
 */
public class LavaDroplet extends SingleQuadParticle {

    // Its colour as it cools: yellow-orange, then orange, then a dull red
    private static final float[] HOT = {1F, 0.75F, 0.3F};
    private static final float[] WARM = {1F, 0.38F, 0.05F};
    private static final float[] COOL = {0.55F, 0.09F, 0.02F};
    private static final float WARM_AT = 0.4F;
    // Heavier than a spark: it falls back rather than drifting
    private static final float WEIGHT = 0.9F;
    private static final float DRAG = 0.98F;
    // Thicker and shorter than a spark's streak
    private static final float HALF_WIDTH = 0.045F;
    private static final float STREAK = 1.5F;
    // Goes out over the last share of its life, if it hasn't landed by then
    private static final float FADE_OUT = 0.25F;
    // Ticks before it can fall back into the lava it was thrown from
    private static final int CLEAR_TICKS = 3;
    private static final int FULL_BRIGHT = 0xF000F0;

    private final BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

    /**
     * A droplet.
     *
     * @param xd how fast it is thrown, in blocks a tick
     */
    public static Particle create(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        return new LavaDroplet(level, x, y, z, xd, yd, zd);
    }

    protected LavaDroplet(ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
        // The constructor without velocity: the one with it adds a random velocity of its own
        super(level, x, y, z, DSurroundParticleSprites.WATERFALL_MIST.get(level.getRandom()));
        var random = level.getRandom();
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        this.lifetime = 30 + random.nextInt(15);
        this.friction = DRAG;
        this.gravity = WEIGHT;
        // Stops on what it lands on
        this.hasPhysics = true;
        this.setSize(0.1F, 0.1F);
        this.quadSize = HALF_WIDTH * 1.5F;
    }

    @Override
    public @NotNull SingleQuadParticle.Layer getLayer() {
        return SoftParticles.flameLayer();
    }

    @Override
    protected int getLightColor(float partialTick) {
        return FULL_BRIGHT;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.removed)
            return;

        if (this.onGround) {
            // Landed on something solid: a wisp of smoke as it goes out
            this.level.addParticle(ParticleTypes.SMOKE, this.x, this.y + 0.05D, this.z, 0D, 0.02D, 0D);
            this.remove();
            return;
        }
        // Back into the lava
        if (this.age > CLEAR_TICKS && this.yd < 0D) {
            this.pos.set(this.x, this.y, this.z);
            var fluid = this.level.getFluidState(this.pos);
            if (fluid.is(FluidTags.LAVA) && this.y < this.pos.getY() + fluid.getHeight(this.level, this.pos))
                this.remove();
        }
    }

    @Override
    public void extract(@NotNull QuadParticleRenderState state, @NotNull Camera camera, float partialTick) {
        float life = Mth.clamp((this.age + partialTick) / this.lifetime, 0F, 1F);
        float alpha = brightness(life);
        if (alpha <= 0F)
            return;
        Streaks.add(camera, partialTick, this.xo, this.yo, this.zo, this.x, this.y, this.z, STREAK, HALF_WIDTH,
                colorAt(life), alpha, this.getLightColor(partialTick), this.getU0(), this.getU1(), this.getV0(), this.getV1());
        // Its own quad as well, clear: the particle engine draws the flames' group only if something in it has a quad,
        // and the streaks are drawn as that group is (see Streaks)
        this.setAlpha(0F);
        super.extract(state, camera, partialTick);
    }

    /**
     * How bright it is at a point in its life, 0 to 1: bright until the last {@link #FADE_OUT} of it, then going out
     */
    static float brightness(float life) {
        return Mth.clamp((1F - life) / FADE_OUT, 0F, 1F);
    }

    // Reused for the colour as it is drawn: particles are drawn on the render thread only
    private static final float[] COLOR = new float[3];

    /**
     * Its colour at a point in its life, 0 to 1: from yellow-orange through orange to a dull red. The array is reused.
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
