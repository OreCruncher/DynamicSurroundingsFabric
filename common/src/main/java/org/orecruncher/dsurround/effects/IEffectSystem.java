package org.orecruncher.dsurround.effects;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

import java.util.function.Consumer;
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

    // ---- For showing tracked effects in the world (DiagnosticsOverlay's EFFECTS mode) -----------------------

    /**
     * The system's name, as shown in diagnostics.
     */
    default String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * The color the system's effects are shown in, as RGB.
     */
    default int getDiagnosticColor() {
        return 0xA0A0A0;
    }

    /**
     * Calls {@code consumer} with each effect the system is tracking. Systems that don't track effects by position
     * do nothing.
     */
    default void forEachEffect(Consumer<IBlockEffect> consumer) {
    }

    /**
     * Adds lines describing {@code effect} (one of this system's) for showing next to it, after its system's name.
     */
    default void describeEffect(IBlockEffect effect, Consumer<String> lines) {
    }
}
