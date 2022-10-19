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

import java.util.List;
import java.util.Queue;

/**
 * {@link java.util.Queue} backed by Redis
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RQueue<V> extends Queue<V>, RExpirable, RQueueAsync<V> {

    /**
     * Retrieves and removes last available tail element of this queue queue and adds it at the head of <code>queueName</code>.
     *
     * @param queueName - names of destination queue
     * @return the tail of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    V pollLastAndOfferFirstTo(String queueName);

    /**
     * Returns all queue elements at once
     * 
     * @return elements
     */
    List<V> readAll();

    /**
     * Retrieves and removes the head elements of this queue.
     * Elements amount limited by <code>limit</code> param.
     *
     * @return list of head elements
     */
    List<V> poll(int limit);

    /**
     * Adds object event listener
     *
     * @see org.redisson.api.ExpiredObjectListener
     * @see org.redisson.api.DeletedObjectListener
     * @see org.redisson.api.listener.ListAddListener
     * @see org.redisson.api.listener.ListInsertListener
     * @see org.redisson.api.listener.ListSetListener
     * @see org.redisson.api.listener.ListRemoveListener
     * @see org.redisson.api.listener.ListTrimListener
     *
     * @param listener - object event listener
     * @return listener id
     */
    int addListener(ObjectListener listener);

}
