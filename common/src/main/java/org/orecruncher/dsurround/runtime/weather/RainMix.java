// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.runtime.weather;

import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.runtime.audio.FixedFilter;

/**
 * How one of vanilla's rain sounds is played instead, as 1.12.2 had it (see MixinClientLevel's rain sound): the mod's
 * calm rain, louder as the storm strengthens, a medium rain through middling storms and vanilla's heavier rain from a
 * moderate storm up, each at vanilla's volume and pitch for where it is, scaled; and with rain on surfaces, sounding like
 * the block it lands on (see {@link RainSurface}), through its filter where one can be set, and with the surface's own
 * rain in place of part of it. Worked out before anything plays, so a failure leaves vanilla's sound.
 *
 * @param calmVolume   the mod's calm rain
 * @param mediumVolume the medium rain, 0 for none
 * @param heavyVolume  vanilla's own sound, heavier rain, 0 for none
 * @param pitch        the three rains' pitch
 * @param filter       the filter the three play through, or null for none
 * @param layer        the surface's own rain, or null for none
 * @param layerVolume  its volume; it is played as recorded, neither filtered nor pitched
 */
public record RainMix(float calmVolume, float mediumVolume, float heavyVolume, float pitch, @Nullable FixedFilter filter,
                      @Nullable Identifier layer, float layerVolume) {

    /**
     * @param surface          what the rain lands on, or null with rain on surfaces off
     * @param volume           vanilla's volume for the sound
     * @param pitch            vanilla's pitch for it
     * @param rainLevel        the storm's strength, 0 to 1
     * @param filtersAvailable whether a filter can be set (enhanced sound processing is on)
     */
    public static RainMix of(@Nullable RainSurface surface, float volume, float pitch, float rainLevel, boolean filtersAvailable) {
        FixedFilter filter = null;
        Identifier layer = null;
        float layerVolume = 0F;
        if (surface != null) {
            if (surface.filter() != null && filtersAvailable)
                filter = surface.filter();
            volume *= surface.volume(filter != null);
            pitch *= surface.pitch(filter != null);
            // The surface's own rain takes part of the rain's place, at the surface's volume
            layer = surface.layer();
            if (layer != null) {
                layerVolume = volume * StormSounds.layerShare(rainLevel);
                volume *= StormSounds.keptShare();
            }
        }
        return new RainMix(volume * StormSounds.calmShare(rainLevel), volume * StormSounds.mediumShare(rainLevel),
                volume * StormSounds.heavyShare(rainLevel), pitch, filter, layer, layerVolume);
    }
}
