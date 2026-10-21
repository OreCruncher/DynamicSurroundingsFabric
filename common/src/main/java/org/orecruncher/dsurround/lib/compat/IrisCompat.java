package org.orecruncher.dsurround.lib.compat;

import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.function.ThrottledBooleanSupplier;
import org.orecruncher.dsurround.lib.platform.KnownMod;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is in use (Iris, or Oculus, its NeoForge port). A shader pack replaces the rendering,
 * so shaders of ours wouldn't fit in, and effects that use them are turned off while one is active. Asked through
 * Iris's public API by reflection, so there is no dependency on it. If the API can't be reached, assumes not.
 */
public final class IrisCompat {

    // Asked by every effect drawn with a shader of ours, many times a frame; a player changes it now and then
    private static final long SHADER_PACK_CHECK_NANOS = 50_000_000L;
    // Only when the API was reached
    @Nullable
    private static final ThrottledBooleanSupplier SHADER_PACK_IN_USE;

    static {
        ThrottledBooleanSupplier inUse = null;
        if (KnownMod.IRIS.isInstalled()) {
            try {
                var apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                Object api = apiClass.getMethod("getInstance").invoke(null);
                Method method = apiClass.getMethod("isShaderPackInUse");
                inUse = new ThrottledBooleanSupplier(() -> askShaderPackInUse(api, method), SHADER_PACK_CHECK_NANOS,
                        System::nanoTime);
            } catch (ReflectiveOperationException | LinkageError e) {
                Library.LOGGER.warn("Unable to reach the Iris API; effects drawn with shaders may not suit shader packs: %s", e);
            }
        }
        SHADER_PACK_IN_USE = inUse;
    }

    private IrisCompat() {
    }

    /**
     * Whether an Iris shader pack is in use. Kept for a short while (a tick or so) rather than asked by reflection on
     * every call.
     */
    public static boolean isShaderPackInUse() {
        return SHADER_PACK_IN_USE != null && SHADER_PACK_IN_USE.getAsBoolean();
    }

    private static boolean askShaderPackInUse(Object api, Method method) {
        try {
            return (boolean) method.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
