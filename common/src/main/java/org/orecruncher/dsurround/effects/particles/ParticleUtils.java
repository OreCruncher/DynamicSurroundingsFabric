package org.orecruncher.dsurround.effects.particles;

import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.orecruncher.dsurround.lib.GameUtils;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.random.IRandomizer;
import org.orecruncher.dsurround.lib.random.Randomizer;
import org.orecruncher.dsurround.lib.registry.RegistryUtils;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.Optional;

public final class ParticleUtils {

    private static final IRandomizer RANDOM = Randomizer.current();
    private static volatile Field spriteSetsField;
    private static volatile boolean spriteSetsFieldLookupDone;
    private static volatile Method createParticleMethod;
    private static volatile boolean createParticleMethodLookupDone;

    /**
     * Returns the sprite set for a particle type, or {@code null} if it is not available yet.
     *
     * <p>The old port read ParticleEngine.spriteSets through a mixin accessor.  On 26.2 that accessor
     * can fail to apply and leave ParticleEngine without the synthetic bridge method, causing an
     * AbstractMethodError when a particle class initializes.  This helper first uses the sprite sets
     * captured through the platform particle provider registration path, then falls back to a guarded
     * reflection lookup.  All failures degrade to a null result so callers can skip or fall back to a
     * vanilla particle instead of crashing the client.</p>
     */
    public static SpriteSet getSpriteProvider(ParticleType<?> particleType) {
        var registered = DsurroundParticleSpriteSets.get(particleType);
        if (registered.isPresent()) {
            return registered.get();
        }

        SpriteSet reflected = getSpriteProviderFromEngine(particleType);
        if (reflected != null) {
            DsurroundParticleSpriteSets.register(particleType, reflected);
        }
        return reflected;
    }

    /**
     * Creates a vanilla particle through the client particle engine without the fragile
     * MixinParticleManager invoker path.  The method is reflected once so the source stays tolerant
     * of tiny 26.x visibility/name churn while still using the real vanilla provider registry.
     */
    public static <T extends ParticleOptions> Particle createParticle(T parameters, double x, double y, double z, double velocityX, double velocityY, double velocityZ) {
        try {
            Method method = getCreateParticleMethod(GameUtils.getParticleManager());
            if (method == null) {
                return null;
            }
            Object result = method.invoke(GameUtils.getParticleManager(), parameters, x, y, z, velocityX, velocityY, velocityZ);
            return result instanceof Particle particle ? particle : null;
        } catch (Throwable t) {
            Library.LOGGER.debug("Unable to create particle %s: %s", parameters, t.toString());
            return null;
        }
    }

    public static void addParticle(final Particle particle) {
        if (particle != null) {
            GameUtils.getParticleManager().add(particle);
        }
    }

    public static Vec3 getBreathOrigin(final LivingEntity entity) {
        final Vec3 eyePosition = eyePosition(entity).subtract(0D, entity.isBaby() ? 0.1D : 0.2D, 0D);
        final Vec3 look = entity.getViewVector(1F); // Don't use the other look vector method!
        return eyePosition.add(look.scale(entity.isBaby() ? 0.25D : 0.5D));
    }

    public static Vec3 getLookTrajectory(final LivingEntity entity) {
        return entity.getLookAngle()
                .zRot(RANDOM.nextFloat() * 2F)   // yaw
                .yRot(RANDOM.nextFloat() * 2F)   // pitch
                .normalize();
    }


    private static Method getCreateParticleMethod(ParticleEngine engine) {
        Method cached = createParticleMethod;
        if (cached != null || createParticleMethodLookupDone) {
            return cached;
        }

        createParticleMethodLookupDone = true;
        Class<?> current = engine.getClass();
        while (current != null && current != Object.class) {
            for (Method method : current.getDeclaredMethods()) {
                Class<?>[] params = method.getParameterTypes();
                if (!"createParticle".equals(method.getName())) {
                    continue;
                }
                if (!Particle.class.isAssignableFrom(method.getReturnType())) {
                    continue;
                }
                if (params.length == 7
                        && ParticleOptions.class.isAssignableFrom(params[0])
                        && params[1] == double.class
                        && params[2] == double.class
                        && params[3] == double.class
                        && params[4] == double.class
                        && params[5] == double.class
                        && params[6] == double.class) {
                    method.setAccessible(true);
                    createParticleMethod = method;
                    return method;
                }
            }
            current = current.getSuperclass();
        }
        return null;
    }

