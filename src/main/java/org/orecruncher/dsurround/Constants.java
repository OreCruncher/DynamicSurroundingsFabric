package org.orecruncher.dsurround;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Set;

/**
 * Values that are constant throughout the mod source.
 */
public final class Constants {

    /**
     * The ID of the current mod
     */
    public static String MOD_ID = "dsurround";


    /**
     * Blocks that will be ignored by the system during configuration
     * and processing.
     */
    public static final Set<Block> BLOCKS_TO_IGNORE = new ReferenceOpenHashSet<>(5);

    static {
        BLOCKS_TO_IGNORE.add(Blocks.VOID_AIR);
        BLOCKS_TO_IGNORE.add(Blocks.CAVE_AIR);
        BLOCKS_TO_IGNORE.add(Blocks.AIR);
        BLOCKS_TO_IGNORE.add(Blocks.BARRIER);
        BLOCKS_TO_IGNORE.add(Blocks.COMMAND_BLOCK);
    }

}
