/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import io.netty.buffer.ByteBuf;
import net.bytebuddy.implementation.bind.annotation.*;
import org.redisson.RedissonObject;
import org.redisson.RedissonReference;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.*;
import org.redisson.api.annotation.REntity;
import org.redisson.api.annotation.REntity.TransformationMode;
import org.redisson.api.annotation.RIndex;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.liveobject.misc.ClassUtils;
import org.redisson.liveobject.misc.Introspectior;
import org.redisson.liveobject.resolver.NamingScheme;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.Callable;
import java.util.regex.Pattern;

/**
 * This class is going to be instantiated and becomes a <b>static</b> field of
 * the proxied target class. That is one instance of this class per proxied
 * class.
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class AccessorInterceptor {

    private static final Pattern GETTER_PATTERN = Pattern.compile("^(get|is)");
    private static final Pattern SETTER_PATTERN = Pattern.compile("^(set)");
    private static final Pattern FIELD_PATTERN = Pattern.compile("^(get|set|is)");

    private final CommandAsyncExecutor commandExecutor;

    public AccessorInterceptor(CommandAsyncExecutor commandExecutor) {
        this.commandExecutor = commandExecutor;
    }

    @RuntimeType
    @SuppressWarnings("NestedIfDepth")
    public Object intercept(@Origin Method method,
                            @SuperCall Callable<?> superMethod,
                            @AllArguments Object[] args,
                            @This Object me,
                            @FieldValue("liveObjectLiveMap") RMap<String, Object> liveMap) throws Exception {
        if (isGetter(method, getREntityIdFieldName(me))) {
            return ((RLiveObject) me).getLiveObjectId();
        }
        if (isSetter(method, getREntityIdFieldName(me))) {
            ((RLiveObject) me).setLiveObjectId(args[0]);
            return null;
        }

        String fieldName = getFieldName(me.getClass().getSuperclass(), method);
        Field field = ClassUtils.getDeclaredField(me.getClass().getSuperclass(), fieldName);
        Class<?> fieldType = field.getType();
        
        if (isGetter(method, fieldName)) {
            if (Modifier.isTransient(field.getModifiers())) {
                return field.get(me);
            }

            Object result = liveMap.get(fieldName);
            if (result == null) {
                RObject ar = commandExecutor.getObjectBuilder().createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), fieldType, fieldName);
                if (ar != null) {
                    commandExecutor.getObjectBuilder().store(ar, fieldName, liveMap);
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
                return commandExecutor.getObjectBuilder().fromReference((RedissonReference) result, RedissonObjectBuilder.ReferenceType.DEFAULT);
            }
            return result;
        }
        if (isSetter(method, fieldName)) {
            Object arg = args[0];
            if (Modifier.isTransient(field.getModifiers())) {
                field.set(me, arg);
                return me;
            }
            if (arg != null && ClassUtils.isAnnotationPresent(arg.getClass(), REntity.class)) {
                throw new IllegalStateException("REntity object should be attached to Redisson first");
            }
            
            if (arg instanceof RLiveObject) {
                RLiveObject liveObject = (RLiveObject) arg;

                removeIndex(liveMap, me, field);
                storeIndex(field, me, liveObject.getLiveObjectId());
                
                Class<? extends Object> rEntity = liveObject.getClass().getSuperclass();
                NamingScheme ns = commandExecutor.getObjectBuilder().getNamingScheme(rEntity);

                if (commandExecutor instanceof CommandBatchService) {
                    liveMap.fastPutAsync(fieldName, new RedissonReference(rEntity,
                            ns.getName(rEntity, liveObject.getLiveObjectId())));
                } else {
                    liveMap.fastPut(fieldName, new RedissonReference(rEntity,
                            ns.getName(rEntity, liveObject.getLiveObjectId())));
                }

                return me;
            }
            
            if (!(arg instanceof RObject)
                    && (arg instanceof Collection || arg instanceof Map)
                    && TransformationMode.ANNOTATION_BASED
                            .equals(ClassUtils.getAnnotation(me.getClass().getSuperclass(),
                            REntity.class).fieldTransformation())) {
                RObject rObject = commandExecutor.getObjectBuilder().createObject(((RLiveObject) me).getLiveObjectId(), me.getClass().getSuperclass(), arg.getClass(), fieldName);
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
                if (commandExecutor instanceof CommandBatchService) {
                    commandExecutor.getObjectBuilder().storeAsync((RObject) arg, fieldName, liveMap);
                } else {
                    commandExecutor.getObjectBuilder().store((RObject) arg, fieldName, liveMap);
                }
                return me;
            }

            removeIndex(liveMap, me, field);
            if (arg != null) {
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

    private static final Set<Class<?>> PRIMITIVE_CLASSES = new HashSet<>(Arrays.asList(
                        byte.class, short.class, int.class, long.class, float.class, double.class));

    private void removeIndex(RMap<String, Object> liveMap, Object me, Field field) {
        if (field.getAnnotation(RIndex.class) == null) {
            return;
        }

        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(me.getClass().getSuperclass());
        String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), field.getName());

        CommandBatchService ce;
        if (commandExecutor instanceof CommandBatchService) {
            ce = (CommandBatchService) commandExecutor;
        } else {
            ce = new CommandBatchService(commandExecutor);
        }

        if (Number.class.isAssignableFrom(field.getType()) || PRIMITIVE_CLASSES.contains(field.getType())) {
            RScoredSortedSetAsync<Object> set = new RedissonScoredSortedSet<>(namingScheme.getCodec(), ce, indexName, null);
            set.removeAsync(((RLiveObject) me).getLiveObjectId());
        } else {
            if (ClassUtils.isAnnotationPresent(field.getType(), REntity.class)
                    || commandExecutor.getConnectionManager().isClusterMode()) {
                Object value = liveMap.remove(field.getName());
                if (value != null) {
                    RMultimapAsync<Object, Object> map = new RedissonSetMultimap<>(namingScheme.getCodec(), ce, indexName);
                    Object k = value;
                    if (ClassUtils.isAnnotationPresent(field.getType(), REntity.class)) {
                        k = ((RLiveObject) value).getLiveObjectId();
                    }
                    map.removeAsync(k, ((RLiveObject) me).getLiveObjectId());
                }
            } else {
                removeAsync(ce, indexName, ((RedissonObject) liveMap).getRawName(),
                        namingScheme.getCodec(), ((RLiveObject) me).getLiveObjectId(), field.getName());
            }
        }

        if (ce != commandExecutor) {
            ce.execute();
        }
    }

    private void removeAsync(CommandBatchService ce, String name, String mapName, Codec codec, Object value, String fieldName) {
        ByteBuf valueState = ce.encodeMapValue(codec, value);
        ce.evalWriteAsync(name, codec, RedisCommands.EVAL_VOID,
                  "local oldArg = redis.call('hget', KEYS[2], ARGV[2]);" +
                        "if oldArg == false then " +
                            "return; " +
                        "end;" +
                        "redis.call('hdel', KEYS[2], ARGV[2]); " +
                        "local hash = redis.call('hget', KEYS[1], oldArg); " +
                        "local setName = KEYS[1] .. ':' .. hash; " +
                        "local res = redis.call('srem', setName, ARGV[1]); " +
                        "if res == 1 and redis.call('scard', setName) == 0 then " +
                            "redis.call('hdel', KEYS[1], oldArg); " +
                        "end; ",
            Arrays.asList(name, mapName),
                valueState, ce.encodeMapKey(codec, fieldName));
    }

    private void storeIndex(Field field, Object me, Object arg) {
        if (field.getAnnotation(RIndex.class) == null) {
            return;
        }

        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(me.getClass().getSuperclass());
        String indexName = namingScheme.getIndexName(me.getClass().getSuperclass(), field.getName());

        boolean skipExecution = false;
        CommandBatchService ce;
        if (commandExecutor instanceof CommandBatchService) {
            ce = (CommandBatchService) commandExecutor;
            skipExecution = true;
        } else {
            ce = new CommandBatchService(commandExecutor);
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

    private String getFieldName(Class<?> clazz, Method method) {
        String fieldName = FIELD_PATTERN.matcher(method.getName()).replaceFirst("");
        String propName = fieldName.substring(0, 1).toLowerCase() + fieldName.substring(1);
        try {
            ClassUtils.getDeclaredField(clazz, propName);
            return propName;
        } catch (NoSuchFieldException e) {
            return fieldName;
        }
    }

    private boolean isGetter(Method method, String fieldName) {
        return GETTER_PATTERN.matcher(method.getName()).replaceFirst("").equalsIgnoreCase(fieldName);
    }

    private boolean isSetter(Method method, String fieldName) {
        return SETTER_PATTERN.matcher(method.getName()).replaceFirst("").equalsIgnoreCase(fieldName);
    }

    private static String getREntityIdFieldName(Object o) {
        return Introspectior.getREntityIdFieldName(o.getClass().getSuperclass());
    }

}
