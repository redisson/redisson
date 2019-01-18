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
package org.redisson.api;

import java.util.concurrent.BlockingQueue;

/**
 * Distributed implementation of bounded {@link BlockingQueue}
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBoundedBlockingQueue<V> extends RBlockingQueue<V>, RBoundedBlockingQueueAsync<V> {

    /**
     * Sets queue capacity only if it is not set before.
     *
     * @param capacity - queue capacity
     * @return <code>true</code> if capacity set successfully
     *         <code>false</code> if capacity already set
     */
    boolean trySetCapacity(int capacity);
    
}
