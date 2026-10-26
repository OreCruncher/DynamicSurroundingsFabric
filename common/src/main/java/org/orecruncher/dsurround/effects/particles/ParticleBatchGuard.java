package org.orecruncher.dsurround.effects.particles;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.function.Consumer;

/**
 * Keeps a batch of particles drawing with the state its render type set up. Particles of one render type are added to
 * one batch, each in its own {@code render()}, and drawn together at the end with whatever state is current then. A
 * particle that changes the shader, the texture or the blend in its {@code render()} (some mods' do) gets every
 * particle in the batch drawn that way: Minecraft's campfire smoke and the mod's foam come out as white squares. One
 * that binds another frame buffer gets them drawn there, with Fabulous graphics outside the particles' target, where
 * they hide the water behind them.
 * <p>
 * So the state is noted once the batch has begun, and checked before it is drawn: if it changed, it is put back, and
 * the log says so once. From then on, for a while, it is checked after each particle too, to name the particle (and so
 * the mod) responsible. A particle named is drawn on its own after that, with the state it sets, so it looks right
 * too. Free of the game, so it can be tested; {@link ParticleBatches} ties it to the real state.
 */
public final class ParticleBatchGuard {

    static final int SHADER = 1;
    static final int TEXTURE = 2;
    static final int BLEND = 4;
    static final int FRAMEBUFFER = 8;

    /**
     * How many batches, after the first that was found changed, each particle is checked for: a few seconds' worth.
     * Then only the batches are checked, so a mod that keeps changing the state costs a check a batch, not a check a
     * particle.
     */
    static final int DIAGNOSIS_BATCHES = 600;

    /**
     * The state a batch draws with that matters here
     *
     * @param shader  the shader, compared by identity
     * @param texture the texture the shader samples
     * @param blend       whether blending is on, and its factors
     * @param framebuffer the frame buffer bound for drawing: the particles' target with Fabulous graphics, else the
     *                    main target's
     */
    record State(Object shader, int texture, boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha,
                 int framebuffer) {

        /**
         * A state with no frame buffer read, as when only the shader and texture are
         */
        State(Object shader, int texture, boolean blend, int srcRgb, int dstRgb, int srcAlpha, int dstAlpha) {
            this(shader, texture, blend, srcRgb, dstRgb, srcAlpha, dstAlpha, 0);
        }

        /**
         * What differs from {@code other}, as {@link #SHADER}, {@link #TEXTURE}, {@link #BLEND} and
         * {@link #FRAMEBUFFER} bits
         */
        int differences(State other) {
            int changed = 0;
            if (this.shader != other.shader)
                changed |= SHADER;
            if (this.texture != other.texture)
                changed |= TEXTURE;
            if (this.blend != other.blend || this.srcRgb != other.srcRgb || this.dstRgb != other.dstRgb
                    || this.srcAlpha != other.srcAlpha || this.dstAlpha != other.dstAlpha)
                changed |= BLEND;
            if (this.framebuffer != other.framebuffer)
                changed |= FRAMEBUFFER;
            return changed;
        }
    }

    /**
     * Reading and setting the state
     */
    interface Gl {
        /**
         * The current state
         *
         * @param full whether to read the blend and the frame buffer, which cost more than the rest; if not, they read
         *             as off and 0
         */
        State read(boolean full);

        void restore(State state);
    }

    private final Gl gl;
    private final Consumer<String> warn;

    // The batch being added to, if it is guarded, and the state it began with
    private boolean guarding;
    private String batch = "";
    private State expected;

    // What to check each particle for while looking for the one responsible, and for how many more batches
    private int lookFor;
    private int diagnosisLeft;
    private boolean diagnosed;
    // The particles found changing the state: drawn on their own from then on (see isolates)
    private final Set<Class<?>> culprits = new HashSet<>();

    private boolean warned;
    private int repairs;

    ParticleBatchGuard(Gl gl, Consumer<String> warn) {
        this.gl = gl;
        this.warn = warn;
    }

    /**
     * A batch has begun: its render type has set up the state
     *
     * @param guarded whether to guard it: Minecraft's own particle sheets, whose state is known
     */
    void begun(String batch, boolean guarded) {
        this.guarding = guarded;
        if (guarded) {
            this.batch = batch;
            this.expected = this.gl.read(true);
        }
    }

    /**
     * A particle has added itself to the batch
     */
    void rendered(Object particle) {
        if (!this.guarding || this.lookFor == 0)
            return;
        int changed = this.expected.differences(this.gl.read((this.lookFor & (BLEND | FRAMEBUFFER)) != 0)) & this.lookFor;
        if (changed == 0)
            return;
        if (this.culprits.add(particle.getClass()))
            this.warn.accept("Particle %s changes the %s while Minecraft's %s particles are drawn, which draws them all wrong; Dynamic Surroundings draws that particle on its own from now on. The fix belongs in the mod that particle is from."
                    .formatted(particle.getClass().getName(), describe(changed), this.batch));
        // Put back now, so the rest of the batch is drawn right and the next particle to change it is caught too
        this.gl.restore(this.expected);
    }

    /**
     * Whether a particle is one found changing the state, to be drawn on its own rather than in the batch: it set the
     * state for its own quads (a texture of its own, say), so drawn with the batch's state it would come out wrong
     * itself, and left in the batch it would draw the rest wrong
     */
    boolean isolates(Object particle) {
        return this.guarding && !this.culprits.isEmpty() && this.culprits.contains(particle.getClass());
    }

    /**
     * Puts the batch's state back, after a particle was drawn on its own
     */
    void restore() {
        if (this.guarding)
            this.gl.restore(this.expected);
    }

    /**
     * The batch is about to be drawn
     */
    void beforeDraw() {
        if (!this.guarding)
            return;
        this.guarding = false;
        if (this.diagnosisLeft > 0 && --this.diagnosisLeft == 0)
            this.lookFor = 0;

        int changed = this.expected.differences(this.gl.read(true));
        if (changed == 0)
            return;
        this.repairs++;
        this.gl.restore(this.expected);
        if (!this.warned) {
            this.warned = true;
            this.warn.accept("Minecraft's %s particles were about to be drawn with the %s another mod's particle changed, which draws them wrong (as squares, or to the wrong target); Dynamic Surroundings put it back, and is looking for the particle responsible"
                    .formatted(this.batch, describe(changed)));
        }
        // Look for whoever is responsible, once, for a while
        if (!this.diagnosed) {
            this.diagnosed = true;
            this.diagnosisLeft = DIAGNOSIS_BATCHES;
        }
        if (this.diagnosisLeft > 0)
            this.lookFor |= changed;
    }

    /**
     * How many batches have had their state put back
     */
    int repairs() {
        return this.repairs;
    }

    /**
     * Whether each particle is being checked
     */
    boolean diagnosing() {
        return this.lookFor != 0;
    }

    static String describe(int changed) {
        var parts = new ArrayList<String>(4);
        if ((changed & SHADER) != 0)
            parts.add("shader");
        if ((changed & TEXTURE) != 0)
            parts.add("texture");
        if ((changed & BLEND) != 0)
            parts.add("blend");
        if ((changed & FRAMEBUFFER) != 0)
            parts.add("frame buffer");
        return String.join(" and ", parts);
    }
}
