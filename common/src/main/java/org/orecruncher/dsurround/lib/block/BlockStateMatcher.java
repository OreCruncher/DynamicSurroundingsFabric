package org.orecruncher.dsurround.lib.block;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.Property;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Matches block states against a specification from a config file:
 * <ul>
 *   <li>{@code minecraft:stone}: every state of the block</li>
 *   <li>{@code minecraft:oak_log[axis=y]}: states of the block with those property values (others can be
 *       anything)</li>
 *   <li>{@code #minecraft:logs}: every block in the tag. A tag without a namespace is one of this mod's tags
 *       ({@code #logs} means {@code #dsurround:logs}); a block without one is a vanilla block.</li>
 * </ul>
 * {@link #toSpecification()} gives the specification back in a form that parses to an equal matcher, and the codec
 * uses it when writing.
 */
public abstract class BlockStateMatcher implements IMatcher<BlockState> {

    public static final Codec<IMatcher<BlockState>> CODEC = Codec.STRING
            .comapFlatMap(
                    BlockStateMatcher::manifest,
                    BlockStateMatcher::specificationOf).stable();

    public static final String TAG_TYPE = "#";

    private static DataResult<IMatcher<BlockState>> manifest(String specification) {
        try {
            return DataResult.success(create(specification, true));
        } catch (BlockStateParseException e) {
            return DataResult.error(e::getMessage);
        } catch (RuntimeException e) {
            return DataResult.error(() -> String.format("Unable to parse '%s': %s", specification, e));
        }
    }

    private static String specificationOf(IMatcher<BlockState> matcher) {
        return matcher instanceof BlockStateMatcher m ? m.toSpecification() : matcher.toString();
    }

    /**
     * Creates a matcher from a specification (see the class description).
     *
     * @param allowTags whether a tag ({@code #...}) is acceptable here
     * @throws BlockStateParseException with a message saying what is wrong with the specification
     */
    static BlockStateMatcher create(final String specification, final boolean allowTags) throws BlockStateParseException {
        final String text = specification.trim();
        if (text.startsWith(TAG_TYPE)) {
            if (!allowTags)
                throw new BlockStateParseException(String.format("'%s' is a tag, and tags are not permitted here", specification));
            return createTagMatcher(text);
        }
        return createBlockStateMatcher(BlockStateParser.parse(text));
    }

    private static BlockStateMatcher createTagMatcher(String tagId) throws BlockStateParseException {
        try {
            var id = IdentityUtils.resolveIdentifier(Constants.MOD_ID, tagId);
            return new MatchOnBlockTag(id);
        } catch (Exception e) {
            throw new BlockStateParseException(String.format("'%s' is not a valid block tag", tagId));
        }
    }

    private static BlockStateMatcher createBlockStateMatcher(final BlockStateParser.ParseResult result) throws BlockStateParseException {
        final Block block = result.block();
        final BlockState defaultState = block.defaultBlockState();
        final StateDefinition<Block, BlockState> container = block.getStateDefinition();

        // No properties given: every state of the block
        if (!result.hasProperties())
            return new MatchOnBlock(block);

        // Properties given are always checked, so a typo is reported even for a block with a single state (which has
        // no properties at all)

        // Kept in the order written, so toSpecification() reproduces it
        final Map<Property<?>, Comparable<?>> props = new LinkedHashMap<>();
        for (final Map.Entry<String, String> entry : result.properties().entrySet()) {
            final String name = entry.getKey();
            final Property<?> prop = container.getProperty(name);
            if (prop == null)
                throw new BlockStateParseException(String.format("Property '%s' not found for block '%s'", name, result.blockName()));
            final Optional<?> value = prop.getValue(entry.getValue());
            if (value.isEmpty())
                throw new BlockStateParseException(String.format("Value '%s' for property '%s' not found for block '%s'", entry.getValue(), name, result.blockName()));
            props.put(prop, (Comparable<?>) value.get());
        }

        return new MatchOnBlockState(defaultState, new BlockStateProperties(props));
    }

    /**
     * The specification for this matcher, in the form the config files use. Parsing it gives an equal matcher.
     */
    public abstract String toSpecification();

    /**
     * True if this matcher matches by block tag.
     */
    public boolean isTagMatcher() {
        return false;
    }

    @Override
    public abstract boolean match(BlockState state);

    @Override
    public String toString() {
        return this.toSpecification();
    }
}
