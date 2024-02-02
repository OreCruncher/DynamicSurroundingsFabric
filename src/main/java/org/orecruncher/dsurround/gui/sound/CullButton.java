package org.orecruncher.dsurround.gui.sound;

import net.minecraft.resources.ResourceLocation;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.gui.ToggleButton;

public class CullButton extends ToggleButton {

    // These are 20x20 sprites
    private static final ResourceLocation CULL_ON_SYMBOL = new ResourceLocation(Library.MOD_ID, "textures/gui/sprites/controls/cull_on.png");
    private static final ResourceLocation CULL_OFF_SYMBOL = new ResourceLocation(Library.MOD_ID, "textures/gui/sprites/controls/cull_off.png");

    public CullButton(boolean initialState, OnPress onPress) {
        super(initialState, CULL_ON_SYMBOL, CULL_OFF_SYMBOL, onPress);
    }
}
