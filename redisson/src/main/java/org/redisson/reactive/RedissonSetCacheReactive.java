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
package org.redisson.reactive;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.EvictionScheduler;
import org.redisson.RedissonSetCache;
import org.redisson.api.RSetCacheReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

/**
 * <p>Set-based cache with ability to set TTL for each entry via
 * {@link #put(Object, Object, long, TimeUnit)} method.
 * And therefore has an complex lua-scripts inside.
 * Uses map(value_hash, value) to tie with sorted set which contains expiration record for every value with TTL.
 * </p>
 *
 * <p>Current Redis implementation doesn't have set entry eviction functionality.
 * Thus values are checked for TTL expiration during any value read operation.
 * If entry expired then it doesn't returns and clean task runs hronous.
 * Clean task deletes removes 100 expired entries at once.
 * In addition there is {@link org.redisson.EvictionScheduler}. This scheduler
 * deletes expired entries in time interval between 5 seconds to 2 hours.</p>
 *
 * <p>If eviction is not required then it's better to use {@link org.redisson.reactive.RedissonSet}.</p>
 *
 * @author Nikita Koksharov
 *
 * @param <K> key
 * @param <V> value
 */
public class RedissonSetCacheReactive<V> extends RedissonExpirableReactive implements RSetCacheReactive<V> {

    private final RedissonSetCache<V> instance;
    
    public RedissonSetCacheReactive(EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonSetCache<V>(evictionScheduler, commandExecutor, name);
    }

    public RedissonSetCacheReactive(Codec codec, EvictionScheduler evictionScheduler, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonSetCache<V>(codec, evictionScheduler, commandExecutor, name);
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.ZCARD, getName());
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return reactive(instance.containsAsync(o));
    }

    Publisher<ListScanResult<V>> scanIterator(InetSocketAddress client, long startPos) {
        return reactive(instance.scanIteratorAsync(client, startPos));
    }

    @Override
    public Publisher<V> iterator() {
        return new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long nextIterPos) {
                return RedissonSetCacheReactive.this.scanIterator(client, nextIterPos);
            }
        };
    }

    @Override
    public Publisher<Boolean> add(V value, long ttl, TimeUnit unit) {
        return reactive(instance.addAsync(value, ttl, unit));
    }

    private byte[] encode(V value) throws IOException {
        return codec.getValueEncoder().encode(value);
    }

    @Override
    public Publisher<Long> add(V value) {
        try {
            byte[] objectState = encode(value);

            long timeoutDate = 92233720368547758L;
            return commandExecutor.evalWriteReactive(getName(), codec, RedisCommands.EVAL_LONG,
                    "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
                    + "if expireDateScore ~= false and tonumber(expireDateScore) > tonumber(ARGV[1]) then "
                        + "return 0;"
                    + "end; " +
                    "redis.call('zadd', KEYS[1], ARGV[2], ARGV[3]); " +
                    "return 1; ",
                    Arrays.<Object>asList(getName()), System.currentTimeMillis(), timeoutDate, objectState);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        return reactive(instance.removeAsync(o));
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return reactive(instance.containsAllAsync(c));
    }

    @Override
    public Publisher<Long> addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceeded(0L);
        }

        long score = 92233720368547758L - System.currentTimeMillis();
        List<Object> params = new ArrayList<Object>(c.size()*2 + 1);
        params.add(getName());
        try {
            for (V value : c) {
                byte[] objectState = encode(value);
                params.add(score);
                params.add(objectState);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return commandExecutor.writeReactive(getName(), codec, RedisCommands.ZADD_RAW, params.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return reactive(instance.retainAllAsync(c));
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return reactive(instance.removeAllAsync(c));
    }

    @Override
    public Publisher<Long> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

}
