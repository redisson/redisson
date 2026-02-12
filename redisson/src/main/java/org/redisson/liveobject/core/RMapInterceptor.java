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
import org.redisson.api.RMap;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.liveobject.resolver.MapResolver;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @author Nikita Koksharov
 */
public class RMapInterceptor {

    private final CommandAsyncExecutor commandAsyncExecutor;
    private final MapResolver mapResolver;
    private final Class<?> entityClass;

    public RMapInterceptor(CommandAsyncExecutor commandAsyncExecutor, Class<?> entityClass, MapResolver mapResolver) {
        this.commandAsyncExecutor = commandAsyncExecutor;
        this.mapResolver = mapResolver;
        this.entityClass = entityClass;
    }

    @RuntimeType
    public Object intercept(
            @Origin Method method,
            @AllArguments Object[] args,
            @FieldValue("liveObjectId") Object id,
            @FieldProxy("liveObjectLiveMap") LiveObjectInterceptor.Setter mapSetter,
            @FieldProxy("liveObjectLiveMap") LiveObjectInterceptor.Getter mapGetter
    ) throws Throwable {
        try {
            RMap map = mapResolver.resolve(commandAsyncExecutor, entityClass, id, mapSetter, mapGetter);
            return method.invoke(map, args);
        } catch (InvocationTargetException e) {
            throw e.getCause();
        }
    }
}