    private static SpriteSet getSpriteProviderFromEngine(ParticleType<?> particleType) {
        try {
            Optional<Identifier> maybeId = getParticleId(particleType);
            if (maybeId.isEmpty()) {
                return null;
            }

            ParticleEngine engine = GameUtils.getParticleManager();
            Map<?, ?> spriteSets = getEngineSpriteSets(engine);
            if (spriteSets == null || spriteSets.isEmpty()) {
                return null;
            }

            Identifier id = maybeId.get();
            Object direct = spriteSets.get(id);
            if (direct instanceof SpriteSet spriteSet) {
                return spriteSet;
            }

            String idText = id.toString();
            for (Map.Entry<?, ?> entry : spriteSets.entrySet()) {
                if (entry.getValue() instanceof SpriteSet spriteSet && entry.getKey() != null && idText.equals(entry.getKey().toString())) {
                    return spriteSet;
                }
            }
        } catch (Throwable t) {
            Library.LOGGER.debug("Unable to read particle sprite set for %s: %s", particleType, t.toString());
        }
        return null;
    }

    private static Optional<Identifier> getParticleId(ParticleType<?> particleType) {
        return RegistryUtils.getRegistry(Registries.PARTICLE_TYPE)
                .flatMap(registry -> registry.getResourceKey(particleType).map(ResourceKey::identifier));
    }

    private static Map<?, ?> getEngineSpriteSets(ParticleEngine engine) throws IllegalAccessException {
        Field field = getSpriteSetsField(engine);
        if (field == null) {
            return null;
        }
        Object value = field.get(engine);
        return value instanceof Map<?, ?> map ? map : null;
    }

    private static Field getSpriteSetsField(ParticleEngine engine) {
        Field cached = spriteSetsField;
        if (cached != null || spriteSetsFieldLookupDone) {
            return cached;
        }

        spriteSetsFieldLookupDone = true;

        // Try the known field name first; this is present on 26.1 and may still exist on 26.2.
        Field named = findFieldByName(engine.getClass(), "spriteSets");
        if (isSpriteSetMap(engine, named)) {
            spriteSetsField = named;
            return named;
        }

        // Fall back to scanning Map fields and checking whether their values are SpriteSets.
        Class<?> current = engine.getClass();
        while (current != null && current != Object.class) {
            for (Field field : current.getDeclaredFields()) {
                if (!Map.class.isAssignableFrom(field.getType())) {
                    continue;
                }
                if (isSpriteSetMap(engine, field)) {
                    spriteSetsField = field;
                    return field;
                }
            }
            current = current.getSuperclass();
        }

        return null;
    }

    private static Field findFieldByName(Class<?> type, String name) {
        Class<?> current = type;
        while (current != null && current != Object.class) {
            try {
                Field field = current.getDeclaredField(name);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            } catch (Throwable ignored) {
                return null;
            }
        }
        return null;
    }

    private static boolean isSpriteSetMap(ParticleEngine engine, Field field) {
        if (field == null || !Map.class.isAssignableFrom(field.getType())) {
            return false;
        }
        try {
            field.setAccessible(true);
            Object value = field.get(engine);
            if (!(value instanceof Map<?, ?> map)) {
                return false;
            }
            if (map.isEmpty()) {
                // Empty immediately after bootstrap is not proof, but the named field is still
                // the best available candidate.
                return "spriteSets".equals(field.getName());
            }
            for (Object entryValue : map.values()) {
                if (entryValue instanceof SpriteSet) {
                    return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }

    /*
     * Use some corrective lenses because the MC routine just doesn't lower the
     * height enough for our rendering purpose.
     */
    private static Vec3 eyePosition(final Entity e) {
        var y = e.getEyePosition();
        if (e.isCrouching()) {
            y = y.subtract(0, 0.25D, 0);
        }
        return y;
    }
}
