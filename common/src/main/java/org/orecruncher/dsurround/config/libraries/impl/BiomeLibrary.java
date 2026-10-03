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
import org.orecruncher.dsurround.config.SyntheticBiome;
import org.orecruncher.dsurround.config.biome.BiomeInfo;
import org.orecruncher.dsurround.config.biome.biometraits.BiomeTraits;
import org.orecruncher.dsurround.config.data.BiomeConfigRule;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.Guard;
import org.orecruncher.dsurround.lib.logging.LogThrottle;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.scripting.Script;
import org.orecruncher.dsurround.runtime.BiomeConditionEvaluator;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

public final class BiomeLibrary implements IBiomeLibrary {

    private static final String FILE_NAME = "biomes.json";
    private static final Codec<List<BiomeConfigRule>> CODEC = Codec.list(BiomeConfigRule.CODEC);

    private final IModLog logger;
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

    // Current version of the configs that are loaded.
    private int version = 0;

    public BiomeLibrary(IModLog logger, PlatformFunctions platformFunctions) {
        this.logger = ModLog.createChild(logger, "BiomeLibrary");
        this.ruleFailures = LogThrottle.oncePerKey(this.logger, "biome rule failures", "the next reload");
        this.biomeConditionEvaluator = new BiomeConditionEvaluator(this, this.logger, platformFunctions);
    }

    @Override
    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {

        this.version++;

        // Every scope: biome info depends on tags as well as rules, and a tag sync follows every new biome registry
        this.biomes.clear();
        this.ruleFailures.reset();
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

    private void initializeSyntheticBiome(SyntheticBiome biome) {
        String match = "@" + biome.getName();
        var info = new BiomeInfo(this.version, biome.getId(), biome.getName(), biome.getTraits());

        for (var c : this.biomeConfigs) {
            if (c.biomeSelector().asString().equalsIgnoreCase(match)) {
                info.update(c);
            }
        }

        this.internalBiomes.put(biome, info);
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
        BiomeTraits traits = BiomeTraits.from(id, biome);

        // Build out the info object and store into the biome.  We need to do that
        // so that when applying configs, the script engine can find it.
        final var result = new BiomeInfo(this.version, id, name, traits, biome);
        this.biomes.put(biome, result);

        // Collect any trait changes into the trait collection before applying
        // general rules as these traits can influence decisions.
        this.applyTraits(biome, result);

        // Apply rule configs
        Guard.execute(() -> applyRuleConfigs(biome, result));
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

    private void applyTraits(Biome biome, BiomeInfo info) {
        this.getNonSyntheticBiomeRules(rule -> !rule.traits().isEmpty())
                .forEach(rule -> {
                    try {
                        var applies = this.biomeConditionEvaluator.check(biome, info, rule.biomeSelector());
                        if (applies) {
                            info.mergeTraits(rule);
                        }
                    } catch (Throwable t) {
                        this.reportRuleFailure(rule, t, "Unable to apply traits from rule");
                    }
                });
    }

    private void applyRuleConfigs(Biome biome, BiomeInfo info) {
        this.getNonSyntheticBiomeRules(rule -> rule.traits().isEmpty())
                .forEach(rule -> {
                    try {
                        var applies = this.biomeConditionEvaluator.check(biome, info, rule.biomeSelector());
                        if (applies) {
                            info.update(rule);
                        }
                    } catch (Throwable t) {
                        this.reportRuleFailure(rule, t, "Unable to process biome rule");
                    }
                });

        // Reduce memory consumption as much as possible
        info.trim();
    }

    /**
     * Logs a rule's failure the first time it happens after a reload. Errors the JVM can't recover from (out of
     * memory, stack overflow) are rethrown rather than swallowed.
     */
    private void reportRuleFailure(BiomeConfigRule rule, Throwable t, String message) {
        if (t instanceof VirtualMachineError fatal)
            throw fatal;
        this.ruleFailures.error(rule, t, "%s [%s]", message, rule);
    }

    private Stream<BiomeConfigRule> getNonSyntheticBiomeRules(Predicate<BiomeConfigRule> filter) {
        // Filter out synthetic biome data
        return this.biomeConfigs.stream()
                .filter(c -> !c.biomeSelector().asString().startsWith("@"))
                .filter(filter);
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
