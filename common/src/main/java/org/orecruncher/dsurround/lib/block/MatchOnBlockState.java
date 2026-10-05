package org.orecruncher.dsurround.lib.block;

import net.minecraft.world.level.block.state.BlockState;

/**
 * Matches the states of one block that have particular property values. Properties not listed can have any value.
 */
final class MatchOnBlockState extends MatchOnBlock {

    private final BlockStateProperties props;

    /**
     * Matches exactly this state: every one of its properties must match.
     */
    MatchOnBlockState(BlockState state) {
        this(state, new BlockStateProperties(state));
    }

    MatchOnBlockState(BlockState state, BlockStateProperties props) {
        super(state.getBlock());
        this.props = props;
    }

    @Override
    public boolean match(BlockState state) {
        return super.match(state) && this.props.matches(state);
    }

    @Override
    public String toSpecification() {
        return super.toSpecification() + this.props.getFormattedProperties();
    }

    @Override
    public int hashCode() {
        return 31 * super.hashCode() + this.props.hashCode();
    }

    /**
     * Equal to another MatchOnBlockState for the same block with exactly the same property values.
     */
    @Override
    public boolean equals(final Object obj) {
        return super.equals(obj) && this.props.equals(((MatchOnBlockState) obj).props);
    }
}
