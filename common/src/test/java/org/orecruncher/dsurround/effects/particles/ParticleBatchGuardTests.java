package org.orecruncher.dsurround.effects.particles;

import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.effects.particles.ParticleBatchGuard.State;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for keeping a particle batch drawing with its render type's state: noticing a change, putting it back, and
 * naming the particle responsible.
 */
public class ParticleBatchGuardTests {

    private static final Object PARTICLE_SHADER = new Object();
    private static final Object OTHER_SHADER = new Object();
    // GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ONE
    private static final int SRC_ALPHA = 0x0302, ONE_MINUS_SRC_ALPHA = 0x0303, ONE = 1;
    private static final State SHEET = new State(PARTICLE_SHADER, 7, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);

    /**
     * The state as a fake GL holds it
     */
    private static final class FakeGl implements ParticleBatchGuard.Gl {
        State state = SHEET;
        int reads;
        int blendReads;
        int restores;

        @Override
        public State read(boolean full) {
            this.reads++;
            if (full) {
                this.blendReads++;
                return this.state;
            }
            return new State(this.state.shader(), this.state.texture(), false, 0, 0, 0, 0);
        }

        @Override
        public void restore(State state) {
            this.restores++;
            this.state = state;
        }
    }

    private static final class Smoke {
    }

    private static final class Rogue {
    }

    private final FakeGl gl = new FakeGl();
    private final List<String> warnings = new ArrayList<>();
    private final ParticleBatchGuard guard = new ParticleBatchGuard(this.gl, this.warnings::add);

    /**
     * One batch: begun, the particles added (a rogue one changing the state to {@code rogue}), drawn
     */
    private State batch(boolean guarded, Object[] particles, State rogue) {
        this.guard.begun("PARTICLE_SHEET_TRANSLUCENT", guarded);
        for (var particle : particles) {
            if (particle instanceof Rogue)
                this.gl.state = rogue;
            this.guard.rendered(particle);
        }
        this.guard.beforeDraw();
        return this.gl.state;
    }

    @Test
    void aBatchNobodyChangesIsLeftAlone() {
        var drawnWith = this.batch(true, new Object[]{new Smoke(), new Smoke()}, SHEET);
        assertEquals(SHEET, drawnWith);
        assertEquals(0, this.gl.restores);
        assertTrue(this.warnings.isEmpty());
        // Read once when begun and once before drawing, not for each particle
        assertEquals(2, this.gl.reads);
    }

