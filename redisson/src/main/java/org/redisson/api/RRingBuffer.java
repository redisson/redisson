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
public interface RRingBuffer<V> extends RQueue<V>, RRingBufferAsync<V> {

    /**
     * Sets capacity of the queue only if it wasn't set before.
     *
     * @param capacity - queue capacity
     * @return <code>true</code> if capacity set successfully
     *         <code>false</code> if capacity already set
     */
    boolean trySetCapacity(int capacity);

    /**
     * Sets capacity of the queue and overrides current value.
     * Trims queue if previous capacity value was greater than new.
     *
     * @param capacity - queue capacity
     */
    void setCapacity(int capacity);

    /**
     * Returns remaining capacity of this queue
     * 
     * @return remaining capacity
     */
    int remainingCapacity();   

    /**
     * Returns capacity of this queue
     * 
     * @return queue capacity
     */
    int capacity();
}
