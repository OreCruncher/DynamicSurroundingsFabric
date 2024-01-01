package org.orecruncher.dsurround.gui.hud.plugins;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.gui.hud.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class BlockViewerPlugin implements IDiagnosticPlugin {

    private static final String COLOR = ChatFormatting.AQUA.toString();
    private static final String COLOR_TITLE = COLOR + ChatFormatting.UNDERLINE;

    private final IBlockLibrary blockLibrary;
    private final ITagLibrary tagLibrary;

    public BlockViewerPlugin(IBlockLibrary blockLibrary, ITagLibrary tagLibrary) {
        this.blockLibrary = blockLibrary;
        this.tagLibrary = tagLibrary;
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect);
    }

    private void processBlockHitResult(String type, Level world, BlockHitResult result, Collection<String> data) {
        if (result.getType() != HitResult.Type.BLOCK)
            return;

        data.add(Strings.EMPTY);
        data.add(COLOR_TITLE + type);

        var state = world.getBlockState(result.getBlockPos());
        data.add(state.toString());

        this.tagLibrary.streamTags(state.getBlockHolder())
            .map(tag -> {
                var formatting = ChatFormatting.YELLOW;
                if (Objects.equals(tag.location().getNamespace(), Constants.MOD_ID))
                    formatting = ChatFormatting.GOLD;
                return formatting + "#" + tag.location();
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

        var blockHit = (BlockHitResult)entity.pick(20.0D, 0.0F, false);
        var fluidHit = (BlockHitResult)entity.pick(20.0D, 0.0F, true);

        processBlockHitResult("Block", entity.level(), blockHit, event.right);

        if (!blockHit.getBlockPos().equals(fluidHit.getBlockPos()))
            processBlockHitResult("Fluid", entity.level(), fluidHit, event.right);
    }
}