    @Test
    void aChangedTextureIsPutBackBeforeDrawing() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        var drawnWith = this.batch(true, new Object[]{new Smoke(), new Rogue(), new Smoke()}, wrongTexture);
        assertEquals(SHEET, drawnWith);
        assertEquals(1, this.guard.repairs());
        assertEquals(1, this.warnings.size());
        assertTrue(this.warnings.getFirst().contains("texture"), this.warnings.getFirst());
    }

    @Test
    void aChangedShaderOrBlendIsCaughtToo() {
        var premultiplied = new State(PARTICLE_SHADER, 7, true, ONE, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        assertEquals(SHEET, this.batch(true, new Object[]{new Rogue()}, premultiplied));
        var otherShader = new State(OTHER_SHADER, 7, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        assertEquals(SHEET, this.batch(true, new Object[]{new Rogue()}, otherShader));
        assertEquals(2, this.guard.repairs());
    }

    @Test
    void anotherFrameBufferIsPutBackAndItsParticleNamed() {
        // A particle that binds a frame buffer of its own: the rest of the batch would be drawn there
        var otherTarget = new State(PARTICLE_SHADER, 7, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA, 5);
        assertEquals(SHEET, this.batch(true, new Object[]{new Smoke(), new Rogue()}, otherTarget));
        assertEquals(1, this.guard.repairs());
        assertTrue(this.warnings.getFirst().contains("frame buffer"), this.warnings.getFirst());
        // Then caught as it binds it, and named
        assertEquals(SHEET, this.batch(true, new Object[]{new Rogue(), new Smoke()}, otherTarget));
        assertTrue(this.warnings.stream().anyMatch(w -> w.contains(Rogue.class.getName()) && w.contains("frame buffer")),
                String.join("\n", this.warnings));
        // From then on drawn on its own
        this.guard.begun("PARTICLE_SHEET_TRANSLUCENT", true);
        assertTrue(this.guard.isolates(new Rogue()));
        assertFalse(this.guard.isolates(new Smoke()));
    }

    @Test
    void theParticleResponsibleIsNamedOnce() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        // First batch: only found before drawing, which starts checking each particle
        this.batch(true, new Object[]{new Smoke(), new Rogue()}, wrongTexture);
        assertTrue(this.guard.diagnosing());
        // Next batches: the rogue is caught as it changes the state, and put back at once
        for (int i = 0; i < 5; i++)
            assertEquals(SHEET, this.batch(true, new Object[]{new Smoke(), new Rogue(), new Smoke()}, wrongTexture));
        var named = this.warnings.stream().filter(w -> w.contains(Rogue.class.getName())).toList();
        assertEquals(1, named.size(), "named once, however often");
        assertTrue(named.getFirst().contains("texture"));
        assertTrue(this.warnings.stream().noneMatch(w -> w.contains(Smoke.class.getName())), "a particle that changes nothing isn't blamed");
    }

    @Test
    void particlesAreOnlyCheckedForAWhile() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        this.batch(true, new Object[]{new Rogue()}, wrongTexture);
        for (int i = 0; i < ParticleBatchGuard.DIAGNOSIS_BATCHES; i++)
            this.batch(true, new Object[]{new Smoke()}, SHEET);
        assertFalse(this.guard.diagnosing());
        // And not again, however often it is found changed: then only each batch is checked
        this.batch(true, new Object[]{new Rogue()}, wrongTexture);
        assertFalse(this.guard.diagnosing());
        int before = this.gl.reads;
        this.batch(true, new Object[]{new Smoke(), new Smoke(), new Smoke()}, SHEET);
        assertEquals(2, this.gl.reads - before);
    }

    @Test
    void onlyTheBlendCostsAFullReadWhileLookingForIt() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        this.batch(true, new Object[]{new Rogue()}, wrongTexture);
        int blendReads = this.gl.blendReads;
        this.batch(true, new Object[]{new Smoke(), new Smoke(), new Smoke()}, SHEET);
        // A texture change is looked for without reading the blend for each particle: just begun and before drawing
        assertEquals(2, this.gl.blendReads - blendReads);
    }

    @Test
    void unguardedBatchesAreLeftAlone() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        assertEquals(wrongTexture, this.batch(false, new Object[]{new Rogue()}, wrongTexture));
        assertEquals(0, this.gl.reads);
        assertTrue(this.warnings.isEmpty());
    }

    @Test
    void theParticleResponsibleIsDrawnOnItsOwnOnceNamed() {
        var wrongTexture = new State(PARTICLE_SHADER, 99, true, SRC_ALPHA, ONE_MINUS_SRC_ALPHA, ONE, ONE_MINUS_SRC_ALPHA);
        this.batch(true, new Object[]{new Rogue()}, wrongTexture);
        this.guard.begun("PARTICLE_SHEET_TRANSLUCENT", true);
        assertFalse(this.guard.isolates(new Rogue()), "not before it is named");
        this.gl.state = wrongTexture;
        this.guard.rendered(new Rogue());
        assertTrue(this.guard.isolates(new Rogue()));
        assertFalse(this.guard.isolates(new Smoke()), "only the one named");

        // Drawn on its own with its texture, then the batch's state comes back
        this.gl.state = wrongTexture;
        this.guard.restore();
        assertEquals(SHEET, this.gl.state);
        this.guard.beforeDraw();
        // Not outside a guarded batch
        assertFalse(this.guard.isolates(new Rogue()));
        this.guard.begun("PARTICLE_SHEET_OPAQUE", false);
        assertFalse(this.guard.isolates(new Rogue()));
    }

    @Test
    void whatChangedIsSaidInWords() {
        assertEquals("texture", ParticleBatchGuard.describe(ParticleBatchGuard.TEXTURE));
        assertEquals("shader and texture and blend", ParticleBatchGuard.describe(
                ParticleBatchGuard.SHADER | ParticleBatchGuard.TEXTURE | ParticleBatchGuard.BLEND));
        assertEquals("blend and frame buffer", ParticleBatchGuard.describe(
                ParticleBatchGuard.BLEND | ParticleBatchGuard.FRAMEBUFFER));
    }
}
