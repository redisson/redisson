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
package org.redisson;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayList;
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
import org.redisson.api.RSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RFieldAccessor;
import org.redisson.api.annotation.RId;
import org.redisson.codec.CodecProvider;
import org.redisson.liveobject.LiveObjectTemplate;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.core.FieldAccessorInterceptor;
import org.redisson.liveobject.core.LiveObjectInterceptor;
import org.redisson.liveobject.core.RExpirableInterceptor;
import org.redisson.liveobject.core.RMapInterceptor;
import org.redisson.liveobject.core.RObjectInterceptor;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.provider.ResolverProvider;
import org.redisson.liveobject.resolver.Resolver;

import jodd.bean.BeanCopy;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.description.modifier.Visibility;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodCall;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatchers;

public class RedissonLiveObjectService implements RLiveObjectService {

    private final ConcurrentMap<Class<?>, Class<?>> classCache;
    private final RedissonClient redisson;
    private final CodecProvider codecProvider;
    private final ResolverProvider resolverProvider;

    public RedissonLiveObjectService(RedissonClient redisson, ConcurrentMap<Class<?>, Class<?>> classCache, CodecProvider codecProvider, ResolverProvider resolverProvider) {
        this.redisson = redisson;
        this.classCache = classCache;
        this.codecProvider = codecProvider;
        this.resolverProvider = resolverProvider;
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
    
    public RMap<String, Object> getMap(Object proxied) {
        return ClassUtils.getField(proxied, "liveObjectLiveMap");
    }
    
    @Override
    public <T> T create(Class<T> entityClass) {
        validateClass(entityClass);
        try {
            Class<? extends T> proxyClass = getProxyClass(entityClass);
            Object id = generateId(entityClass);
            T proxied = instantiateLiveObject(proxyClass, id);
            if (!getMap(proxied).fastPut("redisson_live_object", "1")) {
                throw new IllegalArgumentException("Object already exists");
            }
            return proxied;
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    private <T> Object generateId(Class<T> entityClass) throws NoSuchFieldException {
        String idFieldName = getRIdFieldName(entityClass);
        RId annotation = entityClass
                .getDeclaredField(idFieldName)
                .getAnnotation(RId.class);
        Resolver resolver = resolverProvider.getResolver(entityClass,
                annotation.generator(), annotation);
        Object id = resolver.resolve(entityClass, annotation, idFieldName, redisson);
        return id;
    }

    @Override
    public <T, K> T get(Class<T> entityClass, K id) {
        try {
            T proxied = instantiateLiveObject(getProxyClass(entityClass), id);
            return asLiveObject(proxied).isExists() ? proxied : null;
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public <T, K> T getOrCreate(Class<T> entityClass, K id) {
        try {
            T proxied = instantiateLiveObject(getProxyClass(entityClass), id);
            getMap(proxied).fastPut("redisson_live_object", "1");
            return proxied;
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public <T> T attach(T detachedObject) {
        validateDetached(detachedObject);
        Class<T> entityClass = (Class<T>) detachedObject.getClass();
        try {
            String idFieldName = getRIdFieldName(detachedObject.getClass());
            Object id = ClassUtils.getField(detachedObject, idFieldName);
            Class<? extends T> proxyClass = getProxyClass(entityClass);
            return instantiateLiveObject(proxyClass, id);
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public <T> T merge(T detachedObject) {
        T attachedObject = attach(detachedObject);
        getMap(attachedObject).fastPut("redisson_live_object", "1");
        copy(detachedObject, attachedObject);
        return attachedObject;
    }

    @Override
    public <T> T persist(T detachedObject) {
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
        if (getMap(attachedObject).fastPut("redisson_live_object", "1")) {
            copy(detachedObject, attachedObject);
            return attachedObject;
        }
        throw new IllegalArgumentException("This REntity already exists.");
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
                }

                if (isLiveObject(obj.getValue())) {
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
                }
            }
            
            return detached;
        } catch (Exception ex) {
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }
    

    @Override
    public <T> void delete(T attachedObject) {
        validateAttached(attachedObject);
        asLiveObject(attachedObject).delete();
    }

    @Override
    public <T, K> void delete(Class<T> entityClass, K id) {
        asLiveObject(get(entityClass, id)).delete();
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
    public void registerClass(Class cls) {
        if (!classCache.containsKey(cls)) {
            validateClass(cls);
            registerClassInternal(cls);
        }
    }

    @Override
    public void unregisterClass(Class cls) {
        classCache.remove(cls.isAssignableFrom(RLiveObject.class) ? cls.getSuperclass() : cls);
    }

    @Override
    public boolean isClassRegistered(Class cls) {
        return classCache.containsKey(cls) || classCache.containsValue(cls);
    }

    private <T> void copy(T detachedObject, T attachedObject) {
        for (FieldDescription.InDefinedShape field : Introspectior.getFieldsDescription(detachedObject.getClass())) {
            Object object = ClassUtils.getField(detachedObject, field.getName());
            if (object != null && object.getClass().isAnnotationPresent(REntity.class)) {
                throw new IllegalArgumentException("REntity should be attached to Redisson before save");
            }

        }
//        for (FieldDescription.InDefinedShape field : Introspectior.getFieldsDescription(detachedObject.getClass())) {
//            Object obj = ClassUtils.getField(detachedObject, field.getName());
//            if (obj instanceof SortedSet) {
//                ClassUtils.getField(detachedObject, field.getName());
//                SortedSet<Object> redissonSet = (SortedSet<Object>) obj.getValue();
//                Set<Object> set = new TreeSet<Object>(redissonSet.comparator()); 
//                for (Object object : redissonSet) {
//                    if (isLiveObject(object)) {
//                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
//                        if (detachedObject == null) {
//                            detachedObject = detach(object, alreadyDetached);
//                        }
//                        object = detachedObject;
//                    }
//                    set.add(object);
//                }
//                
//                ClassUtils.setField(detached, obj.getKey(), set);
//            } else if (obj.getValue() instanceof RDeque) {
//                Collection<Object> redissonDeque = (Collection<Object>) obj.getValue();
//                Deque<Object> deque = new LinkedList<Object>(); 
//                for (Object object : redissonDeque) {
//                    if (isLiveObject(object)) {
//                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
//                        if (detachedObject == null) {
//                            detachedObject = detach(object, alreadyDetached);
//                        }
//                        object = detachedObject;
//                    }
//                    deque.add(object);
//                }
//                
//                ClassUtils.setField(detached, obj.getKey(), deque);
//            } else if (obj.getValue() instanceof RQueue) {
//                Collection<Object> redissonQueue = (Collection<Object>) obj.getValue();
//                Queue<Object> queue = new LinkedList<Object>(); 
//                for (Object object : redissonQueue) {
//                    if (isLiveObject(object)) {
//                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
//                        if (detachedObject == null) {
//                            detachedObject = detach(object, alreadyDetached);
//                        }
//                        object = detachedObject;
//                    }
//                    queue.add(object);
//                }
//                
//                ClassUtils.setField(detached, obj.getKey(), queue);
//            } else if (obj.getValue() instanceof RSet) {
//                Set<Object> set = new HashSet<Object>(); 
//                Collection<Object> redissonSet = (Collection<Object>) obj.getValue();
//                for (Object object : redissonSet) {
//                    if (isLiveObject(object)) {
//                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
//                        if (detachedObject == null) {
//                            detachedObject = detach(object, alreadyDetached);
//                        }
//                        object = detachedObject;
//                    }
//                    set.add(object);
//                }
//                
//                ClassUtils.setField(detached, obj.getKey(), set);
//            } else if (obj.getValue() instanceof RList) {
//                List<Object> list = new ArrayList<Object>(); 
//                Collection<Object> redissonList = (Collection<Object>) obj.getValue();
//                for (Object object : redissonList) {
//                    if (isLiveObject(object)) {
//                        Object detachedObject = alreadyDetached.get(getMap(object).getName());
//                        if (detachedObject == null) {
//                            detachedObject = detach(object, alreadyDetached);
//                        }
//                        object = detachedObject;
//                    }
//                    list.add(object);
//                }
//
//                ClassUtils.setField(detached, obj.getKey(), list);
//            }
//        }
        String idFieldName = getRIdFieldName(detachedObject.getClass());
        BeanCopy.beans(detachedObject, attachedObject)
                .ignoreNulls(true)
                .exclude(idFieldName)
                .copy();
    }

    private String getRIdFieldName(Class cls) {
        return Introspectior.getFieldsWithAnnotation(cls, RId.class)
                .getOnly()
                .getName();
    }

    private <T, K> T instantiateLiveObject(Class<T> proxyClass, K id) throws Exception {
        if (id == null) {
            throw new IllegalStateException("Non-null value is required for the field with RId annotation.");
        }
        T instance = instantiate(proxyClass, id);
        asLiveObject(instance).setLiveObjectId(id);
        return instance;
    }

    private <T, K> T instantiateDetachedObject(Class<T> cls, K id) throws Exception {
        T instance = instantiate(cls, id);
        String fieldName = getRIdFieldName(cls);
        if (ClassUtils.getField(instance, fieldName) == null) {
            ClassUtils.setField(instance, fieldName, id);
        }
        return instance;
    }

    private <T, K> T instantiate(Class<T> cls, K id) throws Exception {
        for (Constructor<?> constructor : cls.getDeclaredConstructors()) {
            if (constructor.getParameterCount() == 0) {
                constructor.setAccessible(true);
                return (T) constructor.newInstance();
            }
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
        if (!entityClass.isAnnotationPresent(REntity.class)) {
            throw new IllegalArgumentException("REntity annotation is missing from class type declaration.");
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
            idField = entityClass.getDeclaredField(idFieldName);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        if (idField.getType().isAnnotationPresent(REntity.class)) {
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
                .intercept(MethodDelegation.to(
                                new LiveObjectInterceptor(redisson, codecProvider, entityClass,
                                        getRIdFieldName(entityClass)))
                        .appendParameterBinder(FieldProxy.Binder
                                .install(LiveObjectInterceptor.Getter.class,
                                        LiveObjectInterceptor.Setter.class)))
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
                        .and(ElementMatchers.isPublic()))
                .intercept(MethodDelegation.to(
                                new AccessorInterceptor(redisson)))
                .make().load(getClass().getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        classCache.putIfAbsent(entityClass, proxied);
    }
}
