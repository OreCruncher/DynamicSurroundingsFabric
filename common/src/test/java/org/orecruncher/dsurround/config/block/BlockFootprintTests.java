package org.orecruncher.dsurround.config.block;

import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.Test;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.tags.BlockEffectTags;
import org.orecruncher.dsurround.testing.Fakes;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for which blocks take footprints: those in the footprints tag, and those that sound soft underfoot.
 */
public class BlockFootprintTests {

    static {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static final ITagLibrary NO_TAGS = Fakes.of(ITagLibrary.class, Map.of("is", args -> false));

    private static boolean takes(BlockState state) {
        return BlockInfoBuilder.takesFootprints(state, NO_TAGS);
    }

    @Test
    void softGroundTakesPrintsByItsSound() {
        assertTrue(takes(Blocks.SAND.defaultBlockState()));
        assertTrue(takes(Blocks.RED_SAND.defaultBlockState()));
        assertTrue(takes(Blocks.GRAVEL.defaultBlockState()));
        assertTrue(takes(Blocks.SNOW.defaultBlockState()));
        assertTrue(takes(Blocks.SNOW_BLOCK.defaultBlockState()));
        assertTrue(takes(Blocks.MUD.defaultBlockState()));
        assertTrue(takes(Blocks.SOUL_SAND.defaultBlockState()));
        assertTrue(takes(Blocks.SUSPICIOUS_SAND.defaultBlockState()));
    }

    @Test
    void hardGroundDoesNot() {
        assertFalse(takes(Blocks.STONE.defaultBlockState()));
        assertFalse(takes(Blocks.OAK_PLANKS.defaultBlockState()));
        assertFalse(takes(Blocks.ICE.defaultBlockState()));
        assertFalse(takes(Blocks.AIR.defaultBlockState()));
    }

    @Test
    void theTagAddsBlocksThatSoundHard() {
        // Grass and dirt sound like grass and gravel's cousin; the tag brings them in
        var tags = Fakes.of(ITagLibrary.class, Map.of("is", args ->
                args[0] == BlockEffectTags.FOOTPRINTS && args[1] instanceof BlockState s && s.is(Blocks.GRASS_BLOCK)));
        assertFalse(takes(Blocks.GRASS_BLOCK.defaultBlockState()));
        assertTrue(BlockInfoBuilder.takesFootprints(Blocks.GRASS_BLOCK.defaultBlockState(), tags));
    }
}
