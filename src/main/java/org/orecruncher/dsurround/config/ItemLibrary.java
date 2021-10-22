package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.ItemConfigRule;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.sound.ISoundFactory;
import org.orecruncher.dsurround.sound.SoundFactoryBuilder;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ItemLibrary {

    private static final String FILE_NAME = "items.json";
    private static final Codec<List<ItemConfigRule>> CODEC = Codec.list(ItemConfigRule.CODEC);
    private static final IModLog LOGGER = Client.LOGGER.createChild(ItemLibrary.class);

    private static final Reference2ObjectOpenHashMap<Item, ISoundFactory> itemEquipFactories = new Reference2ObjectOpenHashMap<>();
    private static List<ItemConfigRule> itemConfigRules;
    private static List<ItemConfigRule> specificRules;
    private static List<ItemConfigRule> generalRules;

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

        specificRules = itemConfigRules.stream()
                .map(ItemConfigRule::asSpecificMatchOnly)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        generalRules = itemConfigRules.stream()
                .map(ItemConfigRule::asGeneralMatchOnly)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        version++;

        LOGGER.info("%d item configs loaded; version is now %d", itemConfigRules.size(), version);
    }

    public static ISoundFactory getItemEquipSound(ItemStack stack) {

        if (stack.isEmpty())
            return null;

        return itemEquipFactories.computeIfAbsent(stack.getItem(), k -> resolve(stack));
    }

    private static ISoundFactory resolve(ItemStack stack) {
        var item = stack.getItem();
        var itemSound = item.getEquipSound();
        if (itemSound != null) {
            // Normally MC expects to play at volume 1 and pitch 1
            return SoundFactoryBuilder
                    .create(itemSound)
                    .volume(0.5F)
                    .pitchRange(0.8F, 1.2F)
                    .build();
        }

        // Crawl through our rules looking for a match.  Check specific rules
        // before general
        for (var cfg : specificRules)
            if (cfg.match(item))
                return cfg.itemClassType.getToolBarSound();

        for (var cfg : generalRules)
            if (cfg.match(item))
                return cfg.itemClassType.getToolBarSound();

        // use the default
        return ItemClassType.NONE.getToolBarSound();
    }
}
