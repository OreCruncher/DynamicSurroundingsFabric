package org.orecruncher.dsurround.sound;

import net.minecraft.SharedConstants;
import net.minecraft.client.resources.sounds.Sound;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.resources.sounds.TickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.util.valueproviders.ConstantFloat;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for how far a sound carries, and which sounds may be dropped for starting out of range
 */
public class SoundRangeTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    @Test
    void theRangeIsTheAttenuationDistance() {
        assertEquals(16F, SoundRange.audibleRange(1F, 16));
        assertEquals(16F, SoundRange.audibleRange(0.3F, 16), "quieter sounds fade out at the same distance");
    }

    @Test
    void aLoudSoundCarriesFurther() {
        // Regression: a volume above 1 was let through rather than given the game's range
        assertEquals(64F, SoundRange.audibleRange(4F, 16));
    }

    @Test
    void aOneShotSoundDoesntLast() {
        assertFalse(SoundRange.lasts(sound(false, false, SoundInstance.class)));
    }

    @Test
    void loopingMovingAndStreamedSoundsLast() {
        // Regression: a music disc started out of range, or a machine's hum, was dropped and never heard on coming
        // closer
        assertTrue(SoundRange.lasts(sound(true, false, SoundInstance.class)), "looping");
        assertTrue(SoundRange.lasts(sound(false, false, TickableSoundInstance.class)), "ticking, may move closer");
        assertTrue(SoundRange.lasts(sound(false, true, SoundInstance.class)), "streamed");
    }

    @Test
    void anUnresolvedSoundLastsOnlyIfItLoopsOrTicks() {
        assertFalse(SoundRange.lasts(Fakes.of(SoundInstance.class, Map.of("isLooping", args -> false, "getSound", args -> null))));
    }

    private static <T extends SoundInstance> T sound(boolean looping, boolean streamed, Class<T> type) {
        var sound = new Sound(Identifier.withDefaultNamespace("test"), ConstantFloat.of(1F), ConstantFloat.of(1F), 1, Sound.Type.FILE, streamed, false, 16);
        Map<String, Function<Object[], Object>> answers = new HashMap<>();
        answers.put("isLooping", args -> looping);
        answers.put("getSound", args -> sound);
        return Fakes.of(type, answers);
    }
}
