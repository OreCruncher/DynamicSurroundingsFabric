package org.orecruncher.dsurround.sound;

import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class SoundCullTrackerTests {

    private static final Identifier STEP = Identifier.parse("minecraft:block.stone.step");
    private static final Identifier SPLASH = Identifier.parse("minecraft:entity.generic.splash");
    private static final int INTERVAL = 20;

    @Test
    void firstPlayIsAllowedEvenAtStartup() {
        // Regression: a sound never played counted as played at tick 0, so in the first ticks after the client
        // started every culled sound was dropped (and not recorded, so dropped again)
        var tracker = new SoundCullTracker();

        assertFalse(tracker.shouldCull(STEP, 0, INTERVAL));
        assertFalse(tracker.shouldCull(SPLASH, 5, INTERVAL));
    }

    @Test
    void playsWithinTheIntervalAreDropped() {
        var tracker = new SoundCullTracker();
        tracker.shouldCull(STEP, 100, INTERVAL);

        assertTrue(tracker.shouldCull(STEP, 101, INTERVAL));
        assertTrue(tracker.shouldCull(STEP, 119, INTERVAL));
        assertFalse(tracker.shouldCull(STEP, 120, INTERVAL), "a full interval later");
    }

    @Test
    void droppedPlaysDoNotRestartTheInterval() {
        var tracker = new SoundCullTracker();
        tracker.shouldCull(STEP, 100, INTERVAL);
        tracker.shouldCull(STEP, 110, INTERVAL);   // dropped

        assertFalse(tracker.shouldCull(STEP, 120, INTERVAL), "measured from the last play that was allowed");
    }

    @Test
    void eachSoundHasItsOwnInterval() {
        var tracker = new SoundCullTracker();
        tracker.shouldCull(STEP, 100, INTERVAL);

        assertFalse(tracker.shouldCull(SPLASH, 101, INTERVAL));
        assertTrue(tracker.shouldCull(STEP, 101, INTERVAL));
    }
}
