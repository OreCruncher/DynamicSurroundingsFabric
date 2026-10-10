package org.orecruncher.dsurround.testing;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Map;
import java.util.function.Function;

/**
 * Stand-ins for interfaces in tests, without a mocking library. A fake answers the methods named in its answers, by
 * method name (so all overloads of a name share an answer); runs an interface's default methods as written; and
 * fails any other call, so a test can't quietly depend on something it didn't set up.
 */
public final class Fakes {

    private Fakes() {
    }

    @SuppressWarnings("unchecked")
    public static <T> T of(Class<T> type, Map<String, Function<Object[], Object>> answers) {
        InvocationHandler handler = (proxy, method, args) -> {
            var arguments = args == null ? new Object[0] : args;
            var answer = answers.get(method.getName());
            if (answer != null)
                return answer.apply(arguments);
            switch (method.getName()) {
                case "toString":
                    return "fake " + type.getSimpleName();
                case "hashCode":
                    return System.identityHashCode(proxy);
                case "equals":
                    return proxy == arguments[0];
                default:
                    break;
            }
            if (method.isDefault())
                return InvocationHandler.invokeDefault(proxy, method, args);
            throw new UnsupportedOperationException("fake " + type.getSimpleName() + " wasn't given " + method.getName());
        };
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type}, handler);
    }
}
