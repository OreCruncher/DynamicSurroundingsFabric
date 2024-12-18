package org.orecruncher.dsurround.gui.overlay.plugins;

import it.unimi.dsi.fastutil.objects.Object2ObjectOpenHashMap;
import joptsimple.internal.Strings;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.orecruncher.dsurround.Configuration;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.config.libraries.IBlockLibrary;
import org.orecruncher.dsurround.config.libraries.IEntityEffectLibrary;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.eventing.ClientEventHooks;
import org.orecruncher.dsurround.eventing.CollectDiagnosticsEvent;
import org.orecruncher.dsurround.gui.overlay.IDiagnosticPlugin;
import org.orecruncher.dsurround.lib.Comparers;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.gui.ColorPalette;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;

import java.util.*;

public class ViewerPlugin implements IDiagnosticPlugin {

    // Mod packs have a lot of tags. We are only interested in
    // tags for the various frameworks and ourselves.
    private static final Object2ObjectOpenHashMap<String, Style> TAG_STYLES = new Object2ObjectOpenHashMap<>();

    static {
        TAG_STYLES.defaultReturnValue(Style.EMPTY.withColor(ColorPalette.GRAY));
        TAG_STYLES.put(Constants.MOD_ID, Style.EMPTY.withColor(ColorPalette.GOLD));
        TAG_STYLES.put("minecraft", Style.EMPTY.withColor(ColorPalette.FRESH_AIR));
        TAG_STYLES.put("forge", Style.EMPTY.withColor(ColorPalette.AQUAMARINE));
        TAG_STYLES.put("fabric", Style.EMPTY.withColor(ColorPalette.CORNSILK));
        TAG_STYLES.put("c", Style.EMPTY.withColor(ColorPalette.CORNSILK));
    }

    private final Configuration.Logging config;
    private final IBlockLibrary blockLibrary;
    private final ITagLibrary tagLibrary;
    private final IEntityEffectLibrary entityEffectLibrary;

    public ViewerPlugin(Configuration.Logging config, IBlockLibrary blockLibrary, ITagLibrary tagLibrary, IEntityEffectLibrary entityEffectLibrary) {
        this.config = config;
        this.blockLibrary = blockLibrary;
        this.tagLibrary = tagLibrary;
        this.entityEffectLibrary = entityEffectLibrary;
        ClientEventHooks.COLLECT_DIAGNOSTICS.register(this::onCollect);
    }

    private void processBlockHitResult(Level world, BlockHitResult result, Collection<Component> data) {
        if (result.getType() != HitResult.Type.BLOCK)
            return;

        var state = world.getBlockState(result.getBlockPos());
        data.add(Component.literal(state.toString()));

        this.processTags(state.getBlockHolder(), data);
        if (!state.getFluidState().isEmpty()) {
            data.add(Component.literal("Fluid Tags"));
            this.processTags(state.getFluidState().holder(), data);
        }

        var info = this.blockLibrary.getBlockInfo(state);
        var wallOfText = info.toString();
        Arrays.stream(wallOfText.split("\n"))
            .map(l -> l.replaceAll("[\\[\\]]", "").strip())
            .filter(s -> !Strings.isNullOrEmpty(s))
            .map(Component::literal)
            .forEach(data::add);
    }

    private void processEntityHitResult(Entity entity, Collection<Component> data) {

        data.add(Component.literal(String.valueOf(BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()))));

        var holderResult = RegistryUtils.getRegistryEntry(Registries.ENTITY_TYPE, entity.getType());
        if (holderResult.isEmpty())
            return;

        this.processTags(holderResult.get(), data);

        if (entity instanceof LivingEntity le) {
            var info = this.entityEffectLibrary.getEntityEffectInfo(le);
            if (info.isDefault()) {
                data.add(Component.literal("Default Effects"));
            } else {
                info.getEffects().forEach(effect -> data.add(Component.literal(effect.toString())));
            }
        } else {
            data.add(Component.literal("Not a LivingEntity"));
        }
    }

    private void processHeldItem(ItemStack stack, Collection<Component> data) {
        if (stack.isEmpty())
            return;
        var holder = stack.getItemHolder();
        holder.unwrapKey().ifPresent(key -> data.add(Component.literal(key.location().toString())));
        this.processTags(holder, data);
    }

    private <T> void processTags(Holder<T> holder, Collection<Component> data) {
        var query = this.tagLibrary.streamTags(holder)
                .map(TagKey::location);

        if (this.config.filteredTagView)
            query = query.filter(loc -> TAG_STYLES.containsKey(loc.getNamespace()));

        query.sorted(Comparers.IDENTIFIER_NATURAL_COMPARABLE)
            .map(l -> {
                var formatting = TAG_STYLES.get(l.getNamespace());
                return Component.literal("#" + l).withStyle(formatting);
            })
            .forEach(data::add);
    }

    public void onCollect(CollectDiagnosticsEvent event) {
        // Get the block info from the normal diagnostics
        Entity entity = GameUtils.getMC().getCameraEntity();
        if (entity == null)
            return;

        if (entity instanceof LivingEntity le) {
            var stack = le.getItemInHand(InteractionHand.MAIN_HAND);
            if (!stack.isEmpty()) {
                var panelText = event.getSectionText(CollectDiagnosticsEvent.Section.HeldItem);
                this.processHeldItem(stack, panelText);
            }
        }

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
