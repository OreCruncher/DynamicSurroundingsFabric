package org.orecruncher.dsurround.lib.commands.client;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.logging.IModLog;

public abstract class ClientCommand {

    protected static final IModLog LOGGER = Library.getLogger();

    protected final String command;
    private final String resultFormatSuccess;
    private final String resultFormatFailure;

    protected ClientCommand(String command) {
        this.command = command;
        this.resultFormatSuccess = String.format("dsurround.command.%s.success", command);
        this.resultFormatFailure = String.format("dsurround.command.%s.failure", command);
    }

    public abstract void register(CommandDispatcher<FabricClientCommandSource> dispatcher);

    protected void sendSuccess(final FabricClientCommandSource source, Object... args) {
        source.sendFeedback(Text.stringifiedTranslatable(this.resultFormatSuccess, args));
    }

    protected void sendFailure(final FabricClientCommandSource source, Object... args) {
        this.sendFailure2(source, this.resultFormatFailure, args);
    }

    protected void sendFailure2(final FabricClientCommandSource source, String messageFormat, Object... args) {
        source.sendFeedback(Text.stringifiedTranslatable(messageFormat, args));
    }

    protected void send(final FabricClientCommandSource source, Object arg) {
        source.sendFeedback(Text.of(arg.toString()));
    }
}
