package org.orecruncher.dsurround.sound;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the gain a sound plays at: the game's gain (volume, sliders and fades), then the configured scale
 */
public class SoundVolumeEvaluatorTests {

    @Test
    void noScaleIsTheGamesOwnGain() {
        assertEquals(0.35F, SoundVolumeEvaluator.gain(0.35F, 1F), 1e-6F);
        assertEquals(1F, SoundVolumeEvaluator.gain(1F, 1F));
    }

    @Test
    void turningALoudSoundDownQuietensIt() {
        // Regression: an explosion's volume of 4 was scaled before the gain was capped at 1, so 50% still played at
        // full gain. The game's gain is capped already
        assertEquals(0.5F, SoundVolumeEvaluator.gain(1F, 0.5F), 1e-6F);
    }

    @Test
    void raisingASoundStopsAtFullGain() {
        assertEquals(0.8F, SoundVolumeEvaluator.gain(0.2F, 4F), 1e-6F);
        assertEquals(1F, SoundVolumeEvaluator.gain(0.5F, 4F));
    }

    @Test
    void aScaleOfNothingSilences() {
        assertEquals(0F, SoundVolumeEvaluator.gain(1F, 0F));
    }
}
