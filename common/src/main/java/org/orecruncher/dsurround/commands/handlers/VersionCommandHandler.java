package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.version.IVersionChecker;

public class VersionCommandHandler {
    public static Component execute() {
        try {
            var versionChecker = ContainerManager.resolve(IVersionChecker.class);
            var versionQueryResult = versionChecker.getVersionResult();

            if (versionQueryResult.isPresent()) {
                var result = versionQueryResult.get();
                return result.getChatText();
            }
            return Component.literal("Version information is not available");
        } catch (Throwable t) {
            return Component.translatable("dsurround.command.dsversion.failure", t.getMessage());
        }
    }
}
