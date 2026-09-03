package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.Particle;
import org.jetbrains.annotations.NotNull;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.GameUtils;

/**
 * Special particle that proxies a collection in the particle engine. The commonality
 * of the collection is rendering setup. This collection is centered on the player
 * to prevent it from going out of scope. It is modeled on the NoRenderParticle in
 * Minecraft.
 */
public final class ParticleRenderCollection {

    /**
     * Initializes a helper instance used to manage the state of the main particle within the ParticleEngine.
     * Particle rendering will use the default setup.
     *
     * @param name The name of the helper; used in diagnostics
     */
    public static Helper createHelper(String name) {
        return new Helper(name);
    }

    /**
     * Helper that manages related particles in Minecraft's ParticleEngine. The helper will register with events, so
     * instances of this class need to be maintained as singletons throughout the lifetime of the client.
     */
    public static final class Helper {

        private final String name;
        private int submitted;

        Helper(@NotNull String name) {
            this.name = name;
            ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::collectDiagnostics);
        }

        /**
         * Adds a particle to the helper.
         *
         * @param particle The particle to add
         */
        public void add(Particle particle) {
            if (particle != null) {
                GameUtils.getParticleManager().add(particle);
                this.submitted++;
            }
        }

        private void collectDiagnostics(@NotNull CollectDiagnosticsEvent event) {
            event.add(CollectDiagnosticsEvent.Section.Particles, this.name + ": submitted " + this.submitted);
            this.submitted = 0;
        }
    }
}
