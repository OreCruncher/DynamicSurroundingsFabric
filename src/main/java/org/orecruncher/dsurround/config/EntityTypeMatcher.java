package org.orecruncher.dsurround.config;

import com.ibm.icu.impl.ValidIdentifiers;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.util.Identifier;

@Environment(EnvType.CLIENT)
public abstract class EntityTypeMatcher {

    public static final Codec<EntityTypeMatcher> CODEC = Codec.STRING
            .comapFlatMap(
                    EntityTypeMatcher::manifestEntityType,
                    EntityTypeMatcher::toString).stable();

    private static DataResult<EntityTypeMatcher> manifestEntityType(String entityTypeId) {
        try {
            // If it looks like an Identifier then it must be an EntityType
            if (entityTypeId.contains(":")) {
                var type = EntityType.get(entityTypeId);
                return type.<DataResult<EntityTypeMatcher>>map(entityType -> DataResult.success(new MatchOnEntityType(entityType)))
                        .orElseGet(() -> DataResult.error(String.format("Unknown entity type id %s", entityTypeId)));
            }

            // Assume it's a class reference
            try {
                var clazz = Class.forName(entityTypeId);
                return DataResult.success(new MatchOnClass(clazz));
            } catch(Throwable t) {
                return DataResult.error(String.format("Unknown entity class %s", entityTypeId));
            }
        } catch (Throwable t) {
            return DataResult.error(t.getMessage());
        }
    }

    public abstract boolean match(Entity entity);

    private static class MatchOnEntityType extends EntityTypeMatcher {

        private final EntityType<?> type;

        public MatchOnEntityType(EntityType<?> type) {
            this.type = type;
        }

        public boolean match(Entity entity) {
            return this.type == entity.getType();
        }
    }

    private static class MatchOnClass extends EntityTypeMatcher {

        private final Class<?> clazz;

        public MatchOnClass(Class<?> clazz) {
            this.clazz = clazz;
        }

        public boolean match(Entity entity) {
            return this.clazz.isInstance(entity);
        }
    }
}
