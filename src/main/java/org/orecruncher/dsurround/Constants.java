package org.orecruncher.dsurround;

import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.HashSet;
import java.util.Set;

/**
 * Values that are constant throughout the mod source.
 */
public final class Constants {

    /**
     * The ID of the current mod
     */
    public static final String MOD_ID = "dsurround";

    /**
     * ID's of mods that Dynamic Surroundings has an interest for disabling certain features.
     */
    // Brush step sound effect
    public static final String MOD_PRESENCE_FOOTSTEPS = "presencefootsteps";
    // Enhanced sound processing.  Check AudioUtilities.java as well.
    public static final String MOD_SOUND_PHYSICS_REMASTERED = "sound_physics_remastered";
    public static final String CLOTH_CONFIG = "cloth-config";
    public static final String YACL = "yet_another_config_lib_v3";

    /**
     * Collection of MOD IDs that are of interest.  Ease of iteration.
     */
    public static final Set<String> SPECIAL_MODS = new HashSet<>();


    /**
     * Blocks that will be ignored by the system during configuration
     * and processing.
     */
    public static final Set<Block> BLOCKS_TO_IGNORE = new ReferenceOpenHashSet<>(5);

    static {
        SPECIAL_MODS.add(MOD_PRESENCE_FOOTSTEPS);
        SPECIAL_MODS.add(MOD_SOUND_PHYSICS_REMASTERED);
        SPECIAL_MODS.add(CLOTH_CONFIG);
        SPECIAL_MODS.add(YACL);

        BLOCKS_TO_IGNORE.add(Blocks.VOID_AIR);
        BLOCKS_TO_IGNORE.add(Blocks.CAVE_AIR);
        BLOCKS_TO_IGNORE.add(Blocks.AIR);
        BLOCKS_TO_IGNORE.add(Blocks.BARRIER);
        BLOCKS_TO_IGNORE.add(Blocks.COMMAND_BLOCK);
    }

}
