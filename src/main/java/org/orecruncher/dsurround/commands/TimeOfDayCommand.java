package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v1.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v1.FabricClientCommandSource;
import net.minecraft.text.LiteralText;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;

@Environment(EnvType.CLIENT)
public class TimeOfDayCommand {
    public static void register(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(
                ClientCommandManager.literal("dstod").executes(TimeOfDayCommand::execute));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        var calendar = new MinecraftClock();
        calendar.update(GameUtils.getWorld());
        ctx.getSource().sendFeedback(new LiteralText(calendar.getFormattedTime()));
        return 0;
    }

}
