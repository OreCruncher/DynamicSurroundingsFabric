package org.orecruncher.dsurround.lib.platform;

import net.minecraft.client.Minecraft;

import java.util.function.Consumer;

public interface IClientEventRegistrations {

    void register();

    void registerClientTickStart(Consumer<Minecraft> handler);

    void registerClientTickEnd(Consumer<Minecraft> handler);
}
