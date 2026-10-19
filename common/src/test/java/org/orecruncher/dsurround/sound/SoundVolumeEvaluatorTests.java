package org.orecruncher.dsurround.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the gain a sound plays at: the game's volume and slider, then the configured scale
 */
public class SoundVolumeEvaluatorTests {

    @Test
    void noScaleIsTheGamesOwnGain() {
        assertEquals(0.35F, SoundVolumeEvaluator.gain(0.7F, 0.5F, 1F), 1e-6F);
        assertEquals(1F, SoundVolumeEvaluator.gain(4F, 0.5F, 1F), "a loud sound past its slider, as the game has it");
    }

    @Test
    void turningALoudSoundDownQuietensIt() {
        // Regression: an explosion's volume of 4 was scaled before the gain was capped at 1, so 50% still played at
        // full gain
        assertEquals(0.5F, SoundVolumeEvaluator.gain(4F, 1F, 0.5F), 1e-6F);
    }

    @Test
    void raisingASoundStopsAtFullGain() {
        assertEquals(0.8F, SoundVolumeEvaluator.gain(0.2F, 1F, 4F), 1e-6F);
        assertEquals(1F, SoundVolumeEvaluator.gain(0.5F, 1F, 4F));
    }

    @Test
    void aScaleOfNothingSilences() {
        assertEquals(0F, SoundVolumeEvaluator.gain(1F, 1F, 0F));
    }
}
