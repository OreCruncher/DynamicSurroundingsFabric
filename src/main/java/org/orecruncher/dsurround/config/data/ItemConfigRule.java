package org.orecruncher.dsurround.config.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.config.ItemClassType;
import org.orecruncher.dsurround.config.ItemTypeMatcher;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.MatchOnClass;

import java.util.List;
import java.util.stream.Collectors;

@Environment(EnvType.CLIENT)
public class ItemConfigRule {

    public static Codec<ItemConfigRule> CODEC = RecordCodecBuilder.create((instance) ->
            instance.group(
                ItemClassType.CODEC.fieldOf("itemClassType").forGetter(info -> info.itemClassType),
                Codec.list(ItemTypeMatcher.CODEC).fieldOf("items").forGetter(info -> info.items)
            ).apply(instance, ItemConfigRule::new));

    public final ItemClassType itemClassType;
    public final List<IMatcher<Item>> items;

    ItemConfigRule(ItemClassType itemClassType, List<IMatcher<Item>> items) {
        this.itemClassType = itemClassType;
        this.items = items;
    }

    public @Nullable ItemConfigRule asSpecificMatchOnly() {
        var rules = this.items.stream()
                .filter(matcher -> matcher instanceof ItemConfigRule)
                .collect(Collectors.toList());
        if (rules.size() == items.size())
            return this;

        if (rules.size() == 0)
            return null;

        return new ItemConfigRule(this.itemClassType, rules);
    }

    public @Nullable ItemConfigRule asGeneralMatchOnly() {
        var rules = this.items.stream()
                .filter(matcher -> matcher instanceof MatchOnClass)
                .collect(Collectors.toList());
        if (rules.size() == items.size())
            return this;

        if (rules.size() == 0)
            return null;

        return new ItemConfigRule(this.itemClassType, rules);
    }

    public boolean match(Item item) {
        for (var rule : this.items)
            if (rule.match(item))
                return true;
        return false;
    }
}
