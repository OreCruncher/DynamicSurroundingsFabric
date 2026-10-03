package org.orecruncher.dsurround.runtime.audio;

import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.player.Player;

/**
 * What the player's head is in, and how it changes what they hear. Sounds are muffled (less high frequency), quieter,
 * and have less reverb than in air. These are tuning values: adjust them by ear.
 */
public enum Submersion {

    NONE(0F, 1F, 1F, 1F),
    WATER(0.85F, 0.6F, 0.5F, 0.25F),
    LAVA(0.95F, 0.4F, 0.3F, 0.15F);

    /**
     * How much of the direct sound's high frequency is lost: its cutoff is scaled by (1 - this). 0.85 leaves 0.15,
     * about -16 dB at 5 kHz, before any occlusion.
     */
    final float directDampening;

    /**
     * Scale for the direct sound's volume.
     */
    final float directGainScale;

    /**
     * Scale for the volume of each reverb send.
     */
    final float sendGainScale;

    /**
     * Scale for the high frequency of each reverb send.
     */
    final float sendCutoffScale;

    Submersion(float directDampening, float directGainScale, float sendGainScale, float sendCutoffScale) {
        this.directDampening = directDampening;
        this.directGainScale = directGainScale;
        this.sendGainScale = sendGainScale;
        this.sendCutoffScale = sendCutoffScale;
    }

    /**
     * What the player's eyes are in.
     */
    public static Submersion of(Player player) {
        if (player.isEyeInFluid(FluidTags.LAVA))
            return LAVA;
        if (player.isUnderWater())
            return WATER;
        return NONE;
    }
}
