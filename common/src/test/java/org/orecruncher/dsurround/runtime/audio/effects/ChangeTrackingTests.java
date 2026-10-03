package org.orecruncher.dsurround.runtime.audio.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests that filter settings and source properties are only uploaded when they change. The upload itself needs an
 * OpenAL device, so it isn't tested here.
 */
public class ChangeTrackingTests {

    // ---- LowPassData -----------------------------------------------------------------------------------------

    @Test
    void newFilterCountsAsChanged() {
        // So a new sound always gets its first upload (here, "no filter")
        var data = new LowPassData();

        assertTrue(data.takeChanged());
        assertFalse(data.isEnabled());
        assertFalse(data.takeChanged(), "taking it clears it");
    }

    @Test
    void settingTheSameValuesAgainIsNotAChange() {
        var data = new LowPassData();
        data.set(0.5F, 0.25F);
        assertTrue(data.takeChanged());

        data.set(0.5F, 0.25F);

        assertFalse(data.takeChanged());
    }

    @Test
    void newValuesAreAChange() {
        var data = new LowPassData();
        data.set(0.5F, 0.25F);
        data.takeChanged();

        data.set(0.5F, 0.3F);
        assertTrue(data.takeChanged(), "high frequency gain");
        data.set(0.6F, 0.3F);
        assertTrue(data.takeChanged(), "gain");
        assertEquals(0.6F, data.gain());
        assertEquals(0.3F, data.gainHF());
    }

    @Test
    void disablingAndEnablingAreChanges() {
        var data = new LowPassData();
        data.set(0.5F, 0.5F);
        data.takeChanged();

        data.disable();
        assertTrue(data.takeChanged());
        assertFalse(data.isEnabled());
        data.disable();
        assertFalse(data.takeChanged(), "already disabled");

        // Re-enabling with the same values still needs an upload
        data.set(0.5F, 0.5F);
        assertTrue(data.takeChanged());
        assertTrue(data.isEnabled());
    }

    @Test
    void valuesAreClampedToOpenAlsRange() {
        var data = new LowPassData();

        data.set(1.7F, -0.2F);

        assertEquals(1F, data.gain());
        assertEquals(0F, data.gainHF());
    }

    @Test
    void outOfRangeValuesThatClampTheSameAreNotAChange() {
        var data = new LowPassData();
        data.set(1F, 0F);
        data.takeChanged();

        data.set(5F, -5F);

        assertFalse(data.takeChanged());
    }

    // ---- SourcePropertyFloat ---------------------------------------------------------------------------------

    private static SourcePropertyFloat property() {
        return new SourcePropertyFloat(0, 1F, 0F, 10F);
    }

    @Test
    void newPropertyCountsAsChanged() {
        var prop = property();

        assertTrue(prop.takeChanged());
        assertFalse(prop.takeChanged());
    }

    @Test
    void propertyChangesOnlyWithNewValuesOrEnabling() {
        var prop = property();
        prop.takeChanged();

        prop.setValue(1F);
        assertFalse(prop.takeChanged(), "same value");

        prop.setValue(4F);
        assertTrue(prop.takeChanged(), "new value");

        prop.setProcess(true);
        assertTrue(prop.takeChanged(), "enabled");
        prop.setProcess(true);
        assertFalse(prop.takeChanged(), "already enabled");

        prop.setProcess(false);
        assertTrue(prop.takeChanged(), "disabled");
    }

    @Test
    void propertyValueIsClamped() {
        var prop = property();

        prop.setValue(50F);
        assertEquals(10F, prop.getValue());
        prop.setValue(-3F);
        assertEquals(0F, prop.getValue());
    }

    @Test
    void sendCountMatchesTheReverbChannels() {
        assertEquals(4, Efx.SENDS);
    }
}
