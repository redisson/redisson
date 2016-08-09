/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.liveobject.misc;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.redisson.RedissonClient;
import org.redisson.RedissonReference;
import org.redisson.client.codec.Codec;
import org.redisson.api.RBitSet;
import org.redisson.api.RObject;
import org.redisson.liveobject.provider.CodecProvider;
import org.redisson.liveobject.provider.ResolverProvider;
import org.redisson.liveobject.resolver.NamingScheme;
import org.redisson.liveobject.annotation.REntity;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class RedissonObjectFactory {
    
    public static <T> T create(RedissonClient redisson, CodecProvider codecProvider, ResolverProvider resolverProvider, RedissonReference rr, Class<?> expected) throws Exception {
        Class<? extends Object> type = rr.getType();
        if (type != null) {
            if (type.isAnnotationPresent(REntity.class)) {
                REntity anno = type.getAnnotation(REntity.class);
                NamingScheme ns = anno.namingScheme()
                        .getDeclaredConstructor(Codec.class)
                        .newInstance(codecProvider.getCodec(anno, rr.getType()));
                return (T) redisson.getLiveObjectService(codecProvider, resolverProvider).getOrCreate(type, ns.resolveId(rr.getKeyName()));
            }
            List<Class<?>> interfaces = Arrays.asList(rr.getType().getInterfaces());
            for (Method method : RedissonClient.class.getDeclaredMethods()) {
                if (method.getName().startsWith("get")
                        && method.getReturnType().isAssignableFrom(type)
                        && expected.isAssignableFrom(method.getReturnType())
                        && interfaces.contains(method.getReturnType())) {
                    if ((rr.isDefaultCodec() || RBitSet.class.isAssignableFrom(method.getReturnType())) && method.getParameterTypes().length == 1) {
                        return (T) method.invoke(redisson, rr.getKeyName());
                    } else if (!rr.isDefaultCodec()
                            && method.getParameterTypes().length == 2
                            && String.class.equals(method.getParameterTypes()[0])
                            && Codec.class.equals(method.getParameterTypes()[1])) {
                        return (T) method.invoke(redisson, rr.getKeyName(), codecProvider.getCodec(rr.getCodecType()));
                    }
                }
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + rr.getTypeName() + " with codec type of " + rr.getCodecName());
    }
    
    public static <T extends RObject, K extends Codec> T create(RedissonClient redisson, Class<T> expectedType, String name, K codec) throws Exception {
        List<Class<?>> interfaces = Arrays.asList(expectedType.getInterfaces());
        for (Method method : RedissonClient.class.getDeclaredMethods()) {
                if (method.getName().startsWith("get")
                        && method.getReturnType().isAssignableFrom(expectedType)
                        && interfaces.contains(method.getReturnType())) {
                    if ((codec == null || RBitSet.class.isAssignableFrom(method.getReturnType())) && method.getParameterTypes().length == 1) {
                        return (T) method.invoke(redisson, name);
                    } else if (codec != null
                            && method.getParameterTypes().length == 2
                            && String.class.equals(method.getParameterTypes()[0])
                            && Codec.class.equals(method.getParameterTypes()[1])) {
                        return (T) method.invoke(redisson, name, codec);
                    }
                }
            }
        throw new ClassNotFoundException("No RObject is found to match class type of " + (expectedType != null ? expectedType.getName() : "null") + " with codec type of " + (codec != null ? codec.getClass().getName() : "null"));
    }
    
}
