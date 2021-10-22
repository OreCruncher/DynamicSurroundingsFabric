package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.item.Item;
import net.minecraft.util.Identifier;
import net.minecraft.util.registry.Registry;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.MatchOnClass;

@Environment(EnvType.CLIENT)
public class ItemTypeMatcher implements IMatcher<Item> {

    public static final Codec<IMatcher<Item>> CODEC = Codec.STRING
            .comapFlatMap(
                    ItemTypeMatcher::manifest,
                    IMatcher::toString).stable();

    private static DataResult<IMatcher<Item>> manifest(String itemId) {
        try {
            // If it looks like an Identifier then it must be an EntityType
            if (itemId.contains(":")) {
                var item= Registry.ITEM.get(new Identifier(itemId));
                return DataResult.success(new ItemTypeMatcher(item));
            }

            // Assume it's a class reference
            var matcher = MatchOnClass.<Item>parse(itemId);
            if (matcher != null)
                return DataResult.success(matcher);
            return DataResult.error(String.format("Unknown item class(s) %s", itemId));
        } catch (Throwable t) {
            return DataResult.error(t.getMessage());
        }
    }

    private final Item item;

    ItemTypeMatcher(Item item) {
        this.item = item;
    }

    @Override
    public boolean match(Item object) {
        return this.item == object;
    }
}
