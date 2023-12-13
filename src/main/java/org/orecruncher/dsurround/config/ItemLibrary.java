package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.ItemConfigRule;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.sound.ISoundFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Environment(EnvType.CLIENT)
public class ItemLibrary {

    private static final String FILE_NAME = "items.json";
    private static final Codec<List<ItemConfigRule>> CODEC = Codec.list(ItemConfigRule.CODEC);
    private static final IModLog LOGGER = Client.LOGGER.createChild(ItemLibrary.class);

    private static final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private static final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemSwingFactories = new Reference2ObjectOpenHashMap<>();
    private static List<ItemConfigRule> itemConfigRules;
    private static int version;

    public static void load() {
        itemEquipFactories.clear();

        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), FILE_NAME);

        itemConfigRules = new ArrayList<>();

        // Iterate through the configs gathering the EntityEffectType data that is defined.  The result
        // of this process is each EntityType having deduped set of effects that can be applied.
        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                itemConfigRules.addAll(cfg);
        });

        version++;

        LOGGER.info("%d item configs loaded; version is now %d", itemConfigRules.size(), version);
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
        var item = stack.getItem();

        // Crawl through our rules looking for a match.  Check specific rules
        // before general
        for (var cfg : itemConfigRules)
            if (cfg.match(item))
                return resolveSound.apply(cfg.itemClassType);

        // use the default
        return defaultSoundFactory.get();
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
