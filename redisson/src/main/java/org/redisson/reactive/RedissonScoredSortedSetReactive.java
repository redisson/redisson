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

import java.util.concurrent.Callable;

import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.ListScanResult;

import reactor.core.publisher.Flux;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonScoredSortedSetReactive<V>  {

    private final RScoredSortedSetAsync<V> instance;
    
    public RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        this(commandExecutor, name, new RedissonScoredSortedSet<V>(commandExecutor, name, null));
    }

    private RedissonScoredSortedSetReactive(CommandReactiveExecutor commandExecutor, String name, RScoredSortedSetAsync<V> instance) {
        this.instance = instance;
    }
    
    public RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        this(codec, commandExecutor, name, new RedissonScoredSortedSet<V>(codec, commandExecutor, name, null));
    }

    private RedissonScoredSortedSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name, RScoredSortedSetAsync<V> instance) {
        this.instance = instance;
    }
    
    public Flux<V> takeFirstElements() {
        return ElementsStream.takeElements(new Callable<RFuture<V>>() {
            @Override
            public RFuture<V> call() throws Exception {
                return instance.takeFirstAsync();
            }
        });
    }
    
    public Flux<V> takeLastElements() {
        return ElementsStream.takeElements(new Callable<RFuture<V>>() {
            @Override
            public RFuture<V> call() throws Exception {
                return instance.takeLastAsync();
            }
        });
    }

    private Flux<V> scanIteratorReactive(String pattern, int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((RedissonScoredSortedSet<V>) instance).scanIteratorAsync(client, nextIterPos, pattern, count);
            }
        });
    }

    public String getName() {
        return ((RedissonScoredSortedSet<V>) instance).getName();
    }
    
    public Flux<V> iterator() {
        return scanIteratorReactive(null, 10);
    }

    public Flux<V> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    public Flux<V> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    public Flux<V> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

}
