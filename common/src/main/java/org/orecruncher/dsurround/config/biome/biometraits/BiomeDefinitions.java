package org.orecruncher.dsurround.config.biome.biometraits;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Works out what a biome grows from its definition file (see {@link IDefinitionFiles}): how many trees it places in
 * a chunk, and whether it is water: it grows seagrass or kelp, or spawns water creatures (squid,
 * dolphins), as frozen oceans and rivers do with no seagrass to show.
 * <p>
 * Trees are told by name or type, as mods often register their configured features in code, where no file shows
 * them: a placed feature named for trees ({@code trees_woodland}), or one whose configured feature is a tree, or picks
 * among trees, or is named for them; not bushes or shrubs, nor one placed underground (a cave's azaleas). How many is the mean of its count
 * rules; other rules (where in the chunk, what it may grow on) are left out, so it is somewhat more than really grow.
 * The definition is the biome as its mod wrote it: a biome modifier from another mod, or a server's datapack, isn't
 * seen.
 */
public final class BiomeDefinitions {

    // A selector picking among selectors is followed this far
    private static final int MAX_DEPTH = 4;

    private final IDefinitionFiles files;
    private final Map<Identifier, Optional<BiomeDefinition>> biomes = new ConcurrentHashMap<>();

    public BiomeDefinitions(IDefinitionFiles files) {
        this.files = files;
    }

    /**
     * What a biome's definition says it grows, if it has one
     */
    public Optional<BiomeDefinition> read(Identifier biome) {
        return this.biomes.computeIfAbsent(biome, this::readBiome);
    }

    private Optional<BiomeDefinition> readBiome(Identifier biome) {
        return this.files.read(biome.getNamespace(), "biome", biome.getPath())
                .filter(JsonElement::isJsonObject)
                .map(json -> this.define(json.getAsJsonObject()));
    }

    private BiomeDefinition define(JsonObject biome) {
        float trees = 0F;
        boolean aquatic = spawnsWaterCreatures(biome);
        for (var feature : vegetation(biome)) {
            if (feature.getPath().contains("seagrass") || feature.getPath().contains("kelp"))
                aquatic = true;
            var placed = this.object("placed_feature", feature);
            if (!this.isTree(feature, placed, 0) || (placed != null && underground(placed)))
                continue;
            trees += placed == null ? Float.NaN : count(placed);
        }
        return new BiomeDefinition(trees, aquatic);
    }

    /**
     * Whether a feature is placed below the surface: at heights through the world, or by scanning for a floor or
     * ceiling (the lush caves' azaleas), not on the surface's heightmap
     */
    private static boolean underground(JsonObject placed) {
        var placement = placed.get("placement");
        if (placement == null || !placement.isJsonArray())
            return false;
        for (var rule : placement.getAsJsonArray()) {
            if (rule.isJsonObject()) {
                var type = type(rule.getAsJsonObject());
                if (type.equals("height_range") || type.equals("environment_scan"))
                    return true;
            }
        }
        return false;
    }

    private static boolean spawnsWaterCreatures(JsonObject biome) {
        var spawners = biome.get("spawners");
        if (spawners == null || !spawners.isJsonObject())
            return false;
        var creatures = spawners.getAsJsonObject().get("water_creature");
        return creatures != null && creatures.isJsonArray() && !creatures.getAsJsonArray().isEmpty();
    }

    /**
     * The placed features of all a biome's steps, by id; tags and features written out in place are left out. Not
     * only the vegetation step: a step only orders the features, and mods put trees in others (No Man's Land in the
     * underground decoration and top layer steps). Underground placements are told by their rules instead.
     */
    private static List<Identifier> vegetation(JsonObject biome) {
        var result = new ArrayList<Identifier>();
        var steps = biome.get("features");
        if (steps == null || !steps.isJsonArray())
            return result;
        for (var step : steps.getAsJsonArray()) {
            var entries = step.isJsonArray() ? step.getAsJsonArray().asList() : List.of(step);
            for (var entry : entries) {
                var id = id(entry);
                if (id != null)
                    result.add(id);
            }
        }
        return result;
    }

    private boolean isTree(@Nullable Identifier id, @Nullable JsonObject placed, int depth) {
        if (id != null && isBush(id))
            return false;
        if (id != null && id.getPath().contains("tree"))
            return true;
        if (placed == null || depth > MAX_DEPTH)
            return false;
        var feature = placed.get("feature");
        if (feature == null)
            return false;
        var configuredId = id(feature);
        if (configuredId != null && isBush(configuredId))
            return false;
        if (configuredId != null && configuredId.getPath().contains("tree"))
            return true;
        var configured = feature.isJsonObject() ? feature.getAsJsonObject() : this.object("configured_feature", configuredId);
        return configured != null && this.isTreeConfigured(configured, depth);
    }

    /**
     * Bushes and shrubs are named or placed as trees ({@code trees_rocky_shrubland} places {@code oak_bush}), but
     * don't make a forest
     */
    private static boolean isBush(Identifier id) {
        return id.getPath().contains("bush") || id.getPath().contains("shrub");
    }

    /**
     * Whether a configured feature is a tree (by its type: {@code minecraft:tree}, or a mod's tree type), or picks
     * among features any of which is
     */
    private boolean isTreeConfigured(JsonObject configured, int depth) {
        var type = configured.get("type");
        if (type != null && type.isJsonPrimitive() && type.getAsString().contains("tree"))
            return true;
        var config = configured.get("config");
        if (config == null || !config.isJsonObject())
            return false;
        var choices = new ArrayList<JsonElement>();
        var features = config.getAsJsonObject().get("features");
        if (features != null) {
            for (var choice : features.isJsonArray() ? features.getAsJsonArray().asList() : List.of(features)) {
                // A random selector's choices carry a chance with the feature
                if (choice.isJsonObject() && choice.getAsJsonObject().has("chance"))
                    choice = choice.getAsJsonObject().get("feature");
                choices.add(choice);
            }
        }
        for (var key : List.of("default", "feature_true", "feature_false")) {
            var choice = config.getAsJsonObject().get(key);
            if (choice != null)
                choices.add(choice);
        }
        for (var choice : choices) {
            var id = id(choice);
            var placed = choice.isJsonObject() ? choice.getAsJsonObject() : this.object("placed_feature", id);
            if (this.isTree(id, placed, depth + 1))
                return true;
        }
        return false;
    }

    /**
     * How many times a placed feature is placed in a chunk, on average, from its count rules; NaN if one can't be
     * worked out
     */
    static float count(JsonObject placed) {
        var placement = placed.get("placement");
        if (placement == null || !placement.isJsonArray())
            return 1F;
        float count = 1F;
        for (var element : placement.getAsJsonArray()) {
            if (!element.isJsonObject())
                continue;
            var rule = element.getAsJsonObject();
            switch (type(rule)) {
                case "count", "count_on_every_layer" -> count *= mean(rule.get("count"));
                case "rarity_filter" -> count /= Math.max(1F, number(rule, "chance", 1F));
                case "noise_threshold_count" ->
                        count *= (number(rule, "below_noise", 0F) + number(rule, "above_noise", 0F)) / 2F;
                // The noise averages nothing, leaving the offset
                case "noise_based_count" ->
                        count *= Math.max(0F, number(rule, "noise_offset", 0F) * number(rule, "noise_to_count_ratio", 0F));
                default -> {
                }
            }
        }
        return count;
    }

    /**
     * The mean of an integer provider: a number, or a constant, uniform, weighted list, clamped, biased or trapezoid
     * one; NaN for any other
     */
    static float mean(@Nullable JsonElement provider) {
        if (provider == null)
            return Float.NaN;
        if (provider.isJsonPrimitive())
            return provider.getAsJsonPrimitive().isNumber() ? provider.getAsFloat() : Float.NaN;
        if (!provider.isJsonObject())
            return Float.NaN;
        var p = provider.getAsJsonObject();
        float min = number(p, "min_inclusive", Float.NaN), max = number(p, "max_inclusive", Float.NaN);
        return switch (type(p)) {
            case "constant" -> number(p, "value", Float.NaN);
            case "uniform" -> (min + max) / 2F;
            // A uniform pick between the least and a uniform pick: most often low
            case "biased_to_bottom" -> min + (max - min) / 4F;
            case "trapezoid" -> (number(p, "min", Float.NaN) + number(p, "max", Float.NaN)) / 2F;
            case "clamped" -> clamp(mean(p.get("source")), min, max);
            case "clamped_normal" -> clamp(number(p, "mean", Float.NaN), min, max);
            case "weighted_list" -> weightedMean(p.get("distribution"));
            default -> Float.NaN;
        };
    }

    // Math.clamp throws on bounds that are missing or the wrong way round
    private static float clamp(float value, float min, float max) {
        if (Float.isNaN(value) || Float.isNaN(min) || Float.isNaN(max) || min > max)
            return Float.NaN;
        return Math.clamp(value, min, max);
    }

    private static float weightedMean(@Nullable JsonElement distribution) {
        if (distribution == null || !distribution.isJsonArray())
            return Float.NaN;
        float sum = 0F, weights = 0F;
        for (var entry : distribution.getAsJsonArray()) {
            if (!entry.isJsonObject())
                return Float.NaN;
            float weight = number(entry.getAsJsonObject(), "weight", Float.NaN);
            sum += mean(entry.getAsJsonObject().get("data")) * weight;
            weights += weight;
        }
        return weights > 0F ? sum / weights : Float.NaN;
    }

    private static String type(JsonObject object) {
        var type = object.get("type");
        if (type == null || !type.isJsonPrimitive())
            return "";
        var name = type.getAsString();
        return name.startsWith("minecraft:") ? name.substring("minecraft:".length()) : name;
    }

    private static float number(JsonObject object, String key, float otherwise) {
        var value = object.get(key);
        return value != null && value.isJsonPrimitive() && value.getAsJsonPrimitive().isNumber() ? value.getAsFloat() : otherwise;
    }

    /**
     * The id a reference names, or null for a tag or anything written out in place
     */
    private static @Nullable Identifier id(JsonElement reference) {
        if (!reference.isJsonPrimitive() || !reference.getAsJsonPrimitive().isString())
            return null;
        var text = reference.getAsString();
        return text.startsWith("#") ? null : Identifier.tryParse(text);
    }

    private @Nullable JsonObject object(String folder, @Nullable Identifier id) {
        if (id == null)
            return null;
        return this.files.read(id.getNamespace(), folder, id.getPath())
                .filter(JsonElement::isJsonObject)
                .map(JsonElement::getAsJsonObject)
                .orElse(null);
    }
}
