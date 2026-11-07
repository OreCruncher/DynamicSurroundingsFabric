// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config.libraries.impl;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import net.minecraft.SharedConstants;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.block.BlockInfoBuilder;
import org.orecruncher.dsurround.config.data.BlockConfigRule;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.lib.seasons.ISeasonalInformation;
import org.orecruncher.dsurround.lib.system.ISystemClock;
import org.orecruncher.dsurround.lib.threading.RecordingLog;
import org.orecruncher.dsurround.runtime.ConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;
import org.orecruncher.dsurround.runtime.PlatformFunctions;
import org.orecruncher.dsurround.runtime.oracle.ILevelOracle;
import org.orecruncher.dsurround.runtime.variables.*;
import org.orecruncher.dsurround.runtime.weather.IWeatherSimulation;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactory;
import org.orecruncher.dsurround.testing.ConfigFuzz;
import org.orecruncher.dsurround.testing.ConfigFuzz.Source;
import org.orecruncher.dsurround.testing.Fakes;

import java.lang.reflect.RecordComponent;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Fuzzes what the libraries do with the rules that decode, past decoding ({@code DsConfigFuzzTests} covers that), with
 * the files the mod ships edited the way a hand-edited file might be broken (see {@link ConfigFuzz}):
 * <ul>
 *     <li>Scripts (biome selectors, sound chances, conditions) are text until first evaluated. Garbled, none may
 *     escape the evaluator, stall it, or be reported more than once.</li>
 *     <li>Block rules, applied to the game's blocks as {@link BlockLibrary} applies them (through {@link RuleGuard}):
 *     a bad rule costs at most itself. Every block's info still builds, and every block the rule matches neither
 *     before nor after its edit comes out exactly as it did.</li>
 *     <li>Sound factories that decode make their sounds: no exception, and a volume and pitch that are numbers.</li>
 * </ul>
 * Reproducible as {@code DsConfigFuzzTests} is: case {@code i} uses {@code new Random(seed + i)}, and a failing block
 * rule is shrunk to a minimal one and printed.
 */
