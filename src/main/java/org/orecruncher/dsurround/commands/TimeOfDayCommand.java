package org.orecruncher.dsurround.commands;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.text.Text;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.MinecraftClock;

@Environment(EnvType.CLIENT)
public class TimeOfDayCommand {
    public static void register(@Nullable CommandDispatcher<FabricClientCommandSource> dispatcher) {
        if (dispatcher == null) {
            return;
        }
        dispatcher.register(
                ClientCommandManager.literal("dstod").executes(TimeOfDayCommand::execute));
    }

    private static int execute(CommandContext<FabricClientCommandSource> ctx) {
        var calendar = new MinecraftClock();
        calendar.update(GameUtils.getWorld());
        ctx.getSource().sendFeedback(Text.of(calendar.getFormattedTime()));
        return 0;
    }

}
