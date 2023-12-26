package org.orecruncher.dsurround.config;

import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.effects.particles.ParticleSheets;
import org.orecruncher.dsurround.lib.random.XorShiftRandom;

public enum WaterRippleStyle {

    NONE (new Identifier(Constants.MOD_ID, "none")),
    PIXELATED_CIRCLE(ParticleSheets.TEXTURE_WATER_RIPPLE_PIXELATED_CIRCLE) {
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

    private final Identifier resource;

    WaterRippleStyle(final Identifier texture) {
        this.resource = texture;
    }

    public Identifier getTexture() {
        return this.resource;
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
        return 12 + XorShiftRandom.current().nextInt(8);
    }
}
