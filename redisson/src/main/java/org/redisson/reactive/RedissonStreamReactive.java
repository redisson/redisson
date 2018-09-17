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

import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonStream;
import org.redisson.api.PendingEntry;
import org.redisson.api.PendingResult;
import org.redisson.api.RFuture;
import org.redisson.api.RStream;
import org.redisson.api.RStreamAsync;
import org.redisson.api.RStreamReactive;
import org.redisson.api.StreamId;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandReactiveExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <K> type of key
 * @param <V> type of value
 */
public class RedissonStreamReactive<K, V> extends RedissonExpirableReactive implements RStreamReactive<K, V> {

    RStreamAsync<K, V> instance;
    
    public RedissonStreamReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name, new RedissonStream<K, V>(commandExecutor, name));
        this.instance = (RStream<K, V>) super.instance;
    }

    public RedissonStreamReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name, new RedissonStream<K, V>(codec, commandExecutor, name));
        this.instance = (RStream<K, V>) super.instance;
    }
    
    @Override
    public Publisher<Void> createGroup(final String groupName) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.createGroupAsync(groupName);
            }
        });
    }

    @Override
    public Publisher<Void> createGroup(final String groupName, final StreamId id) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.createGroupAsync(groupName, id);
            }
        });
    }

    @Override
    public Publisher<Long> ack(final String groupName, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.ackAsync(groupName, ids);
            }
        });
    }

    @Override
    public Publisher<PendingResult> listPending(final String groupName) {
        return reactive(new Supplier<RFuture<PendingResult>>() {
            @Override
            public RFuture<PendingResult> get() {
                return instance.listPendingAsync(groupName);
            }
        });
    }

    @Override
    public Publisher<List<PendingEntry>> listPending(final String groupName, final StreamId startId, final StreamId endId, final int count) {
        return reactive(new Supplier<RFuture<List<PendingEntry>>>() {
            @Override
            public RFuture<List<PendingEntry>> get() {
                return instance.listPendingAsync(groupName, startId, endId, count);
            }
        });
    }

    @Override
    public Publisher<List<PendingEntry>> listPending(final String groupName, final StreamId startId, final StreamId endId, final int count,
            final String consumerName) {
        return reactive(new Supplier<RFuture<List<PendingEntry>>>() {
            @Override
            public RFuture<List<PendingEntry>> get() {
                return instance.listPendingAsync(groupName, startId, endId, count, consumerName);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> claim(final String groupName, final String consumerName, final long idleTime,
            final TimeUnit idleTimeUnit, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.claimAsync(groupName, consumerName, idleTime, idleTimeUnit, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> readGroup(final String groupName, final String consumerName, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readGroupAsync(groupName, consumerName, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> readGroup(final String groupName, final String consumerName, final int count,
            final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readGroupAsync(groupName, consumerName, count, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> readGroup(final String groupName, final String consumerName, final long timeout,
            final TimeUnit unit, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readGroupAsync(groupName, consumerName, timeout, unit, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> readGroup(final String groupName, final String consumerName, final int count, final long timeout,
            final TimeUnit unit, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readGroupAsync(groupName, consumerName, timeout, unit, ids);
            }
        });
    }

    @Override
    public Publisher<Long> size() {
        return reactive(new Supplier<RFuture<Long>>() {
            @Override
            public RFuture<Long> get() {
                return instance.sizeAsync();
            }
        });
    }

    @Override
    public Publisher<StreamId> add(final K key, final V value) {
        return reactive(new Supplier<RFuture<StreamId>>() {
            @Override
            public RFuture<StreamId> get() {
                return instance.addAsync(key, value);
            }
        });
    }

    @Override
    public Publisher<Void> add(final StreamId id, final K key, final V value) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addAsync(id, key, value);
            }
        });
    }

    @Override
    public Publisher<StreamId> add(final K key, final V value, final int trimLen, final boolean trimStrict) {
        return reactive(new Supplier<RFuture<StreamId>>() {
            @Override
            public RFuture<StreamId> get() {
                return instance.addAsync(key, value, trimLen, trimStrict);
            }
        });
    }

    @Override
    public Publisher<Void> add(final StreamId id, final K key, final V value, final int trimLen, final boolean trimStrict) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addAsync(id, key, value, trimLen, trimStrict);
            }
        });
    }

    @Override
    public Publisher<StreamId> addAll(final Map<K, V> entries) {
        return reactive(new Supplier<RFuture<StreamId>>() {
            @Override
            public RFuture<StreamId> get() {
                return instance.addAllAsync(entries);
            }
        });
    }

    @Override
    public Publisher<Void> addAll(final StreamId id, final Map<K, V> entries) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addAllAsync(id, entries);
            }
        });
    }

    @Override
    public Publisher<StreamId> addAll(final Map<K, V> entries, final int trimLen, final boolean trimStrict) {
        return reactive(new Supplier<RFuture<StreamId>>() {
            @Override
            public RFuture<StreamId> get() {
                return instance.addAllAsync(entries, trimLen, trimStrict);
            }
        });
    }

    @Override
    public Publisher<Void> addAll(final StreamId id, final Map<K, V> entries, final int trimLen, final boolean trimStrict) {
        return reactive(new Supplier<RFuture<Void>>() {
            @Override
            public RFuture<Void> get() {
                return instance.addAllAsync(id, entries, trimLen, trimStrict);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> read(final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readAsync(ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> read(final int count, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readAsync(count, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> read(final long timeout, final TimeUnit unit, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readAsync(timeout, unit, ids);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> read(final int count, final long timeout, final TimeUnit unit, final StreamId... ids) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.readAsync(count, timeout, unit, ids);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final StreamId id, final String name2, final StreamId id2) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(id, name2, id2);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final StreamId id, final String name2, final StreamId id2, final String name3,
            final StreamId id3) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(id, name2, id2, name3, id3);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final StreamId id, final Map<String, StreamId> nameToId) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(id, nameToId);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final StreamId id, final String name2, final  StreamId id2) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, id, name2, id2);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final StreamId id, final String name2, final StreamId id2,
            final String name3, final StreamId id3) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, id, name2, id2, name3, id3);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final StreamId id,
            final Map<String, StreamId> nameToId) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, id, nameToId);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final long timeout, final TimeUnit unit, final StreamId id, final String name2,
            final StreamId id2) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(timeout, unit, id, name2, id2);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final long timeout, final TimeUnit unit, final StreamId id, final String name2,
            final StreamId id2, final String name3, final StreamId id3) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(timeout, unit, id, name2, id2, name3, id3);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final long timeout, final TimeUnit unit, final StreamId id,
            final Map<String, StreamId> nameToId) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(timeout, unit, id, nameToId);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final long timeout, final TimeUnit unit, final StreamId id,
            final String name2, final StreamId id2) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, timeout, unit, id, name2, id2);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final long timeout, final TimeUnit unit, final StreamId id,
            final String name2, final StreamId id2, final String name3, final StreamId id3) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, timeout, unit, id, name2, id2, name3, id3);
            }
        });
    }

    @Override
    public Publisher<Map<String, Map<StreamId, Map<K, V>>>> read(final int count, final long timeout, final TimeUnit unit, final StreamId id,
            final Map<String, StreamId> nameToId) {
        return reactive(new Supplier<RFuture<Map<String, Map<StreamId, Map<K, V>>>>>() {
            @Override
            public RFuture<Map<String, Map<StreamId, Map<K, V>>>> get() {
                return instance.readAsync(count, timeout, unit, id, nameToId);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> range(final StreamId startId, final StreamId endId) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.rangeAsync(startId, endId);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> range(final int count, final StreamId startId, final StreamId endId) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.rangeAsync(count, startId, endId);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> rangeReversed(final StreamId startId, final StreamId endId) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.rangeReversedAsync(startId, endId);
            }
        });
    }

    @Override
    public Publisher<Map<StreamId, Map<K, V>>> rangeReversed(final int count, final StreamId startId, final StreamId endId) {
        return reactive(new Supplier<RFuture<Map<StreamId, Map<K, V>>>>() {
            @Override
            public RFuture<Map<StreamId, Map<K, V>>> get() {
                return instance.rangeReversedAsync(startId, endId);
            }
        });
    }

}
