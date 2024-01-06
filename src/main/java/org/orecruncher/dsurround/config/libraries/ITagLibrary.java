package org.orecruncher.dsurround.config.libraries;

import it.unimi.dsi.fastutil.Pair;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.Set;
import java.util.stream.Stream;

public interface ITagLibrary extends ILibrary {

    boolean is(TagKey<Block> tagKey, BlockState entry);
    boolean is(TagKey<Item> tagKey, ItemStack entry);
    default boolean is(TagKey<Item> tagKey, Item item) {
        return this.is(tagKey, new ItemStack(item));
    }
    boolean is(TagKey<Biome> tagKey, Biome entry);
    boolean is(TagKey<EntityType<?>> tagKey, EntityType<?> entry);

    <T> String asString(Stream<TagKey<T>> tagStream);
    <T> Stream<Pair<TagKey<T>, Set<T>>> getEntriesByTag(ResourceKey<? extends Registry<T>> registry);
    <T> Stream<TagKey<T>> streamTags(Holder<T> registryEntry);
}
