package org.orecruncher.dsurround.lib.reflection;

import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class HandleCache {

    private static final Map<Method, IMethodCallHandler> METHOD_HANDLE_CACHE = new ConcurrentHashMap<>();
    private static final Map<Class<?>, IMethodCallHandler> FUNCTIONAL_INTERFACE_CACHE = new ConcurrentHashMap<>();

    HandleCache() {}

    public static IMethodCallHandler forFunctionalInterface(Class<?> clazz) {
        return FUNCTIONAL_INTERFACE_CACHE.computeIfAbsent(clazz, c -> {
            var method = ReflectionHelper.findSamMethod(c);
            return asMethodCallHandler(method);
        });
    }

    public static IMethodCallHandler forMethod(Class<?> type, String name, Class<?>... parameterTypes) {
        return forMethod(type, new String[]{name}, parameterTypes);
    }

    public static IMethodCallHandler forMethod(Class<?> type, String[] names,  Class<?>... parameterTypes) {
        return ReflectionHelper.findMethod(type, names, parameterTypes)
                .map(HandleCache::forMethod)
                .orElseThrow();
    }

    public static IMethodCallHandler forMethod(Method method) {
        return METHOD_HANDLE_CACHE.computeIfAbsent(method, HandleCache::asMethodCallHandler);
    }

    private static IMethodCallHandler asMethodCallHandler(Method method) {
        try {
            var methodHandle = MethodHandles.lookup().unreflect(method);
            return (target, args) -> {
                try {
                    return methodHandle.bindTo(target).invokeWithArguments(args);
                } catch (Throwable e) {
                    throw new RuntimeException(e);
                }
            };
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

}
