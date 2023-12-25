package org.orecruncher.dsurround.platform.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.argument.IdentifierArgumentType;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.util.Identifier;

import org.orecruncher.dsurround.commands.BiomeCommandHandler;
import org.orecruncher.dsurround.lib.commands.client.ClientCommand;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;

class BiomeCommand extends ClientCommand {

    BiomeCommand() {
        super("dsbiome");
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                    // TODO: Figure out how to have it look up in the biome registry similar to locate command.  Registry
                    // access appears to be missing client side.
                    .then(argument("biomeId", IdentifierArgumentType.identifier())
                    .then(argument("script", MessageArgumentType.message()).executes(this::execute))));
    }

    public int execute(CommandContext<FabricClientCommandSource> ctx) {
        var biomeId = ctx.getArgument("biomeId", Identifier.class);
        var script = ctx.getArgument("script", MessageArgumentType.MessageFormat.class);
        return this.execute(ctx, () -> BiomeCommandHandler.execute(biomeId, script.getContents()));
    }
}
