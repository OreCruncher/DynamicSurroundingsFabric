package org.orecruncher.dsurround.tags;

import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import org.orecruncher.dsurround.Constants;

import java.util.Collection;
import java.util.HashSet;

public class EntityEffectTags {

    static final Collection<TagKey<EntityType<?>>> TAGS = new HashSet<>();

    public static final TagKey<EntityType<?>> BOW_PULL = of("bow_pull");
    public static final TagKey<EntityType<?>> FROST_BREATH = of("frost_breath");
    // Breath from a mouth out in front of the body; takes the place of frost_breath for those in both
    public static final TagKey<EntityType<?>> ANIMAL_BREATH = of("animal_breath");
    public static final TagKey<EntityType<?>> ITEM_SWING = of("item_swing");
    public static final TagKey<EntityType<?>> TOOLBAR = of("toolbar");
    public static final TagKey<EntityType<?>> BRUSH_STEP = of ("brush_step");
    // The reverse of the others: entities that keep vanilla's fire rather than get the burning effect
    public static final TagKey<EntityType<?>> VANILLA_FIRE = of("vanilla_fire");
    // Also the reverse: entities whose health changes don't pop off
    public static final TagKey<EntityType<?>> NO_HEALTH_POPOFFS = of("no_health_popoffs");
    // The footprints mobs leave, by style; players' style is in the config
    public static final TagKey<EntityType<?>> FOOTPRINTS_SQUARE = of("footprints/square");
    public static final TagKey<EntityType<?>> FOOTPRINTS_PAW = of("footprints/paw");
    public static final TagKey<EntityType<?>> FOOTPRINTS_HOOF = of("footprints/hoof");
    public static final TagKey<EntityType<?>> FOOTPRINTS_BIRD = of("footprints/bird");
    // Whose prints are bigger than their size alone gives (bears), smaller (skeletons' bony feet), or much smaller
    // (wolves and cats: as wide as a player, but with small paws)
    public static final TagKey<EntityType<?>> FOOTPRINTS_LARGE = of("footprints/large");
    public static final TagKey<EntityType<?>> FOOTPRINTS_SMALL = of("footprints/small");
    public static final TagKey<EntityType<?>> FOOTPRINTS_TINY = of("footprints/tiny");

    private static TagKey<EntityType<?>> of(String id) {
        var tagKey = TagKey.create(Registries.ENTITY_TYPE, Constants.asId("effects/" + id));
        TAGS.add(tagKey);
        return tagKey;
    }
}