public class LibraryRulesFuzzTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
        // Block effect producers take the evaluator from the container as they are made: one that never runs a script.
        // Tag matchers take the tag library from it: no block in any tag. No other test resolves either, and one
        // that did would have failed for want of a registration
        ContainerManager.getRootContainer()
                .registerSingleton(IConditionEvaluator.class, Fakes.of(IConditionEvaluator.class, Map.of("check", args -> false, "eval", args -> 0)))
                .registerSingleton(ITagLibrary.class, Fakes.of(ITagLibrary.class, Map.of("is", args -> false)));
    }

    private static final int SCRIPT_CASES = Integer.getInteger("dsconfig.libfuzz.scripts", 3_000);
    private static final int BLOCK_CASES = Integer.getInteger("dsconfig.libfuzz.blocks", 400);
    private static final int SOUND_CASES = Integer.getInteger("dsconfig.libfuzz.sounds", 2_000);
    private static final long SEED = Long.getLong("dsconfig.libfuzz.seed", 20261107L);
    // The longest a garbled script may take, compiled and run four times over
    private static final long STALL_NANOS = 250_000_000L;
    // Blocks each block rule case is tried on: those the rule matched before its edit, and a sample of the rest
    private static final int SAMPLED_BLOCKS = 120;
    private static final int MATCHED_BLOCKS = 40;

    private static final List<List<Source>> SOURCES = ConfigFuzz.loadSources();

    // ---- Scripts ---------------------------------------------------------------------------------------------

    @Test
    void garbledScriptsNeitherEscapeNorStallNorRepeatThemselves() {
        var scripts = shippedScripts();
        assertTrue(scripts.size() > 50, "scripts found in the shipped files: " + scripts.size());

        var log = new RecordingLog();
        var evaluator = evaluator(log);
        // So garbled scripts reach compiling and running, not only the lexer: most shipped ones run cleanly here (67
        // of 80 on 2026-11-07; the rest are biome selectors such as @underground, which aren't scripts, and state.*,
        // whose scanner isn't here)
        long clean = scripts.stream().filter(text -> {
            int before = log.entries.size();
            evaluator.check(new Script(text));
            return log.entries.size() == before;
        }).count();
        assertTrue(clean * 2 > scripts.size(), "shipped scripts that run cleanly: " + clean + " of " + scripts.size());

        for (int i = 0; i < SCRIPT_CASES; i++) {
            var random = new Random(SEED + i);
            var text = ConfigFuzz.garble(random, ConfigFuzz.pick(random, scripts));
            // A fresh Script each time, as each rule decoded holds its own
            int before = log.entries.size();
            long started = System.nanoTime();
            try {
                for (int n = 0; n < 3; n++)
                    evaluator.check(new Script(text));
                evaluator.eval(new Script(text));
            } catch (Throwable t) {
                fail("case %d: %s escaped evaluating %s".formatted(i, t, show(text)));
            }
            long took = System.nanoTime() - started;
            assertTrue(took < STALL_NANOS, "case %d: %d ms evaluating %s".formatted(i, took / 1_000_000, show(text)));
            long reports = log.entries.subList(before, log.entries.size()).stream().filter(e -> e.level() == IModLog.Level.ERROR).count();
            assertTrue(reports <= 1, "case %d: reported %d times evaluating %s".formatted(i, reports, show(text)));
        }
    }

    // The evaluator as the game builds it, with every variable set and function the scripts can name. Their values
    // are never updated (nothing ticks them), and what they would read the world through is never asked
    private static IConditionEvaluator evaluator(IModLog log) {
        var oracle = Fakes.of(ILevelOracle.class, Map.of());
        var seasons = Fakes.of(ISeasonalInformation.class, Map.of());
        var clock = Fakes.of(ISystemClock.class, Map.of());
        return new ConditionEvaluator(log, new PlatformFunctions(clock),
                new BiomeVariables(Fakes.of(IBiomeLibrary.class, Map.of())), new DimensionVariables(),
                new DiurnalVariables(oracle), new PlayerVariables(oracle),
                new WeatherVariables(oracle, seasons, Fakes.of(IWeatherSimulation.class, Map.of()), null),
                new EnvironmentState(null), new GlobalVariables(new Configuration()), new SeasonVariables(seasons));
    }

    // Every script in every shipped file, as decoded
    private static List<String> shippedScripts() {
        var found = new LinkedHashSet<String>();
        for (var sources : SOURCES)
            for (var source : sources) {
                var decoded = source.kind().codec().parse(JsonOps.INSTANCE, source.root()).getOrThrow();
                for (var entry : source.kind().decodedEntries().apply(decoded))
                    collectScripts(entry, found);
            }
        return List.copyOf(found);
    }

    private static void collectScripts(@Nullable Object value, Set<String> found) {
        switch (value) {
            case null -> {
            }
            case Script script -> found.add(script.asString());
            case Optional<?> optional -> optional.ifPresent(v -> collectScripts(v, found));
            case Collection<?> collection -> collection.forEach(v -> collectScripts(v, found));
            case Record record -> {
                for (RecordComponent component : record.getClass().getRecordComponents()) {
                    try {
                        collectScripts(component.getAccessor().invoke(record), found);
                    } catch (ReflectiveOperationException e) {
                        throw new IllegalStateException(e);
                    }
                }
            }
            default -> {
            }
        }
    }

    // ---- Sound factories -------------------------------------------------------------------------------------

    @Test
    void editedSoundFactoriesStillMakeSounds() {
        var sources = SOURCES.stream().flatMap(List::stream).filter(s -> s.kind() == ConfigFuzz.SOUND_FACTORIES).toList();
        assertFalse(sources.isEmpty(), "shipped sound factory files");
        int made = 0;
        for (int i = 0; i < SOUND_CASES; i++) {
            var random = new Random(SEED + i);
            var source = ConfigFuzz.pick(random, sources);
            var entries = entries(source);
            var edited = ConfigFuzz.edit(random, ConfigFuzz.pick(random, entries));
            var decoded = SoundFactory.CODEC.parse(JsonOps.INSTANCE, edited).result();
            if (decoded.isEmpty())
                continue;
            var factory = decoded.get();
            try {
                factory.createAtLocation(1.5D, 64D, -3.5D, 0.8F);
                factory.createAsAdditional();
                factory.createAsMusic();
                // What the sounds are made with (an instance's own getters need the game's sound manager)
                var sampler = Randomizer.create(i);
                for (int n = 0; n < 8; n++) {
                    float volume = factory.volume().sample(sampler), pitch = factory.pitch().sample(sampler);
                    assertTrue(Float.isFinite(volume) && Float.isFinite(pitch),
                            "case %d: volume %s, pitch %s from %s".formatted(i, volume, pitch, ConfigFuzz.abbreviate(ConfigFuzz.show(edited))));
                }
                made++;
            } catch (Throwable t) {
                fail("case %d: %s escaped making sounds from %s".formatted(i, t, ConfigFuzz.abbreviate(ConfigFuzz.show(edited))));
            }
        }
        assertTrue(made * 4 > SOUND_CASES, "cases whose edited factory decoded and made sounds: " + made + " of " + SOUND_CASES);
    }

    // ---- Block rules -----------------------------------------------------------------------------------------

    @Test
    void aBadBlockRuleCostsOnlyItself() {
        var sources = SOURCES.stream().flatMap(List::stream).filter(s -> s.kind() == ConfigFuzz.BLOCKS).toList();
        assertFalse(sources.isEmpty(), "shipped blocks files");
        var states = BuiltInRegistries.BLOCK.stream().map(b -> b.defaultBlockState()).toList();
        var baselines = new HashMap<Source, Baseline>();

        for (int i = 0; i < BLOCK_CASES; i++) {
            var random = new Random(SEED + i);
            var source = ConfigFuzz.pick(random, sources);
            var baseline = baselines.computeIfAbsent(source, s -> Baseline.of(s, states));
            int index = random.nextInt(baseline.rules.size());
            var edited = ConfigFuzz.edit(random, entries(source).get(index));
            var tried = blocksToTry(random, baseline, index, states);

            var failure = checkBlocks(source, baseline, index, edited, tried);
            if (decodedAndMatched(baseline, index, source, edited, tried))
                exercised++;
            if (failure != null) {
                var minimal = ConfigFuzz.shrink(edited, candidate -> checkBlocks(source, baseline, index, candidate, tried) != null);
                fail("case %d, %s entry %d: %s%nMinimal entry: %s".formatted(i, source.path(), index, failure,
                        ConfigFuzz.abbreviate(ConfigFuzz.show(minimal))));
            }
        }
        // So edited rules reach being applied, not only decoding
        assertTrue(exercised * 4 > BLOCK_CASES, "cases whose edited rule decoded and matched a block: " + exercised + " of " + BLOCK_CASES);
    }

    // Cases whose edited rule decoded and was applied to a block
    private static int exercised;

    private static boolean decodedAndMatched(Baseline baseline, int index, Source source, JsonElement edited, List<BlockState> tried) {
        var root = source.root().deepCopy();
        Objects.requireNonNull(source.kind().entries().apply(root)).set(index, edited);
        var rules = decode(root);
        if (rules == null || rules.size() != baseline.rules.size())
            return false;
        var changed = rules.get(index);
        return tried.stream().anyMatch(changed::match);
    }

    /**
     * What a file's unedited rules make of each block, worked out once
     */
    private record Baseline(List<BlockConfigRule> rules, Map<BlockState, String> infos, List<List<BlockState>> matched) {

        static Baseline of(Source source, List<BlockState> states) {
            var rules = decode(source.root());
            assertNotNull(rules, source.path());
            var infos = new HashMap<BlockState, String>();
            for (var state : states)
                infos.put(state, info(rules, state, null));
            var matched = new ArrayList<List<BlockState>>();
            for (var rule : rules)
                matched.add(states.stream().filter(rule::match).limit(MATCHED_BLOCKS).toList());
            return new Baseline(rules, infos, matched);
        }
    }

    private static List<BlockState> blocksToTry(Random random, Baseline baseline, int index, List<BlockState> states) {
        var tried = new LinkedHashSet<>(baseline.matched.get(index));
        for (int n = 0; n < SAMPLED_BLOCKS; n++)
            tried.add(ConfigFuzz.pick(random, states));
        return List.copyOf(tried);
    }

    private static @Nullable String checkBlocks(Source source, Baseline baseline, int index, JsonElement edited, List<BlockState> tried) {
        var root = source.root().deepCopy();
        var entries = Objects.requireNonNull(source.kind().entries().apply(root));
        entries.set(index, edited);
        var rules = decode(root);
        // A file that no longer decodes at all is DsConfigFuzzTests' concern
        if (rules == null)
            return null;
        var original = baseline.rules.get(index);
        // The edited rule, if it decoded: in the same place, the file's entry count unchanged
        var changed = rules.size() == baseline.rules.size() ? rules.get(index) : null;

        for (var state : tried) {
            String info;
            try {
                info = info(rules, state, null);
            } catch (Throwable t) {
                return "%s escaped building %s".formatted(t, state);
            }
            boolean touched = original.match(state) || (changed != null && changed.match(state));
            if (!touched && !info.equals(baseline.infos.get(state)))
                return "%s, which the rule matches neither before nor after, came out differently:%n%s%nwas:%n%s"
                        .formatted(state, info, baseline.infos.get(state));
        }
        return null;
    }

    /**
     * A block's info as {@link BlockLibrary} builds it, described for comparing
     *
     * @param failures where the rules that threw go, if anywhere
     */
    private static String info(List<BlockConfigRule> rules, BlockState state, @Nullable List<BlockConfigRule> failures) {
        var builder = new BlockInfoBuilder(1, state, SERVICES);
        RuleGuard.forEach(rules,
                rule -> {
                    if (rule.match(state))
                        builder.apply(rule);
                },
                (rule, t) -> {
                    if (failures != null)
                        failures.add(rule);
                });
        var info = builder.build();
        return info + "tracks: " + info.tracks() + "; mud when wet: " + info.tracksMudWhenWet();
    }

    private static @Nullable List<BlockConfigRule> decode(JsonElement root) {
        try {
            var decoded = ConfigFuzz.BLOCKS.codec().parse(JsonOps.INSTANCE, root).resultOrPartial(ignored -> {
            });
            if (decoded.isEmpty())
                return null;
            List<BlockConfigRule> rules = new ArrayList<>();
            for (var entry : ConfigFuzz.BLOCKS.decodedEntries().apply(decoded.get()))
                rules.add((BlockConfigRule) entry);
            return rules;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static List<JsonElement> entries(Source source) {
        var list = new ArrayList<JsonElement>();
        Objects.requireNonNull(source.kind().entries().apply(source.root())).forEach(list::add);
        return list;
    }

    // Sound factories named by their id, so infos compare by what they name; no block in a tag
    private static final ConfigServices SERVICES = new ConfigServices(new RecordingLog(),
            Fakes.of(ISoundLibrary.class, Map.of("getSoundFactoryOrDefault", args -> factory((Identifier) args[0]))),
            ContainerManager.resolve(ITagLibrary.class),
            ContainerManager.resolve(IConditionEvaluator.class));

    private static ISoundFactory factory(Identifier id) {
        return Fakes.of(ISoundFactory.class, Map.of("getLocation", args -> id, "toString", args -> id.toString()));
    }

    private static String show(String text) {
        return ConfigFuzz.abbreviate('"' + text + '"');
    }
}
