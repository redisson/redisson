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

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.redisson.RedissonLiveObjectService;
import org.redisson.RedissonMap;
import org.redisson.RedissonQueue;
import org.redisson.RedissonReference;
import org.redisson.RedissonSet;
import org.redisson.RedissonSortedSet;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.api.RObject;
import org.redisson.api.RObjectAsync;
import org.redisson.api.RObjectReactive;
import org.redisson.api.RObjectRx;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.api.RedissonRxClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RId;
import org.redisson.api.annotation.RObjectField;
import org.redisson.client.codec.Codec;
import org.redisson.codec.DefaultReferenceCodecProvider;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.config.Config;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 *
 */
public class RedissonObjectBuilder {

    private static final Map<Class<?>, Class<? extends RObject>> TRANSLATABLE_TYPE_MAPPING = new LinkedHashMap<Class<?>, Class<? extends RObject>>();

    static {
        TRANSLATABLE_TYPE_MAPPING.put(SortedSet.class,      RedissonSortedSet.class);
        TRANSLATABLE_TYPE_MAPPING.put(Set.class,            RedissonSet.class);
        TRANSLATABLE_TYPE_MAPPING.put(ConcurrentMap.class,  RedissonMap.class);
        TRANSLATABLE_TYPE_MAPPING.put(Map.class,            RedissonMap.class);
        TRANSLATABLE_TYPE_MAPPING.put(BlockingDeque.class,  RedissonBlockingDeque.class);
        TRANSLATABLE_TYPE_MAPPING.put(Deque.class,          RedissonDeque.class);
        TRANSLATABLE_TYPE_MAPPING.put(BlockingQueue.class,  RedissonBlockingQueue.class);
        TRANSLATABLE_TYPE_MAPPING.put(Queue.class,          RedissonQueue.class);
        TRANSLATABLE_TYPE_MAPPING.put(List.class,           RedissonList.class);
    }

    private final Config config;
    
    private static final Map<Class<?>, RedissonIntrospector.CodecMethodRef> REFERENCES = RedissonIntrospector.getObjectBuilderMethods();
    private static final Map<String, Class<?>> SUPPORTED_TYPES = RedissonIntrospector.getSupportedTypes();

    private final ReferenceCodecProvider codecProvider = new DefaultReferenceCodecProvider();
    
    public RedissonObjectBuilder(Config config) {
        super();
        this.config = config;
    }

    public ReferenceCodecProvider getReferenceCodecProvider() {
        return codecProvider;
    }
    
    public void store(Object ar, String fieldName, RMap<String, Object> liveMap) {
        Codec codec = ClassUtils.tryGetFieldWithGetter(ar, "codec");
        String name = ClassUtils.tryGetFieldWithGetter(ar, "name");
        if (codec != null) {
            codecProvider.registerCodec((Class) codec.getClass(), codec);
        }
        liveMap.fastPut(fieldName,
                new RedissonReference(ar.getClass(), name, codec));
    }
    
