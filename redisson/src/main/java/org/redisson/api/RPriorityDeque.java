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

import java.util.Deque;
import java.util.stream.Stream;

/**
 * Redis based priority deque.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RPriorityDeque<V> extends Deque<V>, RPriorityQueue<V> {

    /**
     * Returns stream of elements contained in this deque in reverse order
     *
     * @return stream of elements in reverse order
     */
    Stream<V> descendingStream();
    
}
