package org.orecruncher.dsurround.config;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * Types of SoundEvent acoustics that can be defined for biome.  The play of these sounds is in
 * addition to what Minecraft may decided to do.  They do not replace (yet?)
 */
public enum SoundEventType {
    /**
     * SoundEvent plays without attenuation and loops continually while conditions persist.
     */
    LOOP("loop"),
    /**
     * SoundEvent that will randomly play around the player based on conditions within the game
     * (Older SPOT sound, but similar to the new Minecraft MOOD sound).
     */
    MOOD("mood"),
    /**
     * SoundEvent that can randomly play based on conditions within the game.  It will play
     * without attenuation and will not loop.
     */
    ADDITION("addition"),
    /**
     * Music that will play when Minecraft's music picker selects a biome sound for play.
     * Not currently used.
     */
    MUSIC("music");

    private static final Map<String, SoundEventType> BY_NAME = Arrays.stream(values()).collect(Collectors.toMap(SoundEventType::getName, (category) -> category));
    public static final Codec<SoundEventType> CODEC = Codec.STRING.comapFlatMap(DataResult.partialGet(BY_NAME::get, () -> "unknown sound event type"), d -> d.name);

    private final String name;

    SoundEventType(String name) {
        this.name = name;
    }

    public static SoundEventType byName(String name) {
        return BY_NAME.get(name);
    }

    public String getName() {
        return this.name;
    }
}
