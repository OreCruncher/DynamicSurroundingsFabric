package org.orecruncher.dsurround.lib.block;

import net.minecraft.block.BlockState;
import net.minecraft.block.Material;

public class MatchOnMaterial extends BlockStateMatcher {

    private final Material material;

    MatchOnMaterial(Material material) {
        this.material = material;
    }

    @Override
    public boolean isEmpty() {
        return false;
    }

    @Override
    public boolean match(BlockState state) {
        return state.getMaterial() == this.material;
    }
}
