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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSetCache;
import org.redisson.ScanIterator;
import org.redisson.api.RFuture;
import org.redisson.api.RSetCache;
import org.redisson.client.RedisClient;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

import io.netty.buffer.ByteBuf;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetCacheReactive<V> {

    private final RSetCache<V> instance;
    private final CommandReactiveExecutor commandExecutor;
    
    public RedissonSetCacheReactive(CommandReactiveExecutor commandExecutor, RSetCache<V> instance) {
        this.commandExecutor = commandExecutor;
        this.instance = instance;
    }

    public Publisher<V> iterator() {
        return Flux.create(new SetReactiveIterator<V>() {
            @Override
            protected RFuture<ListScanResult<Object>> scanIterator(RedisClient client, long nextIterPos) {
                return ((ScanIterator)instance).scanIteratorAsync(instance.getName(), client, nextIterPos, null, 10);
            }
        });
    }

    public Publisher<Integer> add(V value) {
        long timeoutDate = 92233720368547758L;
        return commandExecutor.evalWriteReactive(instance.getName(), instance.getCodec(), RedisCommands.EVAL_INTEGER,
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
                + "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then "
                    + "return 0;"
                + "end; " +
                "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                "return 1; ",
                Arrays.<Object>asList(instance.getName()), System.currentTimeMillis(), timeoutDate, ((RedissonSetCache)instance).encode(value));
    }

    public Publisher<Integer> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return Mono.just(0);
        }

        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(instance.getName());
        for (V value : c) {
            ByteBuf objectState = ((RedissonSetCache)instance).encode(value);
            params.add(score);
            params.add(objectState);
        }

        return commandExecutor.writeReactive(instance.getName(), instance.getCodec(), RedisCommands.ZADD_RAW, params.toArray());
    }

    public Publisher<Integer> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>() {
            @Override
            public Publisher<Integer> add(Object o) {
                return RedissonSetCacheReactive.this.add((V)o);
            }
        }.addAll(c);
    }

}
