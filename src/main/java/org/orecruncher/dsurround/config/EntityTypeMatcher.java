package org.orecruncher.dsurround.config;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import org.orecruncher.dsurround.config.libraries.ITagLibrary;
import org.orecruncher.dsurround.lib.IMatcher;
import org.orecruncher.dsurround.lib.IdentityUtils;
import org.orecruncher.dsurround.lib.di.ContainerManager;

public abstract class EntityTypeMatcher implements IMatcher<Entity> {

    public static final Codec<IMatcher<Entity>> CODEC = Codec.STRING
            .comapFlatMap(
                    EntityTypeMatcher::manifest,
                    IMatcher::toString).stable();

    private static DataResult<IMatcher<Entity>> manifest(String entityTypeId) {
        try {
            if (entityTypeId.startsWith("#")) {
                // Entity tag
                final var id = IdentityUtils.resolveIdentifier(entityTypeId);
                var tagKey = TagKey.create(Registries.ENTITY_TYPE, id);
                return DataResult.success(new MatchOnEntityTag(tagKey));
            }
            else if (entityTypeId.contains(":")) {
                // If it looks like an Identifier, then it must be an EntityType
                var type = EntityType.byString(entityTypeId);
                return type.<DataResult<IMatcher<Entity>>>map(entityType -> DataResult.success(new MatchOnEntityType(entityType)))
                        .orElseGet(() -> DataResult.error(() -> String.format("Unknown entity type id %s", entityTypeId)));
            } else {
                return DataResult.error(() -> String.format("Unknown entity class(s) %s", entityTypeId));
            }
        } catch (Throwable t) {
            return DataResult.error(t::getMessage);
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

    private static class MatchOnEntityTag extends EntityTypeMatcher {
        private static final ITagLibrary TAG_LIBRARY = ContainerManager.resolve(ITagLibrary.class);

        private final TagKey<EntityType<?>> tagKey;

        public MatchOnEntityTag(TagKey<EntityType<?>> tagKey) {
            this.tagKey = tagKey;
        }

        public boolean match(Entity entity) {
            return TAG_LIBRARY.isIn(this.tagKey, entity.getType());
        }
    }
}
