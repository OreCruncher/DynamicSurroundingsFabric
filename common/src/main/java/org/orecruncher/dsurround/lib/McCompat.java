package org.orecruncher.dsurround.lib;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.Music;
import net.minecraft.sounds.SoundEvent;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Locale;
import java.util.Optional;

/**
 * Compatibility shims for the Minecraft 26.x unobfuscated API transition.
 *
 * <p>The public API was renamed in several places between the older mapped
 * sources and 26.x.  Keeping the reflection isolated here lets the rest of the
 * mod compile while the call sites are migrated to the final names.</p>
 */
public final class McCompat {

    private static final SoundEvent FALLBACK_SOUND = SoundEvent.createVariableRangeEvent(
            Identifier.fromNamespaceAndPath("minecraft", "ui.button.click")
    );

    private McCompat() {
    }

    public static SoundEvent musicSoundEvent(Music music) {
        Object value = invokeAny(music, "sound", "event", "soundEvent", "getEvent").orElse(null);
        value = unwrapHolder(value);
        if (value instanceof SoundEvent soundEvent) {
            return soundEvent;
        }
        return FALLBACK_SOUND;
    }

    public static int musicMinDelay(Music music, int fallback) {
        return invokeAny(music, "minDelay", "getMinDelay")
                .map(McCompat::asInt)
                .orElse(fallback);
    }

    public static int musicMaxDelay(Music music, int fallback) {
        return invokeAny(music, "maxDelay", "getMaxDelay")
                .map(McCompat::asInt)
                .orElse(fallback);
    }

    public static boolean musicReplaceCurrentMusic(Music music, boolean fallback) {
        return invokeAny(music, "replaceCurrentMusic", "shouldReplaceCurrentMusic")
                .map(McCompat::asBoolean)
                .orElse(fallback);
    }

    public static boolean optionsHideGui(Object options) {
        return invokeAny(options, "hideGui", "isHideGui", "hideGui")
                .or(() -> readField(options, "hideGui"))
                .map(McCompat::asBoolean)
                .orElse(false);
    }

    public static int selectedHotbarSlot(Object inventory) {
        return invokeAny(inventory, "getSelectedSlot", "selectedSlot", "getSelected", "selected")
                .or(() -> readField(inventory, "selected", "selectedSlot"))
                .map(McCompat::asInt)
                .orElse(0);
    }

    public static float worldTimeOfDay(Object world, float partialTick) {
        var exact = invoke(world, "getTimeOfDay", new Class<?>[]{float.class}, partialTick)
                .or(() -> invoke(world, "getTimeOfDay", new Class<?>[]{int.class}, (int) partialTick))
                .or(() -> invoke(world, "getSkyAngle", new Class<?>[]{float.class}, partialTick))
                .or(() -> invoke(world, "skyAngle", new Class<?>[]{float.class}, partialTick));
        if (exact.isPresent()) {
            return asFloat(exact.get());
        }

        long dayTime = Math.floorMod(worldDayTime(world), 24000L);
        // Vanilla day angle approximation: 0 at noon, 0.5 at midnight.
        return ((dayTime + 6000L) % 24000L) / 24000.0F;
    }

    public static long worldDayTime(Object world) {
        return invokeAny(world, "getDayTime", "dayTime", "getTimeOfDay", "getGameTime")
                .or(() -> readField(world, "dayTime", "timeOfDay"))
                .map(McCompat::asLong)
                .orElse(0L);
    }

    public static float worldMoonBrightness(Object world) {
        return invokeAny(world, "getMoonBrightness", "getMoonSize", "moonBrightness")
                .map(McCompat::asFloat)
                .orElse(1.0F);
    }

    public static String worldVersionName(Object version) {
        return invokeAny(version, "getName", "name", "id")
                .map(String::valueOf)
                .orElseGet(() -> String.valueOf(version));
    }

    public static String soundManagerDebugString(Object soundManager) {
        return invokeAny(soundManager, "getDebugString", "getDebugInfo", "debugString")
                .map(String::valueOf)
                .orElse("");
    }

    public static void addToast(Object minecraft, Object toast) {
        var toastManager = invokeAny(minecraft, "getToasts", "getToastManager", "toasts")
                .or(() -> readField(minecraft, "toastManager", "toasts"));
        if (toastManager.isEmpty()) {
            return;
        }

        for (var method : allMethods(toastManager.get().getClass())) {
            if (!"addToast".equals(method.getName()) || method.getParameterCount() != 1) {
                continue;
            }
            if (!method.getParameterTypes()[0].isAssignableFrom(toast.getClass())) {
                continue;
            }
            try {
                method.setAccessible(true);
                method.invoke(toastManager.get(), toast);
                return;
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Try the next matching overload.
            }
        }
    }

