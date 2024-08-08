/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.*;
import org.redisson.api.*;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RObjectField;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.ScoredEntry;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.MapScanResult;
import org.redisson.codec.DefaultReferenceCodecProvider;
import org.redisson.codec.ReferenceCodecProvider;
import org.redisson.config.Config;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.resolver.NamingScheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentMap;

/**
 * 
 * @author Rui Gu
 * @author Nikita Koksharov
 *
 */
public class RedissonObjectBuilder {

    public enum ReferenceType {RXJAVA, REACTIVE, DEFAULT}

    private static final Map<Class<?>, Class<? extends RObject>> SUPPORTED_CLASS_MAPPING = new LinkedHashMap<>();
    private static final Map<Class<?>, Method> DEFAULT_CODEC_REFERENCES = new HashMap<>();
    private static final Map<Class<?>, Method> CUSTOM_CODEC_REFERENCES = new HashMap<>();

    static {
        SUPPORTED_CLASS_MAPPING.put(SortedSet.class,      RedissonSortedSet.class);
        SUPPORTED_CLASS_MAPPING.put(Set.class,            RedissonSet.class);
        SUPPORTED_CLASS_MAPPING.put(ConcurrentMap.class,  RedissonMap.class);
        SUPPORTED_CLASS_MAPPING.put(Map.class,            RedissonMap.class);
        SUPPORTED_CLASS_MAPPING.put(BlockingDeque.class,  RedissonBlockingDeque.class);
        SUPPORTED_CLASS_MAPPING.put(Deque.class,          RedissonDeque.class);
        SUPPORTED_CLASS_MAPPING.put(BlockingQueue.class,  RedissonBlockingQueue.class);
        SUPPORTED_CLASS_MAPPING.put(Queue.class,          RedissonQueue.class);
        SUPPORTED_CLASS_MAPPING.put(List.class,           RedissonList.class);
        
        fillCodecMethods(RedissonClient.class, RObject.class);
        fillCodecMethods(RedissonReactiveClient.class, RObjectReactive.class);
        fillCodecMethods(RedissonRxClient.class, RObjectRx.class);
    }

    private final Config config;
    private RedissonClient redisson;
    private RedissonReactiveClient redissonReactive;
    private RedissonRxClient redissonRx;
    
    private final ReferenceCodecProvider codecProvider = new DefaultReferenceCodecProvider();
    
    public RedissonObjectBuilder(RedissonClient redisson) {
        super();
        this.config = redisson.getConfig();
        this.redisson = redisson;

        Codec codec = config.getCodec();
        codecProvider.registerCodec((Class<Codec>) codec.getClass(), codec);
    }

    public RedissonObjectBuilder(RedissonReactiveClient redissonReactive) {
        super();
        this.config = redissonReactive.getConfig();
        this.redissonReactive = redissonReactive;

        Codec codec = config.getCodec();
        codecProvider.registerCodec((Class<Codec>) codec.getClass(), codec);
    }

    public RedissonObjectBuilder(RedissonRxClient redissonRx) {
        super();
        this.config = redissonRx.getConfig();
        this.redissonRx = redissonRx;

        Codec codec = config.getCodec();
        codecProvider.registerCodec((Class<Codec>) codec.getClass(), codec);
    }

    public void storeAsync(RObject ar, String fieldName, RMap<String, Object> liveMap) {
        Codec codec = ar.getCodec();
        if (codec != null) {
            codecProvider.registerCodec((Class) codec.getClass(), codec);
        }
        liveMap.fastPutAsync(fieldName,
                new RedissonReference(ar.getClass(), ar.getName(), codec));
    }
    
    public void store(RObject ar, String fieldName, RMap<String, Object> liveMap) {
        Codec codec = ar.getCodec();
        if (codec != null) {
            codecProvider.registerCodec((Class) codec.getClass(), codec);
        }
        liveMap.fastPut(fieldName,
                new RedissonReference(ar.getClass(), ar.getName(), codec));
    }
    
