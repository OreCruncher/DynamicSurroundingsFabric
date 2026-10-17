package org.orecruncher.dsurround.effects.particles;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * A soft puff of mist or steam: drawn soft (see {@link SoftParticles}) so it fades into the blocks it meets rather
 * than cutting through them, it fades in quickly and out slowly, grows as it spreads, and turns as it goes. Many
 * overlapping make a cloud. How it moves is each kind's own: a subclass pushes it about, then lets this tick it.
 * <p>
 * A subclass sets {@link #startSize}, {@link #peakAlpha} and {@link #rollSpeed} as it is made.
 */
public abstract class SoftPuffParticle extends TextureSheetParticle {

    private final float fadeIn;
    private final float fadeOut;
    private final float growth;

    /**
     * Half its width when new, as quadSize is
     */
    protected float startSize;
    /**
     * Its alpha once faded in
     */
    protected float peakAlpha;
    /**
     * How far it turns each tick, in radians
     */
    protected float rollSpeed;

    /**
     * @param fadeIn  the fraction of its life spent fading in at the start
     * @param fadeOut the fraction of its life spent fading out at the end
     * @param growth  how much it grows over its life: 1.5 makes it 2.5 times its starting size by the end
     */
    protected SoftPuffParticle(ClientLevel level, double x, double y, double z, float fadeIn, float fadeOut, float growth) {
        // The constructor without velocity: the one with it adds a random velocity of its own
        super(level, x, y, z);
        this.fadeIn = fadeIn;
        this.fadeOut = fadeOut;
        this.growth = growth;
        this.alpha = 0F;
        // Drifts through blocks rather than stopping at them: the soft shader fades it where it meets them
        this.hasPhysics = false;
    }

    @Override
    public @NotNull ParticleRenderType getRenderType() {
        return DSurroundParticleRenderType.PARTICLE_SHEET_MIST;
    }

    /**
     * Ages it (removing it at the end of its life), moves it with its drag and gravity, and turns it. A subclass
     * pushes it about first.
     */
    @Override
    public void tick() {
        super.tick();
        this.oRoll = this.roll;
        this.roll += this.rollSpeed;
    }

    /**
     * How far through its life it is, 0 to 1, between ticks.
     */
    protected float lifeFraction(float partialTick) {
        return Mth.clamp((this.age + partialTick) / this.lifetime, 0F, 1F);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return this.startSize * (1F + this.growth * this.lifeFraction(partialTick));
    }

    @Override
    public void render(@NotNull VertexConsumer buffer, @NotNull Camera camera, float partialTick) {
        // Set each frame, so the fades are smooth rather than stepping once a tick
        this.alpha = this.peakAlpha * fade(this.lifeFraction(partialTick), this.fadeIn, this.fadeOut);
        super.render(buffer, camera, partialTick);
    }

    /**
     * How faded in it is, 0 to 1, at a point in its life: rising over the first {@code fadeIn} of it, falling over the
     * last {@code fadeOut}, and whole in between
     *
     * @param life how far through its life it is, 0 to 1
     */
    static float fade(float life, float fadeIn, float fadeOut) {
        return Mth.clamp(Math.min(life / fadeIn, (1F - life) / fadeOut), 0F, 1F);
    }
}
