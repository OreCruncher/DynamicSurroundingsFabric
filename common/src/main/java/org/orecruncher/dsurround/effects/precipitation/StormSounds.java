package org.orecruncher.dsurround.effects.precipitation;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.orecruncher.dsurround.Constants;

/**
 * How storms sound, as 1.12.2 had them: rain is the mod's calm rain, louder as the storm strengthens, with vanilla's
 * heavier rain blended in from a moderate storm up, so a drizzle patters and a downpour roars. Dust storms blow a dust
 * sound where the dust is.
 * <p>
 * Volumes are shares of the volume vanilla gives its rain sound where it is played (louder in the open than on a roof
 * above), so the two stay in step with it.
 */
public final class StormSounds {

    public static final ResourceLocation RAIN = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "weather.rain");
    public static final ResourceLocation DUST = ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "weather.dust");

    /**
     * The calm rain's volume in the lightest drizzle, as a share of vanilla's; it rises to all of it at full strength
     */
    static final float QUIETEST = 0.5F;
    /**
     * Vanilla's heavier rain comes in from this strength, and is at its own volume from {@link #HEAVY_FULL}
     */
    static final float HEAVY_FROM = 0.5F;
    static final float HEAVY_FULL = 0.9F;
    /**
     * The dust sound's volume, as vanilla's rain is in the open, and how far its pitch varies either way, as it is a
     * single sound
     */
    static final float DUST_VOLUME = 0.2F;
    static final float DUST_PITCH_RANGE = 0.1F;
    /**
     * As vanilla times its rain sound: a sound on a tick when a roll of 0 to 2 is less than the ticks since the last
     */
    public static final int SOUND_CHANCE = 3;

    private StormSounds() {
    }

    /**
     * The calm rain's volume at a storm's strength, as a share of vanilla's
     */
    public static float calmShare(float rainLevel) {
        return Mth.lerp(Mth.clamp(rainLevel, 0F, 1F), QUIETEST, 1F);
    }

    /**
     * Vanilla's heavier rain at a storm's strength, as a share of its own volume: none in light rain, rising smoothly
     */
    public static float heavyShare(float rainLevel) {
        var t = Mth.clamp((rainLevel - HEAVY_FROM) / (HEAVY_FULL - HEAVY_FROM), 0F, 1F);
        return t * t * (3F - 2F * t);
    }

    /**
     * The dust sound's volume at a storm's strength
     */
    public static float dustVolume(float rainLevel) {
        return DUST_VOLUME * calmShare(rainLevel);
    }

    /**
     * A pitch for the dust sound, from a roll of 0 to 1
     */
    public static float dustPitch(float roll) {
        return 1F + (roll * 2F - 1F) * DUST_PITCH_RANGE;
    }

    /**
     * Whether a sound plays this tick, as vanilla times its rain: given a roll of 0 to {@link #SOUND_CHANCE} - 1 and
     * the ticks since the last sound
     */
    public static boolean isTimeForSound(int roll, int ticksSinceLast) {
        return roll < ticksSinceLast;
    }
}
