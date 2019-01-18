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
package org.redisson.rx;

import org.reactivestreams.Publisher;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RLexSortedSet;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.reactivex.Flowable;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLexSortedSetRx {

    private final RLexSortedSet instance;
    
    public RedissonLexSortedSetRx(RLexSortedSet instance) {
        this.instance = instance;
    }

    public Flowable<Boolean> addAll(Publisher<? extends String> c) {
        return new PublisherAdder<String>() {
            @Override
            public RFuture<Boolean> add(Object e) {
                return instance.addAsync((String)e);
            }
        }.addAll(c);
    }
    
    private Flowable<String> scanIteratorReactive(final String pattern, final int count) {
        return new SetRxIterator<String>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(final RedisClient client, final long nextIterPos) {
                return ((RedissonScoredSortedSet<String>)instance).scanIteratorAsync(client, nextIterPos, pattern, count);
            }
        }.create();
    }

    public Flowable<String> iterator() {
        return scanIteratorReactive(null, 10);
    }

    public Flowable<String> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    public Flowable<String> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    public Flowable<String> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

}
