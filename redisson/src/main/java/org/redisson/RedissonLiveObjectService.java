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
package org.redisson;

import jodd.bean.BeanCopy;
import jodd.bean.BeanUtil;
import jodd.introspector.ClassIntrospector;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.type.TypeDescription;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.api.*;
import org.redisson.api.annotation.*;
import org.redisson.api.condition.Condition;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.decoder.ListMultiDecoder2;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.liveobject.LiveObjectSearch;
import org.redisson.liveobject.LiveObjectTemplate;
import org.redisson.liveobject.core.*;
import org.redisson.liveobject.misc.AdvBeanCopy;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;
import org.redisson.liveobject.resolver.RIdResolver;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

public class RedissonLiveObjectService implements RLiveObjectService {

    private static final ConcurrentMap<Class<? extends RIdResolver<?>>, RIdResolver<?>> PROVIDER_CACHE = new ConcurrentHashMap<>();

    private final ConcurrentMap<Class<?>, Class<?>> classCache;
    private final CommandAsyncExecutor commandExecutor;
    private final LiveObjectSearch seachEngine;

    public RedissonLiveObjectService(ConcurrentMap<Class<?>, Class<?>> classCache,
                                     CommandAsyncExecutor commandExecutor) {
        this.classCache = classCache;
        this.commandExecutor = commandExecutor;
        this.seachEngine = new LiveObjectSearch(commandExecutor);

        if (commandExecutor.getServiceManager().getLiveObjectLatch().compareAndSet(false, true)) {
            RPatternTopic topic = new RedissonPatternTopic(StringCodec.INSTANCE, commandExecutor, "__keyevent@*:expired");
            topic.addListenerAsync(String.class, (pattern, channel, msg) -> {
                if (msg.contains("redisson_live_object:")) {
                    Class<?> entity = resolveEntity(msg);
                    NamingScheme scheme = commandExecutor.getObjectBuilder().getNamingScheme(entity);
                    Object id = scheme.resolveId(msg);
                    deleteExpired(id, entity);
                }
            });
        }
    }

    private Class<?> resolveEntity(String name) {
        try {
            String className = name.substring(name.lastIndexOf(":")+1);
            return Class.forName(className);
        } catch (ClassNotFoundException e) {
            throw new IllegalStateException("Unable to resolve entity from [" + name + "]", e);
        }
    }


    //TODO: Add ttl renewal functionality
//    @Override
//    public <T, K> T get(Class<T> entityClass, K id, long timeToLive, TimeUnit timeUnit) {
//        T instance = get(entityClass, id);
//        RMap map = ((RLiveObject) instance).getLiveObjectLiveMap();
//        map.put("RLiveObjectDefaultTimeToLiveValue", timeToLive);
//        map.put("RLiveObjectDefaultTimeToLiveUnit", timeUnit.toString());
//        map.expire(timeToLive, timeUnit);
//        return instance;
//    }

    private RMap<String, Object> getMap(Object proxied) {
        return ClassUtils.getField(proxied, "liveObjectLiveMap");
    }

    private <T> Object generateId(Class<T> entityClass, String idFieldName) throws NoSuchFieldException {
        RId annotation = ClassUtils.getDeclaredField(entityClass, idFieldName)
                .getAnnotation(RId.class);
        RIdResolver<?> resolver = getResolver(annotation.generator());
        Object id = resolver.resolve(entityClass, annotation, idFieldName, commandExecutor);
        return id;
    }

