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
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.*;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.REntity.TransformationMode;
import org.redisson.api.annotation.RId;
import org.redisson.api.annotation.RIndex;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.connection.ConnectionManager;
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

    private final CommandAsyncExecutor commandExecutor;
    private final ConnectionManager connectionManager;

    public AccessorInterceptor(CommandAsyncExecutor commandExecutor, ConnectionManager connectionManager) {
        this.commandExecutor = commandExecutor;
        this.connectionManager = connectionManager;
    }

    @RuntimeType
    @SuppressWarnings("NestedIfDepth")
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
                RObject ar = connectionManager.getCommandExecutor().getObjectBuilder().createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), fieldType, fieldName);
                if (ar != null) {
                    connectionManager.getCommandExecutor().getObjectBuilder().store(ar, fieldName, liveMap);
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
                return connectionManager.getCommandExecutor().getObjectBuilder().fromReference((RedissonReference) result);
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
                NamingScheme ns = connectionManager.getCommandExecutor().getObjectBuilder().getNamingScheme(rEntity);

                if (commandExecutor instanceof CommandBatchService) {
                    liveMap.fastPutAsync(fieldName, new RedissonReference(rEntity,
                            ns.getName(rEntity, fieldType, getREntityIdFieldName(liveObject),
                                    liveObject.getLiveObjectId())));
                } else {
                    liveMap.fastPut(fieldName, new RedissonReference(rEntity,
                            ns.getName(rEntity, fieldType, getREntityIdFieldName(liveObject),
                                    liveObject.getLiveObjectId())));
                }

                return me;
            }
            
            if (!(arg instanceof RObject)
                    && (arg instanceof Collection || arg instanceof Map)
                    && TransformationMode.ANNOTATION_BASED
                            .equals(ClassUtils.getAnnotation(me.getClass().getSuperclass(),
                            REntity.class).fieldTransformation())) {
                RObject rObject = connectionManager.getCommandExecutor().getObjectBuilder().createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), arg.getClass(), fieldName);
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
                connectionManager.getCommandExecutor().getObjectBuilder().store((RObject) arg, fieldName, liveMap);
                return me;
            }

            if (arg == null) {
                Object oldArg = liveMap.remove(fieldName);
                if (field.getAnnotation(RIndex.class) != null) {
                    NamingScheme namingScheme = connectionManager.getCommandExecutor().getObjectBuilder().getNamingScheme(me.getClass().getSuperclass());
                    String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), fieldName);

                    CommandBatchService ce;
                    if (commandExecutor instanceof CommandBatchService) {
                        ce = (CommandBatchService) commandExecutor;
                    } else {
                        ce = new CommandBatchService(connectionManager);
                    }

                    if (oldArg instanceof Number) {
                        RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
                        set.removeAsync(((RLiveObject) me).getLiveObjectId());
                    } else {
                        RMultimapAsync<Object, Object> map = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                        if (oldArg instanceof RLiveObject) {
                            map.removeAsync(((RLiveObject) oldArg).getLiveObjectId(), ((RLiveObject) me).getLiveObjectId());
                        } else {
                            map.removeAsync(oldArg, ((RLiveObject) me).getLiveObjectId());
                        }
                    }

                    ce.execute();
                }
            } else {
                storeIndex(field, me, arg);

                if (commandExecutor instanceof CommandBatchService) {
                    liveMap.fastPutAsync(fieldName, arg);
                } else {
                    liveMap.fastPut(fieldName, arg);
                }
            }
            return me;
        }
        return superMethod.call();
    }

    protected void storeIndex(Field field, Object me, Object arg) {
        if (field.getAnnotation(RIndex.class) != null) {
            NamingScheme namingScheme = connectionManager.getCommandExecutor().getObjectBuilder().getNamingScheme(me.getClass().getSuperclass());
            String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), field.getName());

            boolean skipExecution = false;
            CommandBatchService ce;
            if (commandExecutor instanceof CommandBatchService) {
                ce = (CommandBatchService) commandExecutor;
                skipExecution = true;
            } else {
                ce = new CommandBatchService(connectionManager);
            }

            if (arg instanceof Number) {
                RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
                set.addAsync(((Number) arg).doubleValue(), ((RLiveObject) me).getLiveObjectId());
            } else {
                RMultimapAsync<Object, Object> map = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                map.putAsync(arg, ((RLiveObject) me).getLiveObjectId());
            }

            if (!skipExecution) {
                ce.execute();
            }
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

    private static String getREntityIdFieldName(Object o) {
        return Introspectior
                .getFieldsWithAnnotation(o.getClass().getSuperclass(), RId.class)
                .getOnly()
                .getName();
    }

}
