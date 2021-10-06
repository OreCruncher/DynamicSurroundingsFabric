package org.orecruncher.dsurround.config;

import com.google.gson.annotations.SerializedName;

/**
 * Types of SoundEvent acoustics that can be defined for biome.  The play of these sounds is in
 * addition to what Minecraft may decided to do.  They do not replace (yet?)
 */
public enum SoundEventType {
    /**
     * SoundEvent plays without attenuation and loops continually while conditions persist.
     */
    @SerializedName(value = "loop", alternate = {"LOOP"})
    LOOP,
    /**
     * SoundEvent that will randomly play around the player based on conditions within the game
     * (Older SPOT sound, but similar to the new Minecraft MOOD sound).
     */
    @SerializedName(value = "mood", alternate = {"MOOD"})
    MOOD,
    /**
     * SoundEvent that can randomly play based on conditions within the game.  It will play
     * without attenuation and will not loop.
     */
    @SerializedName(value = "addition", alternate = {"ADDITION"})
    ADDITION,
    /**
     * Music that will play when Minecraft's music picker selects a biome sound for play.
     * Not currently used.
     */
    @SerializedName(value = "music", alternate = {"MUSIC"})
    MUSIC
}