    private RIdResolver<?> getResolver(Class<? extends RIdResolver<?>> resolverClass) {
        if (!PROVIDER_CACHE.containsKey(resolverClass)) {
            try {
                PROVIDER_CACHE.putIfAbsent(resolverClass, resolverClass.newInstance());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return PROVIDER_CACHE.get(resolverClass);
    }

    public <T> T createLiveObject(Class<T> entityClass, Object id) {
        registerClass(entityClass);
        Class<?> proxyClass = classCache.get(entityClass);
        return (T) instantiateLiveObject(proxyClass, id);
    }

    private <T> T createLiveObject(Class<T> entityClass, Object id, CommandAsyncExecutor commandExecutor, Map<Class<?>, Class<?>> classCache) {
        Class<?> proxyClass = classCache.get(entityClass);
        if (proxyClass == null) {
            validateClass(entityClass);
            proxyClass = createProxy(entityClass, commandExecutor);
            classCache.put(entityClass, proxyClass);
        }
        return (T) instantiateLiveObject(proxyClass, id);
    }

    @Override
    public <T> T get(Class<T> entityClass, Object id) {
        T proxied = createLiveObject(entityClass, id);
        if (asLiveObject(proxied).isExists()) {
            return proxied;
        }
        return null;
    }

    @Override
    public <T> Collection<T> find(Class<T> entityClass, Condition condition) {
        Set<Object> ids = seachEngine.find(entityClass, condition);

        return ids.stream()
                    .map(id -> createLiveObject(entityClass, id))
                    .collect(Collectors.toList());
    }

    @Override
    public long count(Class<?> entityClass, Condition condition) {
        Set<Object> ids = seachEngine.find(entityClass, condition);
        return ids.size();
    }

    @Override
    public <T> T attach(T detachedObject) {
        validateDetached(detachedObject);
        Class<T> entityClass = (Class<T>) detachedObject.getClass();
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        Object id = ClassUtils.getField(detachedObject, idFieldName);
        return createLiveObject(entityClass, id);
    }

    private <T> T attach(T detachedObject, CommandAsyncExecutor commandExecutor, Map<Class<?>, Class<?>> classCache) {
        validateDetached(detachedObject);
        Class<T> entityClass = (Class<T>) detachedObject.getClass();
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        Object id = ClassUtils.getField(detachedObject, idFieldName);
        return createLiveObject(entityClass, id, commandExecutor, classCache);
    }

    @Override
    public <T> T merge(T detachedObject) {
        Map<Object, Object> alreadyPersisted = new HashMap<Object, Object>();
        return persist(detachedObject, alreadyPersisted, RCascadeType.MERGE);
    }

    @Override
    public <T> T persist(T detachedObject) {
        Map<Object, Object> alreadyPersisted = new HashMap<Object, Object>();
        return persist(detachedObject, alreadyPersisted, RCascadeType.PERSIST);
    }

    @Override
    public <T> List<T> persist(T... detachedObjects) {
        return persist(RCascadeType.PERSIST, detachedObjects);
    }

    public <T> List<T> merge(T... detachedObjects) {
        return persist(RCascadeType.MERGE, detachedObjects);
    }

    public <T> List<T> persist(RCascadeType type, T... detachedObjects) {
        CommandBatchService batchService = new CommandBatchService(commandExecutor);

        Map<Class<?>, Class<?>> classCache = new HashMap<>();
        Map<T, Object> detached2Attached = new LinkedHashMap<>();
        Map<String, Object> name2id = new HashMap<>();

        for (T detachedObject : detachedObjects) {
            Object id = getId(detachedObject);

            T attachedObject = attach(detachedObject, batchService, classCache);
            RMap<String, Object> liveMap = getMap(attachedObject);

            detached2Attached.put(detachedObject, attachedObject);
            name2id.put(liveMap.getName(), id);
        }

        if (type == RCascadeType.PERSIST) {
            CommandBatchService checkExecutor = new CommandBatchService(batchService);
            for (Entry<String, Object> entry : name2id.entrySet()) {
                RMap<String, Object> map = new RedissonMap<>(checkExecutor, entry.getKey(), null, null, null);
                map.containsKeyAsync("redisson_live_object");
            }

            BatchResult<?> checkResponse = checkExecutor.execute();
            for (int i = 0; i < checkResponse.getResponses().size(); i++) {
                Boolean value = (Boolean) checkResponse.getResponses().get(i);
                if (value) {
                    List<Object> list = new ArrayList<>(name2id.values());
                    Object id = list.get(i);
                    throw new IllegalArgumentException("Object with id=" + id + " already exists.");
                }
            }
        }

        for (Entry<T, Object> entry : detached2Attached.entrySet()) {
            T detachedObject = entry.getKey();
            Object attachedObject = entry.getValue();

            for (FieldDescription.InDefinedShape field : Introspectior.getAllFields(detachedObject.getClass())) {
                Object object = ClassUtils.getField(detachedObject, field.getName());
                if (object == null
                        || object instanceof Collection
                            || ClassUtils.isAnnotationPresent(object.getClass(), REntity.class)) {
                    continue;
                }

                validateAnnotation(detachedObject, field.getName());
            }

            String idFieldName = getRIdFieldName(detachedObject.getClass());
            copy(detachedObject, attachedObject, Arrays.asList(idFieldName));
        }

        ClassIntrospector.get().reset();

        batchService.execute();
        return new ArrayList<>(detached2Attached.keySet());
    }

    private <T> Object getId(T detachedObject) {
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        Object id = ClassUtils.getField(detachedObject, idFieldName);
        if (id == null) {
            try {
                id = generateId(detachedObject.getClass(), idFieldName);
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(e);
            }
            ClassUtils.setField(detachedObject, idFieldName, id);
        }
        return id;
    }


    private <T> T persist(T detachedObject, Map<Object, Object> alreadyPersisted, RCascadeType type) {
        validateDetached(detachedObject);
        Object id = getId(detachedObject);

        T attachedObject = attach(detachedObject);
        alreadyPersisted.put(detachedObject, attachedObject);
        RMap<String, Object> liveMap = getMap(attachedObject);

        List<String> excludedFields = new ArrayList<>();
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        excludedFields.add(idFieldName);
        boolean fastResult = liveMap.fastPut("redisson_live_object", "1");
        if (type == RCascadeType.PERSIST && !fastResult) {
            throw new IllegalArgumentException("This REntity already exists.");
        }

        for (FieldDescription.InDefinedShape field : Introspectior.getAllFields(detachedObject.getClass())) {
            Object object = ClassUtils.getField(detachedObject, field.getName());
            if (object == null) {
                continue;
            }

            RObject rObject = commandExecutor.getObjectBuilder().createObject(id, detachedObject.getClass(), object.getClass(), field.getName());
            if (rObject != null) {
                commandExecutor.getObjectBuilder().store(rObject, field.getName(), liveMap);
                if (rObject instanceof SortedSet) {
                    ((RSortedSet) rObject).trySetComparator(((SortedSet) object).comparator());
                }

                if (rObject instanceof Collection) {
                    Collection coll = (Collection) rObject;
                    if (type == RCascadeType.MERGE) {
                        coll.clear();
                    }

                    for (Object obj : (Collection<Object>) object) {
                        if (obj != null && ClassUtils.isAnnotationPresent(obj.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(obj);
                            if (persisted == null
                                    && checkCascade(detachedObject, type, field.getName())) {
                                persisted = persist(obj, alreadyPersisted, type);
                            }
                            obj = persisted;
                        }
                        coll.add(obj);
                    }
                } else if (rObject instanceof Map) {
                    Map<Object, Object> rMap = (Map<Object, Object>) rObject;
                    if (type == RCascadeType.MERGE) {
                        rMap.clear();
                    }

                    Map<?, ?> map = (Map<?, ?>) object;
                    for (Entry<?, ?> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        Object value = entry.getValue();

                        if (key != null && ClassUtils.isAnnotationPresent(key.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(key);
                            if (persisted == null
                                    && checkCascade(detachedObject, type, field.getName())) {
                                persisted = persist(key, alreadyPersisted, type);
                            }
                            key = persisted;
                        }

                        if (value != null && ClassUtils.isAnnotationPresent(value.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(value);
                            if (persisted == null
                                    && checkCascade(detachedObject, type, field.getName())) {
                                persisted = persist(value, alreadyPersisted, type);
                            }
                            value = persisted;
                        }

                        rMap.put(key, value);
                    }
                }
                excludedFields.add(field.getName());
            } else if (ClassUtils.isAnnotationPresent(object.getClass(), REntity.class)) {
                Object persisted = alreadyPersisted.get(object);
                if (persisted == null) {
                    if (checkCascade(detachedObject, type, field.getName())) {
                        persisted = persist(object, alreadyPersisted, type);
                    }
                }

                excludedFields.add(field.getName());
                BeanUtil.pojo.setSimpleProperty(attachedObject, field.getName(), persisted);
            } else {
                validateAnnotation(detachedObject, field.getName());
            }

        }
        copy(detachedObject, attachedObject, excludedFields);
        return attachedObject;
    }

    private void validateAnnotation(Object instance, String fieldName) {
        Class<?> clazz = instance.getClass();
        if (isLiveObject(instance)) {
            clazz = clazz.getSuperclass();
        }
        
        RCascade annotation = ClassUtils.getAnnotation(clazz, fieldName, RCascade.class);
        if (annotation != null) {
            throw new IllegalArgumentException("RCascade annotation couldn't be defined for non-Redisson object '" + clazz + "' and field '" + fieldName + "'");
        }
    }

    private <T> boolean checkCascade(Object instance, RCascadeType type, String fieldName) {
        Class<?> clazz = instance.getClass();
        if (isLiveObject(instance)) {
            clazz = clazz.getSuperclass();
        }
        
        RCascade annotation = ClassUtils.getAnnotation(clazz, fieldName, RCascade.class);
        if (annotation != null && (Arrays.asList(annotation.value()).contains(type)
                || Arrays.asList(annotation.value()).contains(RCascadeType.ALL))) {
            return true;
        }
        return false;
    }
    
    @Override
    public <T> T detach(T attachedObject) {
        Map<String, Object> alreadyDetached = new HashMap<String, Object>();
        return detach(attachedObject, alreadyDetached);
    }

    @SuppressWarnings("unchecked")
    private <T> T detach(T attachedObject, Map<String, Object> alreadyDetached) {
        validateAttached(attachedObject);
        T detached = instantiateDetachedObject((Class<T>) attachedObject.getClass().getSuperclass(), asLiveObject(attachedObject).getLiveObjectId());
        BeanCopy.beans(attachedObject, detached).declared(true, true).copy();
        alreadyDetached.put(getMap(attachedObject).getName(), detached);
        
        for (Entry<String, Object> obj : getMap(attachedObject).entrySet()) {
            if (!checkCascade(attachedObject, RCascadeType.DETACH, obj.getKey())) {
                continue;
            }
            
            if (obj.getValue() instanceof RSortedSet) {
                SortedSet<Object> redissonSet = (SortedSet<Object>) obj.getValue();
                Set<Object> set = new TreeSet<Object>(redissonSet.comparator()); 
                for (Object object : redissonSet) {
                    if (isLiveObject(object)) {
                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(object, alreadyDetached);
                        }
                        object = detachedObject;
                    }
                    set.add(object);
                }
                
                ClassUtils.setField(detached, obj.getKey(), set);
            } else if (obj.getValue() instanceof RDeque) {
                Collection<Object> redissonDeque = (Collection<Object>) obj.getValue();
                Deque<Object> deque = new LinkedList<Object>(); 
                for (Object object : redissonDeque) {
                    if (isLiveObject(object)) {
                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(object, alreadyDetached);
                        }
                        object = detachedObject;
                    }
                    deque.add(object);
                }
                
                ClassUtils.setField(detached, obj.getKey(), deque);
            } else if (obj.getValue() instanceof RQueue) {
                Collection<Object> redissonQueue = (Collection<Object>) obj.getValue();
                Queue<Object> queue = new LinkedList<Object>(); 
                for (Object object : redissonQueue) {
                    if (isLiveObject(object)) {
                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(object, alreadyDetached);
                        }
                        object = detachedObject;
                    }
                    queue.add(object);
                }
                
                ClassUtils.setField(detached, obj.getKey(), queue);
            } else if (obj.getValue() instanceof RSet) {
                Set<Object> set = new HashSet<Object>(); 
                Collection<Object> redissonSet = (Collection<Object>) obj.getValue();
                for (Object object : redissonSet) {
                    if (isLiveObject(object)) {
                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(object, alreadyDetached);
                        }
                        object = detachedObject;
                    }
                    set.add(object);
                }
                
                ClassUtils.setField(detached, obj.getKey(), set);
            } else if (obj.getValue() instanceof RList) {
                List<Object> list = new ArrayList<Object>(); 
                Collection<Object> redissonList = (Collection<Object>) obj.getValue();
                for (Object object : redissonList) {
                    if (isLiveObject(object)) {
                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(object, alreadyDetached);
                        }
                        object = detachedObject;
                    }
                    list.add(object);
                }

                ClassUtils.setField(detached, obj.getKey(), list);
            } else if (isLiveObject(obj.getValue())) {
                Object detachedObject = alreadyDetached.get(getMap(obj.getValue()).getName());
                if (detachedObject == null) {
                    detachedObject = detach(obj.getValue(), alreadyDetached);
                }
                ClassUtils.setField(detached, obj.getKey(), detachedObject);
            } else if (obj.getValue() instanceof RMap) {
                Map<Object, Object> map = new LinkedHashMap<Object, Object>(); 
                Map<Object, Object> redissonMap = (Map<Object, Object>) obj.getValue();
                for (Entry<Object, Object> entry : redissonMap.entrySet()) {
                    Object key = entry.getKey();
                    Object value = entry.getValue();
                    
                    if (isLiveObject(key)) {
                        Object detachedObject = alreadyDetached.get(getMap(key).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(key, alreadyDetached);
                        }
                        key = detachedObject;
                    }
                    
                    if (isLiveObject(value)) {
                        Object detachedObject = alreadyDetached.get(getMap(value).getName());
                        if (detachedObject == null) {
                            detachedObject = detach(value, alreadyDetached);
                        }
                        value = detachedObject;
                    }
                    map.put(key, value);
                }
                
                ClassUtils.setField(detached, obj.getKey(), map);
            } else {
                validateAnnotation(detached, obj.getKey());
            }
        }
        
        return detached;
    }
    
