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

import java.util.concurrent.TimeUnit;

/**
 * Distributed implementation of delayed queue.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RDelayedQueue<V> extends RQueue<V>, RDestroyable {

    /**
     * Inserts element into this queue with 
     * specified transfer delay to destination queue.
     * 
     * @param e the element to add
     * @param delay for transition
     * @param timeUnit for delay
     */
    void offer(V e, long delay, TimeUnit timeUnit);
    
    /**
     * Inserts element into this queue with 
     * specified transfer delay to destination queue.
     * 
     * @param e the element to add
     * @param delay for transition
     * @param timeUnit for delay
     * @return void
     */
    RFuture<Void> offerAsync(V e, long delay, TimeUnit timeUnit);
    
}