    public RObject createObject(Object id, Class<?> clazz, Class<?> fieldType, String fieldName) {
        Class<? extends RObject> mappedClass = getMappedClass(fieldType);
        try {
            if (mappedClass != null) {
                Codec fieldCodec = getFieldCodec(clazz, mappedClass, fieldName);
                NamingScheme fieldNamingScheme = getNamingScheme(clazz, fieldCodec);
                String referenceName = fieldNamingScheme.getFieldReferenceName(clazz, id, mappedClass, fieldName);
                
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
    private Codec getFieldCodec(Class<?> rEntity, Class<? extends RObject> rObjectClass, String fieldName) throws ReflectiveOperationException {
        Field field = ClassUtils.getDeclaredField(rEntity, fieldName);
        if (field.isAnnotationPresent(RObjectField.class)) {
            RObjectField anno = field.getAnnotation(RObjectField.class);
            return codecProvider.getCodec(anno, rEntity, rObjectClass, fieldName, config);
        } else {
            REntity anno = ClassUtils.getAnnotation(rEntity, REntity.class);
            return codecProvider.getCodec(anno, rEntity, config);
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

    private Class<? extends RObject> getMappedClass(Class<?> cls) {
        for (Entry<Class<?>, Class<? extends RObject>> entrySet : SUPPORTED_CLASS_MAPPING.entrySet()) {
            if (entrySet.getKey().isAssignableFrom(cls)) {
                return entrySet.getValue();
            }
        }
        return null;
    }
    
    private static void fillCodecMethods(Class<?> clientClazz, Class<?> objectClazz) {
        for (Method method : clientClazz.getDeclaredMethods()) {
            if (!method.getReturnType().equals(Void.TYPE)
                    && objectClazz.isAssignableFrom(method.getReturnType())
                    && method.getName().startsWith("get")) {

                Class<?> cls = method.getReturnType();
                if (method.getParameterTypes().length == 2 //first param is name, second param is codec.
                        && String.class == method.getParameterTypes()[0]
                            && Codec.class.isAssignableFrom(method.getParameterTypes()[1])) {
                    CUSTOM_CODEC_REFERENCES.put(cls, method);
                } else if (method.getParameterTypes().length == 1
                            && String.class == method.getParameterTypes()[0]) {
                    DEFAULT_CODEC_REFERENCES.put(cls, method);
                }
            }
        }
    }

    public Object fromReference(RedissonReference rr, ReferenceType type) throws ReflectiveOperationException {
        if (type == ReferenceType.REACTIVE) {
            return fromReference(redissonReactive, rr);
        } else if (type == ReferenceType.RXJAVA) {
            return fromReference(redissonRx, rr);
        }
        return fromReference(redisson, rr);
    }
    
    private Object fromReference(RedissonClient redisson, RedissonReference rr) throws ReflectiveOperationException {
        Class<?> type = rr.getType();
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

    private Object getObject(Object redisson, RedissonReference rr, Class<?> type,
            ReferenceCodecProvider codecProvider) throws ReflectiveOperationException {
        if (type != null) {
            if (!DEFAULT_CODEC_REFERENCES.containsKey(type) && type.getInterfaces().length > 0) {
                type = type.getInterfaces()[0];
            }

            if (isDefaultCodec(rr)) {
                Method m = DEFAULT_CODEC_REFERENCES.get(type);
                if (m != null) {
                    return m.invoke(redisson, rr.getKeyName());
                }
            } else {
                Method m = CUSTOM_CODEC_REFERENCES.get(type);
                if (m != null) {
                    return m.invoke(redisson, rr.getKeyName(), codecProvider.getCodec(rr.getCodecType()));
                }
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + rr.getTypeName() + " with codec type of " + rr.getCodec());
    }
    
    private boolean isDefaultCodec(RedissonReference rr) {
        return rr.getCodec() == null
                || rr.getCodec().equals(config.getCodec().getClass().getName());
    }

    private Object fromReference(RedissonRxClient redisson, RedissonReference rr) throws ReflectiveOperationException {
        Class<?> type = rr.getRxJavaType();
        /**
         * Live Object from reference in rxjava client is not supported yet.
         */
        return getObject(redisson, rr, type, codecProvider);
    }
    
    private Object fromReference(RedissonReactiveClient redisson, RedissonReference rr) throws ReflectiveOperationException {
        Class<?> type = rr.getReactiveType();
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
        if (object instanceof RObjectRx && !(object instanceof RLiveObject)) {
            Class<?> clazz = object.getClass().getInterfaces()[0];

            RObjectRx rObject = (RObjectRx) object;
            if (rObject.getCodec() != null) {
                codecProvider.registerCodec((Class) rObject.getCodec().getClass(), rObject.getCodec());
            }
            return new RedissonReference(clazz, rObject.getName(), rObject.getCodec());
        }

        try {
            if (object instanceof RLiveObject) {
                Class<?> rEntity = object.getClass().getSuperclass();
                NamingScheme ns = getNamingScheme(rEntity);

                return new RedissonReference(rEntity,
                        ns.getName(rEntity, ((RLiveObject) object).getLiveObjectId()));
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        return null;
    }

    private <T extends RObject, K extends Codec> T createRObject(RedissonClient redisson, Class<T> expectedType, String name, K codec) throws ReflectiveOperationException {
        Class<?>[] interfaces = expectedType.getInterfaces();
        for (Class<?> iType : interfaces) {
            boolean isDefaultCodec = codec.getClass() == config.getCodec().getClass();

            if (isDefaultCodec) {
                Method builder = DEFAULT_CODEC_REFERENCES.get(iType);
                if (builder != null) {
                    return (T) builder.invoke(redisson, name);
                }
            } else {
                Method builder = CUSTOM_CODEC_REFERENCES.get(iType);
                if (builder != null) {
                    return (T) builder.invoke(redisson, name, codec);
                }
            }
        }
        
        String codecName = null;
        if (codec != null) {
            codecName = codec.getClass().getName();
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + expectedType.getName() + " with codec type of " + codecName);
    }

    public Object tryHandleReference(Object o, ReferenceType type) throws ReflectiveOperationException {
        boolean hasConversion = false;
        if (o instanceof List) {
            List<Object> r = (List<Object>) o;
            for (int i = 0; i < r.size(); i++) {
                Object ref = tryHandleReference0(r.get(i), type);
                if (ref != r.get(i)) {
                    r.set(i, ref);
                }
            }
            return o;
        } else if (o instanceof Set) {
            Set<Object> set;
            Set<Object> r = (Set<Object>) o;
            boolean useNewSet = o instanceof LinkedHashSet;
            try {
                set = (Set<Object>) o.getClass().getConstructor().newInstance();
            } catch (Exception exception) {
                set = new LinkedHashSet<>();
            }
            for (Object i : r) {
                Object ref = tryHandleReference0(i, type);
                //Not testing for ref changes because r.add(ref) below needs to
                //fail on the first iteration to be able to perform fall back
                //if failure happens.
                //
                //Assuming the failure reason is systematic such as put method
                //is not supported or implemented, and not an occasional issue
                //like only one element fails.
                if (useNewSet) {
                    set.add(ref);
                } else {
                    try {
                        r.add(ref);
                        set.add(i);
                    } catch (Exception e) {
                        //r is not supporting add operation, like
                        //LinkedHashMap$LinkedEntrySet and others.
                        //fall back to use a new set.
                        useNewSet = true;
                        set.add(ref);
                    }
                }
                hasConversion |= ref != i;
            }

            if (!hasConversion) {
                return o;
            } else if (useNewSet) {
                return set;
            } else if (!set.isEmpty()) {
                r.removeAll(set);
            }
            return o;
        } else if (o instanceof Map) {
            Map<Object, Object> r = (Map<Object, Object>) o;
            for (Map.Entry<Object, Object> e : r.entrySet()) {
                if (e.getKey() instanceof RedissonReference
                        || e.getValue() instanceof RedissonReference) {
                    Object key = e.getKey();
                    Object value = e.getValue();
                    if (e.getKey() instanceof RedissonReference) {
                        key = fromReference((RedissonReference) e.getKey(), type);
                        r.remove(e.getKey());
                    }
                    if (e.getValue() instanceof RedissonReference) {
                        value = fromReference((RedissonReference) e.getValue(), type);
                    }
                    r.put(key, value);
                }
            }

            return o;
        } else if (o instanceof ListScanResult) {
            tryHandleReference(((ListScanResult) o).getValues(), type);
            return o;
        } else if (o instanceof MapScanResult) {
            MapScanResult scanResult = (MapScanResult) o;
            Map oldMap = ((MapScanResult) o).getMap();
            Map map = (Map) tryHandleReference(oldMap, type);
            if (map != oldMap) {
                MapScanResult<Object, Object> newScanResult
                        = new MapScanResult<Object, Object>(scanResult.getPos(), map);
                newScanResult.setRedisClient(scanResult.getRedisClient());
                return newScanResult;
            } else {
                return o;
            }
        } else {
            return tryHandleReference0(o, type);
        }
    }

    private Object tryHandleReference0(Object o, ReferenceType type) throws ReflectiveOperationException {
        if (o instanceof RedissonReference) {
            return fromReference((RedissonReference) o, type);
        } else if (o instanceof ScoredEntry && ((ScoredEntry) o).getValue() instanceof RedissonReference) {
            ScoredEntry<?> se = (ScoredEntry<?>) o;
            return new ScoredEntry(se.getScore(), fromReference((RedissonReference) se.getValue(), type));
        } else if (o instanceof Map.Entry) {
            Map.Entry old = (Map.Entry) o;
            Object key = tryHandleReference0(old.getKey(), type);
            Object value = tryHandleReference0(old.getValue(), type);
            if (value != old.getValue() || key != old.getKey()) {
                return new AbstractMap.SimpleEntry(key, value);
            }
        }
        return o;
    }

}
