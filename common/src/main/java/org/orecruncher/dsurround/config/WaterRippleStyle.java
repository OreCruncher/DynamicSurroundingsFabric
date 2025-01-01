package org.orecruncher.dsurround.config;

import dev.architectury.registry.registries.RegistrySupplier;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import org.orecruncher.dsurround.effects.particles.Particles;
import org.orecruncher.dsurround.lib.random.Randomizer;

public enum WaterRippleStyle {

    NONE(null),
    PIXELATED_CIRCLE(Particles.WATER_RIPPLE_PIXELATED) {
        private final int FRAMES = 7;
        private final float DELTA = 1F / this.FRAMES;
        private final int MAX_AGE = this.FRAMES * 2;

        @Override
        public float getU1(final int age) {
            return (int) (age / 2F) * this.DELTA;
        }

        @Override
        public float getU2(final int age) {
            return getU1(age) + this.DELTA;
        }

        @Override
        public boolean doScaling() {
            return false;
        }

        @Override
        public int getMaxAge() {
            return this.MAX_AGE;
        }
    };

    private final RegistrySupplier<SimpleParticleType> particleTypeRegistrySupplier;

    WaterRippleStyle(RegistrySupplier<SimpleParticleType> particleTypeSupplier) {
        this.particleTypeRegistrySupplier = particleTypeSupplier;
    }

    public ParticleType<SimpleParticleType> getParticleType() {
        return this.particleTypeRegistrySupplier.get();
    }

    public float getU1(final int age) {
        return 0F;
    }

    public float getU2(final int age) {
        return 1F;
    }

    public float getV1(final int age) {
        return 0F;
    }

    public float getV2(final int age) {
        return 1F;
    }

    public boolean doScaling() {
        return true;
    }

    public boolean doAlpha() {
        return true;
    }

    public int getMaxAge() {
        return 12 + Randomizer.current().nextInt(8);
    }
}
