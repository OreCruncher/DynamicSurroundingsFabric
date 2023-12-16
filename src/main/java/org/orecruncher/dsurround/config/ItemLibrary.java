package org.orecruncher.dsurround.config;

import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.*;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.sound.SoundCategory;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;
import org.orecruncher.dsurround.tags.ItemEffectTags;

import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class ItemLibrary {

    private static final IModLog LOGGER = Client.LOGGER.createChild(ItemLibrary.class);

    private static final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemSwingFactories = new Reference2ObjectOpenHashMap<>();
    private static int version;

    public static void load() {
        itemEquipFactories.clear();
        itemSwingFactories.clear();
        version++;
        LOGGER.info("Item library configured; version is now %d", version);
    }

    public static ISoundFactory getItemEquipSound(ItemStack stack) {

        if (stack.isEmpty())
            return null;

        return itemEquipFactories.computeIfAbsent(stack.getItem(), k -> resolve(stack, ItemClassType::getToolBarSound, ItemClassType.NONE::getToolBarSound));
    }

    public static @Nullable ISoundFactory getItemSwingSound(ItemStack stack) {
        if (stack.isEmpty())
            return null;

        return itemSwingFactories.computeIfAbsent(stack.getItem(), k -> resolve(stack, ItemClassType::getSwingSound, () -> null));
    }

    private static ISoundFactory resolve(ItemStack stack, Function<ItemClassType, ISoundFactory> resolveSound, Supplier<ISoundFactory> defaultSoundFactory) {

        var itemClassType = resolveClassType(stack);

        if (itemClassType == ItemClassType.NONE) {
            SoundEvent itemEquipSound = getSoundEvent(stack);
            if (itemEquipSound != null)
                return SoundFactoryBuilder
                        .create(itemEquipSound)
                        .category(SoundCategory.PLAYERS).volume(0.5F).pitchRange(0.8F, 1.2F).build();
            return defaultSoundFactory.get();
        }

        return resolveSound.apply(itemClassType);
    }

    @Nullable
    private static SoundEvent getSoundEvent(ItemStack stack) {
        // Look for special Equipment and ArmorItem types since they may have built in equip sounds
        var item = stack.getItem();
        SoundEvent itemEquipSound = null;

        if (item instanceof Equipment equipment)
            itemEquipSound = equipment.getEquipSound();
        else if (item instanceof ArmorItem armor)
            itemEquipSound = armor.getEquipSound();
        else if (item instanceof ElytraItem elytraItem)
            itemEquipSound = elytraItem.getEquipSound();
        return itemEquipSound;
    }

    public static ItemClassType resolveClassType(ItemStack stack) {
        if (stack.isIn(ItemEffectTags.AXES))
            return ItemClassType.AXE;
        if (stack.isIn(ItemEffectTags.BOOKS))
            return ItemClassType.BOOK;
        if (stack.isIn(ItemEffectTags.BOWS))
            return ItemClassType.BOW;
        if (stack.isIn(ItemEffectTags.POTIONS))
            return ItemClassType.POTION;
        if (stack.isIn(ItemEffectTags.CROSSBOWS))
            return ItemClassType.CROSSBOW;
        if (stack.isIn(ItemEffectTags.SHIELDS))
            return ItemClassType.SHIELD;
        if (stack.isIn(ItemEffectTags.SWORDS))
            return ItemClassType.SWORD;
        if (stack.isIn(ItemEffectTags.TOOLS))
            return ItemClassType.TOOL;

        return ItemClassType.NONE;
    }

    public static Stream<String> dumpItems() {
        var blockRegistry = GameUtils.getRegistryManager().get(RegistryKeys.ITEM).getEntrySet();
        return blockRegistry.stream().map(kvp -> formatItemOutput(kvp.getKey().getValue(), kvp.getValue())).sorted();
    }

    private static String formatItemOutput(Identifier id, Item item) {
        var items = GameUtils.getWorld().getRegistryManager().get(RegistryKeys.ITEM);

        var tags = "null";
        var entry = items.getEntry(items.getRawId(item));
        if (entry.isPresent()) {
            tags = entry.get()
                    .streamTags()
                    .map(TagKey::toString)
                    .sorted()
                    .collect(Collectors.joining(","));
        }

        StringBuilder builder = new StringBuilder();
        builder.append(id.toString());
        builder.append("\nTags: ").append(tags);
        builder.append("\n");
        return builder.toString();
    }
}
