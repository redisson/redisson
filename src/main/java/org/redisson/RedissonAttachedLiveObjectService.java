package org.redisson;

import java.lang.reflect.Field;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.ByteCodeElement;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.dynamic.DynamicType;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.FieldAccessor;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatcher;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.core.RObject;
import org.redisson.liveobject.CodecProvider;
import org.redisson.liveobject.LiveObjectTemplate;
import org.redisson.liveobject.RAttachedLiveObjectService;
import org.redisson.liveobject.RLiveObject;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.misc.Introspectior;

public class RedissonAttachedLiveObjectService implements RAttachedLiveObjectService {

    private final Map<Class, Class> classCache;
    private final RedissonClient redisson;

    private final CodecProvider codecProvider;

    public RedissonAttachedLiveObjectService(RedissonClient redisson, Map<Class, Class> classCache, CodecProvider codecProvider) {
        this.redisson = redisson;
        this.classCache = classCache;
        this.codecProvider = codecProvider;
    }

    //TODO: Support ID Generator
    @Override
    public <T, K> T get(Class<T> entityClass, K id, long ttl) {
        T instance = get(entityClass, id);
        return instance;
    }

    @Override
    public <T, K> T get(Class<T> entityClass, K id) {
        try {
            //TODO: support class with no arg constructor
            return getProxyClass(entityClass).getConstructor(id.getClass()).newInstance(id);
        } catch (Exception ex) {
            unregisterClass(entityClass);
            throw new RuntimeException(ex);
        }
    }

    private <T, K> Class<? extends T> getProxyClass(Class<T> entityClass) throws Exception {
        if (!classCache.containsKey(entityClass)) {
            registerClass(entityClass);
        }
        return classCache.get(entityClass);
    }

    private <T, K> void registerClass(Class<T> entityClass) throws Exception {
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
        FieldDescription.InDefinedShape idField = fieldsWithRIdAnnotation.getOnly();
        String idFieldName = idField.getName();
        if (entityClass.getDeclaredField(idFieldName).getType().isAnnotationPresent(REntity.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of which class is annotated with REntity.");
        }
        if (entityClass.getDeclaredField(idFieldName).getType().isAssignableFrom(RObject.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of RObject");
        }
        DynamicType.Builder<T> builder = new ByteBuddy()
                .subclass(entityClass);
        for (FieldDescription.InDefinedShape field
                : Introspectior.getTypeDescription(LiveObjectTemplate.class)
                .getDeclaredFields()) {
            builder = builder.define(field);
        }
        Class<? extends T> loaded = builder.method(ElementMatchers.isDeclaredBy(
                Introspectior.getTypeDescription(RLiveObject.class))
                .and(ElementMatchers.isGetter().or(ElementMatchers.isSetter())))
                .intercept(FieldAccessor.ofBeanProperty())
                .implement(RLiveObject.class)
                .method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))
                        .and(ElementMatchers.not(ElementMatchers.isDeclaredBy(RLiveObject.class)))
                        .and(ElementMatchers.isGetter()
                                .or(ElementMatchers.isSetter()))
                        .and(ElementMatchers.isPublic()))
                .intercept(MethodDelegation.to(
                                new AccessorInterceptor(redisson, codecProvider, entityClass, idFieldName)))
                .make().load(getClass().getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded();
        classCache.putIfAbsent(entityClass, loaded);
    }

    public void unregisterClass(Class cls) {
        classCache.remove(cls.isAssignableFrom(RLiveObject.class) ? cls.getSuperclass() : cls);
    }

    /**
     * @return the codecProvider
     */
    public CodecProvider getCodecProvider() {
        return codecProvider;
    }

}
