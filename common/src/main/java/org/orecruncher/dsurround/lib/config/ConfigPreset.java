package org.orecruncher.dsurround.lib.config;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.text.Localization;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/**
 * A named set of values for some of a configuration's properties, applied in one go from the config screen. A preset
 * is an action, not a setting: applying it sets the values it lists and nothing else, and nothing remembers which
 * preset was applied. Presets list only the properties they care about, so applying one leaves the rest alone.
 * <p>
 * A presets file is a JSON array of objects, each with an {@code id}, {@code values} (dotted property paths, as in
 * the config file, to the values to give them), and optionally a {@code name} and {@code description} to show; without
 * them the translations "root.preset.id" and "root.preset.id.tooltip" are used. Each is in a {@code group}, a row of
 * buttons of its own ({@link #FEATURES} if not given; "root.presets.group.name" names it). Files are merged in the order
 * they are read (see {@link #merge}): a preset with an id already seen replaces it, and {@code "hidden": true} removes
 * it.
 *
 * @param id          the preset's name in commands and translation keys
 * @param name        what to call it, or null to use its translation
 * @param description what it does, or null to use its translation
 * @param group       the row of buttons it is in
 * @param hidden      whether this entry removes the preset with its id rather than defining one
 * @param values      each property's dotted path, and the value to give it, in the order they are listed
 */
