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

import java.lang.annotation.Annotation;
import org.redisson.liveobject.resolver.Resolver;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface ResolverProvider {

    /**
     * To retrieve a resolver based on the the class requiring values to be 
     * resolved, the resolver type, and annotation which may carry any required
     * configurations.
     * 
     * @param cls the class requires value to be resolved
     * @param resolverClass the resolver type
     * @param anno annotation with configurations
     * @return a Resolver instance
     */
    Resolver<?, ?, ?> getResolver(Class<?> cls, Class<? extends Resolver> resolverClass, Annotation anno);

    /**
     * To register a resolver based on the the class it can provide value to,
     * the resolver type, the resolver instance to be cached.
     * 
     * @param cls
     * @param resolverClass
     * @param resolver
     */
    void registerResolver(Class<?> cls, Class<? extends Resolver> resolverClass, Resolver resolver);
    
}
