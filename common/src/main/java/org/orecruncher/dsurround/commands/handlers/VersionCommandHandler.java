package org.orecruncher.dsurround.commands.handlers;

import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.di.ContainerManager;
import org.orecruncher.dsurround.lib.version.IVersionChecker;
import org.orecruncher.dsurround.lib.version.VersionCheckException;
import org.orecruncher.dsurround.lib.version.VersionResult;

import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class VersionCommandHandler {

    // Longer than at startup: the player asked, so it is worth waiting a little for a slow connection
    private static final int TIMEOUT_SECONDS = 10;

    /**
     * Starts a version check in the background and returns a message saying so. Commands run on the render thread,
     * and the check goes over the network, so the result is posted to chat when it arrives.
     */
    public static Component execute() {
        var versionChecker = ContainerManager.resolve(IVersionChecker.class);
        CompletableFuture
                .supplyAsync(versionChecker::getVersionResult)
                .orTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .handle(VersionCommandHandler::describe)
                .thenAccept(message -> GameUtils.getMC().execute(
                        () -> GameUtils.getPlayer().ifPresent(p -> p.sendSystemMessage(message))));
        return Component.translatable("dsurround.command.dsversion.checking");
    }

    /**
     * The chat message for the outcome of a check: the result, that there is no recommendation for this Minecraft
     * version, or why the check failed.
     */
    static Component describe(@Nullable Optional<VersionResult> result, @Nullable Throwable error) {
        if (error != null) {
            var reason = VersionCheckException.describe(error);
            Library.LOGGER.warn("Unable to check for an update: %s", reason);
            return Component.translatable("dsurround.command.dsversion.failure", reason);
        }
        if (result != null && result.isPresent())
            return result.get().getChatText();
        return Component.translatable("dsurround.command.dsversion.unavailable");
    }
}
