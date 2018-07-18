/**
 * Copyright 2018 Nikita Koksharov
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
package org.redisson.liveobject.core;

import java.lang.reflect.Field;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

import org.redisson.RedissonBlockingDeque;
import org.redisson.RedissonBlockingQueue;
import org.redisson.RedissonDeque;
import org.redisson.RedissonList;
import org.redisson.RedissonMap;
import org.redisson.RedissonQueue;
import org.redisson.RedissonReference;
import org.redisson.RedissonSet;
import org.redisson.RedissonSortedSet;
import org.redisson.api.RMap;
import org.redisson.api.RObject;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RObjectField;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.resolver.NamingScheme;
import org.redisson.misc.RedissonObjectFactory;

import io.netty.util.internal.PlatformDependent;

/**
 * 
 * @author Rui Gu
 * @author Nikita Koksharov
 *
 */
public class RedissonObjectBuilder {

    private static final Map<Class<?>, Class<? extends RObject>> supportedClassMapping = new LinkedHashMap<Class<?>, Class<? extends RObject>>();
    ConcurrentMap<String, NamingScheme> namingSchemeCache = PlatformDependent.newConcurrentHashMap();
    
    static {
        supportedClassMapping.put(SortedSet.class,      RedissonSortedSet.class);
        supportedClassMapping.put(Set.class,            RedissonSet.class);
        supportedClassMapping.put(ConcurrentMap.class,  RedissonMap.class);
        supportedClassMapping.put(Map.class,            RedissonMap.class);
        supportedClassMapping.put(BlockingDeque.class,  RedissonBlockingDeque.class);
        supportedClassMapping.put(Deque.class,          RedissonDeque.class);
        supportedClassMapping.put(BlockingQueue.class,  RedissonBlockingQueue.class);
        supportedClassMapping.put(Queue.class,          RedissonQueue.class);
        supportedClassMapping.put(List.class,           RedissonList.class);
    }

    private final RedissonClient redisson;
    private final ReferenceCodecProvider codecProvider;
    
    public RedissonObjectBuilder(RedissonClient redisson) {
        super();
        this.redisson = redisson;
        this.codecProvider = redisson.getConfig().getReferenceCodecProvider();
    }

    public void store(RObject ar, String fieldName, RMap<String, Object> liveMap) {
        Codec codec = ar.getCodec();
        codecProvider.registerCodec((Class) codec.getClass(), ar, codec);
        liveMap.fastPut(fieldName,
                new RedissonReference(ar.getClass(), ar.getName(), codec));
    }
    
    public RObject createObject(Object id, Class<?> clazz, Class<?> fieldType, String fieldName) {
        Class<? extends RObject> mappedClass = getMappedClass(fieldType);
        try {
            if (mappedClass != null) {
                Codec fieldCodec = getFieldCodec(clazz, mappedClass, fieldName);
                NamingScheme fieldNamingScheme = getFieldNamingScheme(clazz, fieldName, fieldCodec);
                
                RObject obj = RedissonObjectFactory
                        .createRObject(redisson,
                                mappedClass,
                                fieldNamingScheme.getFieldReferenceName(clazz,
                                        id,
                                        mappedClass,
                                        fieldName,
                                        null),
                                fieldCodec);
                return obj;
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return null;
    }
    
    /**
     * WARNING: rEntity has to be the class of @This object.
     */
    private Codec getFieldCodec(Class<?> rEntity, Class<? extends RObject> rObjectClass, String fieldName) throws Exception {
        Field field = ClassUtils.getDeclaredField(rEntity, fieldName);
        if (field.isAnnotationPresent(RObjectField.class)) {
            RObjectField anno = field.getAnnotation(RObjectField.class);
            return codecProvider.getCodec(anno, rEntity, rObjectClass, fieldName);
        } else {
            REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
            return codecProvider.getCodec(anno, (Class<?>) rEntity);
        }
    }
    
    /**
     * WARNING: rEntity has to be the class of @This object.
     */
    private NamingScheme getFieldNamingScheme(Class<?> rEntity, String fieldName, Codec c) throws Exception {
        if (!namingSchemeCache.containsKey(fieldName)) {
            REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
            namingSchemeCache.putIfAbsent(fieldName, anno.namingScheme()
                    .getDeclaredConstructor(Codec.class)
                    .newInstance(c));
        }
        return namingSchemeCache.get(fieldName);
    }

    private Class<? extends RObject> getMappedClass(Class<?> cls) {
        for (Entry<Class<?>, Class<? extends RObject>> entrySet : supportedClassMapping.entrySet()) {
            if (entrySet.getKey().isAssignableFrom(cls)) {
                return entrySet.getValue();
            }
        }
        return null;
    }
    
}
