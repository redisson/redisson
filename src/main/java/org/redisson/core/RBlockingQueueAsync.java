/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
package org.redisson.core;

import java.util.Collection;
import java.util.concurrent.*;

import io.netty.util.concurrent.Future;

/**
 * {@link BlockingQueue} backed by Redis
 *
 * @author Nikita Koksharov
 * @param <V> the type of elements held in this collection
 */
public interface RBlockingQueueAsync<V> extends RQueueAsync<V>, RExpirableAsync {

    Future<Integer> drainToAsync(Collection<? super V> c, int maxElements);

    Future<Integer> drainToAsync(Collection<? super V> c);

    Future<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit);

    Future<V> pollAsync(long timeout, TimeUnit unit);

    Future<V> takeAsync();

    Future<Boolean> putAsync(V e);

    Future<Boolean> offerAsync(V e);

}
