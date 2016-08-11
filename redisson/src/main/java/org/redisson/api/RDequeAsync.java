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

/**
 * {@link java.util.Deque} backed by Redis
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public interface RDequeAsync<V> extends RQueueAsync<V> {

    RFuture<Boolean> removeLastOccurrenceAsync(Object o);

    RFuture<V> removeLastAsync();

    RFuture<V> removeFirstAsync();

    RFuture<Boolean> removeFirstOccurrenceAsync(Object o);

    RFuture<Void> pushAsync(V e);

    RFuture<V> popAsync();

    RFuture<V> pollLastAsync();

    RFuture<V> pollFirstAsync();

    RFuture<V> peekLastAsync();

    RFuture<V> peekFirstAsync();

    RFuture<Boolean> offerLastAsync(V e);

    RFuture<V> getLastAsync();

    RFuture<Void> addLastAsync(V e);

    RFuture<Void> addFirstAsync(V e);

    RFuture<Boolean> offerFirstAsync(V e);

}