    public Object createObject(Object id, Class<?> clazz, Class<?> fieldType, String fieldName, RedissonClient redisson) {
        Class<?> mappedClass = tryTranslatedTypes(fieldType);
        try {
            if (mappedClass != null) {
                Codec fieldCodec = getFieldCodec(clazz, mappedClass, fieldName);
                NamingScheme fieldNamingScheme = getNamingScheme(clazz, fieldCodec);
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
    private Codec getFieldCodec(Class<?> rEntity, Class<?> rObjectClass, String fieldName) throws Exception {
        Field field = ClassUtils.getDeclaredField(rEntity, fieldName);
        if (field.isAnnotationPresent(RObjectField.class)) {
            RObjectField anno = field.getAnnotation(RObjectField.class);
            return codecProvider.getCodec(anno, rEntity, rObjectClass, fieldName, config);
        } else {
            REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
            return codecProvider.getCodec(anno, (Class<?>) rEntity, config);
        }
    }
    
    public NamingScheme getNamingScheme(Class<?> entityClass) {
        REntity anno = ClassUtils.getAnnotation(entityClass, REntity.class);
        Codec codec = codecProvider.getCodec(anno, entityClass, config);
        return getNamingScheme(entityClass, codec);
    }
    
    public NamingScheme getNamingScheme(Class<?> rEntity, Codec c) {
        REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
        try {
            return anno.namingScheme().getDeclaredConstructor(Codec.class).newInstance(c);
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }

    public static Class<?> tryTranslatedTypes(Class<?> cls) {
        if (RObjectAsync.class.isAssignableFrom(cls)
            || RObjectRx.class.isAssignableFrom(cls)) {
            return cls;
        }
        for (Entry<Class<?>, Class<? extends RObject>> entrySet : TRANSLATABLE_TYPE_MAPPING.entrySet()) {
            if (entrySet.getKey().isAssignableFrom(cls)) {
                return entrySet.getValue();
            }
        }
        return null;
    }

    public static Class<?> getSupportedTypes(String simpleName) {
        return SUPPORTED_TYPES.get(simpleName);
    }

    public Object fromReference(RedissonClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getType();
        if (type != null) {
            if (ClassUtils.isAnnotationPresent(type, REntity.class)) {
                RedissonLiveObjectService liveObjectService = (RedissonLiveObjectService) redisson.getLiveObjectService();
                
                NamingScheme ns = getNamingScheme(type);
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
            RedissonIntrospector.CodecMethodRef b = REFERENCES.get(type);
            if (b == null && type.getInterfaces().length > 0) {
                type = type.getInterfaces()[0];
            }
            b = REFERENCES.get(type);
            if (b != null) {
                Method builder = b.get(isDefaultCodec(rr));
                if (isDefaultCodec(rr)) {
                    return builder.invoke(redisson, rr.getKeyName());
                }
                return builder.invoke(redisson, rr.getKeyName(), codecProvider.getCodec(rr.getCodecType()));
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + rr.getTypeName() + " with codec type of " + rr.getCodecName());
    }
    
    private boolean isDefaultCodec(RedissonReference rr) {
        return rr.getCodec() == null;
    }

    public Object fromReference(RedissonRxClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getReactiveType();
        /**
         * Live Object from reference in reactive client is not supported yet.
         */
        return getObject(redisson, rr, type, codecProvider);
    }
    
    public Object fromReference(RedissonReactiveClient redisson, RedissonReference rr) throws Exception {
        Class<? extends Object> type = rr.getReactiveType();
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
            
            RObject rObject = (RObject) object;
            if (rObject.getCodec() != null) {
                codecProvider.registerCodec((Class) rObject.getCodec().getClass(), rObject.getCodec());
            }
            return new RedissonReference(clazz, rObject.getName(), rObject.getCodec());
        }
        if (object instanceof RObjectReactive && !(object instanceof RLiveObject)) {
            Class<?> clazz = object.getClass().getInterfaces()[0];

            RObjectReactive rObject = (RObjectReactive) object;
            if (rObject.getCodec() != null) {
                codecProvider.registerCodec((Class) rObject.getCodec().getClass(), rObject.getCodec());
            }
            return new RedissonReference(clazz, rObject.getName(), rObject.getCodec());
        }
        
        try {
            if (object instanceof RLiveObject) {
                Class<? extends Object> rEntity = object.getClass().getSuperclass();
                NamingScheme ns = getNamingScheme(rEntity);
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

    public <T, K extends Codec> T createRObject(RedissonClient redisson, Class<T> expectedType, String name, K codec) throws Exception {

        List<Class<?>> interfaces;
        if (expectedType.isInterface()) {
            interfaces = new ArrayList<>();
            interfaces.add(expectedType);
        } else {
            interfaces = Arrays.asList(expectedType.getInterfaces());
        }
        for (Class<?> iType : interfaces) {
            if (REFERENCES.containsKey(iType)) {// user cache to speed up things a little.
                Method builder = REFERENCES.get(iType).get(codec == null);
                if (codec == null) {
                    return (T) builder.invoke(redisson, name);
                }
                return (T) builder.invoke(redisson, name, codec);
            }
        }
        
        String type = null;
        if (expectedType != null) {
            type = expectedType.getName();
        }
        String codecName = null;
        if (codec != null) {
            codecName = codec.getClass().getName();
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + type + " with codec type of " + codecName);
    }


}
