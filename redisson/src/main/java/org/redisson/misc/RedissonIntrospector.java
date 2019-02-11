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

import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.api.RObjectRx;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.client.codec.Codec;

import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 *
 * @author Nikita Koksharov
 * @author Rui Gu (https://github.com/jackygurui)
 *
 */
class RedissonIntrospector {

    static class CodecMethodRef {

        Method defaultCodecMethod;
        Method customCodecMethod;

        Method get(boolean value) {
            if (value) {
                return defaultCodecMethod;
            }
            return customCodecMethod;
        }
    }

    private static final Map<Class<?>, CodecMethodRef> references;
    private static final Map<String, Class<?>> simpleNames;

    static {
        HashMap<Class<?>, CodecMethodRef> methodRefHashMap = new HashMap<>();
        fillCodecMethods(methodRefHashMap, RedissonClient.class, RObject.class);
        fillCodecMethods(methodRefHashMap, RedissonReactiveClient.class, RObjectReactive.class);
        fillCodecMethods(methodRefHashMap, RedissonRxClient.class, RObjectRx.class);
        references = Collections.unmodifiableMap(methodRefHashMap);

        final HashMap names = new HashMap(references.size(), 1);
        for (Class<?> cls : references.keySet()) {
            names.put(cls.getSimpleName(), cls);
        }
        simpleNames = Collections.unmodifiableMap(names);
    }

    private static void fillCodecMethods(Map<Class<?>, CodecMethodRef> map, Class<?> clientClazz, Class<?> objectClazz) {
        for (Method method : clientClazz.getDeclaredMethods()) {
            if (!method.getReturnType().equals(Void.TYPE)
                    && objectClazz.isAssignableFrom(method.getReturnType())
                    && method.getName().startsWith("get")) {
                Class<?> cls = method.getReturnType();
                if (!map.containsKey(cls)) {
                    CodecMethodRef ref = new CodecMethodRef();
                    map.put(cls, ref);
                    try {
                        Class<?> asyncClass = Class.forName(cls.getName() + "Async");
                        map.put(asyncClass, ref);
                    } catch (ClassNotFoundException e) {
                        //ignore;
                    }
                }
                CodecMethodRef builder = map.get(cls);
                if (method.getParameterTypes().length == 2 //first param is name, second param is codec.
                        && Codec.class.isAssignableFrom(method.getParameterTypes()[1])) {
                    builder.customCodecMethod = method;
                } else if (method.getParameterTypes().length == 1) {
                    builder.defaultCodecMethod = method;
                }
            }
        }
    }

    static Map<Class<?>, CodecMethodRef> getObjectBuilderMethods() {
        return references;
    }

    static Map<String, Class<?>> getSupportedTypes() {
        return simpleNames;
    }
}