    public static boolean isSinglePlayer(Object minecraft) {
        return invokeAny(minecraft, "isSingleplayer", "isSinglePlayer", "hasSingleplayerServer")
                .map(McCompat::asBoolean)
                .orElse(false);
    }

    public static boolean hasOverlay(Object minecraft) {
        return invokeAny(minecraft, "getOverlay", "overlay")
                .or(() -> readField(minecraft, "overlay"))
                .isPresent();
    }

    public static int worldMinBuildHeight(Object world) {
        return invokeAny(world, "getMinBuildHeight", "getMinY", "getMinBuildY", "minBuildHeight")
                .map(McCompat::asInt)
                .orElse(-64);
    }

    public static int worldMaxBuildHeight(Object world) {
        return invokeAny(world, "getMaxBuildHeight", "getMaxY", "getHeight", "maxBuildHeight")
                .map(McCompat::asInt)
                .orElse(320);
    }

    public static boolean isPlayerMoving(Object player) {
        var bob = readField(player, "bob", "walkDist").map(McCompat::asDouble);
        var oldBob = readField(player, "oBob", "walkDistO").map(McCompat::asDouble);
        if (bob.isPresent() && oldBob.isPresent()) {
            return Double.compare(bob.get(), oldBob.get()) != 0;
        }

        var delta = invokeAny(player, "getDeltaMovement", "getVelocity");
        if (delta.isPresent()) {
            var vec = delta.get();
            double x = readField(vec, "x").map(McCompat::asDouble)
                    .or(() -> invokeAny(vec, "x").map(McCompat::asDouble))
                    .orElse(0.0D);
            double z = readField(vec, "z").map(McCompat::asDouble)
                    .or(() -> invokeAny(vec, "z").map(McCompat::asDouble))
                    .orElse(0.0D);
            return x * x + z * z > 1.0E-8D;
        }

        return false;
    }

    public static KeyMapping createKeyMapping(String translationKey, int code, String categoryTranslationKey) {
        Identifier categoryId = Identifier.fromNamespaceAndPath("dsurround", sanitizePath(categoryTranslationKey));
        Class<?> categoryClass = null;
        for (var nested : KeyMapping.class.getDeclaredClasses()) {
            if ("Category".equals(nested.getSimpleName())) {
                categoryClass = nested;
                break;
            }
        }

        if (categoryClass != null) {
            Object category = createCategory(categoryClass, categoryId, categoryTranslationKey);
            if (category != null) {
                var constructed = constructKeyMapping(translationKey, code, categoryClass, category);
                if (constructed.isPresent()) {
                    return constructed.get();
                }
            }
        }

        // Older constructor kept for local dev environments that still use the
        // pre-26.x key-mapping category string.
        try {
            var ctor = KeyMapping.class.getConstructor(String.class, int.class, String.class);
            return ctor.newInstance(translationKey, code, categoryTranslationKey);
        } catch (ReflectiveOperationException ignored) {
            throw new IllegalStateException("Unable to construct KeyMapping for category " + categoryTranslationKey);
        }
    }

