package org.orecruncher.dsurround.config;

import com.google.common.collect.ImmutableList;
import com.mojang.serialization.Codec;
import it.unimi.dsi.fastutil.objects.*;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.LivingEntity;
import org.orecruncher.dsurround.Client;
import org.orecruncher.dsurround.config.data.EntityEffectConfigRule;
import org.orecruncher.dsurround.effects.IEntityEffect;
import org.orecruncher.dsurround.effects.IEntityEffectProducer;
import org.orecruncher.dsurround.effects.entity.EntityEffectInfo;
import org.orecruncher.dsurround.lib.collections.ObjectArray;
import org.orecruncher.dsurround.lib.logging.IModLog;
import org.orecruncher.dsurround.lib.resources.IResourceAccessor;
import org.orecruncher.dsurround.lib.resources.ResourceUtils;
import org.orecruncher.dsurround.xface.ILivingEntityExtended;

import java.util.Collection;
import java.util.List;
import java.util.Set;

@Environment(EnvType.CLIENT)
public class EntityEffectLibrary {

    private static final String FILE_NAME = "effects.json";
    private static final Codec<List<EntityEffectConfigRule>> CODEC = Codec.list(EntityEffectConfigRule.CODEC);
    private static final IModLog LOGGER = Client.LOGGER.createChild(EntityEffectLibrary.class);

    private static final Reference2ObjectMap<EntityType<?>, Set<EntityEffectType>> entityEffects = new Reference2ObjectOpenHashMap<>();
    private static Collection<EntityEffectConfigRule> entityConfigRules;
    private static int version;

    public static void load() {
        entityEffects.clear();
        final Collection<IResourceAccessor> accessors = ResourceUtils.findConfigs(Client.DATA_PATH.toFile(), FILE_NAME);

        entityConfigRules = new ObjectArray<>();
        // Iterate through the configs gathering the EntityEffectType data that is defined.  The result
        // of this process is each EntityType having deduped set of effects that can be applied.
        IResourceAccessor.process(accessors, accessor -> {
            var cfg = accessor.as(CODEC);
            if (cfg != null)
                entityConfigRules.addAll(cfg);
        });

        version++;

        LOGGER.info("%d entity effect configs loaded; version is now %d", entityEffects.size(), version);
    }

    public static boolean doesEntityEffectInfoExist(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        return accessor.getEffectInfo() != null;
    }

    public static void clearEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        accessor.setEffectInfo(null);
    }

    public static EntityEffectInfo getEntityEffectInfo(LivingEntity entity) {
        ILivingEntityExtended accessor = (ILivingEntityExtended) entity;
        var info = accessor.getEffectInfo();

        if (info != null && info.getVersion() == version)
            return info;

        // Going to initialize a new one
        info = null;

        // Find the entity in our map
        var types = entityEffects.get(entity.getType());
        if (types == null) {
            // Didn't find it.  Gather from the rules and cache
            types = gatherEffects(entity);
            entityEffects.put(entity.getType(), types);
        }

        // Project the effect instances
        Set<IEntityEffect> effects = new ReferenceOpenHashSet<>();
        for (var type : types) {
            var effectsToApply = type.produce(entity);
            effects.addAll(effectsToApply);
        }

        // If we have effect instances create a new info object
        if (effects.size() > 0)
            info = new EntityEffectInfo(version, entity, effects);

        // If there are no effects for the entity type default
        if (info == null) {
            info = EntityEffectInfo.DEFAULT;
        }

        accessor.setEffectInfo(info);

        // Initialize the attached effects before returning.  Usually the next step in processing would be
        // to tick the effects.
        info.initialize();

        return info;
    }

    private static Set<EntityEffectType> gatherEffects(LivingEntity entity) {
        // Gather all the effect types that apply to the entity
        Set<EntityEffectType> effectTypes = new ReferenceOpenHashSet<>();
        for (var rule : entityConfigRules) {
            if (rule.match(entity))
                effectTypes.addAll(rule.effects);
        }

        return effectTypes;
    }
}
