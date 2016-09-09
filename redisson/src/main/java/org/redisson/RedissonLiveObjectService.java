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
import java.util.Map;
import java.util.concurrent.ConcurrentMap;

import org.redisson.api.RLiveObject;
import org.redisson.api.RLiveObjectService;
import org.redisson.api.RObject;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.RId;
import org.redisson.liveobject.LiveObjectTemplate;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.core.LiveObjectInterceptor;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.codec.CodecProvider;
import org.redisson.liveobject.provider.ResolverProvider;
import org.redisson.liveobject.resolver.Resolver;

import jodd.bean.BeanCopy;
import jodd.bean.BeanUtil;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.implementation.bind.annotation.FieldProxy;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.api.RExpirable;
import org.redisson.api.RExpirableAsync;
import org.redisson.api.RMap;
import org.redisson.api.RMapAsync;
import org.redisson.api.RObjectAsync;
import org.redisson.api.annotation.RFieldAccessor;
import org.redisson.liveobject.core.FieldAccessorInterceptor;
import org.redisson.liveobject.core.RExpirableInterceptor;
import org.redisson.liveobject.core.RMapInterceptor;
import org.redisson.liveobject.core.RObjectInterceptor;

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
    @Override
    public <T> T create(Class<T> entityClass) {
        try {
            Class<? extends T> proxyClass = getProxyClass(entityClass);
            String idFieldName = getRIdFieldName(entityClass);
            RId annotation = entityClass
                    .getDeclaredField(idFieldName)
                    .getAnnotation(RId.class);
            Resolver resolver = resolverProvider.getResolver(entityClass,
                    annotation.generator(), annotation);
            Object id = resolver.resolve(entityClass, annotation, idFieldName, redisson);
            T proxied = instantiateLiveObject(proxyClass, id);
            return asLiveObject(proxied).isExists() ? null : proxied;
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
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
            return instantiateLiveObject(getProxyClass(entityClass), id);
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
            Class<? extends T> proxyClass = getProxyClass(entityClass);
            return instantiateLiveObject(proxyClass,
                    BeanUtil.pojo.getSimpleProperty(detachedObject,
                            getRIdFieldName(detachedObject.getClass())));
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw ex instanceof RuntimeException ? (RuntimeException) ex : new RuntimeException(ex);
        }
    }

    @Override
    public <T> T merge(T detachedObject) {
        T attachedObject = attach(detachedObject);
        copy(detachedObject, attachedObject);
        return attachedObject;
    }

    @Override
    public <T> T persist(T detachedObject) {
        T attachedObject = attach(detachedObject);
        if (!asLiveObject(attachedObject).isExists()) {
            copy(detachedObject, attachedObject);
            return attachedObject;
        }
        throw new IllegalStateException("This REntity already exists.");
    }

    @Override
    public <T> T detach(T attachedObject) {
        validateAttached(attachedObject);
        try {
            T detached = instantiateDetachedObject((Class<T>) attachedObject.getClass().getSuperclass(), asLiveObject(attachedObject).getLiveObjectId());
            BeanCopy.beans(attachedObject, detached).declared(false, true).copy();
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
    public <T> RMap asRMap(T instance) {
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
        if (BeanUtil.pojo.getSimpleProperty(instance, getRIdFieldName(cls)) == null) {
            BeanUtil.pojo.setSimpleProperty(instance, getRIdFieldName(cls), id);
        }
        return instance;
    }

    private <T, K> T instantiate(Class<T> cls, K id) throws Exception {
        try {
            return cls.newInstance();
        } catch (Exception exception) {
            for (Constructor<?> ctor : classCache.containsKey(cls) ? cls.getConstructors() : cls.getDeclaredConstructors()) {
                if (ctor.getParameterTypes().length == 1 && ctor.getParameterTypes()[0].isAssignableFrom(id.getClass())) {
                    return (T) ctor.newInstance(id);
                }
            }
        }
        throw new NoSuchMethodException("Unable to find constructor matching only the RId field type [" + id.getClass().getCanonicalName() + "].");
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
