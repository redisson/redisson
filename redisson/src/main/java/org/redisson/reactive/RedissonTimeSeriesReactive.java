/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.RedissonObject;
import org.redisson.RedissonTimeSeries;
import org.redisson.ScanResult;
import org.redisson.api.RFuture;
import org.redisson.api.RTimeSeries;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.client.RedisClient;
import reactor.core.publisher.Flux;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonTimeSeriesReactive<V> {

    private final RTimeSeries<V> instance;
    private final RedissonReactiveClient redisson;

    public RedissonTimeSeriesReactive(RTimeSeries<V> instance, RedissonReactiveClient redisson) {
        this.instance = instance;
        this.redisson = redisson;
    }

    public Publisher<V> iterator() {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((RedissonTimeSeries) instance).scanIteratorAsync(((RedissonObject) instance).getRawName(), client, nextIterPos, 10);
            }
        });
    }

}
