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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RLexSortedSet;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import reactor.core.publisher.Flux;


/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLexSortedSetReactive {

    private final RLexSortedSet instance;
    
    public RedissonLexSortedSetReactive(RLexSortedSet instance) {
        this.instance = instance;
    }

    public Publisher<Boolean> addAll(Publisher<? extends String> c) {
        return new PublisherAdder<String>() {
            @Override
            public RFuture<Boolean> add(Object e) {
                return instance.addAsync((String)e);
            }
        }.addAll(c);
    }

    private Publisher<String> scanIteratorReactive(final String pattern, final int count) {
        return Flux.create(new SetReactiveIterator<String>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(final RedisClient client, final long nextIterPos) {
                return ((RedissonScoredSortedSet<String>)instance).scanIteratorAsync(client, nextIterPos, pattern, count);
            }
        });
    }

    public Publisher<String> iterator() {
        return scanIteratorReactive(null, 10);
    }

    public Publisher<String> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    public Publisher<String> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    public Publisher<String> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

}