    @Override
    public <T> void delete(T attachedObject) {
        Set<String> deleted = new HashSet<String>();
        delete(attachedObject, deleted);
    }

    private <T> void delete(T attachedObject, Set<String> deleted) {
        validateAttached(attachedObject);
        
        for (Entry<String, Object> obj : getMap(attachedObject).entrySet()) {
            if (!checkCascade(attachedObject, RCascadeType.DELETE, obj.getKey())) {
                continue;
            }
            
            if (obj.getValue() instanceof RSortedSet) {
                deleteCollection(deleted, (Iterable<?>) obj.getValue());
                ((RObject) obj.getValue()).delete();
            } else if (obj.getValue() instanceof RDeque) {
                deleteCollection(deleted, (Iterable<?>) obj.getValue());
                ((RObject) obj.getValue()).delete();
            } else if (obj.getValue() instanceof RQueue) {
                deleteCollection(deleted, (Iterable<?>) obj.getValue());
                ((RObject) obj.getValue()).delete();
            } else if (obj.getValue() instanceof RSet) {
                deleteCollection(deleted, (Iterable<?>) obj.getValue());
                ((RObject) obj.getValue()).delete();
            } else if (obj.getValue() instanceof RList) {
                deleteCollection(deleted, (Iterable<?>) obj.getValue());
                ((RObject) obj.getValue()).delete();
            } else if (isLiveObject(obj.getValue())) {
                if (deleted.add(((RedissonObject) getMap(obj.getValue())).getRawName())) {
                    delete(obj.getValue(), deleted);
                }
            } else if (obj.getValue() instanceof RMap) {
                RMap<Object, Object> map = (RMap<Object, Object>) obj.getValue();
                deleteCollection(deleted, map.keySet());
                deleteCollection(deleted, map.values());
                ((RObject) obj.getValue()).delete();
            } else {
                validateAnnotation(attachedObject, obj.getKey());
            }
            
        }
        asLiveObject(attachedObject).delete();
    }

