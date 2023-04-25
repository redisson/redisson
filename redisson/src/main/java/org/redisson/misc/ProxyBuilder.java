/**
 * Copyright (c) 2013-2022 Nikita Koksharov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.redisson.misc;

import org.redisson.api.RFuture;
import org.redisson.connection.ServiceManager;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Objects;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 *
 * @author Nikita Koksharov
 *
 */
//TODO refactor to common object and MethodHandles
//MethodHandle mh = MethodHandles.lookup().findVirtual(implementation.getClass(), method.getName(),
//                MethodType.methodType(method.getReturnType(), method.getParameterTypes()));
public class ProxyBuilder {

    public interface Callback {

        Object execute(Callable<RFuture<Object>> callable, Method instanceMethod);

    }

    private static class CacheKey {

        final Method method;
        final Class<?> instanceClass;

        CacheKey(Method method, Class<?> instanceClass) {
            super();
            this.method = method;
            this.instanceClass = instanceClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            CacheKey cacheKey = (CacheKey) o;
            return method.equals(cacheKey.method) && instanceClass.equals(cacheKey.instanceClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(method, instanceClass);
        }
    }

    private static final ConcurrentMap<CacheKey, Method> METHODS_MAPPING = new ConcurrentHashMap<CacheKey, Method>();

    public static <T> T create(Callback commandExecutor, Object instance, Object implementation, Class<T> clazz, ServiceManager serviceManager) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                Method instanceMethod = getMethod(method, instance, implementation);

                if (instanceMethod.getName().endsWith("Async")) {
                    Callable<RFuture<Object>> callable = () -> (RFuture<Object>) instanceMethod.invoke(instance, args);
                    return commandExecutor.execute(callable, method);
                }

                if (implementation != null
                        && instanceMethod.getDeclaringClass().isAssignableFrom(implementation.getClass())) {
                    return instanceMethod.invoke(implementation, args);
                }

                return instanceMethod.invoke(instance, args);
            }
        };
        return (T) Proxy.newProxyInstance(clazz.getClassLoader(), new Class[] { clazz }, handler);
    }

    private static Method getMethod(Method method, Object instance, Object implementation) throws NoSuchMethodException {
        CacheKey key = new CacheKey(method, instance.getClass());
        Method instanceMethod = METHODS_MAPPING.get(key);
        if (instanceMethod == null) {
            if (implementation != null) {
                try {
                    instanceMethod = implementation.getClass().getMethod(method.getName(), method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    try {
                        instanceMethod = instance.getClass().getMethod(method.getName() + "Async", method.getParameterTypes());
                    } catch (Exception e2) {
                        instanceMethod = instance.getClass().getMethod(method.getName(), method.getParameterTypes());
                    }
                }
            } else {
                try {
                    instanceMethod = instance.getClass().getMethod(method.getName() + "Async", method.getParameterTypes());
                } catch (NoSuchMethodException e) {
                    instanceMethod = instance.getClass().getMethod(method.getName(), method.getParameterTypes());
                }
            }

            METHODS_MAPPING.put(key, instanceMethod);
        }
        return instanceMethod;
    }

}