    private static Optional<KeyMapping> constructKeyMapping(String translationKey, int code, Class<?> categoryClass, Object category) {
        for (var ctor : KeyMapping.class.getConstructors()) {
            var parameters = ctor.getParameterTypes();
            if (parameters.length == 3 && parameters[0] == String.class && parameters[1] == int.class && parameters[2].isAssignableFrom(categoryClass)) {
                try {
                    return Optional.of((KeyMapping) ctor.newInstance(translationKey, code, category));
                } catch (ReflectiveOperationException ignored) {
                    // Try the next overload.
                }
            }
            if (parameters.length == 4 && parameters[0] == String.class && parameters[2] == int.class && parameters[3].isAssignableFrom(categoryClass)) {
                try {
                    Object type = defaultInputType(parameters[1]);
                    if (type != null) {
                        return Optional.of((KeyMapping) ctor.newInstance(translationKey, type, code, category));
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try the next overload.
                }
            }
        }
        return Optional.empty();
    }

    private static Object defaultInputType(Class<?> inputTypeClass) {
        if (!inputTypeClass.isEnum()) {
            return null;
        }
        for (var constant : inputTypeClass.getEnumConstants()) {
            if ("KEYSYM".equals(String.valueOf(constant))) {
                return constant;
            }
        }
        var constants = inputTypeClass.getEnumConstants();
        return constants.length > 0 ? constants[0] : null;
    }

    private static Object createCategory(Class<?> categoryClass, Identifier id, String categoryTranslationKey) {
        for (String methodName : new String[]{"register", "create", "of"}) {
            for (var method : categoryClass.getDeclaredMethods()) {
                if (!methodName.equals(method.getName()) || !Modifier.isStatic(method.getModifiers()) || method.getParameterCount() != 1) {
                    continue;
                }
                try {
                    method.setAccessible(true);
                    Class<?> parameter = method.getParameterTypes()[0];
                    if (parameter.isAssignableFrom(Identifier.class)) {
                        return method.invoke(null, id);
                    }
                    if (parameter == String.class) {
                        return method.invoke(null, categoryTranslationKey);
                    }
                } catch (ReflectiveOperationException ignored) {
                    // Try another factory.
                }
            }
        }

        for (Constructor<?> ctor : categoryClass.getDeclaredConstructors()) {
            if (ctor.getParameterCount() != 1) {
                continue;
            }
            try {
                ctor.setAccessible(true);
                Class<?> parameter = ctor.getParameterTypes()[0];
                if (parameter.isAssignableFrom(Identifier.class)) {
                    return ctor.newInstance(id);
                }
                if (parameter == String.class) {
                    return ctor.newInstance(categoryTranslationKey);
                }
            } catch (ReflectiveOperationException ignored) {
                // Try another constructor.
            }
        }

        if (categoryClass.isEnum()) {
            var constants = categoryClass.getEnumConstants();
            return constants.length > 0 ? constants[0] : null;
        }
        return null;
    }

    private static String sanitizePath(String path) {
        return path.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9/._-]", "_");
    }

    private static Object unwrapHolder(Object value) {
        if (value == null) {
            return null;
        }
        return invokeAny(value, "value", "get").orElse(value);
    }

    private static Optional<Object> invokeAny(Object target, String... names) {
        if (target == null) {
            return Optional.empty();
        }
        for (String name : names) {
            var method = findMethod(target.getClass(), name);
            if (method == null || method.getParameterCount() != 0) {
                continue;
            }
            try {
                method.setAccessible(true);
                return Optional.ofNullable(method.invoke(target));
            } catch (IllegalAccessException | InvocationTargetException ignored) {
                // Try next method.
            }
        }
        return Optional.empty();
    }

    private static Optional<Object> invoke(Object target, String name, Class<?>[] parameters, Object... args) {
        if (target == null) {
            return Optional.empty();
        }
        var method = findMethod(target.getClass(), name, parameters);
        if (method == null) {
            return Optional.empty();
        }
        try {
            method.setAccessible(true);
            return Optional.ofNullable(method.invoke(target, args));
        } catch (IllegalAccessException | InvocationTargetException ignored) {
            return Optional.empty();
        }
    }

    private static Optional<Object> readField(Object target, String... names) {
        if (target == null) {
            return Optional.empty();
        }
        for (String name : names) {
            var field = findField(target.getClass(), name);
            if (field == null) {
                continue;
            }
            try {
                field.setAccessible(true);
                return Optional.ofNullable(field.get(target));
            } catch (IllegalAccessException ignored) {
                // Try next field.
            }
        }
        return Optional.empty();
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Keep walking.
            }
        }
        for (Class<?> iface : type.getInterfaces()) {
            try {
                return iface.getDeclaredMethod(name, parameterTypes);
            } catch (NoSuchMethodException ignored) {
                // Keep trying.
            }
        }
        return null;
    }

    private static Field findField(Class<?> type, String name) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                // Keep walking.
            }
        }
        return null;
    }

    private static Method[] allMethods(Class<?> type) {
        var methods = new java.util.ArrayList<Method>();
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            methods.addAll(java.util.Arrays.asList(current.getDeclaredMethods()));
        }
        return methods.toArray(Method[]::new);
    }

    private static int asInt(Object value) {
        return value instanceof Number n ? n.intValue() : Integer.parseInt(String.valueOf(value));
    }

    private static long asLong(Object value) {
        return value instanceof Number n ? n.longValue() : Long.parseLong(String.valueOf(value));
    }

    private static float asFloat(Object value) {
        return value instanceof Number n ? n.floatValue() : Float.parseFloat(String.valueOf(value));
    }

    private static double asDouble(Object value) {
        return value instanceof Number n ? n.doubleValue() : Double.parseDouble(String.valueOf(value));
    }

    private static boolean asBoolean(Object value) {
        return value instanceof Boolean b ? b : Boolean.parseBoolean(String.valueOf(value));
    }
}
