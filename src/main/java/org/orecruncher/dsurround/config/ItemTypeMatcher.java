package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

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
                var tagKey = TagKey.create(Registries.ITEM, id);
                return DataResult.success(new ItemTypeMatcher.MatchOnItemTag(tagKey));
            } else if (itemId.contains(":")) {
                var item = BuiltInRegistries.ITEM.get(id);
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

        private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

        private final TagKey<Item> tagKey;

        public MatchOnItemTag(TagKey<Item> tagKey) {
            this.tagKey = tagKey;
        }

        public boolean match(Item item) {
            return TAG_LIBRARY.is(this.tagKey, item);
        }
    }
}
