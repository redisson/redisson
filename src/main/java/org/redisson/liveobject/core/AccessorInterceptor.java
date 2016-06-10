package org.redisson.liveobject.core;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;
import org.redisson.RedissonClient;
import org.redisson.RedissonReference;
import org.redisson.client.codec.Codec;
import org.redisson.core.RMap;
import org.redisson.core.RObject;
import org.redisson.liveobject.CodecProvider;
import org.redisson.liveobject.RLiveObject;
import org.redisson.liveobject.annotation.REntity;
import org.redisson.liveobject.annotation.RId;
import org.redisson.liveobject.misc.Introspectior;

/**
 * This class is going to be instantiated and becomes a <b>static</b> field of
 * the proxied target class. That is one instance of this class per proxied
 * class.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class AccessorInterceptor {

    private final RedissonClient redisson;
    private final CodecProvider codecProvider;

    public AccessorInterceptor(RedissonClient redisson, CodecProvider codecProvider) throws Exception {
        this.redisson = redisson;
        this.codecProvider = codecProvider;
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @SuperCall Callable<?> superMethod,
            @AllArguments Object[] args, @This Object me) throws Exception {
        if (isGetter(method, getREntityIdFieldName(me))) {
            return ((RLiveObject) me).getLiveObjectId();
        }
        if (isSetter(method, getREntityIdFieldName(me))) {
            ((RLiveObject) me).setLiveObjectId(args[0]);
            return null;
        }
        RMap liveMap = ((RLiveObject) me).getLiveObjectLiveMap();
        String fieldName = getFieldName(method);
        if (isGetter(method, fieldName)) {
            Object result = liveMap.get(fieldName);
            if (result instanceof RedissonReference) {
                return createRedissonObject((RedissonReference) result, method.getReturnType());
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            if (args[0].getClass().getSuperclass().isAnnotationPresent(REntity.class)) {
                Class<? extends Object> rEntity = args[0].getClass().getSuperclass();
                REntity.NamingScheme ns = rEntity.getAnnotation(REntity.class).namingScheme().newInstance();
                return liveMap.put(fieldName, new RedissonReference(rEntity, ns.getName(rEntity, getREntityIdFieldName(args[0]), ((RLiveObject) args[0]).getLiveObjectId())));
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

    private static String getFieldNameSuffix(String fieldName) {
        return fieldName.substring(0, 1).toUpperCase() + fieldName.substring(1);
    }

    private static String getREntityIdFieldName(Object o) throws Exception {
        return Introspectior
                .getFieldsWithAnnotation(o.getClass().getSuperclass(), RId.class)
                .getOnly()
                .getName();
    }

    private Object createRedissonObject(RedissonReference rr, Class expected) throws Exception {
        Class<? extends Object> type = rr.getType();
        if (type != null) {
            if (type.isAnnotationPresent(REntity.class)) {
                REntity.NamingScheme ns = type.getAnnotation(REntity.class).namingScheme().newInstance();
                return (RLiveObject) redisson.getAttachedLiveObjectService(codecProvider).get(type, ns.resolveId(rr.getKeyName()));
            }
            for (Method method : RedissonClient.class.getDeclaredMethods()) {
                if (method.getName().startsWith("get")
                        && method.getReturnType().isAssignableFrom(type)
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
