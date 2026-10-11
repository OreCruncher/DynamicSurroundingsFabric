package org.orecruncher.dsurround.effects.blocks;

import net.minecraft.util.Mth;

/**
 * How hard a flame jet burns over its life: it flares up, burns steadily, and dies down. Apart from
 * {@link FlameJetEffect}, which needs the game's sound library to load, so it can be tested.
 */
final class FlameJetBurn {

    // The jet flares over the first share of its life, and dies down over the last
    static final float FLARE = 0.15F;
    static final float DIE_DOWN = 0.3F;

    private FlameJetBurn() {
    }

    /**
     * How hard the jet burns at a point in its life, 0 to 1: flaring up over the first {@link #FLARE} of it, burning
     * steadily, and dying down over the last {@link #DIE_DOWN}
     */
    static float burn(int age, int ageLimit) {
        if (ageLimit <= 0)
            return 0F;
        float life = age / (float) ageLimit;
        float rising = life / FLARE;
        float falling = (1F - life) / DIE_DOWN;
        return Mth.clamp(Math.min(rising, falling), 0F, 1F);
    }
}
