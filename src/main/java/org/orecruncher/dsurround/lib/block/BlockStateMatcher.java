package org.orecruncher.dsurround.lib.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.state.StateManager;
import net.minecraft.state.property.Property;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;

import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Optional;

public abstract class BlockStateMatcher implements IMatcher<BlockState> {

    public static final Codec<IMatcher<BlockState>> CODEC = Codec.STRING
            .comapFlatMap(
                    BlockStateMatcher::manifest,
                    IMatcher::toString).stable();

    public static final String TAG_TYPE = "#";

    private static DataResult<IMatcher<BlockState>> manifest(String blockId) {
        try {
            return DataResult.success(create(blockId, true, true));
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
        }
    }

    public static IMatcher<BlockState> asGeneric(final BlockState state) {
        return create(state.getBlock());
    }

    public static IMatcher<BlockState> create(final BlockState state) {
        return new MatchOnBlockState(state);
    }

    public static IMatcher<BlockState> create(final Block block) {
        return new MatchOnBlock(block);
    }

    public static IMatcher<BlockState> create(final String blockId) throws BlockStateParseException {
        return create(blockId, true, true);
    }

    public static IMatcher<BlockState> create(final String blockId, boolean allowTags, boolean allowMaterials) throws BlockStateParseException {
        if (blockId.startsWith(TAG_TYPE))
            if (allowTags)
                return createTagMatcher(blockId);
            else
                throw new BlockStateParseException(String.format("Block id %s is for a tag, and it is not permitted in this context", blockId));
        return createBlockStateMatcher(BlockStateParser.parse(blockId));
    }

    private static BlockStateMatcher createTagMatcher(String tagId) throws BlockStateParseException {
        try {
            var id = IdentityUtils.resolveIdentifier(Constants.MOD_ID, tagId);
            return new MatchOnBlockTag(id);
        } catch (Throwable ignored) {
            throw new BlockStateParseException(String.format("%s is not a valid block tag", tagId));
        }
    }

    private static BlockStateMatcher createBlockStateMatcher(final BlockStateParser.ParseResult result) throws BlockStateParseException {
        final Block block = result.getBlock();
        final BlockState defaultState = block.getDefaultState();
        final StateManager<Block, BlockState> container = block.getStateManager();
        if (container.getStates().size() == 1) {
            // Easy case - it's always an identical match because there are no other properties
            return new MatchOnBlock(defaultState.getBlock());
        }

        if (!result.hasProperties()) {
            // No property specification so this is a generic
            return new MatchOnBlock(block);
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

        return new MatchOnBlockState(defaultState, new BlockStateProperties(props));
    }

    public abstract boolean isEmpty();

    public abstract boolean match(BlockState state);

}