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
package org.redisson.api.queue;

/**
 * Arguments object for deque move method.
 * <p>
 * {@link org.redisson.api.RDeque#move(DequeMoveArgs)}
 * {@link org.redisson.api.RDequeAsync#moveAsync(DequeMoveArgs)}
 * {@link org.redisson.api.RDequeRx#move(DequeMoveArgs)}
 * {@link org.redisson.api.RDequeReactive#move(DequeMoveArgs)}
 *
 * @author Nikita Koksharov
 *
 */
public interface DequeMoveArgs {

    /**
     * Define to remove the tail element of this queue.
     *
     * @return arguments object
     */
    static DequeMoveDestination pollLast() {
        return new DequeMoveParams(DequeMoveParams.Direction.RIGHT);
    }

    /**
     * Define to remove the head element of this queue.
     *
     * @return arguments object
     */
    static DequeMoveDestination pollFirst() {
        return new DequeMoveParams(DequeMoveParams.Direction.LEFT);
    }

}
