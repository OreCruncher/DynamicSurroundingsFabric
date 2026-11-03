// SPDX-License-Identifier: MIT
// Copyright (c) 2023-2026 OreCruncher - https://github.com/OreCruncher/DynamicSurroundingsFabric

package org.orecruncher.dsurround.lib.compat;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.lib.Library;
import org.orecruncher.dsurround.lib.function.ThrottledBooleanSupplier;
import org.orecruncher.dsurround.lib.platform.KnownMod;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Whether an Iris shader pack is in use (Iris, or Oculus, its NeoForge port). A shader pack replaces the rendering,
 * so shaders of ours wouldn't fit in, and effects that use them are turned off while one is active. Pipelines of ours
 * that still draw then (particles drawn with vanilla's shader) are assigned one of the pack's programs
 * ({@link #assignPipeline}): Iris draws one it hasn't been told about with none, and they vanished. Asked through
 * Iris's public API by reflection, so there is no dependency on it. If the API can't be reached, assumes not.
 */
public final class IrisCompat {

    // Asked by every effect drawn with a shader of ours, many times a frame; a player changes it now and then
    private static final long SHADER_PACK_CHECK_NANOS = 50_000_000L;

    // Assignments made before the API was looked up, applied once it is
    private static final List<Assignment> PENDING = new ArrayList<>();
    private static boolean apiLookedUp;

    /**
     * The programs of a shader pack a pipeline of ours can be drawn with: Iris's {@code IrisProgram}, by name
     */
    public enum Program {
        PARTICLES_TRANSLUCENT
    }

    private record Assignment(RenderPipeline pipeline, Program program) {
    }

    private IrisCompat() {
    }

    /**
     * Whether an Iris shader pack is in use. Kept for a short while (a tick or so) rather than asked by reflection on
     * every call.
     */
    public static boolean isShaderPackInUse() {
        return Api.SHADER_PACK_IN_USE != null && Api.SHADER_PACK_IN_USE.getAsBoolean();
    }

    /**
     * Tells Iris which of a shader pack's programs to draw a pipeline with while a pack is in use; called as the
     * pipeline is made, before it is first drawn. Kept until Iris's API is first looked up (whether a shader pack is
     * in use is asked as our shaders are checked after a resource reload, before anything is drawn), so a class can
     * call this as it loads without asking the loader about Iris then. Does nothing without Iris, or with one too old
     * to be told.
     */
    public static void assignPipeline(RenderPipeline pipeline, Program program) {
        var assignment = new Assignment(pipeline, program);
        synchronized (PENDING) {
            if (!apiLookedUp) {
                PENDING.add(assignment);
                return;
            }
        }
        Api.assign(assignment);
    }

    private static boolean askShaderPackInUse(Object api, Method method) {
        try {
            return (boolean) method.invoke(api);
        } catch (ReflectiveOperationException | RuntimeException e) {
            return false;
        }
    }

    /**
     * Iris's API, looked up when first wanted
     */
    private static final class Api {

        // Only when the API was reached
        @Nullable
        static final ThrottledBooleanSupplier SHADER_PACK_IN_USE;
        // IrisApi.assignPipeline(RenderPipeline, IrisProgram), its API object and IrisProgram; only when the API has it
        @Nullable
        private static final Method ASSIGN_PIPELINE;
        @Nullable
        private static final Object API;
        @Nullable
        private static final Class<?> PROGRAM_CLASS;

        static {
            ThrottledBooleanSupplier inUse = null;
            Method assign = null;
            Object apiObject = null;
            Class<?> programClass = null;
            if (KnownMod.IRIS.isInstalled()) {
                try {
                    var apiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                    Object api = apiClass.getMethod("getInstance").invoke(null);
                    Method method = apiClass.getMethod("isShaderPackInUse");
                    inUse = new ThrottledBooleanSupplier(() -> askShaderPackInUse(api, method), SHADER_PACK_CHECK_NANOS,
                            System::nanoTime);
                    apiObject = api;
                    programClass = Class.forName("net.irisshaders.iris.api.v0.IrisProgram");
                    assign = apiClass.getMethod("assignPipeline", RenderPipeline.class, programClass);
                } catch (NoSuchMethodException | ClassNotFoundException e) {
                    if (apiObject == null)
                        Library.LOGGER.warn("Unable to reach the Iris API; effects drawn with shaders may not suit shader packs: %s", e);
                    else
                        Library.LOGGER.warn("This Iris can't be told about the mod's pipelines; particles may not show with a shader pack: %s", e);
                    programClass = null;
                } catch (ReflectiveOperationException | LinkageError e) {
                    Library.LOGGER.warn("Unable to reach the Iris API; effects drawn with shaders may not suit shader packs: %s", e);
                    programClass = null;
                }
            }
            SHADER_PACK_IN_USE = inUse;
            ASSIGN_PIPELINE = assign;
            API = apiObject;
            PROGRAM_CLASS = programClass;

            List<Assignment> pending;
            synchronized (PENDING) {
                apiLookedUp = true;
                pending = List.copyOf(PENDING);
                PENDING.clear();
            }
            pending.forEach(Api::assign);
        }

        @SuppressWarnings({"unchecked", "rawtypes"})
        static void assign(Assignment assignment) {
            if (ASSIGN_PIPELINE == null || API == null || PROGRAM_CLASS == null)
                return;
            try {
                ASSIGN_PIPELINE.invoke(API, assignment.pipeline(),
                        Enum.valueOf((Class<? extends Enum>) PROGRAM_CLASS, assignment.program().name()));
            } catch (ReflectiveOperationException | RuntimeException e) {
                Library.LOGGER.warn("Unable to tell Iris about %s: %s", assignment.pipeline().getLocation(), e);
            }
        }
    }
}
