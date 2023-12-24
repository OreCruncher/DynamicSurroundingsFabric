package org.orecruncher.dsurround.lib.platform;

import net.minecraft.client.MinecraftClient;

import java.util.function.Consumer;

public interface IEventRegistrations {

    void register();

    void registerClientTickStart(Consumer<MinecraftClient> handler);

    void registerClientTickEnd(Consumer<MinecraftClient> handler);
}
