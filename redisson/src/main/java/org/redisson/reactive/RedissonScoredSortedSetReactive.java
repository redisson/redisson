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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.RedissonScoredSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RScoredSortedSetAsync;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

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

    private Publisher<V> scanIteratorReactive(final String pattern, final int count) {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(final RedisClient client, final long nextIterPos) {
                return ((RedissonScoredSortedSet<V>)instance).scanIteratorAsync(client, nextIterPos, pattern, count);
            }
        });
    }

    public String getName() {
        return ((RedissonScoredSortedSet)instance).getName();
    }
    
    public Publisher<V> iterator() {
        return scanIteratorReactive(null, 10);
    }

    public Publisher<V> iterator(String pattern) {
        return scanIteratorReactive(pattern, 10);
    }

    public Publisher<V> iterator(int count) {
        return scanIteratorReactive(null, count);
    }

    public Publisher<V> iterator(String pattern, int count) {
        return scanIteratorReactive(pattern, count);
    }

            }
