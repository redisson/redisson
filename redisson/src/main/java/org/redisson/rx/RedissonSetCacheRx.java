/**
 * Copyright 2018 Nikita Koksharov
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

import org.reactivestreams.Publisher;
import org.redisson.ScanIterator;
import org.redisson.api.RFuture;
import org.redisson.api.RSetCache;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

/**
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCacheRx<V> {

    private final RSetCache<V> instance;
    
    public RedissonSetCacheRx(RSetCache<V> instance) {
        this.instance = instance;
    }

    public Publisher<V> iterator() {
        return new SetRxIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((ScanIterator)instance).scanIteratorAsync(instance.getName(), client, nextIterPos, null, 10);
            }
        }.create();
    }

    public Publisher<Boolean> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {
            @Override
            public RFuture<Boolean> add(Object o) {
                return instance.addAsync((V)o);
            }
        }.addAll(c);
    }

}
