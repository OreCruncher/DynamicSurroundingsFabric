package org.orecruncher.dsurround.lib.platform;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the features that stand aside for other mods. The loader can't be asked in tests, so it is stood in for by
 * a set of the mods installed.
 */
public class FeatureConflictTests {

    @Test
    void particleWeatherStandsAsideForEitherWeatherMod() {
        assertEquals(List.of(KnownMod.PARTICLE_RAIN, KnownMod.SIMPLE_WEATHER), FeatureConflict.PARTICLE_WEATHER.mods());
        assertEquals(Optional.of(KnownMod.SIMPLE_WEATHER),
                FeatureConflict.PARTICLE_WEATHER.blockingMod(Set.of(KnownMod.SIMPLE_WEATHER)::contains));
    }

    @Test
    void ripplesStayWithSimpleWeather() {
        // Its drops splash as vanilla's do, so the mod's ripples still take their place
        assertEquals(Optional.empty(), FeatureConflict.RAIN_RIPPLES.blockingMod(Set.of(KnownMod.SIMPLE_WEATHER)::contains));
        assertEquals(Optional.of(KnownMod.PARTICLE_RAIN), FeatureConflict.RAIN_RIPPLES.blockingMod(Set.of(KnownMod.PARTICLE_RAIN)::contains));
    }

    @Test
    void theFirstInstalledModIsTheOneReported() {
        var both = Set.of(KnownMod.PARTICLE_RAIN, KnownMod.SIMPLE_WEATHER);
        assertEquals(Optional.of(KnownMod.PARTICLE_RAIN), FeatureConflict.PARTICLE_WEATHER.blockingMod(both::contains));
    }

    @Test
    void nothingIsBlockedWithoutItsMods() {
        for (var conflict : FeatureConflict.values())
            assertEquals(Optional.empty(), conflict.blockingMod(Set.<KnownMod>of()::contains), conflict::name);
    }

    @Test
    void everyFeatureAndDsconflictsMessageIsTranslated() throws Exception {
        try (var stream = FeatureConflictTests.class.getResourceAsStream("/assets/dsurround/lang/en_us.json")) {
            assertNotNull(stream);
            var lang = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            for (var conflict : FeatureConflict.values())
                assertTrue(lang.has(conflict.translationKey()), conflict::name);
            for (var key : List.of("header", "entry", "none"))
                assertTrue(lang.has("dsurround.command.dsconflicts." + key), key);
        }
    }

    @Test
    void everyFeatureHasAModAndADescription() {
        for (var conflict : FeatureConflict.values()) {
            assertFalse(conflict.mods().isEmpty(), conflict::name);
            assertFalse(conflict.feature().isBlank(), conflict::name);
        }
    }
}
