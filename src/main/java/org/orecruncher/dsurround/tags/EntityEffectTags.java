package org.orecruncher.dsurround.tags;

import net.minecraft.entity.EntityType;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.tag.TagKey;
import net.minecraft.util.Identifier;
import org.orecruncher.dsurround.Client;

public class EntityEffectTags {
    public static final TagKey<EntityType<?>> BOW_PULL = of("bow_pull");
    public static final TagKey<EntityType<?>> FROST_BREATH = of("frost_breath");
    public static final TagKey<EntityType<?>> ITEM_SWING = of("item_swing");
    public static final TagKey<EntityType<?>> TOOLBAR = of("toolbar");
    public static final TagKey<EntityType<?>> STEP_ACCENT = of ("step_accent");

    private static TagKey<EntityType<?>> of(String id) {
        return TagKey.of(RegistryKeys.ENTITY_TYPE, new Identifier(Client.ModId, "effects/" + id));
    }
}
