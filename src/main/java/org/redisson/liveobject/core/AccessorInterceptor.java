/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.liveobject.core;

import java.lang.reflect.Method;
import java.util.concurrent.Callable;
import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
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
import org.redisson.liveobject.misc.RedissonObjectFactory;

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
            @AllArguments Object[] args, @This Object me,
            @FieldValue("liveObjectLiveMap") RMap liveMap) throws Exception {
        if (isGetter(method, getREntityIdFieldName(me))) {
            return ((RLiveObject) me).getLiveObjectId();
        }
        if (isSetter(method, getREntityIdFieldName(me))) {
            ((RLiveObject) me).setLiveObjectId(args[0]);
            return null;
        }
        String fieldName = getFieldName(method);
        if (isGetter(method, fieldName)) {
            Object result = liveMap.get(fieldName);
            if (result instanceof RedissonReference) {
                return RedissonObjectFactory.create(redisson, codecProvider, (RedissonReference) result, method.getReturnType());
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            if (args[0].getClass().getSuperclass().isAnnotationPresent(REntity.class)) {
                Class<? extends Object> rEntity = args[0].getClass().getSuperclass();
                REntity anno = rEntity.getAnnotation(REntity.class);
                REntity.NamingScheme ns = anno.namingScheme()
                        .getDeclaredConstructor(Codec.class)
                        .newInstance(codecProvider.getCodec(anno, (Class) rEntity));
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

}
