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
package org.redisson.api;

import reactor.core.publisher.Mono;

/**
 * RingBuffer based queue evicts elements from the head if queue capacity became full.
 * <p>
 * The head element removed if new element added and queue is full. 
 * <p>
 * Must be initialized with capacity size {@link #trySetCapacity(int)} before usage.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RRingBufferReactive<V> extends RQueueReactive<V> {

    /**
     * Sets queue capacity only if it is not set before.
     *
     * @param capacity - queue capacity
     * @return <code>true</code> if capacity set successfully
     *         <code>false</code> if capacity already set
     */
    Mono<Boolean> trySetCapacity(int capacity);

    /**
     * Sets capacity of the queue and overrides current value.
     * Trims queue if previous capacity value was greater than new.
     *
     * @param capacity - queue capacity
     */
    Mono<Void> setCapacity(int capacity);

    /**
     * Returns remaining capacity of this queue
     * 
     * @return remaining capacity
     */
    Mono<Integer> remainingCapacity();
    
    /**
     * Returns capacity of this queue
     * 
     * @return queue capacity
     */
    Mono<Integer> capacity();
    
}
