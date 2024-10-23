/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson.liveobject.resolver;

import org.redisson.RedissonMap;
import org.redisson.api.RMap;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.liveobject.core.LiveObjectInterceptor;

/**
 *
 * @author Nikita Koksharov
 *
 */
public final class MapResolver {

    public MapResolver() {
    }

    public void remove(Class<?> entityClass) {
    }

    public void remove(Class<?> entityClass, Object id) {
    }

    public void destroy(Class<?> entityClass, Object id) {
    }

    public RMap resolve(CommandAsyncExecutor commandExecutor, Class<?> entityClass, Object id,
                        LiveObjectInterceptor.Setter mapSetter, LiveObjectInterceptor.Getter mapGetter) {
        if (mapGetter.getValue() != null) {
            return (RMap) mapGetter.getValue();
        }

        NamingScheme namingScheme = commandExecutor.getObjectBuilder().getNamingScheme(entityClass);
        String idKey = namingScheme.getName(entityClass, id);

        RMap<Object, Object> lm = new RedissonMap<>(namingScheme.getCodec(), commandExecutor,
                    idKey, null, null, null);

        mapSetter.setValue(lm);
        return lm;
    }

}
