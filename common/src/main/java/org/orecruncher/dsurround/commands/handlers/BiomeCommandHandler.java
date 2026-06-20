package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class BiomeCommandHandler {

    public static Component execute(Identifier biomeIdentifier, String script) {
        return Component.literal("The dsbiome command is temporarily disabled in the Minecraft 26.2 compatibility port: " + biomeIdentifier);
    }
}
