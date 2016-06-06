package org.redisson.liveobject.core;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.redisson.RedissonClient;
import org.redisson.RedissonMap;
import org.redisson.RedissonReference;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.core.RMap;
import org.redisson.core.RObject;
import org.redisson.liveobject.CodecProvider;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.misc.Introspectior;

/**
 * This class is going to be instantiated and becomes a <b>static</b> field of the proxied
 * target class. That is one instance of this class per proxied class.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class AccessorInterceptor {

    private final RedissonClient redisson;
    private final CodecProvider codecProvider;
    private final Class originalClass;
    private final String idFieldName;
    private final REntity.NamingScheme namingScheme;
    private final Class<? extends Codec> codecClass;

    public AccessorInterceptor(RedissonClient redisson, CodecProvider codecProvider, Class entityClass, String idFieldName) throws Exception {
        this.redisson = redisson;
        this.codecProvider = codecProvider;
        this.originalClass = entityClass;
        this.idFieldName = idFieldName;
        REntity anno = ((REntity) entityClass.getAnnotation(REntity.class));
        this.namingScheme = anno.namingScheme().newInstance();
        this.codecClass = anno.codec();
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @SuperCall Callable<?> superMethod,
            @AllArguments Object[] args, @This Object me) throws Exception {
        if (isGetter(method, idFieldName)) {
            return superMethod.call();
        }
        String id = getMapKey(getId(me));
        RMap liveMap = redisson.getMap(id, codecProvider.getCodec(codecClass, RedissonMap.class, id));
        if (isSetter(method, idFieldName)) {
            //TODO: distributed locking maybe required.
            try {
                liveMap.rename(getMapKey(args[0]));
            } catch (RedisException e) {
                if (e.getMessage() == null || !e.getMessage().startsWith("ERR no such key")) {
                    throw e;
                }
            }
            superMethod.call();
            return null;
        }
        String fieldName = getFieldName(method);
        if (isGetter(method, fieldName)) {
            Object result = liveMap.get(fieldName);
            if (method.getReturnType().isAnnotationPresent(REntity.class)) {
                return redisson.getAttachedLiveObjectService(codecProvider)
                        .get((Class<Object>) method.getReturnType(), result);
            } else if (result instanceof RedissonReference) {
                return createRedissonObject((RedissonReference) result, method.getReturnType());
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            if (method.getParameterTypes()[0].isAnnotationPresent(REntity.class)) {
                return liveMap.put(fieldName, getREntityId(args[0]));
            } else if (args[0] instanceof RObject) {
                RObject ar = (RObject) args[0];
                Codec codec = ar.getCodec();
                codecProvider.registerCodec(codec.getClass(), ar, fieldName, codec);
                return liveMap.put(fieldName, new RedissonReference(ar.getClass(), ar.getName(), codec));
            }
            return liveMap.put(fieldName, args[0]);
        }
        return superMethod.call();
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
        return o.getClass().getSuperclass().getDeclaredMethod("get" + getFieldNameSuffix(fieldName)).invoke(o);
    }

    private static Object getREntityId(Object o) throws Exception {
        String idName = Introspectior
                .getFieldsWithAnnotation(o.getClass().getSuperclass(), RId.class)
                .getOnly()
                .getName();
        return getFieldValue(o, idName);
    }

    private RObject createRedissonObject(RedissonReference rr, Class expected) throws Exception {
        if (rr.getType() != null) {
            for (Method method : RedissonClient.class.getDeclaredMethods()) {
                if (method.getName().startsWith("get")
                        && method.getReturnType().isAssignableFrom(rr.getType())
                        && expected.isAssignableFrom(method.getReturnType())) {
                    if (rr.isDefaultCodec() && method.getParameterCount() == 1) {
                        return (RObject) method.invoke(redisson, rr.getKeyName());
                    } else if (!rr.isDefaultCodec()
                            && method.getParameterCount() == 2
                            && String.class.equals(method.getParameterTypes()[0])
                            && Codec.class.equals(method.getParameterTypes()[1])) {
                        return (RObject) method.invoke(redisson, rr.getKeyName(), codecProvider.getCodec(rr.getCodecType()));
                    }
                }
            }
        }
        throw new ClassNotFoundException("No RObject is found to match class type of " + rr.getTypeName() + " with codec type of " + rr.getCodecName());
    }
}
