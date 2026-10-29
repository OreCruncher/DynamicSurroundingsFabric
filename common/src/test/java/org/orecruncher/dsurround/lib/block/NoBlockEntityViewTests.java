package org.orecruncher.dsurround.lib.block;

import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.entity.BlockEntityTypes;
import net.minecraft.world.level.material.Fluids;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The view sound tracing reads the level through from its worker threads.
 */
public class NoBlockEntityViewTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final BlockPos POS = new BlockPos(3, 64, -7);

    // A level with no answer for getBlockEntity: the fake fails the call, so reaching it fails the test
    private static final BlockGetter LEVEL = Fakes.of(BlockGetter.class, Map.of(
            "getBlockState", args -> args[0].equals(POS) ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState(),
            "getFluidState", args -> Fluids.WATER.defaultFluidState(),
            "getHeight", args -> 384,
            "getMinY", args -> -64));

    @Test
    void neverAsksTheLevelForABlockEntity() {
        var view = new NoBlockEntityView(LEVEL);
        assertNull(view.getBlockEntity(POS));
        assertTrue(view.getBlockEntity(POS, BlockEntityTypes.BELL).isEmpty());
    }

    @Test
    void readsTheLevelsBlocksAndFluids() {
        var view = new NoBlockEntityView(LEVEL);
        assertSame(Blocks.STONE.defaultBlockState(), view.getBlockState(POS));
        assertSame(Blocks.AIR.defaultBlockState(), view.getBlockState(POS.above()));
        assertSame(Fluids.WATER.defaultFluidState(), view.getFluidState(POS));
        assertEquals(384, view.getHeight());
        assertEquals(-64, view.getMinY());
    }

    @Test
    void shapesAreReadThroughTheView() {
        // What tracing asks of a block: its shapes are given the view, so a block that looks up its block entity
        // there gets none
        var view = new NoBlockEntityView(LEVEL);
        assertFalse(view.getBlockState(POS).getCollisionShape(view, POS).isEmpty());
        assertTrue(view.getBlockState(POS.above()).getCollisionShape(view, POS.above()).isEmpty());
    }
}
