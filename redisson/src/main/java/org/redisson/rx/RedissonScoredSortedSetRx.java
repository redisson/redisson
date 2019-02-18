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

import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RObject;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.decoder.ListScanResult;

import io.reactivex.Flowable;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonScoredSortedSetRx<V>  {

    private final RScoredSortedSetAsync<V> instance;
    
    public RedissonScoredSortedSetRx(RScoredSortedSetAsync<V> instance) {
        this.instance = instance;
    }
    
    private Flowable<V> scanIteratorReactive(String pattern, int count) {
        return new SetRxIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((RedissonScoredSortedSet<V>) instance).scanIteratorAsync(client, nextIterPos, pattern, count);
            }
        }.create();
    }

    public Flowable<V> takeFirstElements() {
        return ElementsStream.takeElements(() -> {
            return instance.takeFirstAsync();
        });
    }
    
    public Flowable<V> takeLastElements() {
        return ElementsStream.takeElements(() -> {
            return instance.takeLastAsync();
        });
    }
    
    public String getName() {
        return ((RObject) instance).getName();
    }
    
    public Flowable<V> iterator() {
        return scanIteratorReactive(null, 10);
    }

    public Flowable<V> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    public Flowable<V> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    public Flowable<V> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

}
