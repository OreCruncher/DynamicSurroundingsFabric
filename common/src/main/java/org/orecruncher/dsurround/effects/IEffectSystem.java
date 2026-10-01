package org.orecruncher.dsurround.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Predicate;

public interface IEffectSystem {

    /**
     * Performs lifecycle operations for systems
     */
    void tick(Predicate<IBlockEffect> processingPredicate);

    /**
     * Indicates the effect system is enabled for processing
     */
    boolean isEnabled();

    /**
     * Whether {@link #blockScan} does anything. Systems that find their blocks some other way return false, so the
     * scanner doesn't call them for every block in range.
     */
    default boolean wantsBlockScans() {
        return true;
    }

    /**
     * Invoked when a new block comes into the scan area
     */
    void blockScan(Level world, BlockState state, BlockPos pos);

    /**
     * Invoked when a block in the scan area changed (the client received a block update), as opposed to coming into
     * range. Systems that find their blocks indirectly, from a neighbor, can use this to also check the changed
     * block itself. Defaults to {@link #blockScan}.
     */
    default void blockUpdated(Level world, BlockState state, BlockPos pos) {
        this.blockScan(world, state, pos);
    }

    /**
     * Invoked when a block position leaves the scan area
     */
    void blockUnscan(Level world, BlockState state, BlockPos pos);

    /**
     * Invoked when the system should clear because the area scanner reset
     */
    void clear();

    /**
     * Invoked when diagnostic information about the system is requested
     */
    String gatherDiagnostics();

}
