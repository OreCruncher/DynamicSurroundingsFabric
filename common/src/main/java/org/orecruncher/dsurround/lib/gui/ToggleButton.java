package org.orecruncher.dsurround.lib.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;

public abstract class ToggleButton extends Button {

    private final Identifier onSprite;
    private final Identifier offSprite;

    private boolean isOn;

    protected ToggleButton(boolean initialState, Identifier onSprite, Identifier offSprite, OnPress onPress) {
        super(0, 0, 20, 20, Component.empty(), onPress, DEFAULT_NARRATION);

        this.isOn = initialState;
        this.onSprite = onSprite;
        this.offSprite = offSprite;
    }

    public void setOn(boolean flag) {
        this.isOn = flag;
    }

    public boolean toggle() {
        return this.isOn = !this.isOn;
    }

    // Basically what ImageButton does but simplified.
    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int i, int j, float f) {
        Identifier resourceLocation = this.getSpriteToRender();
        guiGraphics.blitSprite(RenderPipelines.GUI_TEXTURED, resourceLocation, this.getX(), this.getY(), this.width, this.height);
    }

    private Identifier getSpriteToRender() {
        return this.isOn ? this.onSprite : this.offSprite;
    }
}