    private void deleteCollection(Set<String> deleted, Iterable<?> objs) {
        for (Object object : objs) {
            if (isLiveObject(object)) {
                if (deleted.add(((RedissonObject) getMap(object)).getRawName())) {
                    delete(object, deleted);
                }
            }
        }
    }

    @Override
    public <T> long delete(Class<T> entityClass, Object... ids) {
        CommandBatchService ce = new CommandBatchService(commandExecutor);
        FieldList<InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(entityClass.getSuperclass(), RIndex.class);
        Set<String> fieldNames = fields.stream().map(f -> f.getName()).collect(Collectors.toSet());

        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);
        for (Object id: ids) {
            delete(id, entityClass, namingScheme, ce, fieldNames);
        }

        BatchResult<Object> r = (BatchResult<Object>) ce.execute();
        return r.getResponses().stream()
                .filter(s -> s instanceof Long)
                .mapToLong(s -> (Long) s).sum();
    }

    private RFuture<Long> delete(Object id, Class<?> entityClass, NamingScheme namingScheme, CommandBatchService ce, Set<String> fieldNames) {
        String mapName = namingScheme.getName(entityClass, id);
        Object liveObjectId = namingScheme.resolveId(mapName);

        deleteCollections(id, entityClass, ce);

        RMap<String, Object> liveMap = new RedissonMap<>(namingScheme.getCodec(), commandExecutor,
                                                mapName, null, null, null);
        Map<String, ?> values = liveMap.getAll(fieldNames);
        for (String fieldName : fieldNames) {
            Object value = values.get(fieldName);
            if (value == null) {
                continue;
            }

            String indexName = namingScheme.getIndexName(entityClass, fieldName);
            if (value instanceof Number) {
                RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
                set.removeAsync(liveObjectId);
            } else {
                RMultimapAsync<Object, Object> idsMultimap = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                idsMultimap.removeAsync(value, liveObjectId);
            }
        }
        return new RedissonKeys(ce).deleteAsync(mapName);
    }

    private void deleteExpired(Object id, Class<?> entityClass) {
        CommandBatchService ce = new CommandBatchService(commandExecutor);
        FieldList<InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(entityClass, RIndex.class);

        deleteCollections(id, entityClass, ce);

        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);
        String mapName = namingScheme.getName(entityClass, id);
        Object liveObjectId = namingScheme.resolveId(mapName);
        TypeDescription.Generic n1 = TypeDescription.Generic.Builder.rawType(Number.class).build();
        for (InDefinedShape field : fields) {
            boolean isNumber = n1.accept(TypeDescription.Generic.Visitor.Assigner.INSTANCE)
                                    .isAssignableFrom(field.getType().asErasure().asBoxed().asGenericType());
            String indexName = namingScheme.getIndexName(entityClass, field.getName());
            if (isNumber) {
                RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
                set.removeAsync(liveObjectId);
            } else {
                RMultimapAsync<Object, Object> idsMultimap = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                idsMultimap.fastRemoveValueAsync(liveObjectId);
            }
        }
        ce.execute();
    }

    private void deleteCollections(Object id, Class<?> entityClass, CommandBatchService ce) {
        for (InDefinedShape field : Introspectior.getAllFields(entityClass)) {
            try {
                Field f = ClassUtils.getDeclaredField(entityClass, field.getName());
                RObject rObject = commandExecutor.getObjectBuilder().createObject(id, entityClass, f.getType(), field.getName());
                if (rObject != null) {
                    RedissonObject ro = (RedissonObject) rObject;
                    ce.writeAsync(ro.getRawName(), RedisCommands.DEL, ro.getRawName());
                }
            } catch (NoSuchFieldException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public RFuture<Long> delete(Object id, Class<?> entityClass, NamingScheme namingScheme, CommandBatchService ce) {
        FieldList<InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(entityClass, RIndex.class);
        Set<String> fieldNames = fields.stream().map(f -> f.getName()).collect(Collectors.toSet());
        return delete(id, entityClass, namingScheme, ce, fieldNames);
    }

    @Override
    public <K> Iterable<K> findIds(Class<?> entityClass) {
        return findIds(entityClass, 10);
    }

    @Override
    public <K> Iterable<K> findIds(Class<?> entityClass, int count) {
        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);
        String pattern = namingScheme.getNamePattern(entityClass);
        RedissonKeys keys = new RedissonKeys(commandExecutor);

        RedisCommand<ListScanResult<String>> command = new RedisCommand<>("SCAN",
                new ListMultiDecoder2(new ListScanResultReplayDecoder(), new ObjectListReplayDecoder<Object>()), new Convertor<Object>() {
            @Override
            public Object convert(Object obj) {
                if (!(obj instanceof String)) {
                    return obj;
                }
                return namingScheme.resolveId(obj.toString());
            }
        });

        return keys.getKeysByPattern(command, pattern, 0, count);
    }

    @Override
    public <T> RLiveObject asLiveObject(T instance) {
        return (RLiveObject) instance;
    }

    @Override
    public <T, K, V> RMap<K, V> asRMap(T instance) {
        return (RMap) instance;
    }

    @Override
    public <T> boolean isLiveObject(T instance) {
        return instance instanceof RLiveObject;
    }

    @Override
    public <T> boolean isExists(T instance) {
        return instance instanceof RLiveObject && asLiveObject(instance).isExists();
    }

    @Override
    public void registerClass(Class<?> cls) {
        if (!classCache.containsKey(cls)) {
            validateClass(cls);
            Class<?> proxyClass = createProxy(cls, commandExecutor);
            classCache.putIfAbsent(cls, proxyClass);
        }
    }

    @Override
    public void unregisterClass(Class<?> cls) {
        if (cls.isAssignableFrom(RLiveObject.class)) {
            classCache.remove(cls.getSuperclass());
        } else {
            classCache.remove(cls);
        }
    }

    @Override
    public boolean isClassRegistered(Class<?> cls) {
        return classCache.containsKey(cls) || classCache.containsValue(cls);
    }

    private <T> void copy(T detachedObject, T attachedObject, List<String> excludedFields) {
        new AdvBeanCopy(detachedObject, attachedObject)
                .ignoreNulls(true)
                .exclude(excludedFields.toArray(new String[excludedFields.size()]))
                .copy();
    }

    private String getRIdFieldName(Class<?> cls) {
        return Introspectior.getREntityIdFieldName(cls);
    }

    private <T> T instantiateLiveObject(Class<T> proxyClass, Object id) {
        if (id == null) {
            throw new IllegalStateException("Non-null value is required for the field with RId annotation.");
        }
        T instance = instantiate(proxyClass);
        asLiveObject(instance).setLiveObjectId(id);
        return instance;
    }

    private <T, K> T instantiateDetachedObject(Class<T> cls, K id) {
        T instance = instantiate(cls);
        String fieldName = getRIdFieldName(cls);
        if (ClassUtils.getField(instance, fieldName) == null) {
            ClassUtils.setField(instance, fieldName, id);
        }
        return instance;
    }

    private <T> T instantiate(Class<T> cls) {
        try {
            for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
                if (constructor.getParameterTypes().length == 0) {
                    constructor.setAccessible(true);
                    return (T) constructor.newInstance();
                }
            }
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
        throw new IllegalArgumentException("Can't find default constructor for " + cls);
    }

    private <T> void validateClass(Class<T> entityClass) {
        if (entityClass.isAnonymousClass() || entityClass.isLocalClass()) {
            throw new IllegalArgumentException(entityClass.getName() + " is not publically accessable.");
        }
        if (!ClassUtils.isAnnotationPresent(entityClass, REntity.class)) {
            throw new IllegalArgumentException("REntity annotation is missing from class type declaration.");
        }

        FieldList<FieldDescription.InDefinedShape> fields = Introspectior.getFieldsWithAnnotation(entityClass, RIndex.class);
        fields = fields.filter(ElementMatchers.fieldType(ElementMatchers.hasSuperType(
                                ElementMatchers.anyOf(Map.class, Collection.class, RObject.class))));
        for (InDefinedShape field : fields) {
            throw new IllegalArgumentException("RIndex annotation couldn't be defined for field '" + field.getName() + "' with type '" + field.getType() + "'");
        }
        
        FieldList<FieldDescription.InDefinedShape> fieldsWithRIdAnnotation
                = Introspectior.getFieldsWithAnnotation(entityClass, RId.class);
        if (fieldsWithRIdAnnotation.size() == 0) {
            throw new IllegalArgumentException("RId annotation is missing from class field declaration.");
        }
        if (fieldsWithRIdAnnotation.size() > 1) {
            throw new IllegalArgumentException("Only one field with RId annotation is allowed in class field declaration.");
        }
        FieldDescription.InDefinedShape idFieldDescription = fieldsWithRIdAnnotation.getOnly();
        String idFieldName = idFieldDescription.getName();
        Field idField = null;
        try {
            idField = ClassUtils.getDeclaredField(entityClass, idFieldName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (ClassUtils.isAnnotationPresent(idField.getType(), REntity.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of which class is annotated with REntity.");
        }
        if (idField.getType().isAssignableFrom(RObject.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of RObject");
        }
    }

    private <T> void validateDetached(T detachedObject) {
        if (detachedObject instanceof RLiveObject) {
            throw new IllegalArgumentException("The object supplied is already a RLiveObject");
        }
    }

    private <T> void validateAttached(T attachedObject) {
        if (!(attachedObject instanceof RLiveObject)) {
            throw new IllegalArgumentException("The object supplied is must be a RLiveObject");
        }
    }
    
    private <T> Class<? extends T> createProxy(Class<T> entityClass, CommandAsyncExecutor commandExecutor) {
        DynamicType.Builder<T> builder = new ByteBuddy()
                .subclass(entityClass);
        for (FieldDescription.InDefinedShape field
                : Introspectior.getTypeDescription(LiveObjectTemplate.class)
                .getDeclaredFields()) {
            builder = builder.define(field);
        }

        Class<? extends T> proxied = builder.method(ElementMatchers.isDeclaredBy(
                ElementMatchers.anyOf(RLiveObject.class, RExpirable.class, RObject.class))
                .and(ElementMatchers.isGetter().or(ElementMatchers.isSetter())
                        .or(ElementMatchers.named("isPhantom"))
                        .or(ElementMatchers.named("delete"))))
                .intercept(MethodDelegation.withDefaultConfiguration()
                        .withBinders(FieldProxy.Binder
                                .install(LiveObjectInterceptor.Getter.class,
                                        LiveObjectInterceptor.Setter.class))
                        .to(new LiveObjectInterceptor(commandExecutor, this, entityClass, getRIdFieldName(entityClass))))
//                .intercept(MethodDelegation.to(
//                                new LiveObjectInterceptor(redisson, codecProvider, entityClass,
//                                        getRIdFieldName(entityClass)))
//                        .appendParameterBinder(FieldProxy.Binder
//                                .install(LiveObjectInterceptor.Getter.class,
//                                        LiveObjectInterceptor.Setter.class)))
                .implement(RLiveObject.class)
                .method(ElementMatchers.isAnnotatedWith(RFieldAccessor.class)
                        .and(ElementMatchers.named("get")
                        .or(ElementMatchers.named("set"))))
                .intercept(MethodDelegation.to(FieldAccessorInterceptor.class))
                
                .method(ElementMatchers.isDeclaredBy(RExpirable.class)
                        .or(ElementMatchers.isDeclaredBy(RExpirableAsync.class)))
                .intercept(MethodDelegation.to(RExpirableInterceptor.class))
                .implement(RExpirable.class)
                
                .method(ElementMatchers.isDeclaredBy(Map.class)
                        .or(ElementMatchers.isDeclaredBy(ConcurrentMap.class))
                        .or(ElementMatchers.isDeclaredBy(RMapAsync.class))
                        .or(ElementMatchers.isDeclaredBy(RMap.class)))
                .intercept(MethodDelegation.to(RMapInterceptor.class))
                .implement(RMap.class)

                .method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RLiveObject.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RExpirable.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RExpirableAsync.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RObject.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RObjectAsync.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(ConcurrentMap.class)))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(Map.class)))
                        .and(ElementMatchers.isGetter()
                                .or(ElementMatchers.isSetter()))
                        .and(ElementMatchers.isPublic()
                                .or(ElementMatchers.isProtected()))
                        )
                .intercept(MethodDelegation.to(new AccessorInterceptor(commandExecutor)))
                
                .make().load(entityClass.getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        return proxied;
    }
}
