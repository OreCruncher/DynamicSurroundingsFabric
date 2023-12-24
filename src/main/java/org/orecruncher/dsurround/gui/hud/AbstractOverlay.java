package org.orecruncher.dsurround.gui.hud;

import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

@Environment(EnvType.CLIENT)
public abstract class AbstractOverlay {

    public AbstractOverlay() {

    }

    public abstract void render(DrawContext context);

    public void tick(MinecraftClient client) {

    }

}
