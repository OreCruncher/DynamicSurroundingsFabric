package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.MatchOnClass;

@Environment(EnvType.CLIENT)
public abstract class EntityTypeMatcher implements IMatcher<Entity> {

    public static final Codec<IMatcher<Entity>> CODEC = Codec.STRING
            .comapFlatMap(
                    EntityTypeMatcher::manifestEntityType,
                    IMatcher::toString).stable();

    private static DataResult<IMatcher<Entity>> manifestEntityType(String entityTypeId) {
        try {
            // If it looks like an Identifier then it must be an EntityType
            if (entityTypeId.contains(":")) {
                var type = EntityType.get(entityTypeId);
                return type.<DataResult<IMatcher<Entity>>>map(entityType -> DataResult.success(new MatchOnEntityType(entityType)))
                        .orElseGet(() -> DataResult.error(String.format("Unknown entity type id %s", entityTypeId)));
            }

            // Assume it's a class reference
            var matcher = MatchOnClass.<Entity>parse(entityTypeId);
            if (matcher != null)
                return DataResult.success(matcher);
            return DataResult.error(String.format("Unknown entity class(s) %s", entityTypeId));
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
}
