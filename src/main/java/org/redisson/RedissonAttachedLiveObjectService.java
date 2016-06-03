package org.redisson;

import io.netty.util.internal.PlatformDependent;
import java.util.Map;
import net.bytebuddy.ByteBuddy;
import net.bytebuddy.dynamic.loading.ClassLoadingStrategy;
import net.bytebuddy.implementation.MethodDelegation;
import net.bytebuddy.matcher.ElementMatchers;
import org.redisson.command.CommandExecutor;
import org.redisson.liveobject.RAttachedLiveObjectService;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.core.AccessorInterceptor;
import org.redisson.liveobject.misc.Introspectior;

public class RedissonAttachedLiveObjectService<T, K> implements RAttachedLiveObjectService<T, K> {

    private static final Map<Class, Class> classCache
            = PlatformDependent.<Class, Class>newConcurrentHashMap();
    private static final Map<Class, Class> proxyCache
            = PlatformDependent.<Class, Class>newConcurrentHashMap();
    private final RedissonClient redisson;
    private final CommandExecutor commandExecutor;

    public RedissonAttachedLiveObjectService(RedissonClient redisson, CommandExecutor commandExecutor) {
        this.redisson = redisson;
        this.commandExecutor = commandExecutor;
    }

    //TODO: Support ID Generator
    
    @Override
    public T get(Class<T> entityClass, K id, long ttl) {
        throw new UnsupportedOperationException("Not supported yet."); //To change body of generated methods, choose Tools | Templates.
    }

    @Override
    public T get(Class<T> entityClass, K id) {
        try {
            //TODO: support class with no arg constructor
            return getProxyClass(entityClass).getConstructor(id.getClass()).newInstance(id);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }

    private Class<? extends T> getProxyClass(Class<T> entityClass) throws Exception {
        if (!classCache.containsKey(entityClass)) {
            registerClass(entityClass);
        }
        return classCache.get(entityClass);
    }

    private void registerClass(Class<T> entityClass) throws Exception {
        //TODO: check annotation on the entityClass
        String idFieldName = Introspectior.getFieldsWithAnnotation(entityClass, RId.class)
                .getOnly()
                .getName();
        classCache.putIfAbsent(entityClass, new ByteBuddy()
                .subclass(entityClass)
                .method(ElementMatchers.not(ElementMatchers.isDeclaredBy(Object.class))
                        .and(ElementMatchers.isGetter()
                                .or(ElementMatchers.isSetter()))
                        .and(ElementMatchers.isPublic()))
                .intercept(MethodDelegation.to(new AccessorInterceptor<T, K>(redisson, entityClass, idFieldName, commandExecutor)))
                .make().load(getClass().getClassLoader(),
                        ClassLoadingStrategy.Default.WRAPPER)
                .getLoaded());
        proxyCache.putIfAbsent(classCache.get(entityClass), entityClass);
    }
    
    public static Class getActualClass(Class proxyClass) {
        return proxyCache.get(proxyClass);
    }

}
