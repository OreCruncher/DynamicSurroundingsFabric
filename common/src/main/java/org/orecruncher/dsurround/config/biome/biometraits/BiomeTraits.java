package org.orecruncher.dsurround.config.biome.biometraits;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.biome.Biome;
import org.orecruncher.dsurround.config.BiomeTrait;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.*;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * A biome's traits, and where each came from.
 * <p>
 * Worked out in order of how much each source can be trusted: the biome's tags, then (for a vanilla biome) the mod's
 * table, which settles its dimension, temperature, moisture and vegetation, then what follows from those. Then, for a biome not in the table, guesses from its effects (the vanilla
 * music it plays, its grass), its climate and its name, each only filling in the groups ({@link TraitGroup}:
 * dimension, temperature, moisture, vegetation, kind) that what came before left empty, then what follows from the
 * guesses. Last, conflicting traits are cleaned up.
 */
public final class BiomeTraits {

    private static final IModLog LOGGER = ContainerManager.memoize(IModLog.class);

    private static final IBiomeTraitAnalyzer VANILLA = new VanillaBiomeAnalyzer();
    private static final IBiomeTraitAnalyzer DERIVE = new BiomeTraitAnalyzer();
    private static final IBiomeTraitAnalyzer CLEANUP = new BiomeTraitCleanup();
    // Guesses, most trusted first
    private static final List<Map.Entry<TraitSource, IBiomeTraitAnalyzer>> GUESSES = List.of(
            Map.entry(TraitSource.EFFECTS, new BiomeEffectsAnalyzer()),
            Map.entry(TraitSource.CLIMATE, new BiomeClimateAnalyzer()),
            Map.entry(TraitSource.NAME, new BiomeNameFallbackAnalyzer()));

    // In the traits' order, so toString() lists them as it always has
    private final EnumMap<BiomeTrait, TraitSource> traits;
    private boolean updatedByMerge;

    private BiomeTraits(EnumMap<BiomeTrait, TraitSource> traits) {
        this.traits = traits;
    }

    /**
     * Works out a game biome's traits
     *
     * @param tagLibrary answers which tags the biome has
     */
    public static BiomeTraits from(ResourceLocation id, Biome biome, ITagLibrary tagLibrary) {
        var result = from(BiomeEvidence.of(id, biome), new BiomeTagAnalyzer(tagLibrary));
        LOGGER.debug("[BiomeTraits] %s: %s", id, result.describe());
        return result;
    }

    /**
     * Works out a biome's traits from what is known of it
     *
     * @param tags gives the traits from the biome's tags
     */
    static BiomeTraits from(BiomeEvidence biome, IBiomeTraitAnalyzer tags) {
        var result = new BiomeTraits(new EnumMap<>(BiomeTrait.class));
        result.apply(tags, TraitSource.TAG, biome);
        if (VanillaBiomeAnalyzer.covers(biome.id())) {
            result.applyTable(biome);
            result.apply(DERIVE, TraitSource.DERIVED, biome);
        } else {
            result.apply(DERIVE, TraitSource.DERIVED, biome);
            for (var guess : GUESSES)
                result.apply(guess.getValue(), guess.getKey(), biome);
            result.apply(DERIVE, TraitSource.DERIVED_FROM_GUESS, biome);
        }
        result.apply(CLEANUP, null, biome);
        return result;
    }

    /**
     * Runs an analyzer over the traits so far, noting where those it adds came from. A guess can't add a trait to a
     * group that already had one before it; it can add to one that hadn't (several kinds at once), and traits in no
     * group.
     *
     * @param source where the analyzer's traits come from; null for one that only takes traits away
     */
    private void apply(IBiomeTraitAnalyzer analyzer, TraitSource source, BiomeEvidence biome) {
        var working = EnumSet.noneOf(BiomeTrait.class);
        working.addAll(this.traits.keySet());
        analyzer.analyze(biome, working);

        this.traits.keySet().retainAll(working);
        if (source == null)
            return;

        var filled = EnumSet.noneOf(TraitGroup.class);
        if (source.isGuess())
            for (var trait : this.traits.keySet()) {
                var group = TraitGroup.of(trait);
                if (group != null)
                    filled.add(group);
            }

        for (var trait : working) {
            if (this.traits.containsKey(trait))
                continue;
            var group = TraitGroup.of(trait);
            if (group == null || !filled.contains(group))
                this.traits.put(trait, source);
        }
    }

