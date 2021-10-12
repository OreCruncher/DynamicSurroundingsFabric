package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.BlockState;

public class MatchOnBlockState extends MatchOnBlock {

    // Sometimes an exact match of state is needed. The state being compared
    // would have to match all these properties.
    private final BlockStateProperties props;

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
    public boolean equals(final Object obj) {
        if (obj instanceof final MatchOnBlockState m) {
            return super.equals(obj) && m.props.matches(this.props);
        }
        return false;
    }

    @Override
    public String toString() {
        return super.toString() + this.props.getFormattedProperties();
    }
}
