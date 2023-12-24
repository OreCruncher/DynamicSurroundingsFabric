package org.orecruncher.dsurround.gui.hud.plugins;

import joptsimple.internal.Strings;
import net.minecraft.entity.Entity;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.world.World;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class BlockViewerPlugin implements IDiagnosticPlugin {

    private static final String COLOR = Formatting.AQUA.toString();
    private static final String COLOR_TITLE = COLOR + Formatting.UNDERLINE;

    private final IBlockLibrary blockLibrary;

    public BlockViewerPlugin(IBlockLibrary blockLibrary) {
        this.blockLibrary = blockLibrary;
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect);
    }

    private void processBlockHitResult(String type, World world, BlockHitResult result, Collection<String> data) {
        if (result.getType() != HitResult.Type.BLOCK)
            return;

        data.add(Strings.EMPTY);
        data.add(COLOR_TITLE + type);

        var state = world.getBlockState(result.getBlockPos());
        data.add(state.toString());

        // TODO:  These tags are from the server.  Does not cover cases of dsurround.
        state.streamTags()
            .map(tag -> {
                var formatting = Formatting.YELLOW;
                if (Objects.equals(tag.id().getNamespace(), Client.ModId))
                    formatting = Formatting.GOLD;
                return formatting + "#" + tag.id().toString();
            })
            .sorted()
            .forEach(data::add);

        var info = this.blockLibrary.getBlockInfo(state);
        var wallOfText = info.toString();
        Arrays.stream(wallOfText.split("\n"))
            .map(l -> l.replaceAll("[\\[\\]]", "").strip())
            .filter(s -> !Strings.isNullOrEmpty(s))
            .forEach(data::add);
    }

    public void onCollect(ClientEventHooks.CollectDiagnosticsEvent event) {
        // Get the block info from the normal diagnostics
        Entity entity = GameUtils.getMC().getCameraEntity();
        if (entity == null)
            return;

        var blockHit = (BlockHitResult)entity.raycast(20.0D, 0.0F, false);
        var fluidHit = (BlockHitResult)entity.raycast(20.0D, 0.0F, true);

        processBlockHitResult("Block", entity.getEntityWorld(), blockHit, event.right);

        if (!blockHit.getBlockPos().equals(fluidHit.getBlockPos()))
            processBlockHitResult("Fluid", entity.getEntityWorld(), fluidHit, event.right);
    }
}
