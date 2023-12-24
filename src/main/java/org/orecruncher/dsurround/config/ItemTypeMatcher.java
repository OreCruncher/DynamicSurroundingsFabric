package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.item.Item;
import net.minecraft.registry.Registries;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.tags.TagHelpers;

public abstract class ItemTypeMatcher implements IMatcher<Item> {

    public static final Codec<IMatcher<Item>> CODEC = Codec.STRING
            .comapFlatMap(
                    ItemTypeMatcher::manifest,
                    IMatcher::toString).stable();

    private static DataResult<IMatcher<Item>> manifest(String itemId) {
        try {
            final var id = IdentityUtils.resolveIdentifier(itemId);
            if (itemId.startsWith("#")) {
                // Item tag
                var tagKey = TagKey.of(RegistryKeys.ITEM, id);
                return DataResult.success(new ItemTypeMatcher.MatchOnItemTag(tagKey));
            } else if (itemId.contains(":")) {
                var item = Registries.ITEM.get(id);
                return DataResult.success(new ItemTypeMatcher.MatchOnItem(item));
            }

            return DataResult.error(() -> String.format("Unknown item class(s) %s", itemId));
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
        }
    }

    public abstract boolean match(Item object);

    private static class MatchOnItem extends ItemTypeMatcher {
        private final Item item;

        MatchOnItem(Item item) {
            this.item = item;
        }

        @Override
        public boolean match(Item item) {
            return this.item == item;
        }
    }

    private static class MatchOnItemTag extends ItemTypeMatcher {

        private final TagKey<Item> tagKey;

        public MatchOnItemTag(TagKey<Item> tagKey) {
            this.tagKey = tagKey;
        }

        public boolean match(Item item) {
            return TagHelpers.isIn(this.tagKey, item);
        }
    }
}
