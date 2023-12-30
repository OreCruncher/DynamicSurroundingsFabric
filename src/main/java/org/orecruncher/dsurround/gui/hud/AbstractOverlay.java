package org.orecruncher.dsurround.gui.hud;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

public abstract class AbstractOverlay {

    public AbstractOverlay() {

    }

    public abstract void render(DrawContext context);

    public void tick(MinecraftClient client) {

    }

}
