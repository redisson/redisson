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
package org.redisson.api.queue;

/**
 * Arguments object for queue move method.
 *
 * @author Nikita Koksharov
 *
 */
public interface QueueMoveElementsOrder extends QueueMoveElementsArgs {

    /**
     * Defines to add all elements at once preserving their relative order.
     * <p>
     * Applied by default.
     *
     * @return arguments object
     */
    QueueMoveElementsArgs bulk();

    /**
     * Defines to add elements one by one. Each element is added
     * before the next one is removed from this queue.
     *
     * @return arguments object
     */
    QueueMoveElementsArgs oneByOne();

}
