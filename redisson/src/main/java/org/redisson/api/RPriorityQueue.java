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

import java.util.Comparator;
import java.util.List;
import java.util.Queue;

/**
 * 
 * Redis based priority queue.
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public interface RPriorityQueue<V> extends Queue<V>, RObject {

    /**
     * Returns comparator used by this queue
     * 
     * @return comparator object
     */
    Comparator<? super V> comparator();
    
    /**
     * Returns all queue elements at once
     * 
     * @return elements
     */
    List<V> readAll();
    
    /**
     * Retrieves and removes last available tail element of this queue queue and adds it at the head of <code>queueName</code>.
     *
     * @param queueName - names of destination queue
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    V pollLastAndOfferFirstTo(String queueName);
    
    /**
     * Sets new comparator only if current set is empty
     *
     * @param comparator for values
     * @return <code>true</code> if new comparator setted
     *         <code>false</code> otherwise
     */
    boolean trySetComparator(Comparator<? super V> comparator);

}
