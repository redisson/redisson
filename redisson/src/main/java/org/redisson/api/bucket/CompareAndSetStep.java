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
package org.redisson.api.bucket;

/**
 * Intermediate builder interface for compare-and-set operations.
 * Returned by condition factory methods and requires {@link #set(Object)} to be called.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface CompareAndSetStep<V> {

    /**
     * Sets the new value to be stored if the condition is met.
     * This method is required.
     *
     * @param value new value to set
     * @return CompareAndSetArgs for optional configuration (timeToLive, expireAt)
     */
    CompareAndSetArgs<V> set(V value);

}