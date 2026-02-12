/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import net.bytebuddy.implementation.bind.annotation.*;
import org.redisson.RedissonLiveObjectService;
import org.redisson.RedissonObject;
import org.redisson.api.RFuture;
import org.redisson.api.RLiveObject;
import org.redisson.api.RMap;
import org.redisson.client.RedisException;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.command.CommandBatchService;
import org.redisson.liveobject.resolver.MapResolver;
import org.redisson.liveobject.resolver.NamingScheme;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class LiveObjectInterceptor {

    public interface Getter {

        Object getValue();
    }

    public interface Setter {

        void setValue(Object value);
    }

    private final CommandAsyncExecutor commandExecutor;
    private final Class<?> entityClass;
    private final NamingScheme namingScheme;
    private final RedissonLiveObjectService service;
    private final MapResolver mapResolver;

    public LiveObjectInterceptor(CommandAsyncExecutor commandExecutor, RedissonLiveObjectService service, Class<?> entityClass,
                                 MapResolver mapResolver) {
        this.service = service;
        this.mapResolver = mapResolver;
        this.commandExecutor = commandExecutor;
        this.entityClass = entityClass;
        this.namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);
    }

    @RuntimeType
    public Object intercept(
            @Origin Method method,
            @AllArguments Object[] args,
            @This Object me,
            @FieldValue("liveObjectId") Object id,
            @FieldProxy("liveObjectId") Setter idSetter,
            @FieldProxy("liveObjectId") Getter idGetter,
            @FieldValue("liveObjectLiveMap") RMap<String, ?> map,
            @FieldProxy("liveObjectLiveMap") Setter mapSetter,
            @FieldProxy("liveObjectLiveMap") Getter mapGetter
    ) throws Throwable {
        if ("setLiveObjectId".equals(method.getName())) {
            if (args[0].getClass().isArray()) {
                throw new UnsupportedOperationException("RId value cannot be an array.");
            }
            if (idGetter.getValue() != null) {
                mapResolver.remove(entityClass, idGetter.getValue());

                String idKey = namingScheme.getName(entityClass, args[0]);
                if (map != null) {
                    if (((RedissonObject) map).getRawName().equals(idKey)) {
                        return map;
                    }
                    try {
                        map.rename(idKey);
                        idSetter.setValue(args[0]);
                        return null;
                    } catch (RedisException e) {
                        if (e.getMessage() == null || !e.getMessage().startsWith("ERR no such key")) {
                            throw e;
                        }
                        //key may already renamed by others.
                    }
                }
            }

            idSetter.setValue(args[0]);
            return null;
        }

        if ("getLiveObjectId".equals(method.getName())) {
            return id;
        }

        if ("delete".equals(method.getName())) {
            CommandBatchService ce;
            if (commandExecutor instanceof CommandBatchService) {
                ce = (CommandBatchService) commandExecutor;
            } else {
                ce = new CommandBatchService(commandExecutor);
            }

            Object idd = ((RLiveObject) me).getLiveObjectId();
            RFuture<Long> deleteFuture = service.delete(idd, me.getClass().getSuperclass(), namingScheme, ce);
            ce.execute();

            return commandExecutor.get(deleteFuture.toCompletableFuture()) > 0;
        }

        if (map == null) {
            map = mapResolver.resolve(commandExecutor, entityClass, id, mapSetter, mapGetter);
        }

        if ("getLiveObjectLiveMap".equals(method.getName())) {
            return map;
        }

        try {
            return method.invoke(map, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }

}
