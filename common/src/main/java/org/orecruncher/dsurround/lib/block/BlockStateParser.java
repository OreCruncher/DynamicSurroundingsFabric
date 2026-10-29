package org.orecruncher.dsurround.lib.block;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parses block specifications from config files: a block id, optionally followed by properties in brackets, as in
 * {@code minecraft:oak_log[axis=y]} or {@code minecraft:oak_stairs[facing=north, half=top]}.
 * <p>
 * A missing namespace means {@code minecraft}. Spaces around names, values and commas are ignored. Every problem is
 * reported with a message saying what is wrong, so pack authors can fix the line.
 */
final class BlockStateParser {

    private BlockStateParser() {
    }

    /**
     * Parses the specification. The block must exist; whether its properties exist is checked by the caller, which
     * knows the block's state definition.
     */
    static ParseResult parse(final String specification) throws BlockStateParseException {
        final String text = specification.trim();
        if (text.isEmpty())
            throw new BlockStateParseException("Block specification is empty");

        String blockPart = text;
        Map<String, String> properties = Map.of();

        final int open = text.indexOf('[');
        if (open >= 0) {
            if (open == 0)
                throw new BlockStateParseException(String.format("Missing block name before '[' in '%s'", specification));
            final int close = text.indexOf(']');
            if (close < 0)
                throw new BlockStateParseException(String.format("Missing ']' in '%s'", specification));
            if (close != text.length() - 1)
                throw new BlockStateParseException(String.format("Unexpected text after ']' in '%s'", specification));
            blockPart = text.substring(0, open).trim();
            properties = parseProperties(text.substring(open + 1, close), specification);
        } else if (text.indexOf(']') >= 0) {
            throw new BlockStateParseException(String.format("']' without '[' in '%s'", specification));
        }

        final Identifier resource = Identifier.tryParse(blockPart);
        if (resource == null)
            throw new BlockStateParseException(String.format("Invalid block name '%s' in '%s'", blockPart, specification));

        // containsKey rather than comparing against air: getValue() returns air for unknown ids, and "air" itself is valid
        if (!BuiltInRegistries.BLOCK.containsKey(resource))
            throw new BlockStateParseException(String.format("Unknown block '%s' in '%s'", resource, specification));

        return new ParseResult(resource.toString(), BuiltInRegistries.BLOCK.getValue(resource), properties);
    }

    /**
     * Parses {@code name=value} pairs separated by commas. An empty list ({@code []}) is allowed and means no
     * properties.
     */
    private static Map<String, String> parseProperties(final String body, final String specification) throws BlockStateParseException {
        if (body.isBlank())
            return Map.of();

        // Insertion ordered, so error messages and output follow the order the author wrote
        final Map<String, String> properties = new LinkedHashMap<>();
        for (final String rawEntry : body.split(",", -1)) {
            final String entry = rawEntry.trim();
            if (entry.isEmpty())
                throw new BlockStateParseException(String.format("Empty property entry (extra comma?) in '%s'", specification));

            final int eq = entry.indexOf('=');
            if (eq < 0)
                throw new BlockStateParseException(String.format("Property '%s' has no value (expected name=value) in '%s'", entry, specification));

            final String name = entry.substring(0, eq).trim();
            final String value = entry.substring(eq + 1).trim();
            if (name.isEmpty())
                throw new BlockStateParseException(String.format("Property with value '%s' has no name in '%s'", value, specification));
            if (value.isEmpty())
                throw new BlockStateParseException(String.format("Property '%s' has no value in '%s'", name, specification));
            if (!PatternValidation.BLOCKSTATE_PROPERTY_NAME.matcher(name).matches())
                throw new BlockStateParseException(String.format("Property name '%s' is invalid (use lowercase letters, digits and _) in '%s'", name, specification));
            if (properties.put(name, value) != null)
                throw new BlockStateParseException(String.format("Property '%s' is listed more than once in '%s'", name, specification));
        }
        return Collections.unmodifiableMap(properties);
    }

    /**
     * @param blockName  the block's id in namespace:path form
     * @param block      the block from the registry
     * @param properties the properties as written, in order; empty if none
     */
    record ParseResult(String blockName, Block block, Map<String, String> properties) {

        boolean hasProperties() {
            return !this.properties.isEmpty();
        }

        @Override
        public @NotNull String toString() {
            if (!this.hasProperties())
                return this.blockName;
            return this.blockName + this.properties.entrySet().stream()
                    .map(e -> e.getKey() + "=" + e.getValue())
                    .collect(Collectors.joining(",", "[", "]"));
        }
    }
}
