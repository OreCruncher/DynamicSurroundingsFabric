package org.orecruncher.dsurround.config.libraries.impl;

import org.orecruncher.dsurround.runtime.PlatformFunctions;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.locale.Language;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ConfigServices;
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.biome.BiomeInfoBuilder;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.config.libraries.ISoundLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.function.Guard;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.BiomeConditionEvaluator;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class BiomeLibrary implements IBiomeLibrary {

    private static final String FILE_NAME = "biomes.json";
    private static final Codec<List<BiomeConfigRule>> CODEC = Codec.list(BiomeConfigRule.CODEC);

    private final IModLog logger;
    private final ISoundLibrary soundLibrary;
    private final ITagLibrary tagLibrary;
    // What each BiomeInfo is given; see services()
    @Nullable
    private ConfigServices services;
    private final BiomeConditionEvaluator biomeConditionEvaluator;

    // Mapping of biomes to their respective data
    private final Map<SyntheticBiome, BiomeInfo> internalBiomes = new EnumMap<>(SyntheticBiome.class);

    /*
     * Info for real biomes, built on first use. Keyed by identity: Biome doesn't override equals/hashCode.
     *
     * Cleared on every reload, tag syncs included. Joining a world brings a new biome registry (new Biome objects)
     * followed by a tag sync, so that is where entries for the previous world's biomes are dropped. Because the map
     * is emptied whenever the version changes, an entry is never out of date. (A WeakHashMap can't do this job: each
     * BiomeInfo holds its Biome, so every value would keep its own key alive.)
     */
    private final Map<Biome, BiomeInfo> biomes = new Reference2ObjectOpenHashMap<>(128);

    // Cached list of biome config rules.  Need to hold onto them
    // because they may be needed to handle a dynamic biome load.
    private final ObjectArray<BiomeConfigRule> biomeConfigs = new ObjectArray<>(128);

    // A broken rule fails for every biome it is checked against, so each is reported once per reload rather than
    // once per biome
    private final LogThrottle<BiomeConfigRule> ruleFailures;

    // Applying the rules to a biome failing outside any one rule: a bug, so every one is logged, at most once per biome
    // per reload
    private final Guard applyRules;

    // Current version of the configs that are loaded.
    private int version = 0;

    public BiomeLibrary(IModLog logger, PlatformFunctions platformFunctions, ISoundLibrary soundLibrary, ITagLibrary tagLibrary) {
        this.logger = ModLog.createChild(logger, "BiomeLibrary");
        this.soundLibrary = soundLibrary;
        this.tagLibrary = tagLibrary;
        this.ruleFailures = LogThrottle.oncePerKey(this.logger, "biome rule failures", "the next reload");
        this.applyRules = Guard.named("Applying biome rules", this.logger, Guard.UNLIMITED);
        this.biomeConditionEvaluator = new BiomeConditionEvaluator(this, this.logger, platformFunctions);
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {

        this.version++;

        // Every scope: biome info depends on tags as well as rules, and a tag sync follows every new biome registry
        this.biomes.clear();
        this.ruleFailures.reset();
        this.applyRules.reset();
        this.biomeConditionEvaluator.reset();

        if (scope == IReloadEvent.Scope.TAGS) {
            this.logger.info("received tag update notification; version is now %d", this.version);
            return;
        }

        // Wipe out the internal biome cache.  These will be reset.
        this.internalBiomes.clear();
        this.biomeConfigs.clear();

        var findResults = resourceUtilities.findModResources(CODEC, FILE_NAME);
        findResults.forEach(result -> this.biomeConfigs.addAll(result.resourceContent()));

        // Ensure they are in priority order where the least is towards the beginning
        // of the list.
        this.biomeConfigs.sort(Comparator.comparingInt(BiomeConfigRule::priority));

        for (var b : SyntheticBiome.values())
            initializeSyntheticBiome(b);

        this.logger.info("%d biome configs loaded; version is now %d", this.biomeConfigs.size(), this.version);
    }

    /**
     * What each BiomeInfo is given. Built on first use: the condition evaluator can't be given to this library when
     * it is created, as the evaluator's biome variables need this library (a cycle), but by the time biome info is
     * built, during a reload, everything has been created.
     */
    private ConfigServices services() {
        if (this.services == null)
            this.services = new ConfigServices(this.logger, this.soundLibrary, this.tagLibrary, ContainerManager.resolve(IConditionEvaluator.class));
        return this.services;
    }

    private void initializeSyntheticBiome(SyntheticBiome biome) {
        String match = "@" + biome.getName();
        var builder = new BiomeInfoBuilder(this.version, biome.getId(), biome.getName(), biome.getTraits(), this.services());

        for (var c : this.biomeConfigs) {
            if (c.biomeSelector().asString().equalsIgnoreCase(match)) {
                builder.apply(c);
            }
        }

        this.internalBiomes.put(biome, builder.build());
    }

    private static Registry<Biome> getActiveRegistry() {
        return RegistryUtils.getRegistry(Registries.BIOME).orElseThrow();
    }

    /**
     * The info if it has already been built, otherwise null. Never builds anything, so it is safe in places like
     * mixins that can run before the library is ready.
     */
    @Override
    public @Nullable BiomeInfo findBiomeInfo(Biome biome) {
        return this.biomes.get(biome);
    }

    @Override
    public BiomeInfo getBiomeInfo(Biome biome) {
        var info = this.biomes.get(biome);
        return info != null ? info : this.buildInfo(biome);
    }

    private BiomeInfo buildInfo(Biome biome) {
        var id = getBiomeId(biome);
        var name = getBiomeName(id);
        BiomeTraits traits = BiomeTraits.from(id, biome, this.tagLibrary);

        // The rule selectors are checked against the builder, so the info isn't needed until it is finished
        final var builder = new BiomeInfoBuilder(this.version, id, name, traits, biome, this.services());

        // Collect any trait changes into the trait collection before applying
        // general rules as these traits can influence decisions. Each rule's own failure is reported by the rule; a
        // failure outside them still leaves the biome built, so it isn't tried again on every lookup.
        this.applyRules.run(() -> this.applyTraits(biome, builder), id);

        // Apply rule configs
        this.applyRules.run(() -> this.applyRuleConfigs(biome, builder), id);

        final var result = builder.build();
        this.biomes.put(biome, result);
        return result;
    }

    @Override
    public int getVersion() {
        return this.version;
    }

    @Override
    public BiomeInfo getBiomeInfo(SyntheticBiome biome) {
        return this.internalBiomes.get(biome);
    }

    @Override
    public Object eval(Biome biome, Script script) {
        var info = this.getBiomeInfo(biome);
        return this.biomeConditionEvaluator.eval(biome, info, script);
    }

    // A rule that throws is reported once per reload and skipped; the rest still apply
    private void applyTraits(Biome biome, BiomeInfoBuilder builder) {
        RuleGuard.forEach(this.getNonSyntheticBiomeRules(rule -> !rule.traits().isEmpty()),
                rule -> {
                    if (this.biomeConditionEvaluator.check(biome, builder, rule.biomeSelector()))
                        builder.mergeTraits(rule);
                },
                (rule, t) -> this.ruleFailures.error(rule, t, "Unable to apply traits from rule [%s]", rule));
    }

    private void applyRuleConfigs(Biome biome, BiomeInfoBuilder builder) {
        RuleGuard.forEach(this.getNonSyntheticBiomeRules(rule -> rule.traits().isEmpty()),
                rule -> {
                    if (this.biomeConditionEvaluator.check(biome, builder, rule.biomeSelector()))
                        builder.apply(rule);
                },
                (rule, t) -> this.ruleFailures.error(rule, t, "Unable to process biome rule [%s]", rule));
    }

    private Iterable<BiomeConfigRule> getNonSyntheticBiomeRules(Predicate<BiomeConfigRule> filter) {
        // Filter out synthetic biome data
        return this.biomeConfigs.stream()
                .filter(c -> !c.biomeSelector().asString().startsWith("@"))
                .filter(filter)
                .toList();
    }

    private static ResourceLocation getBiomeId(Biome biome) {
        return RegistryUtils.getRegistryEntry(Registries.BIOME, biome)
                .map(holder -> holder.unwrapKey().orElseThrow().location()).orElseThrow();
    }

    @Override
    public String getBiomeName(ResourceLocation id) {
        final String fmt = String.format("biome.%s.%s", id.getNamespace(), id.getPath());
        return Language.getInstance().getOrDefault(fmt);
    }

    @Override
    public Stream<String> dump() {
        var realBiomes = getActiveRegistry()
                .stream()
                .map(this::getBiomeInfo)
                .map(BiomeInfo::toString)
                .sorted();

        var fakeBiomes = this.internalBiomes.values()
                .stream()
                .map(BiomeInfo::toString)
                .sorted();

        return Stream.of(realBiomes, fakeBiomes).flatMap(Function.identity());
    }
}