    /**
     * Adds a vanilla biome's traits from the table. In a group a biome has one of (dimension, temperature, moisture,
     * vegetation), the table's replaces what the tags said: the convention tags disagree with it on a few (they make
     * the windswept hills cold, the mushroom fields hot), and a vanilla biome should be the same on every server,
     * vanilla servers sending only Minecraft's own tags. Kinds the tags add are kept.
     */
    private void applyTable(BiomeEvidence biome) {
        var table = EnumSet.noneOf(BiomeTrait.class);
        VANILLA.analyze(biome, table);
        for (var group : TraitGroup.values()) {
            if (group == TraitGroup.KIND || Collections.disjoint(table, group.members()))
                continue;
            this.traits.keySet().removeIf(trait -> group.members().contains(trait) && !table.contains(trait));
        }
        for (var trait : table)
            this.traits.putIfAbsent(trait, TraitSource.VANILLA);
    }

    /**
     * Traits given outright, as a synthetic biome's are
     */
    public static BiomeTraits of(BiomeTrait... traits) {
        var result = new BiomeTraits(new EnumMap<>(BiomeTrait.class));
        for (var trait : traits)
            result.traits.put(trait, TraitSource.CONFIG);
        return result;
    }

    public void clear() {
        this.traits.clear();
    }

    /**
     * Adds traits from a config rule
     */
    public void merge(Collection<BiomeTrait> traits) {
        int count = this.traits.size();
        for (var trait : traits)
            this.traits.putIfAbsent(trait, TraitSource.CONFIG);
        this.updatedByMerge = this.updatedByMerge || count != this.traits.size();
    }

    public boolean contains(String trait) {
        return this.contains(BiomeTrait.of(trait));
    }

    public boolean contains(BiomeTrait trait) {
        return this.traits.containsKey(trait);
    }

    public void forEach(Consumer<BiomeTrait> consumer) {
        for (var t : this.traits.keySet())
            consumer.accept(t);
    }

    /**
     * Where a trait came from, or null if the biome doesn't have it
     */
    public TraitSource sourceOf(BiomeTrait trait) {
        return this.traits.get(trait);
    }

    /**
     * The groups whose traits were guessed, with the traits: those a tag file or config rule would make certain.
     * Groups with no trait at all aren't included.
     */
    public Map<TraitGroup, Set<BiomeTrait>> guessedGroups() {
        return this.groups(TraitSource::isGuess);
    }

    /**
     * The groups whose traits were guessed from something other than the numbers they are about: the music, grass,
     * name, or what follows from those. A temperature or moisture from the biome's climate is its own numbers, so
     * isn't doubted. These are what the biome dump asks to have checked.
     */
    public Map<TraitGroup, Set<BiomeTrait>> doubtfulGroups() {
        var result = this.groups(TraitSource::isGuess);
        for (var group : List.of(TraitGroup.TEMPERATURE, TraitGroup.MOISTURE)) {
            var traits = result.get(group);
            if (traits != null) {
                traits.removeIf(trait -> this.traits.get(trait) == TraitSource.CLIMATE);
                if (traits.isEmpty())
                    result.remove(group);
            }
        }
        return result;
    }

    /**
     * The groups every biome should have something in (a dimension and a kind) that this one has nothing in.
     * Moisture and vegetation may rightly be neither (an ocean has no vegetation); temperature always comes from the
     * climate.
     */
    public Set<TraitGroup> missingGroups() {
        var result = EnumSet.noneOf(TraitGroup.class);
        for (var group : List.of(TraitGroup.DIMENSION, TraitGroup.KIND))
            if (Collections.disjoint(this.traits.keySet(), group.members()))
                result.add(group);
        return result;
    }

    private Map<TraitGroup, Set<BiomeTrait>> groups(Predicate<TraitSource> sources) {
        var result = new EnumMap<TraitGroup, Set<BiomeTrait>>(TraitGroup.class);
        this.traits.forEach((trait, source) -> {
            var group = TraitGroup.of(trait);
            if (group != null && sources.test(source))
                result.computeIfAbsent(group, g -> EnumSet.noneOf(BiomeTrait.class)).add(trait);
        });
        return result;
    }

    /**
     * The traits by where they came from, as the biome dump shows them: "COLD, FOREST (tags); WET (climate)"
     */
    public String describe() {
        if (this.traits.isEmpty())
            return "none";
        var bySource = new EnumMap<TraitSource, List<String>>(TraitSource.class);
        this.traits.forEach((trait, source) -> bySource.computeIfAbsent(source, s -> new ArrayList<>()).add(trait.getName()));
        return bySource.entrySet().stream()
                .map(e -> String.join(", ", e.getValue()) + " (" + e.getKey().label() + ")")
                .collect(Collectors.joining("; "));
    }

    /**
     * The traits as scripts (biome.getTraits()) and the diagnostics overlay see them: "[COLD, FOREST]", marked with
     * a star when a config rule added any
     */
    public String toString() {
        var temp = this.traits.keySet()
                .stream()
                .map(BiomeTrait::getName)
                .collect(Collectors.joining(", "));

        var fmt = this.updatedByMerge ? "*[%s]" : "[%s]";
        return fmt.formatted(temp);
    }
}
