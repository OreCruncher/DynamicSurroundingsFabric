package org.orecruncher.dsurround.gui.overlay.plugins;

import joptsimple.internal.Strings;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;

public class ViewerPlugin implements IDiagnosticPlugin {

    private final IBlockLibrary blockLibrary;
    private final ITagLibrary tagLibrary;
    private final IEntityEffectLibrary entityEffectLibrary;

    public ViewerPlugin(IBlockLibrary blockLibrary, ITagLibrary tagLibrary, IEntityEffectLibrary entityEffectLibrary) {
        this.blockLibrary = blockLibrary;
        this.tagLibrary = tagLibrary;
        this.entityEffectLibrary = entityEffectLibrary;
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect);
    }

    private void processBlockHitResult(Level world, BlockHitResult result, Collection<String> data) {
        if (result.getType() != HitResult.Type.BLOCK)
            return;

        var state = world.getBlockState(result.getBlockPos());
        data.add(state.toString());

        this.processTags(state.getBlockHolder(), data);
        this.processTags(state.getFluidState().holder(), data);

        var info = this.blockLibrary.getBlockInfo(state);
        var wallOfText = info.toString();
        Arrays.stream(wallOfText.split("\n"))
            .map(l -> l.replaceAll("[\\[\\]]", "").strip())
            .filter(s -> !Strings.isNullOrEmpty(s))
            .forEach(data::add);
    }

    private void processEntityHitResult(Entity entity, Collection<String> data) {

        data.add(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType())));

        var holderResult = RegistryUtils.getRegistryEntry(Registries.ENTITY_TYPE, entity.getType());
        if (holderResult.isEmpty())
            return;

        this.processTags(holderResult.get(), data);

        if (entity instanceof LivingEntity le) {
            var info = this.entityEffectLibrary.getEntityEffectInfo(le);
            if (info.isDefault()) {
                data.add("Default Effects");
            } else {
                info.getEffects().forEach(effect -> data.add(effect.toString()));
            }
        } else {
            data.add("Not a LivingEntity");
        }
    }

    private <T> void processTags(Holder<T> holder, Collection<String> data) {
        this.tagLibrary.streamTags(holder)
                .map(tag -> {
                    var txt = "#" + tag.location();
                    if (Objects.equals(tag.location().getNamespace(), Constants.MOD_ID))
                        txt = ChatFormatting.GOLD + txt;
                    return txt;
                })
                .sorted()
                .forEach(data::add);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        // Get the block info from the normal diagnostics
        Entity entity = GameUtils.getMC().getCameraEntity();
        if (entity == null)
            return;

        var blockHit = (BlockHitResult)entity.pick(20.0D, 0.0F, false);
        var fluidHit = (BlockHitResult)entity.pick(20.0D, 0.0F, true);
        var entityHit = GameUtils.getMC().crosshairPickEntity;

        var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.BlockView);
        processBlockHitResult(entity.level(), blockHit, panelText);

        if (!blockHit.getBlockPos().equals(fluidHit.getBlockPos())) {
            panelText = event.getSectionText(CollectDiagnosticsEvent.Section.FluidView);
            processBlockHitResult(entity.level(), fluidHit, panelText);
        }

        if (entityHit != null) {
            panelText = event.getSectionText(CollectDiagnosticsEvent.Section.EntityView);
            processEntityHitResult(entityHit, panelText);
        }
    }
}
