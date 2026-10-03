package org.orecruncher.dsurround.runtime;

import net.minecraft.SharedConstants;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.biome.BiomeGenerationSettings;
import net.minecraft.world.level.biome.BiomeSpecialEffects;
import net.minecraft.world.level.biome.MobSpawnSettings;
import org.apache.logging.log4j.Level;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.system.IStopwatch;
import org.orecruncher.dsurround.lib.system.ISystemClock;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for the condition evaluators and the platform script functions.
 */
public class EvaluatorTests {

    @BeforeAll
    static void setup() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private record FixedClock(Instant now) implements ISystemClock {
        @Override
        public long getUtcNanosNow() {
            return 0;
        }

        @Override
        public Instant getUtcNow() {
            return this.now;
        }

        @Override
        public IStopwatch getStopwatch() {
            return null;
        }
    }

    private static final class CountingLog implements IModLog {
        final List<String> errors = new ArrayList<>();

        @Override
        public boolean isDebugging() {
            return false;
        }

        @Override
        public boolean isTracing(int mask) {
            return false;
        }

        @Override
        public void log(Level level, @Nullable Throwable t, String format, @Nullable Object... params) {
            if (level == Level.ERROR)
                this.errors.add(params == null || params.length == 0 ? format : String.format(format, params));
        }
    }

    /**
     * A biome library whose biome lookup always fails.
     */
    private static final class BrokenBiomeLibrary implements IBiomeLibrary {
        @Override
        public BiomeInfo findBiomeInfo(Biome biome) {
            throw new IllegalStateException("broken");
        }

        @Override
        public BiomeInfo getBiomeInfo(Biome biome) {
            throw new IllegalStateException("broken");
        }

        @Override
        public BiomeInfo getBiomeInfo(SyntheticBiome biome) {
            throw new IllegalStateException("broken");
        }

        @Override
        public String getBiomeName(ResourceLocation id) {
            return "";
        }

        @Override
        public Object eval(Biome biome, Script script) {
            return false;
        }

        @Override
        public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        }

        @Override
        public int getVersion() {
            return 0;
        }

        @Override
        public Stream<String> dump() {
            return Stream.empty();
        }
    }

    private static Biome biome() {
        return new Biome.BiomeBuilder()
                .hasPrecipitation(true)
                .temperature(0.8F)
                .downfall(0.5F)
                .specialEffects(new BiomeSpecialEffects.Builder()
                        .fogColor(0).waterColor(0).waterFogColor(0).skyColor(0)
                        .build())
                .mobSpawnSettings(MobSpawnSettings.EMPTY)
                .generationSettings(BiomeGenerationSettings.EMPTY)
                .build();
    }

    // ---- platform.isCurrentDateInRangeOf ----------------------------------------------------------------------------

    // 11 pm on Christmas Eve in London
    private static final Instant CHRISTMAS_EVE_LATE_UTC = Instant.parse("2026-12-24T23:00:00Z");

    @Test
    void todayIsThePlayersOwnDate() {
        // Regression: "today" was the UTC date, so holidays started or ended hours away from the player's midnight
        var clock = new FixedClock(CHRISTMAS_EVE_LATE_UTC);

        assertFalse(new PlatformFunctions(clock, ZoneOffset.UTC).isCurrentDateInRangeOf(12, 25, 0), "still the 24th in UTC");
        assertTrue(new PlatformFunctions(clock, ZoneId.of("Europe/Berlin")).isCurrentDateInRangeOf(12, 25, 0), "already the 25th in Berlin");
        assertFalse(new PlatformFunctions(clock, ZoneId.of("America/Los_Angeles")).isCurrentDateInRangeOf(12, 25, 0), "afternoon of the 24th in Los Angeles");
    }

    @Test
    void theDefaultZoneIsTheSystemZone() {
        // The public constructor, used by the container, is the player's zone
        var clock = new FixedClock(CHRISTMAS_EVE_LATE_UTC);

        assertEquals(new PlatformFunctions(clock, ZoneId.systemDefault()).isCurrentDateInRangeOf(12, 25, 0),
                new PlatformFunctions(clock).isCurrentDateInRangeOf(12, 25, 0));
    }

    // ---- BiomeConditionEvaluator -------------------------------------------------------------------------------------

    @Test
    void aBiomeThatCantBeSetUpIsLoggedOncePerReload() {
        // Regression: every rule checked against the biome logged the same stack trace again
        var log = new CountingLog();
        var evaluator = new BiomeConditionEvaluator(new BrokenBiomeLibrary(), log, new PlatformFunctions(new FixedClock(Instant.EPOCH)));
        var biome = biome();
        var script = new Script("true");

        assertEquals(false, evaluator.eval(biome, script));
        assertEquals(false, evaluator.eval(biome, script));
        assertEquals(1, log.errors.size(), log.errors.toString());

        evaluator.reset();
        evaluator.eval(biome, script);
        assertEquals(2, log.errors.size(), "logged again after a reload");
    }

    // ---- ConditionEvaluator ticking ------------------------------------------------------------------------------------

    @Test
    void ticksWhileInGame() {
        assertTrue(ConditionEvaluator.shouldTick(true, false, true));
        assertTrue(ConditionEvaluator.shouldTick(true, false, false));
    }

    @Test
    void holdsStillWhilePaused() {
        assertFalse(ConditionEvaluator.shouldTick(true, true, true));
    }

    @Test
    void ticksOnceAfterLeavingTheGame() {
        // Regression: the variables stopped updating on disconnect and kept the last world's values
        assertTrue(ConditionEvaluator.shouldTick(false, false, true));
        assertFalse(ConditionEvaluator.shouldTick(false, false, false), "only the first tick out of game");
    }
}
