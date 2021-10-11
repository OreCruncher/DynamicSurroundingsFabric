package org.orecruncher.dsurround.lib.reflection;

import com.google.common.base.Preconditions;
import org.apache.commons.lang3.StringUtils;
import org.jetbrains.annotations.Nullable;
import org.orecruncher.dsurround.Client;

import java.lang.reflect.Method;

@SuppressWarnings("unused")
public class ReflectedMethod<R> {

    protected final String className;
    protected final String methodName;
    protected final Method method;

    public ReflectedMethod(final String className, final String methodName,
                           @Nullable final String obfMethodName, Class<?>... parameters) {
        this.className = className;
        this.methodName = methodName;
        this.method = ReflectionHelper.resolveMethod(className, new String[]{methodName, obfMethodName}, parameters);

        if (isNotAvailable()) {
            final String msg = String.format("Unable to locate method [%s::%s]", this.className, methodName);
            Client.LOGGER.warn(msg);
        }
    }

    public ReflectedMethod( final Class<?> clazz,  final String methodName,
                           @Nullable final String obfMethodName, Class<?>... parameters) {
        Preconditions.checkNotNull(clazz);
        Preconditions.checkArgument(StringUtils.isNotEmpty(methodName), "Field name cannot be empty");
        this.className = clazz.getName();
        this.methodName = methodName;
        this.method = ReflectionHelper.resolveMethod(clazz, new String[]{methodName, obfMethodName}, parameters);

        if (isNotAvailable()) {
            final String msg = String.format("Unable to locate method [%s::%s]", this.className, methodName);
            Client.LOGGER.warn(msg);
        }
    }

    public boolean isNotAvailable() {
        return this.method == null;
    }

    @SuppressWarnings("unchecked")
    public R invoke(Object ref, Object... params) {
        check();
        try {
            return (R) this.method.invoke(ref, params);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected void check() {
        if (isNotAvailable()) {
            final String msg = String.format("Uninitialized method [%s::%s]", this.className, this.methodName);
            throw new IllegalStateException(msg);
        }
    }

}