/**
 * Copyright 2016 Nikita Koksharov
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

import java.util.Collection;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;

/**
 * {@link BlockingQueue} backed by Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingQueueReactive<V> extends RQueueReactive<V> {

    /**
     * Retrieves and removes first available head element of <b>any</b> queue,
     * waiting up to the specified wait time if necessary for an element to become available
     * in any of defined queues <b>including</b> queue own.
     *
     * @param queueNames - names of queue
     * @param timeout how long to wait before giving up, in units of
     *        {@code unit}
     * @param unit a {@code TimeUnit} determining how to interpret the
     *        {@code timeout} parameter
     * @return Publisher object with the head of this queue, or {@code null} if the
     *         specified waiting time elapses before an element is available
     */
    Publisher<V> pollFromAny(long timeout, TimeUnit unit, String ... queueNames);

    Publisher<Integer> drainTo(Collection<? super V> c, int maxElements);

    Publisher<Integer> drainTo(Collection<? super V> c);

    Publisher<V> pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit);

    Publisher<V> poll(long timeout, TimeUnit unit);

    Publisher<V> take();

    Publisher<Integer> put(V e);

}
