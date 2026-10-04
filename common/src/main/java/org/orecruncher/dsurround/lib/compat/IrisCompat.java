package org.orecruncher.dsurround.lib.compat;

import dev.architectury.platform.Platform;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Constants;
import org.orecruncher.dsurround.lib.Library;

import java.lang.reflect.Method;

/**
 * Whether an Iris shader pack is in use (Iris, or Oculus, its NeoForge port). A shader pack replaces the rendering,
 * so shaders of ours wouldn't fit in, and effects that use them are turned off while one is active. Asked through
 * Iris's public API by reflection, so there is no dependency on it. If the API can't be reached, assumes not.
 */
public final class IrisCompat {

    @Nullable
    private static final Object API;
    @Nullable
    private static final Method IS_SHADER_PACK_IN_USE;

    static {
        Object api = null;
        Method method = null;
        if (Platform.isModLoaded(Constants.IRIS) || Platform.isModLoaded(Constants.OCULUS)) {
            try {
                var apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                api = apiClass.getMethod("getInstance").invoke(null);
                method = apiClass.getMethod("isShaderPackInUse");
            } catch (ReflectiveOperationException | LinkageError e) {
                Library.LOGGER.warn("Unable to reach the Iris API; effects drawn with shaders may not suit shader packs: %s", e);
            }
        }
        API = api;
        IS_SHADER_PACK_IN_USE = method;
    }

    private IrisCompat() {
    }

    public static boolean isShaderPackInUse() {
        if (API == null || IS_SHADER_PACK_IN_USE == null)
            return false;
        try {
            return (boolean) IS_SHADER_PACK_IN_USE.invoke(API);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }
}
