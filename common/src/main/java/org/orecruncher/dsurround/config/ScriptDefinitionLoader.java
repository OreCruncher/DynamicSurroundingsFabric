// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.util.ExtraCodecs;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IBiomeLibrary;
import org.orecruncher.dsurround.eventing.IReloadEvent;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.logging.ModLog;
import org.orecruncher.dsurround.lib.resources.DiscoveredResource;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions.Definition;
import org.orecruncher.dsurround.lib.scripting.ScriptDefinitions.Section;
import org.orecruncher.dsurround.runtime.IConditionEvaluator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Reads the script definitions (see {@link ScriptDefinitions}) from every dsconfigs/scripts.json, in the mod's own
 * assets, resource packs and the config folder's configs/(namespace)/, and hands them to the biome rules' and the
 * conditions' script engines, before the libraries that use them load. Ours come first; a later file's definition of a
 * name replaces an earlier one's, whatever it was, and the replacement is logged. A file:
 * <pre>
 * {
 *   "constants":  { "APPARITION_CHANCE": 0.1 },
 *   "biome":      { "isHaunted": "SPOOKY &amp;&amp; !UNDERGROUND" },
 *   "conditions": { "isHalloween": "platform.isCurrentDateInRangeOf(10, 31, 7)" }
 * }
 * </pre>
 * Names are written without their {@code def.}; scripts use {@code def.APPARITION_CHANCE} and
 * {@code def.isHalloween()}. A constant is true or false, a number, or a string; a function is an expression. Keys
 * starting with an underscore are comments.
 */
public final class ScriptDefinitionLoader {

    static final String FILE_NAME = "scripts.json";

    private final IConditionEvaluator conditions;
    private final IBiomeLibrary biomes;
    private final IModLog logger;

    public ScriptDefinitionLoader(IConditionEvaluator conditions, IBiomeLibrary biomes, IModLog logger) {
        this.conditions = conditions;
        this.biomes = biomes;
        this.logger = ModLog.createChild(logger, "Scripts");
    }

    public void reload(ResourceUtilities resourceUtilities, IReloadEvent.Scope scope) {
        if (scope == IReloadEvent.Scope.TAGS)
            return;
        var files = resourceUtilities.findModResources(ExtraCodecs.JSON, FILE_NAME);
        var definitions = load(files, this.logger::warn, this.logger::info);
        this.biomes.defineScripts(definitions, this.logger::warn);
        this.conditions.defineScripts(definitions, this.logger::warn);
        this.logger.info("%d script definitions loaded", definitions.definitions().size());
    }

    /**
     * The definitions in the files, merged: each problem is described to {@code problems}, and each definition that
     * replaces another to {@code replaced}, naming the namespaces whose files they are in
     */
    public static ScriptDefinitions load(Collection<DiscoveredResource<JsonElement>> files, Consumer<String> problems, Consumer<String> replaced) {
        // Files in our namespace first (the mod's own, then resource packs', then the config folder's), so the others
        // can replace ours; the resource manager lists namespaces alphabetically
        var ordered = new ArrayList<>(files);
        ordered.sort(Comparator.comparing(file -> !file.namespace().equals(Constants.MOD_ID)));

        Map<String, Definition> merged = new LinkedHashMap<>();
        for (var file : ordered) {
            var source = "[" + file.namespace() + "] " + FILE_NAME;
            if (!(file.resourceContent() instanceof JsonObject root)) {
                problems.accept(source + ": not an object of sections");
                continue;
            }
            for (var section : root.entrySet()) {
                if (section.getKey().startsWith("_"))
                    continue;
                var kind = switch (section.getKey()) {
                    case "constants" -> Section.CONSTANT;
                    case "biome" -> Section.BIOME;
                    case "conditions" -> Section.CONDITIONS;
                    default -> null;
                };
                if (kind == null) {
                    problems.accept("%s: unknown section '%s' (constants, biome or conditions)".formatted(source, section.getKey()));
                    continue;
                }
                if (!(section.getValue() instanceof JsonObject entries)) {
                    problems.accept("%s: '%s' is not an object of names".formatted(source, section.getKey()));
                    continue;
                }
                for (var entry : entries.entrySet()) {
                    var name = entry.getKey();
                    if (name.startsWith("_"))
                        continue;
                    var definition = definition(kind, name, entry.getValue(), source, problems);
                    if (definition == null)
                        continue;
                    var key = definition.name().toLowerCase(Locale.ROOT);
                    var earlier = merged.remove(key);
                    if (earlier != null && !earlier.source().equals(source))
                        replaced.accept("%s from %s replaces the %s from %s".formatted(definition.name(), source, earlier.section(), earlier.source()));
                    merged.put(key, definition);
                }
            }
        }
        var byName = new LinkedHashMap<String, Definition>();
        for (var definition : merged.values())
            byName.put(definition.name(), definition);
        return new ScriptDefinitions(byName);
    }

    private static Definition definition(Section kind, String name, JsonElement value, String source, Consumer<String> problems) {
        if (!ScriptDefinitions.isValidName(name)) {
            problems.accept("%s: '%s' can't be a name: a letter or underscore, then letters, digits and underscores (no 'def.')".formatted(source, name));
            return null;
        }
        var fullName = ScriptDefinitions.fullName(name);
        if (kind == Section.CONSTANT) {
            Object constant = null;
            if (value.isJsonPrimitive()) {
                var primitive = value.getAsJsonPrimitive();
                if (primitive.isBoolean())
                    constant = primitive.getAsBoolean();
                else if (primitive.isNumber())
                    constant = primitive.getAsDouble();
                else if (primitive.isString())
                    constant = primitive.getAsString();
            }
            if (constant == null || constant instanceof Double d && !Double.isFinite(d)) {
                problems.accept("%s: constant %s must be true or false, a number, or a string".formatted(source, fullName));
                return null;
            }
            return new Definition(fullName, kind, constant, source);
        }
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString() || value.getAsString().isBlank()) {
            problems.accept("%s: %s must be an expression, in a string".formatted(source, fullName));
            return null;
        }
        return new Definition(fullName, kind, value.getAsString(), source);
    }
}
