package org.orecruncher.dsurround.runtime.diagnostics;

import joptsimple.internal.Strings;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.orecruncher.dsurround.config.BlockLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.math.TimerEMA;

import java.util.Arrays;
import java.util.Collection;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class BlockViewer {

    private static final String COLOR = Formatting.AQUA.toString();
    private static final String COLOR_TITLE = COLOR + Formatting.UNDERLINE;

    public static void register() {
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(BlockViewer::onCollect);
    }

    private static void processBlockHitResult(String type, World world, BlockHitResult result, Collection<String> data) {
        if (result.getType() != HitResult.Type.BLOCK)
            return;

        data.add(Strings.EMPTY);
        data.add(COLOR_TITLE + type);

        var state = world.getBlockState(result.getBlockPos());
        data.add(state.getBlock().toString());

        var info = BlockLibrary.getBlockInfo(state);
        var wallOfText = info.toString();
        var lines = Arrays.stream(wallOfText.split("\n"))
                .map(l -> l.replace("[", "").replace("]", "").strip())
                .filter(s -> !Strings.isNullOrEmpty(s)).toList();

        data.addAll(lines);
    }

    private static void onCollect(Collection<String> left, Collection<String> right, Collection<TimerEMA> timerEMAS) {
        // Get the block info from the normal diagnostics
        Entity entity = GameUtils.getMC().getCameraEntity();
        if (entity == null)
            return;

        var blockHit = (BlockHitResult)entity.raycast(20.0D, 0.0F, false);
        var fluidHit = (BlockHitResult)entity.raycast(20.0D, 0.0F, true);

        processBlockHitResult("Block", entity.getEntityWorld(), blockHit, right);

        if (!blockHit.getBlockPos().equals(fluidHit.getBlockPos()))
            processBlockHitResult("Fluid", entity.getEntityWorld(), fluidHit, right);
    }
}
