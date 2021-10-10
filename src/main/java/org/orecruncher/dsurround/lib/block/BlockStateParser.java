package org.orecruncher.dsurround.lib.block;

import com.google.common.collect.ImmutableMap;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.PatternValidation;
import org.orecruncher.dsurround.lib.logging.IModLog;

import java.util.Arrays;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Utility functions for parsing and handling string based block names.  Handles things like properties, both fully
 * and partially described.
 */
@SuppressWarnings("unused")
final class BlockStateParser {

    private static final IModLog LOGGER = Client.LOGGER.createChild(BlockStateParser.class);

    private BlockStateParser() {

    }

    /**
     * Parses the block state string passed in and returns the result of that parsing.
     */
    public static ParseResult parse(final String blockName) throws BlockStateParseException {

        String temp = blockName;
        int idx = temp.indexOf('+');

        String extras = null;

        if (idx > 0) {
            extras = temp.substring(idx + 1);
            temp = temp.substring(0, idx);
        }

        Map<String, String> properties = ImmutableMap.of();

        idx = temp.indexOf('[');
        if (idx > 0) {
            try {

                int end = temp.indexOf(']');
                if (end < 1) {
                    throw new BlockStateParseException("Missing ']'");
                }

                String propString = temp.substring(idx + 1, end);
                properties = Arrays.stream(propString.split(","))
                        .map(elem -> elem.split("="))
                        .collect(Collectors.toMap(e -> e[0], e -> e[1]));

                // Validate the property names
                for (var propName : properties.keySet())
                    if (!PatternValidation.BLOCKSTATE_PROPERTY_NAME.matcher(propName).matches()) {
                        throw new BlockStateParseException(String.format("Property name %s for block %s is invalid", propName, blockName));
                    }

                // Trim it down to validate the Identifier
                temp = temp.substring(0, idx);
            } catch (final Throwable ignore) {
                throw new BlockStateParseException(String.format("Unable to parse properties of '%s'", blockName));
            }
        }

        if (!Identifier.isValid(temp)) {
            throw new BlockStateParseException(String.format("Invalid block name '%s' for entry '%s'", temp, blockName));
        }

        final Identifier resource = new Identifier(temp);
        final Block block = Registry.BLOCK.get(resource);
        if (block == Blocks.AIR && !"mincraft:air".equals(temp)) {
            throw new BlockStateParseException(String.format("Unknown block '%s' for entry '%s'", temp, blockName));
        }

        return new ParseResult(temp, block, properties, extras);
    }

    public final static class ParseResult {

        /**
         * Name of the blockName in standard domain:path form.
         */
        private final String blockName;

        /**
         * The block from the registries
         */
        private final Block block;

        /**
         * The parsed properties after the blockName name, if present
         */
        private final Map<String, String> properties;

        /**
         * Extra information that may have been appended at the end
         */
        private final String extras;

        private ParseResult(final String blockName, final Block block, final Map<String, String> props, @Nullable final String extras) {
            this.blockName = blockName;
            this.block = block;
            this.properties = props;
            this.extras = extras;
        }

        public String getBlockName() {
            return this.blockName;
        }

        public Block getBlock() {
            return this.block;
        }

        public boolean hasProperties() {
            return this.properties.size() > 0;
        }

        public Map<String, String> getProperties() {
            return this.properties;
        }

        public boolean hasExtras() {
            return !StringUtils.isEmpty(this.extras);
        }

        @Nullable
        public String getExtras() {
            return this.extras;
        }

        @Override

        public String toString() {
            final StringBuilder builder = new StringBuilder();
            builder.append(getBlockName());
            if (hasProperties()) {
                builder.append('[');
                final String props = getProperties()
                        .entrySet()
                        .stream()
                        .map(Objects::toString)
                        .collect(Collectors.joining(","));
                builder.append(props);
                builder.append(']');
            }

            if (!StringUtils.isEmpty(this.extras)) {
                builder.append('+').append(this.extras);
            }

            return builder.toString();
        }
    }

}