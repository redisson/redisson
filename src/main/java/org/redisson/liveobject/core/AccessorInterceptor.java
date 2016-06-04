package org.redisson.liveobject.core;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.redisson.RedissonAttachedLiveObjectService;
import org.redisson.RedissonClient;
import org.redisson.RedissonReference;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RMap;
import org.redisson.core.RObject;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.misc.Introspectior;

/**
 *
 * @author ruigu
 */
public class AccessorInterceptor {

    private final RedissonClient redisson;
    private final Class originalClass;
    private final String idFieldName;
    private final REntity.NamingScheme namingScheme;
    private final CommandAsyncExecutor commandExecutor;
    private RMap liveMap;

    public AccessorInterceptor(RedissonClient redisson, Class entityClass,
            String idFieldName, CommandAsyncExecutor commandExecutor) throws Exception {
        this.redisson = redisson;
        this.originalClass = entityClass;
        this.idFieldName = idFieldName;
        this.commandExecutor = commandExecutor;
        this.namingScheme = ((REntity) entityClass.getAnnotation(REntity.class))
                .namingScheme().newInstance();
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @SuperCall Callable<?> superMethod,
            @AllArguments Object[] args, @This Object me) throws Exception {
        if (isGetter(method, idFieldName)) {
            return superMethod.call();
        }
        initLiveMapIfRequired(getId(me));
        if (isSetter(method, idFieldName)) {
            superMethod.call();
            try {
                liveMap.rename(getMapKey(args[0]));
            } catch (RedisException e) {
                if (e.getMessage() == null || !e.getMessage().startsWith("ERR no such key")) {
                    throw e;
                }
            }
            liveMap = null;
            return null;
        }
        String fieldName = getFieldName(method);
        if (isGetter(method, fieldName)) {
            Object result = liveMap.get(fieldName);
            if (method.getReturnType().isAnnotationPresent(REntity.class)) {
                return redisson.getAttachedLiveObjectService()
                        .get((Class<Object>) method.getReturnType(), result);
            } else if (result instanceof RedissonReference) {
                RedissonReference r = ((RedissonReference) result);
                return r.getType()
                        .getConstructor(Codec.class, CommandAsyncExecutor.class, String.class)
                        .newInstance(r.isDefaultCodec()
                                        ? commandExecutor.getConnectionManager().getCodec()
                                        : r.getCodec(), commandExecutor, r.getKeyName());
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            if (method.getParameterTypes()[0].isAnnotationPresent(REntity.class)) {
                return liveMap.fastPut(fieldName, getREntityId(args[0]));
            } else if (args[0] instanceof RObject) {
                RObject ar = (RObject) args[0];
                return liveMap.fastPut(fieldName, new RedissonReference((Class<RObject>) args[0].getClass(), ar.getName()));
            }
            return liveMap.fastPut(fieldName, args[0]);
        }
        return superMethod.call();
    }

    private void initLiveMapIfRequired(Object id) {
        if (liveMap == null) {
            liveMap = redisson.getMap(getMapKey(id));
        }
    }

    private String getFieldName(Method method) {
        return method.getName().substring(3, 4).toLowerCase() + method.getName().substring(4);
    }

    private boolean isGetter(Method method, String fieldName) {
        return method.getName().startsWith("get")
                && method.getName().endsWith(getFieldNameSuffix(fieldName));
    }

    private boolean isSetter(Method method, String fieldName) {
        return method.getName().startsWith("set")
                && method.getName().endsWith(getFieldNameSuffix(fieldName));
    }

    private String getMapKey(Object id) {
        return namingScheme.getName(originalClass, idFieldName, id);
    }

    private Object getId(Object me) throws Exception {
        return originalClass.getDeclaredMethod("get" + getFieldNameSuffix(idFieldName)).invoke(me);
    }

    private static String getFieldNameSuffix(String fieldName) {
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    private static Object getFieldValue(Object o, String fieldName) throws Exception {
        return RedissonAttachedLiveObjectService.getActualClass(o.getClass()).getDeclaredMethod("get" + getFieldNameSuffix(fieldName)).invoke(o);
    }

    private static Object getREntityId(Object o) throws Exception {
        String idName = Introspectior
                .getFieldsWithAnnotation(RedissonAttachedLiveObjectService.getActualClass(o.getClass()), RId.class)
                .getOnly()
                .getName();
        return getFieldValue(o, idName);
    }

}