public record ConfigPreset(String id, @Nullable String name, @Nullable String description, String group, boolean hidden,
                           Map<String, JsonElement> values) {

    /**
     * The group of a preset that names none: the features presets turn on and off
     */
    public static final String FEATURES = "features";

    public ConfigPreset {
        values = Collections.unmodifiableMap(new LinkedHashMap<>(values));
    }

    /**
     * A preset in the {@link #FEATURES} group
     */
    public ConfigPreset(String id, @Nullable String name, @Nullable String description, boolean hidden,
                        Map<String, JsonElement> values) {
        this(id, name, description, FEATURES, hidden, values);
    }

    /**
     * The presets in a presets file. An entry that isn't a valid preset is described to {@code problems} and left out,
     * so one mistake in a modpack's file doesn't lose the rest.
     */
    public static List<ConfigPreset> parse(@Nullable JsonElement tree, Consumer<String> problems) {
        if (tree == null)
            return List.of();
        if (!tree.isJsonArray()) {
            problems.accept("a presets file is an array of presets");
            return List.of();
        }

        var presets = new ArrayList<ConfigPreset>();
        for (var element : tree.getAsJsonArray()) {
            if (!(element instanceof JsonObject object)) {
                problems.accept("a preset is an object, not " + element);
                continue;
            }
            var id = string(object, "id");
            if (id == null || id.isEmpty() || !id.chars().allMatch(ConfigPreset::isIdCharacter)) {
                problems.accept("a preset needs an id of letters, digits and _: " + object);
                continue;
            }
            var group = string(object, "group");
            if (group == null)
                group = FEATURES;
            if (group.isEmpty() || !group.chars().allMatch(ConfigPreset::isIdCharacter)) {
                problems.accept("preset '" + id + "' needs a group of letters, digits and _: " + group);
                continue;
            }
            var hidden = object.get("hidden") instanceof JsonPrimitive p && p.isBoolean() && p.getAsBoolean();
            var values = new LinkedHashMap<String, JsonElement>();
            if (object.get("values") instanceof JsonObject v) {
                for (var entry : v.entrySet())
                    values.put(entry.getKey(), entry.getValue());
            } else if (!hidden) {
                problems.accept("preset '" + id + "' needs an object of values");
                continue;
            }
            presets.add(new ConfigPreset(id, string(object, "name"), string(object, "description"), group, hidden, values));
        }
        return presets;
    }

    private static boolean isIdCharacter(int c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    private static @Nullable String string(JsonObject object, String member) {
        return object.get(member) instanceof JsonPrimitive p && p.isString() ? p.getAsString() : null;
    }

    /**
     * The presets from several files, read in order: each file's presets go after those already seen, except that one
     * with an id already seen takes its place, and a hidden one removes it.
     */
    public static List<ConfigPreset> merge(List<List<ConfigPreset>> files) {
        var merged = new LinkedHashMap<String, ConfigPreset>();
        for (var file : files) {
            for (var preset : file) {
                if (preset.hidden())
                    merged.remove(preset.id());
                else
                    merged.put(preset.id(), preset);
            }
        }
        return List.copyOf(merged.values());
    }

    /**
     * The preset with only the values it can apply to {@code config}: each value naming a property, of a type and in a
     * range it can have, that doesn't need a restart and that {@code allowed} accepts by its path. Each value left out
     * is described to {@code problems}.
     */
    public ConfigPreset validated(ConfigurationData config, Predicate<String> allowed, Consumer<String> problems) {
        var kept = new LinkedHashMap<String, JsonElement>();
        for (var entry : this.values.entrySet()) {
            var path = entry.getKey();
            var target = resolve(config.getSpecification(), config, path);
            var value = target == null ? null : convert(target.property(), entry.getValue());
            String problem = null;
            if (target == null)
                problem = "no such property";
            else if (value == null)
                problem = entry.getValue() + " isn't a value it can have";
            else if (!target.inRange(value))
                problem = entry.getValue() + " is outside its range";
            else if (target.property().isClientRestartRequired() || target.property().isWorldRestartRequired())
                problem = "it needs a restart";
            else if (!allowed.test(path))
                problem = "presets can't change it";

            if (problem == null)
                kept.put(path, entry.getValue());
            else
                problems.accept("preset '" + this.id + "', " + path + ": " + problem);
        }
        return new ConfigPreset(this.id, this.name, this.description, this.group, this.hidden, kept);
    }

    /**
     * The groups of some presets, in the order the presets first name them: a row of buttons each
     */
    public static List<String> groups(Collection<ConfigPreset> presets) {
        return presets.stream().map(ConfigPreset::group).distinct().toList();
    }

    /**
     * The translation key of a group's name; its description is this key with ".tooltip".
     */
    public static String groupLanguageKey(ConfigurationData config, String group) {
        return config.getTranslationRoot() + ".presets.group." + group;
    }

    /**
     * What to call a group: its translation, or its name
     */
    public static String displayGroup(ConfigurationData config, String group) {
        return Localization.loadIfPresent(groupLanguageKey(config, group)).orElse(group);
    }

    /**
     * The translation key of the preset's name; its description is this key with ".tooltip".
     */
    public String languageKey(ConfigurationData config) {
        return config.getTranslationRoot() + ".preset." + this.id;
    }

    /**
     * What to call the preset: its own name, its translation, or its id.
     */
    public String displayName(ConfigurationData config) {
        return Optional.ofNullable(this.name)
                .or(() -> Localization.loadIfPresent(this.languageKey(config)))
                .orElse(this.id);
    }

    /**
     * What the preset does: its own description, its translation, or nothing.
     */
    public String displayDescription(ConfigurationData config) {
        return Optional.ofNullable(this.description)
                .or(() -> Localization.loadIfPresent(this.languageKey(config) + ".tooltip"))
                .orElse("");
    }

    /**
     * Sets the values the preset lists in {@code config}, without saving it. A value that doesn't fit its property
     * (no such property, the wrong type, an unknown enum name) is left out, and one outside its property's range is
     * limited to it; either is described in the result.
     *
     * @return a description of each value that was left out or limited, empty if every value applied as listed
     */
    public List<String> apply(ConfigurationData config) {
        var problems = new ArrayList<String>();
        for (var entry : this.values.entrySet()) {
            var path = entry.getKey();
            var target = resolve(config.getSpecification(), config, path);
            if (target == null) {
                problems.add(path + ": no such property");
                continue;
            }
            var value = convert(target.property(), entry.getValue());
            if (value == null) {
                problems.add(path + ": " + entry.getValue() + " isn't a value it can have");
                continue;
            }
            if (!target.set(value))
                problems.add(path + ": " + entry.getValue() + " is outside its range");
        }
        return problems;
    }

    /**
     * A preset of the same properties at the values {@code config} has for them now: applying it puts them back. A
     * path naming no property is left out.
     */
    public ConfigPreset current(ConfigurationData config) {
        var values = new LinkedHashMap<String, JsonElement>();
        for (var path : this.values.keySet()) {
            var target = resolve(config.getSpecification(), config, path);
            if (target == null)
                continue;
            var value = target.property().getValue(target.holder());
            values.put(path, switch (value) {
                case Boolean b -> new JsonPrimitive(b);
                case Number n -> new JsonPrimitive(n);
                case Enum<?> e -> new JsonPrimitive(e.name());
                default -> new JsonPrimitive(value.toString());
            });
        }
        return new ConfigPreset(this.id, this.name, this.description, this.group, false, values);
    }

    /**
     * A value the preset would change.
     *
     * @param group    the group holding the property, or null if it is at the top
     * @param property the property
     * @param from     its value now
     * @param to       the value the preset gives it, limited to its range
     */
    public record Change(@Nullable ConfigElement.PropertyGroup group, ConfigElement.PropertyValue<?> property,
                         Object from, Object to) {
    }

    /**
     * What applying the preset to {@code config} would change, in the order the preset lists them: values that already
     * match, and those {@link #apply} would leave out, aren't included.
     */
    @SuppressWarnings("unchecked")
    public List<Change> changes(ConfigurationData config) {
        var changes = new ArrayList<Change>();
        for (var entry : this.values.entrySet()) {
            var target = resolve(config.getSpecification(), config, entry.getKey());
            var value = target == null ? null : convert(target.property(), entry.getValue());
            if (value == null)
                continue;
            var property = (ConfigElement.PropertyValue<Object>) target.property();
            var from = property.getValue(target.holder());
            var to = property.clamp(value);
            if (!to.equals(from))
                changes.add(new Change(target.group(), property, from, to));
        }
        return changes;
    }

    /**
     * A property, the group it is in (null at the top) and the object holding it.
     */
    record Target(@Nullable ConfigElement.PropertyGroup group, ConfigElement.PropertyValue<?> property, Object holder) {

        @SuppressWarnings("unchecked")
        boolean inRange(Object value) {
            return ((ConfigElement.PropertyValue<Object>) this.property).clamp(value).equals(value);
        }

        /**
         * Sets the property's value, limited to its range.
         *
         * @return false if it had to be limited
         */
        @SuppressWarnings("unchecked")
        boolean set(Object value) {
            var property = (ConfigElement.PropertyValue<Object>) this.property;
            var limited = property.clamp(value);
            property.setValue(this.holder, limited);
            return limited.equals(value);
        }
    }

    /**
     * The property at a dotted path, or null if there is none.
     */
    static @Nullable Target resolve(Collection<ConfigElement<?>> elements, Object holder, String path) {
        return resolve(null, elements, holder, path);
    }

    private static @Nullable Target resolve(@Nullable ConfigElement.PropertyGroup parent, Collection<ConfigElement<?>> elements, Object holder, String path) {
        var dot = path.indexOf('.');
        var name = dot < 0 ? path : path.substring(0, dot);
        for (var element : elements) {
            if (!element.fieldName().equals(name))
                continue;
            if (dot < 0)
                return element instanceof ConfigElement.PropertyValue<?> pv ? new Target(parent, pv, holder) : null;
            if (element instanceof ConfigElement.PropertyGroup group)
                return resolve(group, group.getChildren(), group.getInstance(holder), path.substring(dot + 1));
            return null;
        }
        return null;
    }

    /**
     * A value from the file as the property's type, or null if it isn't one the property can have.
     */
    static @Nullable Object convert(ConfigElement.PropertyValue<?> property, JsonElement value) {
        if (!(value instanceof JsonPrimitive primitive))
            return null;
        return switch (property) {
            case ConfigElement.BooleanValue ignored -> primitive.isBoolean() ? primitive.getAsBoolean() : null;
            case ConfigElement.IntegerValue ignored -> {
                if (!primitive.isNumber())
                    yield null;
                var number = primitive.getAsDouble();
                yield number == Math.rint(number) && number >= Integer.MIN_VALUE && number <= Integer.MAX_VALUE
                        ? (int) number : null;
            }
            case ConfigElement.DoubleValue ignored -> primitive.isNumber() ? primitive.getAsDouble() : null;
            case ConfigElement.StringValue ignored -> primitive.isString() ? primitive.getAsString() : null;
            case ConfigElement.EnumValue e -> {
                if (!primitive.isString())
                    yield null;
                for (var constant : e.getEnumClass().getEnumConstants())
                    if (constant.name().equals(primitive.getAsString()))
                        yield constant;
                yield null;
            }
            default -> null;
        };
    }
}
