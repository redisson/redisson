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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
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
import org.redisson.RedissonLiveObjectService;
import org.redisson.RedissonMap;
import org.redisson.RedissonQueue;
import org.redisson.RedissonReference;
import org.redisson.RedissonSet;
import org.redisson.RedissonSortedSet;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.api.RObject;
import org.redisson.api.RObjectReactive;
import org.redisson.api.RObjectRx;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RId;
import org.redisson.api.annotation.RObjectField;
import org.redisson.client.codec.Codec;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.config.Config;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

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

    private final Config config;
    private final ReferenceCodecProvider codecProvider;
    
    public static class CodecMethodRef {

        Method defaultCodecMethod;
        Method customCodecMethod;

        Method get(boolean value) {
            if (value) {
                return defaultCodecMethod;
            }
            return customCodecMethod;
        }
    }
    
    private static final Map<Class<?>, CodecMethodRef> references = new HashMap<Class<?>, CodecMethodRef>();
    
    public RedissonObjectBuilder(Config config) {
        super();
        this.config = config;
        this.codecProvider = config.getReferenceCodecProvider();
        fillCodecMethods(references, RedissonClient.class, RObject.class);
        fillCodecMethods(references, RedissonReactiveClient.class, RObjectReactive.class);
        fillCodecMethods(references, RedissonRxClient.class, RObjectRx.class);
    }

    public void store(RObject ar, String fieldName, RMap<String, Object> liveMap) {
        Codec codec = ar.getCodec();
        if (codec != null) {
            codecProvider.registerCodec((Class) codec.getClass(), codec);
        }
        liveMap.fastPut(fieldName,
                new RedissonReference(ar.getClass(), ar.getName(), codec));
    }
    
    public RObject createObject(Object id, Class<?> clazz, Class<?> fieldType, String fieldName, RedissonClient redisson) {
        Class<? extends RObject> mappedClass = getMappedClass(fieldType);
        try {
            if (mappedClass != null) {
                Codec fieldCodec = getFieldCodec(clazz, mappedClass, fieldName);
                NamingScheme fieldNamingScheme = getFieldNamingScheme(clazz, fieldName, fieldCodec);
                String referenceName = fieldNamingScheme.getFieldReferenceName(clazz, id, mappedClass, fieldName, null);
                
                return createRObject(redisson, mappedClass, referenceName, fieldCodec);
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
            return codecProvider.getCodec(anno, rEntity, rObjectClass, fieldName, config);
        } else {
            REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
            return codecProvider.getCodec(anno, (Class<?>) rEntity, config);
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
    
    private void fillCodecMethods(Map<Class<?>, CodecMethodRef> b, Class<?> clientClazz, Class<?> objectClazz) {
        for (Method method : clientClazz.getDeclaredMethods()) {
            if (!method.getReturnType().equals(Void.TYPE)
                    && objectClazz.isAssignableFrom(method.getReturnType())
                    && method.getName().startsWith("get")) {
                Class<?> cls = method.getReturnType();
                if (!b.containsKey(cls)) {
                    b.put(cls, new CodecMethodRef());
                }
                CodecMethodRef builder = b.get(cls);
                if (method.getParameterTypes().length == 2 //first param is name, second param is codec.
                        && Codec.class.isAssignableFrom(method.getParameterTypes()[1])) {
                    builder.customCodecMethod = method;
                } else if (method.getParameterTypes().length == 1) {
                    builder.defaultCodecMethod = method;
                }
            }
        }
    }

    public Object fromReference(RedissonClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getType();
        ReferenceCodecProvider codecProvider = redisson.getConfig().getReferenceCodecProvider();
        if (type != null) {
            if (ClassUtils.isAnnotationPresent(type, REntity.class)) {
                RedissonLiveObjectService liveObjectService = (RedissonLiveObjectService) redisson.getLiveObjectService();
                REntity anno = ClassUtils.getAnnotation(type, REntity.class);
                NamingScheme ns = anno.namingScheme()
                        .getDeclaredConstructor(Codec.class)
                        .newInstance(codecProvider.getCodec(anno, type, redisson.getConfig()));
                Object id = ns.resolveId(rr.getKeyName());
                return liveObjectService.createLiveObject(type, id);
            }
        }

        return getObject(redisson, rr, type, codecProvider);
    }

    private Object getObject(Object redisson, RedissonReference rr, Class<? extends Object> type,
            ReferenceCodecProvider codecProvider)
            throws IllegalAccessException, InvocationTargetException, Exception, ClassNotFoundException {
        if (type != null) {
            CodecMethodRef b = references.get(type);
            if (b == null && type.getInterfaces().length > 0) {
                type = type.getInterfaces()[0];
            }
            b = references.get(type);
            if (b != null) {
                Method builder = b.get(isDefaultCodec(rr));
                return (isDefaultCodec(rr)
                        ? builder.invoke(redisson, rr.getKeyName())
                                : builder.invoke(redisson, rr.getKeyName(), codecProvider.getCodec(rr.getCodecType())));
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + rr.getTypeName() + " with codec type of " + rr.getCodecName());
    }
    
    private boolean isDefaultCodec(RedissonReference rr) {
        return rr.getCodec() == null;
    }

    public Object fromReference(RedissonRxClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getReactiveType();
        ReferenceCodecProvider codecProvider = redisson.getConfig().getReferenceCodecProvider();
        /**
         * Live Object from reference in reactive client is not supported yet.
         */
        return getObject(redisson, rr, type, codecProvider);
    }
    
    public Object fromReference(RedissonReactiveClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getReactiveType();
        ReferenceCodecProvider codecProvider = redisson.getConfig().getReferenceCodecProvider();
        /**
         * Live Object from reference in reactive client is not supported yet.
         */
        return getObject(redisson, rr, type, codecProvider);
    }

    public RedissonReference toReference(Object object) {
        if (object != null && ClassUtils.isAnnotationPresent(object.getClass(), REntity.class)) {
            throw new IllegalArgumentException("REntity should be attached to Redisson before save");
        }
        
        if (object instanceof RObject && !(object instanceof RLiveObject)) {
            Class<?> clazz = object.getClass().getInterfaces()[0];
            
            RObject rObject = ((RObject) object);
            if (rObject.getCodec() != null) {
                config.getReferenceCodecProvider().registerCodec((Class) rObject.getCodec().getClass(), rObject.getCodec());
            }
            return new RedissonReference(clazz, rObject.getName(), rObject.getCodec());
        }
        if (object instanceof RObjectReactive && !(object instanceof RLiveObject)) {
            Class<?> clazz = object.getClass().getInterfaces()[0];

            RObjectReactive rObject = ((RObjectReactive) object);
            if (rObject.getCodec() != null) {
                config.getReferenceCodecProvider().registerCodec((Class) rObject.getCodec().getClass(), rObject.getCodec());
            }
            return new RedissonReference(clazz, rObject.getName(), rObject.getCodec());
        }
        
        try {
            if (object instanceof RLiveObject) {
                Class<? extends Object> rEntity = object.getClass().getSuperclass();
                REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
                NamingScheme ns = anno.namingScheme()
                        .getDeclaredConstructor(Codec.class)
                        .newInstance(config.getReferenceCodecProvider().getCodec(anno, (Class) rEntity, config));
                String name = Introspectior
                        .getFieldsWithAnnotation(rEntity, RId.class)
                        .getOnly().getName();
                Class<?> type = ClassUtils.getDeclaredField(rEntity, name).getType();
                
                return new RedissonReference(rEntity,
                        ns.getName(rEntity, type, name, ((RLiveObject) object).getLiveObjectId()));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return null;
    }

    public <T extends RObject, K extends Codec> T createRObject(RedissonClient redisson, Class<T> expectedType, String name, K codec) throws Exception {
        List<Class<?>> interfaces = Arrays.asList(expectedType.getInterfaces());
        for (Class<?> iType : interfaces) {
            if (references.containsKey(iType)) {// user cache to speed up things a little.
                Method builder = references.get(iType).get(codec != null);
                return (T) (codec != null
                        ? builder.invoke(redisson, name)
                        : builder.invoke(redisson, name, codec));
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + (expectedType != null ? expectedType.getName() : "null") + " with codec type of " + (codec != null ? codec.getClass().getName() : "null"));
    }
    
}
