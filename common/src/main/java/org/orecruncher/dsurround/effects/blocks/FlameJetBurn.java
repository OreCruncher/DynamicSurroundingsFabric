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

    // A lava jet throws droplets on this share of the times it emits, at full burn, and at most this many at once
    static final float DROPLET_CHANCE = 0.5F;
    static final int MAX_DROPLETS = 4;

    /**
     * How many droplets a lava jet throws up when it emits: none most times, more the deeper the lava under it and
     * the harder it is burning, and fewer with the Particles setting lowered
     *
     * @param strength the depth of the lava under it, in blocks
     * @param burn     how hard it is burning, from {@link #burn}
     * @param share    the share of particles the Particles setting allows
     * @param chance   a roll of 0 to 1 for whether it throws any this time
     * @param count    a roll of 0 to 1 for how many
     */
    static int droplets(int strength, float burn, float share, float chance, float count) {
        if (burn <= 0F || chance >= DROPLET_CHANCE * burn * share)
            return 0;
        int most = Math.min(MAX_DROPLETS, 1 + strength / 2);
        return 1 + Math.min(most - 1, (int) (count * most));
    }

    /**
     * How fast a lava jet throws a droplet up, in blocks a tick: faster from deeper lava, and from a jet burning
     * hard
     *
     * @param roll a roll of 0 to 1, so they don't all reach the same height
     */
    static double dropletSpeed(int strength, float burn, float roll) {
        return (0.22D + 0.025D * Math.min(strength, 8) + 0.12D * roll) * (0.6D + 0.4D * burn);
    }
}
