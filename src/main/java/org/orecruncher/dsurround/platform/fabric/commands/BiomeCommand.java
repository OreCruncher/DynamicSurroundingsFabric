package org.orecruncher.dsurround.platform.fabric.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.command.argument.MessageArgumentType;
import net.minecraft.command.argument.RegistryEntryArgumentType;
import net.minecraft.registry.BuiltinRegistries;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.server.command.CommandManager;

import net.minecraft.world.biome.Biome;
import org.orecruncher.dsurround.commands.BiomeCommandHandler;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommandManager.literal;
import static net.minecraft.command.argument.RegistryEntryArgumentType.INVALID_TYPE_EXCEPTION;

class BiomeCommand extends ClientCommand {

    private static final String BIOME_PARAMETER = "biome";
    private static final String SCRIPT_PARAMETER = "script";

    private final CommandRegistryAccess registryAccess;

    BiomeCommand() {
        super("dsbiome");

        // Yeah - this reaches into server side definitions.  But it appears safe...
        this.registryAccess = CommandManager.createRegistryAccess(BuiltinRegistries.createWrapperLookup());
    }

    public void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal(this.command)
                .then(argument(BIOME_PARAMETER, RegistryEntryArgumentType.registryEntry(this.registryAccess, RegistryKeys.BIOME))
                .then(argument(SCRIPT_PARAMETER, MessageArgumentType.message()).executes(this::execute))));
    }

    @SuppressWarnings("unchecked")
    public int execute(CommandContext<FabricClientCommandSource> ctx) throws CommandSyntaxException {
        RegistryEntry.Reference<Biome> reference = ctx.getArgument(BIOME_PARAMETER, RegistryEntry.Reference.class);
        RegistryKey<Biome> registryKey = reference.registryKey();
        if (!registryKey.isOf(RegistryKeys.BIOME)) {
            throw INVALID_TYPE_EXCEPTION.create(registryKey.getValue(), registryKey.getRegistry(), RegistryKeys.BIOME.getValue());
        }

        var script = ctx.getArgument(SCRIPT_PARAMETER, MessageArgumentType.MessageFormat.class);
        return this.execute(ctx, () -> BiomeCommandHandler.execute(registryKey.getValue(), script.getContents()));
    }
}
