package org.orecruncher.dsurround.gui.hud;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

public abstract class AbstractOverlay {

    public AbstractOverlay() {

    }

    public abstract void render(GuiGraphics context);

    public void tick(Minecraft client) {

    }

}
