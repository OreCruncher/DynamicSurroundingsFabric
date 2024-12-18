package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.config.libraries.AssetLibraryEvent;
import org.orecruncher.dsurround.config.libraries.IReloadEvent;
import org.orecruncher.dsurround.lib.resources.ResourceUtilities;

public class ReloadCommandHandler {

    public static Component execute() {
        try {
            var resourceUtilities = ResourceUtilities.createForCurrentState();
            AssetLibraryEvent.RELOAD.raise().onReload(resourceUtilities, IReloadEvent.Scope.ALL);
            return Component.translatable("dsurround.command.dsreload.success");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsreload.failure", t.getMessage());
        }
    }
}
