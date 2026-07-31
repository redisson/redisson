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
public interface QueueMoveElementsAmount extends QueueMoveElementsArgs {

    /**
     * Defines maximum amount of elements to move.
     * If this queue holds fewer elements then all of them are moved.
     *
     * @param value maximum amount of elements
     * @return arguments object
     */
    QueueMoveElementsOrder count(int value);

    /**
     * Defines exact amount of elements to move.
     * If this queue holds fewer elements then no element is moved.
     *
     * @param value exact amount of elements
     * @return arguments object
     */
    QueueMoveElementsOrder exactly(int value);

}
