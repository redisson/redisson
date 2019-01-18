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
package org.redisson;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Queue;
import java.util.Set;
import java.util.SortedSet;
import java.util.TreeSet;
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.RCascadeType;
import org.redisson.api.RDeque;
import org.redisson.api.RExpirable;
import org.redisson.api.RExpirableAsync;
import org.redisson.api.RList;
import org.redisson.api.RLiveObject;
import org.redisson.api.RLiveObjectService;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RObject;
import org.redisson.api.RObjectAsync;
import org.redisson.api.RQueue;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.RCascade;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RFieldAccessor;
import org.redisson.api.annotation.RId;
import org.redisson.api.annotation.RIndex;
import org.redisson.api.condition.Condition;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.liveobject.LiveObjectTemplate;
import org.redisson.liveobject.condition.ANDCondition;
import org.redisson.liveobject.condition.EQCondition;
import org.redisson.liveobject.condition.ORCondition;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.core.FieldAccessorInterceptor;
import org.redisson.liveobject.core.LiveObjectInterceptor;
import org.redisson.liveobject.core.RExpirableInterceptor;
import org.redisson.liveobject.core.RMapInterceptor;
import org.redisson.liveobject.core.RObjectInterceptor;
import org.redisson.liveobject.misc.AdvBeanCopy;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;
import org.redisson.liveobject.resolver.Resolver;

import io.netty.util.internal.PlatformDependent;
import jodd.bean.BeanCopy;
import jodd.bean.BeanUtil;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldDescription.InDefinedShape;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatchers;

public class RedissonLiveObjectService implements RLiveObjectService {

    private static final ConcurrentMap<Class<? extends Resolver>, Resolver<?, ?, ?>> providerCache = PlatformDependent.newConcurrentHashMap();
    private final ConcurrentMap<Class<?>, Class<?>> classCache;
    private final RedissonClient redisson;
    private final CommandAsyncExecutor commandExecutor;

