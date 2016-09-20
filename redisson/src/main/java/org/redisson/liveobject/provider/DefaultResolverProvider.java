/**
 * Copyright 2016 Nikita Koksharov
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
package org.redisson.liveobject.provider;

import io.netty.util.internal.PlatformDependent;
import java.lang.annotation.Annotation;
import java.util.concurrent.ConcurrentMap;
import org.redisson.liveobject.resolver.Resolver;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public class DefaultResolverProvider implements ResolverProvider {
    
    public transient final ConcurrentMap<Class<? extends Resolver>, Resolver<?, ?, ?>> providerCache = PlatformDependent.newConcurrentHashMap();

    @Override
    public Resolver<?, ?, ?> getResolver(Class<?> cls, Class<? extends Resolver> resolverClass, Annotation anno) {
        if (!providerCache.containsKey(resolverClass)) {
            try {
                providerCache.putIfAbsent(resolverClass, resolverClass.newInstance());
            } catch (Exception ex) {
                throw new RuntimeException(ex);
            }
        }
        return providerCache.get(resolverClass);
    }

    @Override
    public void registerResolver(Class<?> cls, Class<? extends Resolver> resolverClass, Resolver resolver) {
        providerCache.putIfAbsent(resolverClass, resolver);
    }
    
}
