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
package org.redisson.liveobject.provider;

import java.lang.annotation.Annotation;
import org.redisson.liveobject.resolver.Resolver;

/**
 *
 * @author Rui Gu (https://github.com/jackygurui)
 */
public interface ResolverProvider {

    Resolver getResolver(Class cls, Class<? extends Resolver> resolverClass, Annotation anno);
    void registerResolver(Class cls, Class<? extends Resolver> resolverClass, Resolver resolver);
    
}
