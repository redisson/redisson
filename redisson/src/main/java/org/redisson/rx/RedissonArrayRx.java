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
package org.redisson.rx;

import io.reactivex.rxjava3.processors.ReplayProcessor;
import org.reactivestreams.Publisher;
import org.redisson.api.RArray;
import org.redisson.api.array.ArrayEntry;

/**
 * RxJava3 facade backing the streaming methods of {@link org.redisson.api.RArrayRx}.
 * <p>
 * Only the methods declared here override the generic async-to-rx proxy; the rest of
 * the interface is served by mapping to the corresponding {@code *Async} methods.
 *
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonArrayRx<V> {

    private final RArray<V> instance;

    public RedissonArrayRx(RArray<V> instance) {
        this.instance = instance;
    }

    public Publisher<ArrayEntry<V>> iterator() {
        return iterator(10);
    }

    public Publisher<ArrayEntry<V>> iterator(int count) {
        ReplayProcessor<ArrayEntry<V>> p = ReplayProcessor.create();
        return p.doOnRequest(new ArrayEntryRxIteratorConsumer<>(p, instance, count));
    }

}
