package org.orecruncher.dsurround.gui.sound;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.gui.ToggleButton;

public class BlockButton extends ToggleButton {

    // These are 20x20 sprites
    private static final ResourceLocation BLOCK_ON_SYMBOL = Constants.asId("controls/block_on");
    private static final ResourceLocation BLOCK_OFF_SYMBOL = Constants.asId("controls/block_off");

    public BlockButton(boolean initialState, OnPress onPress) {
        super(initialState, BLOCK_ON_SYMBOL, BLOCK_OFF_SYMBOL, onPress);
    }
}