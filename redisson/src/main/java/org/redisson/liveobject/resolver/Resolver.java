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
package org.redisson.liveobject.resolver;

import java.lang.annotation.Annotation;

import org.redisson.api.RedissonClient;

/**
 * A resolver is used to provide value based on contextual parameters 
 *
 * @author Rui Gu (https://github.com/jackygurui)
 * @param <T> Field instance type
 * @param <A> Annotation to resolve
 * @param <V> Value type
 */
public interface Resolver<T, A extends Annotation, V> {

    /**
     * Used to provide a value instance based on contextual parameters.
     * 
     * Actual behavior may vary depending on implementation
     * 
     * @param value object
     * @param annotation object
     * @param idFieldName name of field
     * @param redisson instance
     * @return resolved value
     */
    public V resolve(T value, A annotation, String idFieldName, RedissonClient redisson);

}
