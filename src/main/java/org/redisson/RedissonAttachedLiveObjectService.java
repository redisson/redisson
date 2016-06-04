package org.redisson;

import io.netty.util.internal.PlatformDependent;
import java.lang.reflect.Modifier;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.description.field.FieldDescription;
import net.bytebuddy.description.field.FieldList;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RObject;
import org.redisson.liveobject.RAttachedLiveObjectService;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.misc.Introspectior;

public class RedissonAttachedLiveObjectService implements RAttachedLiveObjectService {

    private static final Map<Class, Class> classCache
            = PlatformDependent.<Class, Class>newConcurrentHashMap();
    private static final Map<Class, Class> proxyCache
            = PlatformDependent.<Class, Class>newConcurrentHashMap();

    private final RedissonClient redisson;
    private final CommandAsyncExecutor commandExecutor;

    public RedissonAttachedLiveObjectService(RedissonClient redisson, CommandAsyncExecutor commandExecutor) {
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    //TODO: Support ID Generator
    @Override
    public <T, K> T get(Class<T> entityClass, K id, long ttl) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
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
        FieldList<FieldDescription.InDefinedShape> fieldsWithRIdAnnotation = Introspectior.getFieldsWithAnnotation(entityClass, RId.class);
        if (fieldsWithRIdAnnotation.size() == 0) {
            throw new IllegalArgumentException("RId annotation is missing from class field declaration.");
        }
        if (fieldsWithRIdAnnotation.size() > 1) {
            throw new IllegalArgumentException("Only one field with RId annotation is allowed in class field declaration.");
        }
        FieldDescription.ForLoadedField idField = (FieldDescription.ForLoadedField) fieldsWithRIdAnnotation.getOnly();
        String idFieldName = idField.getName();
        if (entityClass.getDeclaredField(idFieldName).getType().isAnnotationPresent(REntity.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of which class is annotated with REntity.");
        }
        if (entityClass.getDeclaredField(idFieldName).getType().isAssignableFrom(RObject.class)) {
            throw new IllegalArgumentException("Field with RId annotation cannot be a type of RObject");
        }
        classCache.putIfAbsent(entityClass, new ByteBuddy()
                .subclass(entityClass)
                .method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))
                        .and(ElementMatchers.isGetter()
                                .or(ElementMatchers.isSetter()))
                        .and(ElementMatchers.isPublic()))
                .intercept(MethodDelegation.to(new AccessorInterceptor(redisson, entityClass, idFieldName, commandExecutor)))
                .make().load(getClass().getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded());
        proxyCache.putIfAbsent(classCache.get(entityClass), entityClass);
    }

    public static void unregisterProxy(Class proxy) {
        Class cls = proxyCache.remove(proxy);
        if (cls != null) {
            classCache.remove(cls);
        }
    }

    public static void unregisterClass(Class cls) {
        Class proxy = classCache.remove(cls);
        if (proxy != null) {
            proxyCache.remove(proxy);
        }
    }

    public static Class getActualClass(Class proxyClass) {
        return proxyCache.get(proxyClass);
    }

}
