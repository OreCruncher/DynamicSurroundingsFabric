package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.Camera;
import net.minecraft.client.particle.Particle;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * Minecraft 26.x renders particles through extracted render states. The old custom proxy particle is
 * replaced with a helper that directly submits particles to the vanilla particle engine.
 */
public final class ParticleRenderCollection {

    private ParticleRenderCollection() {
    }

    public static final class Helper<TParticle extends Particle> {

        private final String name;
        private int submitted;

        public Helper(@NotNull String name, @NotNull Supplier<Identifier> textureSupplier) {
            this(name, textureSupplier, null);
        }

        public Helper(@NotNull String name, @NotNull Supplier<Identifier> textureSupplier, @Nullable Consumer<Camera> setup) {
            this.name = name;
            ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::collectDiagnostics);
        }

        public void add(TParticle particle) {
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
