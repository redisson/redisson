/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
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
        
        Object execute(Method mm, Object instance, Method instanceMethod, Object[] args);
        
    }

    private static class CacheKey {
        
        Method method;
        Class<?> instanceClass;
        
        CacheKey(Method method, Class<?> instanceClass) {
            super();
            this.method = method;
            this.instanceClass = instanceClass;
        }

        @Override
        @SuppressWarnings("AvoidInlineConditionals")
        public int hashCode() {
            final int prime = 31;
            int result = 1;
            result = prime * result + ((instanceClass == null) ? 0 : instanceClass.hashCode());
            result = prime * result + ((method == null) ? 0 : method.hashCode());
            return result;
        }

        @Override
        public boolean equals(Object obj) {
            if (this == obj)
                return true;
            if (obj == null)
                return false;
            if (getClass() != obj.getClass())
                return false;
            CacheKey other = (CacheKey) obj;
            if (instanceClass == null) {
                if (other.instanceClass != null)
                    return false;
            } else if (!instanceClass.equals(other.instanceClass))
                return false;
            if (method == null) {
                if (other.method != null)
                    return false;
            } else if (!method.equals(other.method))
                return false;
            return true;
        }
        
    }
    
    private static final ConcurrentMap<CacheKey, Method> METHODS_MAPPING = new ConcurrentHashMap<CacheKey, Method>();
    
    public static <T> T create(Callback commandExecutor, Object instance, Object implementation, Class<T> clazz) {
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
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
                
                Method mm = instanceMethod;
                if (instanceMethod.getName().endsWith("Async")) {
                    return commandExecutor.execute(mm, instance, method, args);
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
    
}
