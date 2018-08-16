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

import java.util.Set;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSetMultimap;
import org.redisson.api.RFuture;
import org.redisson.api.RSet;
import org.redisson.api.RSetMultimap;
import org.redisson.api.RSetMultimapReactive;
import org.redisson.api.RSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> key type
 * @param <V> value type
 */
public class RedissonSetMultimapReactive<K, V> extends RedissonBaseMultimapReactive<K, V> implements RSetMultimapReactive<K, V> {

    public RedissonSetMultimapReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonSetMultimap<K, V>(commandExecutor, name), commandExecutor, name);
    }

    public RedissonSetMultimapReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(new RedissonSetMultimap<K, V>(codec, commandExecutor, name), codec, commandExecutor, name);
    }

    @Override
    public RSetReactive<V> get(K key) {
        RSet<V> set = ((RSetMultimap<K, V>)instance).get(key);
        return new RedissonSetReactive<V>(codec, commandExecutor, set.getName(), set);
    }

    @Override
    public Publisher<Set<V>> getAll(final K key) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return (RFuture<Set<V>>)(Object)((RSetMultimap<K, V>)instance).getAllAsync(key);
            }
        });
    }

    @Override
    public Publisher<Set<V>> removeAll(final Object key) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return (RFuture<Set<V>>)(Object)((RSetMultimap<K, V>)instance).removeAllAsync(key);
            }
        });
    }

    @Override
    public Publisher<Set<V>> replaceValues(final K key, final Iterable<? extends V> values) {
        return reactive(new Supplier<RFuture<Set<V>>>() {
            @Override
            public RFuture<Set<V>> get() {
                return (RFuture<Set<V>>)(Object)((RSetMultimap<K, V>)instance).replaceValuesAsync(key, values);
            }
        });
    }

}
