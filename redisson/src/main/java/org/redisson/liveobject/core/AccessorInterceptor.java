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
package org.redisson.liveobject.core;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;

import org.redisson.RedissonReference;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.api.RObject;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RedissonClient;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.REntity.TransformationMode;
import org.redisson.api.annotation.RId;
import org.redisson.api.annotation.RIndex;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

import net.bytebuddy.implementation.bind.annotation.AllArguments;
import net.bytebuddy.implementation.bind.annotation.FieldValue;
import net.bytebuddy.implementation.bind.annotation.Origin;
import net.bytebuddy.implementation.bind.annotation.RuntimeType;
import net.bytebuddy.implementation.bind.annotation.SuperCall;
import net.bytebuddy.implementation.bind.annotation.This;

/**
 * This class is going to be instantiated and becomes a <b>static</b> field of
 * the proxied target class. That is one instance of this class per proxied
 * class.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class AccessorInterceptor {

    private final RedissonClient redisson;
    private final RedissonObjectBuilder objectBuilder;

    public AccessorInterceptor(RedissonClient redisson, RedissonObjectBuilder objectBuilder) {
        this.redisson = redisson;
        this.objectBuilder = objectBuilder;
    }

    @RuntimeType
    public Object intercept(@Origin Method method, @SuperCall Callable<?> superMethod,
            @AllArguments Object[] args, @This Object me,
            @FieldValue("liveObjectLiveMap") RMap<String, Object> liveMap) throws Exception {
        if (isGetter(method, getREntityIdFieldName(me))) {
            return ((RLiveObject) me).getLiveObjectId();
        }
        if (isSetter(method, getREntityIdFieldName(me))) {
            ((RLiveObject) me).setLiveObjectId(args[0]);
            return null;
        }

        String fieldName = getFieldName(method);
        Field field = ClassUtils.getDeclaredField(me.getClass().getSuperclass(), fieldName);
        Class<?> fieldType = field.getType();
        
        if (isGetter(method, fieldName)) {
            Object result = liveMap.get(fieldName);
            if (result == null) {
                RObject ar = objectBuilder.createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), fieldType, fieldName, redisson);
                if (ar != null) {
                    objectBuilder.store(ar, fieldName, liveMap);
                    return ar;
                }
            }
            
            if (result != null && fieldType.isEnum()) {
                if (result instanceof String) {
                    return Enum.valueOf((Class) fieldType, (String) result);
                }
                return result;
            }
            if (result instanceof RedissonReference) {
                return objectBuilder.fromReference(redisson, (RedissonReference) result);
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            Object arg = args[0];
            if (arg != null && ClassUtils.isAnnotationPresent(arg.getClass(), REntity.class)) {
                throw new IllegalStateException("REntity object should be attached to Redisson first");
            }
            
            if (arg instanceof RLiveObject) {
                RLiveObject liveObject = (RLiveObject) arg;
                
                storeIndex(field, me, liveObject.getLiveObjectId());
                
                Class<? extends Object> rEntity = liveObject.getClass().getSuperclass();
                NamingScheme ns = objectBuilder.getNamingScheme(rEntity);
                liveMap.fastPut(fieldName, new RedissonReference(rEntity,
                        ns.getName(rEntity, fieldType, getREntityIdFieldName(liveObject),
                                liveObject.getLiveObjectId())));
                return me;
            }
            
            if (!(arg instanceof RObject)
                    && (arg instanceof Collection || arg instanceof Map)
                    && TransformationMode.ANNOTATION_BASED
                            .equals(ClassUtils.getAnnotation(me.getClass().getSuperclass(),
                            REntity.class).fieldTransformation())) {
                RObject rObject = objectBuilder.createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), arg.getClass(), fieldName, redisson);
                if (arg != null) {
                    if (rObject instanceof Collection) {
                        Collection<?> c = (Collection<?>) rObject;
                        c.clear();
                        c.addAll((Collection) arg);
                    } else {
                        Map<?, ?> m = (Map<?, ?>) rObject;
                        m.clear();
                        m.putAll((Map) arg);
                    }
                }
                if (rObject != null) {
                    arg = rObject;
                }
            }
            
            if (arg instanceof RObject) {
                objectBuilder.store((RObject) arg, fieldName, liveMap);
                return me;
            }

            if (arg == null) {
                Object oldArg = liveMap.remove(fieldName);
                if (field.getAnnotation(RIndex.class) != null) {
                    NamingScheme namingScheme = objectBuilder.getNamingScheme(me.getClass().getSuperclass());
                    String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), fieldName);
                    RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
                    if (oldArg instanceof RLiveObject) {
                        map.remove(((RLiveObject) oldArg).getLiveObjectId(), ((RLiveObject) me).getLiveObjectId());
                    } else {
                        map.remove(oldArg, ((RLiveObject) me).getLiveObjectId());
                    }
                }
            } else {
                storeIndex(field, me, arg);

                liveMap.fastPut(fieldName, arg);
            }
            return me;
        }
        return superMethod.call();
    }

    protected void storeIndex(Field field, Object me, Object arg) {
        if (field.getAnnotation(RIndex.class) != null) {
            NamingScheme namingScheme = objectBuilder.getNamingScheme(me.getClass().getSuperclass());
            String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), field.getName());
            RSetMultimap<Object, Object> map = redisson.getSetMultimap(indexName, namingScheme.getCodec());
            map.put(arg, ((RLiveObject) me).getLiveObjectId());
        }
    }

    private String getFieldName(Method method) {
        String name = method.getName();
        int i = 4;
        if (name.startsWith("is")) {
            i = 3;
        }
        return name.substring(i - 1, i).toLowerCase() + name.substring(i);
    }

    private boolean isGetter(Method method, String fieldName) {
        return method.getName().equals("get" + getFieldNameSuffix(fieldName))
                || method.getName().equals("is" + getFieldNameSuffix(fieldName));
    }

    private boolean isSetter(Method method, String fieldName) {
        return method.getName().equals("set" + getFieldNameSuffix(fieldName));
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
