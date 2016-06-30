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
package org.redisson.core;

import io.netty.util.concurrent.Future;

/**
 * {@link java.util.Deque} backed by Redis
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeAsync<V> extends RQueueAsync<V> {

    Future<Boolean> removeLastOccurrenceAsync(Object o);

    Future<V> removeLastAsync();

    Future<V> removeFirstAsync();

    Future<Boolean> removeFirstOccurrenceAsync(Object o);

    Future<Void> pushAsync(V e);

    Future<V> popAsync();

    Future<V> pollLastAsync();

    Future<V> pollFirstAsync();

    Future<V> peekLastAsync();

    Future<V> peekFirstAsync();

    Future<Boolean> offerLastAsync(V e);

    Future<V> getLastAsync();

    Future<Void> addLastAsync(V e);

    Future<Void> addFirstAsync(V e);

    Future<Boolean> offerFirstAsync(V e);

}
