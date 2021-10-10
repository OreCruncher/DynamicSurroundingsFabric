package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public final class BlockStateMatcher {

    // All instances will have this defined
    private final Block block;
    private final Identifier blockId;

    // Sometimes an exact match of state is needed. The state being compared
    // would have to match all these properties.
    private final BlockStateProperties props;

    BlockStateMatcher(final BlockState state) {
        this(state.getBlock(), state.getEntries());
    }

    BlockStateMatcher(final Block block) {
        this.block = block;
        this.blockId = Registry.BLOCK.getId(block);
        this.props = BlockStateProperties.NONE;
    }

    BlockStateMatcher(final Block block,
                      final Map<Property<?>, Comparable<?>> props) {
        this.block = block;
        this.blockId = Registry.BLOCK.getId(block);
        this.props = props.size() > 0 ? new BlockStateProperties(props) : BlockStateProperties.NONE;
    }

    public static BlockStateMatcher asGeneric(final BlockState state) {
        return new BlockStateMatcher(state.getBlock());
    }

    public static BlockStateMatcher create(final BlockState state) {
        return new BlockStateMatcher(state);
    }

    public static BlockStateMatcher create(final Block block) {
        return new BlockStateMatcher(block);
    }

    public static BlockStateMatcher create(final String blockId) throws BlockStateParseException {
        return create(BlockStateParser.parse(blockId));
    }

    private static BlockStateMatcher create(final BlockStateParser.ParseResult result) throws BlockStateParseException {
        final Block block = result.getBlock();
        final BlockState defaultState = block.getDefaultState();
        final StateManager<Block, BlockState> container = block.getStateManager();
        if (container.getStates().size() == 1) {
            // Easy case - it's always an identical match because there are no other properties
            return new BlockStateMatcher(defaultState);
        }

        if (!result.hasProperties()) {
            // No property specification so this is a generic
            return new BlockStateMatcher(block);
        }

        final Map<String, String> properties = result.getProperties();
        final Map<Property<?>, Comparable<?>> props = new IdentityHashMap<>(properties.size());

        // Blow out the property list
        for (final Map.Entry<String, String> entry : properties.entrySet()) {
            final String s = entry.getKey();
            final Property<?> prop = container.getProperty(s);
            if (prop != null) {
                final Optional<?> optional = prop.parse(entry.getValue());
                if (optional.isPresent()) {
                    props.put(prop, (Comparable<?>) optional.get());
                } else {
                    var msg = String.format("Value '%s' for property '%s' not found for block '%s'", entry.getValue(), s, result.getBlockName());
                    throw new BlockStateParseException(msg);
                }
            } else {
                var msg = String.format("Property %s not found for block %s", s, result.getBlockName());
                throw new BlockStateParseException(msg);
            }
        }

        return new BlockStateMatcher(defaultState.getBlock(), props);
    }

    public boolean isEmpty() {
        return this.block == Blocks.AIR || this.block == Blocks.CAVE_AIR || this.block == Blocks.VOID_AIR;
    }

    public Block getBlock() {
        return this.block;
    }

    @Override
    public int hashCode() {
        // Only do the block hash code.  Reason is that BlockStateMatcher does not honor the equality contract set
        // forth by Object.  Equals can perform a partial match.
        return this.block.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj instanceof final BlockStateMatcher m) {
            return this.block == m.block && m.props.matches(this.props);
        }
        return false;
    }

    @Override
    public String toString() {
        return this.blockId + this.props.getFormattedProperties();
    }

}