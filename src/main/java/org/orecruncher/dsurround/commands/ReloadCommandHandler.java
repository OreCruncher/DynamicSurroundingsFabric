package org.orecruncher.dsurround.commands;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;

public class ReloadCommandHandler {

    public static Component execute() {
        try {
            AssetLibraryEvent.RELOAD.raise().onReload(IReloadEvent.Scope.ALL);
            return Component.translatable("dsurround.command.dsreload.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsreload.failure", t.getMessage());
        }
    }
}
