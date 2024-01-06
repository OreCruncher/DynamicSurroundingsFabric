package org.orecruncher.dsurround.commands;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;

public class ReloadCommandHandler {

    public static Component execute() {
        try {
            AssetLibraryEvent.reload();
            return Component.translatable("dsurround.command.dsreload.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsreload.failure", t.getMessage());
        }
    }
}
