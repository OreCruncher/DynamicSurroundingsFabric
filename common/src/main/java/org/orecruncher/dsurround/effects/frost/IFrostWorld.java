package org.orecruncher.dsurround.effects.frost;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What frost needs to know of the world: in the game, the client's level; in tests, a fake
 */
public interface IFrostWorld {

    BlockGetter blocks();

    /**
     * Whether a block is glass that frosts
     */
    boolean frosts(BlockState state);

    /**
     * Whether a block is drawn with the translucent blocks (stained glass), not cut out as clear glass is
     */
    default boolean translucent(BlockState state) {
        return false;
    }

    /**
     * Whether nothing above a position stops rain or snow falling to it: no roof, glass included, and no tree
     */
    boolean seesSky(BlockPos pos);

    /**
     * The sky light at a position, 0 to 15
     */
    int skyLight(BlockPos pos);

    /**
     * The packed light at a position, as the lightmap takes it
     */
    int packedLight(BlockPos pos);

    /**
     * The temperature at a position, as the game's (a snowy plain is 0, a plain 0.8), with the season's
     */
    float temperature(BlockPos pos);
}
