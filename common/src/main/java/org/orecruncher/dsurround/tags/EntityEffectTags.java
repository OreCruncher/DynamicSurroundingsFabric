package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class EntityEffectTags {

    static final Collection<TagKey<EntityType<?>>> TAGS = new HashSet<>();

    public static final TagKey<EntityType<?>> BOW_PULL = of("bow_pull");
    public static final TagKey<EntityType<?>> FROST_BREATH = of("frost_breath");
    public static final TagKey<EntityType<?>> ITEM_SWING = of("item_swing");
    public static final TagKey<EntityType<?>> TOOLBAR = of("toolbar");
    public static final TagKey<EntityType<?>> BRUSH_STEP = of ("brush_step");

    private static TagKey<EntityType<?>> of(String id) {
        var tagKey = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.fromNamespaceAndPath(Constants.MOD_ID, "effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }
}