    public RedissonLiveObjectService(RedissonClient redisson, ConcurrentMap<Class<?>, Class<?>> classCache, CommandAsyncExecutor commandExecutor) {
        this.redisson = redisson;
        this.classCache = classCache;
        this.commandExecutor = commandExecutor;
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
    
    private <T> Object generateId(Class<T> entityClass) throws NoSuchFieldException {
        String idFieldName = getRIdFieldName(entityClass);
        RId annotation = ClassUtils.getDeclaredField(entityClass, idFieldName)
                .getAnnotation(RId.class);
        Resolver resolver = getResolver(entityClass, annotation.generator(), annotation);
        Object id = resolver.resolve(entityClass, annotation, idFieldName, redisson);
        return id;
    }
    
    private Resolver<?, ?, ?> getResolver(Class<?> cls, Class<? extends Resolver> resolverClass, Annotation anno) {
        if (!providerCache.containsKey(resolverClass)) {
            try {
                providerCache.putIfAbsent(resolverClass, resolverClass.newInstance());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return providerCache.get(resolverClass);
    }

    public <T> T createLiveObject(Class<T> entityClass, Object id) {
        return instantiateLiveObject(getProxyClass(entityClass), id);
    }
    
    @Override
    public <T> T get(Class<T> entityClass, Object id) {
        T proxied = createLiveObject(entityClass, id);
        return asLiveObject(proxied).isExists() ? proxied : null;
    }

    Set<Object> traverseAnd(ANDCondition condition, NamingScheme namingScheme, Class<?> entityClass) {
        Set<Object> allIds = new HashSet<Object>();
        RSet<Object> firstSet = null;
        List<String> names = new ArrayList<String>();
        boolean isAllEqConditions = true;
        for (Condition cond : condition.getConditions()) {
            if (cond instanceof EQCondition) {
                EQCondition eqc = (EQCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, eqc.getName());
                RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                RSet<Object> values = map.get(eqc.getValue());
                if (firstSet == null) {
                    firstSet = values;
                } else {
                    names.add(values.getName());
                }
            }
            if (cond instanceof ORCondition) {
                isAllEqConditions = false;
                Collection<Object> ids = traverseOr((ORCondition) cond, namingScheme, entityClass);
                allIds.addAll(ids);
            }
        }
        if (!isAllEqConditions && allIds.isEmpty()) {
            return Collections.emptySet();
        }
        
        if (firstSet != null) {
            if (names.isEmpty()) {
                if (!isAllEqConditions && !allIds.isEmpty()) {
                    allIds.retainAll(firstSet.readAll());    
                } else {
                    allIds.addAll(firstSet.readAll());
                }
            } else {
                Set<Object> intersect = firstSet.readIntersection(names.toArray(new String[names.size()]));
                if (!isAllEqConditions && !allIds.isEmpty()) {
                    allIds.retainAll(intersect);    
                } else {
                    allIds.addAll(intersect);
                }
            }
        }
        return allIds;
    }

    Set<Object> traverseOr(ORCondition condition, NamingScheme namingScheme, Class<?> entityClass) {
        Set<Object> allIds = new HashSet<Object>();
        RSet<Object> firstSet = null;
        List<String> names = new ArrayList<String>();
        for (Condition cond : condition.getConditions()) {
            if (cond instanceof EQCondition) {
                EQCondition eqc = (EQCondition) cond;
                
                String indexName = namingScheme.getIndexName(entityClass, eqc.getName());
                RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                RSet<Object> values = map.get(eqc.getValue());
                if (firstSet == null) {
                    firstSet = values;
                } else {
                    names.add(values.getName());
                }
            }
            if (cond instanceof ANDCondition) {
                Collection<Object> ids = traverseAnd((ANDCondition) cond, namingScheme, entityClass);
                allIds.addAll(ids);
            }
        }
        if (firstSet != null) {
            if (names.isEmpty()) {
                allIds.addAll(firstSet.readAll());
            } else {
                allIds.addAll(firstSet.readUnion(names.toArray(new String[names.size()])));
            }
        }
        return allIds;
    }
    
    @Override
    public <T> Collection<T> find(Class<T> entityClass, Condition condition) {
        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);

        Set<Object> ids = Collections.emptySet();
        if (condition instanceof EQCondition) {
            EQCondition c = (EQCondition) condition;
            String indexName = namingScheme.getIndexName(entityClass, c.getName());
            RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
            ids = map.getAll(c.getValue());
        } else if (condition instanceof ORCondition) {
            ids = traverseOr((ORCondition) condition, namingScheme, entityClass);
        } else if (condition instanceof ANDCondition) {
            ids = traverseAnd((ANDCondition) condition, namingScheme, entityClass);
        }
        
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<T> result = new ArrayList<T>(ids.size());
        for (Object id : ids) {
            T proxied = createLiveObject(entityClass, id);
            result.add(proxied);
        }
        return result;
    }

    @Override
    public <T> T attach(T detachedObject) {
        validateDetached(detachedObject);
        Class<T> entityClass = (Class<T>) detachedObject.getClass();
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        Object id = ClassUtils.getField(detachedObject, idFieldName);
        return createLiveObject(entityClass, id);
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
    
    private <T> T persist(T detachedObject, Map<Object, Object> alreadyPersisted, RCascadeType type) {
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        Object id = ClassUtils.getField(detachedObject, idFieldName);
        if (id == null) {
            try {
                id = generateId(detachedObject.getClass());
            } catch (NoSuchFieldException e) {
                throw new IllegalArgumentException(e);
            }
            ClassUtils.setField(detachedObject, idFieldName, id);
        }
        
        T attachedObject = attach(detachedObject);
        alreadyPersisted.put(detachedObject, attachedObject);
        RMap<String, Object> liveMap = getMap(attachedObject);
        
        List<String> excludedFields = new ArrayList<String>();
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
            
            RObject rObject = commandExecutor.getObjectBuilder().createObject(id, detachedObject.getClass(), object.getClass(), field.getName(), redisson);
            if (rObject != null) {
                commandExecutor.getObjectBuilder().store(rObject, field.getName(), liveMap);
                if (rObject instanceof SortedSet) {
                    ((RSortedSet)rObject).trySetComparator(((SortedSet)object).comparator());
                }
                
                if (rObject instanceof Collection) {
                    for (Object obj : (Collection<Object>)object) {
                        if (obj != null && ClassUtils.isAnnotationPresent(obj.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(obj);
                            if (persisted == null) {
                                if (checkCascade(detachedObject, type, field.getName())) {
                                    persisted = persist(obj, alreadyPersisted, type);
                                }
                            }
                            obj = persisted;
                        }
                        ((Collection)rObject).add(obj);
                    }
                } else if (rObject instanceof Map) {
                    Map<Object, Object> rMap = (Map<Object, Object>) rObject;
                    Map<?, ?> map = (Map<?, ?>)rObject;
                    for (Entry<?, ?> entry : map.entrySet()) {
                        Object key = entry.getKey();
                        Object value = entry.getValue();

                        if (key != null && ClassUtils.isAnnotationPresent(key.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(key);
                            if (persisted == null) {
                                if (checkCascade(detachedObject, type, field.getName())) {
                                    persisted = persist(key, alreadyPersisted, type);
                                }
                            }
                            key = persisted;
                        }

                        if (value != null && ClassUtils.isAnnotationPresent(value.getClass(), REntity.class)) {
                            Object persisted = alreadyPersisted.get(value);
                            if (persisted == null) {
                                if (checkCascade(detachedObject, type, field.getName())) {
                                    persisted = persist(value, alreadyPersisted, type);
                                }
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
        try {
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
        } catch (Exception ex) {
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
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
                deleteCollection(deleted, (Iterable<?>)obj.getValue());
                ((RObject)obj.getValue()).delete();
            } else if (obj.getValue() instanceof RDeque) {
                deleteCollection(deleted, (Iterable<?>)obj.getValue());
                ((RObject)obj.getValue()).delete();
            } else if (obj.getValue() instanceof RQueue) {
                deleteCollection(deleted, (Iterable<?>)obj.getValue());
                ((RObject)obj.getValue()).delete();
            } else if (obj.getValue() instanceof RSet) {
                deleteCollection(deleted, (Iterable<?>)obj.getValue());
                ((RObject)obj.getValue()).delete();
            } else if (obj.getValue() instanceof RList) {
                deleteCollection(deleted, (Iterable<?>)obj.getValue());
                ((RObject)obj.getValue()).delete();
            } else if (isLiveObject(obj.getValue())) {
                if (deleted.add(getMap(obj.getValue()).getName())) {
                    delete(obj.getValue(), deleted);
                }
            } else if (obj.getValue() instanceof RMap) {
                RMap<Object, Object> map = (RMap<Object, Object>)obj.getValue();
                deleteCollection(deleted, map.keySet());
                deleteCollection(deleted, map.values());
                ((RObject)obj.getValue()).delete();
            } else {
                validateAnnotation(attachedObject, obj.getKey());
            }
            
        }
        asLiveObject(attachedObject).delete();
    }

    private void deleteCollection(Set<String> deleted, Iterable<?> objs) {
        for (Object object : objs) {
            if (isLiveObject(object)) {
                if (deleted.add(getMap(object).getName())) {
                    delete(object, deleted);
                }
            }
        }
    }

    @Override
    public <T> boolean delete(Class<T> entityClass, Object id) {
        T entity = createLiveObject(entityClass, id);
        return asLiveObject(entity).delete();
    }

    @Override
    public <T> RLiveObject asLiveObject(T instance) {
        return (RLiveObject) instance;
    }

    @Override
    public <T> RExpirable asRExpirable(T instance) {
        return (RExpirable) instance;
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
            registerClassInternal(cls);
        }
    }

    @Override
    public void unregisterClass(Class<?> cls) {
        classCache.remove(cls.isAssignableFrom(RLiveObject.class) ? cls.getSuperclass() : cls);
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
        return Introspectior.getFieldsWithAnnotation(cls, RId.class)
                .getOnly()
                .getName();
    }

    private <T> T instantiateLiveObject(Class<T> proxyClass, Object id) {
        if (id == null) {
            throw new IllegalStateException("Non-null value is required for the field with RId annotation.");
        }
        T instance = instantiate(proxyClass, id);
        asLiveObject(instance).setLiveObjectId(id);
        return instance;
    }

    private <T, K> T instantiateDetachedObject(Class<T> cls, K id) {
        T instance = instantiate(cls, id);
        String fieldName = getRIdFieldName(cls);
        if (ClassUtils.getField(instance, fieldName) == null) {
            ClassUtils.setField(instance, fieldName, id);
        }
        return instance;
    }

    private <T> T instantiate(Class<T> cls, Object id) {
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

    private <T> Class<? extends T> getProxyClass(Class<T> entityClass) {
        registerClass(entityClass);
        return (Class<? extends T>) classCache.get(entityClass);
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
    
    private <T> void registerClassInternal(Class<T> entityClass) {
        DynamicType.Builder<T> builder = new ByteBuddy()
                .subclass(entityClass);
        for (FieldDescription.InDefinedShape field
                : Introspectior.getTypeDescription(LiveObjectTemplate.class)
                .getDeclaredFields()) {
            builder = builder.define(field);
        }

        Class<? extends T> proxied = builder.method(ElementMatchers.isDeclaredBy(
                Introspectior.getTypeDescription(RLiveObject.class))
                .and(ElementMatchers.isGetter().or(ElementMatchers.isSetter())
                        .or(ElementMatchers.named("isPhantom"))
                        .or(ElementMatchers.named("delete"))))
                .intercept(MethodDelegation.withDefaultConfiguration()
                        .withBinders(FieldProxy.Binder
                                .install(LiveObjectInterceptor.Getter.class,
                                        LiveObjectInterceptor.Setter.class))
                        .to(new LiveObjectInterceptor(redisson, entityClass,
                                getRIdFieldName(entityClass), commandExecutor.getObjectBuilder())))
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
                
                .method(ElementMatchers.isDeclaredBy(RObject.class)
                        .or(ElementMatchers.isDeclaredBy(RObjectAsync.class)))
                .intercept(MethodDelegation.to(RObjectInterceptor.class))
                .implement(RObject.class)
                
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
                .intercept(MethodDelegation.to(new AccessorInterceptor(redisson, commandExecutor.getObjectBuilder())))
                
                .make().load(entityClass.getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        classCache.putIfAbsent(entityClass, proxied);
    }
}
