package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.item.Item;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.ItemTypeMatcher;
import org.orecruncher.dsurround.lib.IMatcher;

import java.util.List;

public record ItemConfigRule(
        ItemClassType itemClassType,
        List<IMatcher<Item>> items) {

    public static Codec<ItemConfigRule> CODEC = RecordCodecBuilder.create((instance) -> instance.group(
            ItemClassType.CODEC.fieldOf("itemClassType").forGetter(ItemConfigRule::itemClassType),
            Codec.list(ItemTypeMatcher.CODEC).fieldOf("items").forGetter(ItemConfigRule::items))
            .apply(instance, ItemConfigRule::new));

    public boolean match(Item item) {
        for (var rule : this.items)
            if (rule.match(item))
                return true;
        return false;
    }
}
