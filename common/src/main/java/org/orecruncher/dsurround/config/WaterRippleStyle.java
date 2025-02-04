package org.orecruncher.dsurround.config;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.random.Randomizer;

public enum WaterRippleStyle {

    NONE("none"),
    PIXELATED_CIRCLE("textures/particles/pixel_ripples.png") {
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

    private final ResourceLocation resource;

    WaterRippleStyle(final String texture) {
        this.resource = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, texture);
    }

    public ResourceLocation getTexture() {
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
        return 12 + Randomizer.current().nextInt(8);
    }
}
