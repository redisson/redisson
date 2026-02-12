/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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
package org.redisson;

import org.redisson.api.CuckooFilterInfo;
import org.redisson.api.RCuckooFilter;
import org.redisson.api.RFuture;
import org.redisson.api.cuckoofilter.CuckooFilterAddArgs;
import org.redisson.api.cuckoofilter.CuckooFilterAddArgsImpl;
import org.redisson.api.cuckoofilter.CuckooFilterInitArgs;
import org.redisson.api.cuckoofilter.CuckooFilterInitArgsImpl;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ContainsSetDecoder;
import org.redisson.command.CommandAsyncExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/**
 * Distributed implementation of cuckoo filter
 * based on Redis Bloom module {@code CF.*} commands.
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCuckooFilter<V> extends RedissonExpirable implements RCuckooFilter<V> {

    private final Codec codec;

    public RedissonCuckooFilter(Codec codec,
                                 CommandAsyncExecutor commandExecutor,
                                 String name) {
        super(commandExecutor, name);
        this.codec = codec;
    }

    // ─── CF.RESERVE ──────────────────────────────────────────────

    @Override
    public void init(long capacity) {
        get(initAsync(capacity));
    }

    @Override
    public RFuture<Void> initAsync(long capacity) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CF_RESERVE,
                getRawName(), capacity);
    }

    @Override
    public void init(CuckooFilterInitArgs args) {
        get(initAsync(args));
    }

    @Override
    public RFuture<Void> initAsync(CuckooFilterInitArgs args) {
        CuckooFilterInitArgsImpl a = (CuckooFilterInitArgsImpl) args;

        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.add(a.getCapacity());

        if (a.getBucketSize() != null) {
            params.add("BUCKETSIZE");
            params.add(a.getBucketSize());
        }
        if (a.getMaxIterations() != null) {
            params.add("MAXITERATIONS");
            params.add(a.getMaxIterations());
        }
        if (a.getExpansion() != null) {
            params.add("EXPANSION");
            params.add(a.getExpansion());
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CF_RESERVE,
                params.toArray());
    }

    // ─── CF.ADD ──────────────────────────────────────────────────

    @Override
    public boolean add(V element) {
        return get(addAsync(element));
    }

    @Override
    public RFuture<Boolean> addAsync(V element) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CF_ADD,
                getRawName(), encode(element));
    }

    // ─── CF.INSERT ───────────────────────────────────────────────

    @Override
    public Set<V> add(CuckooFilterAddArgs<V> args) {
        return get(addAsync(args));
    }

    @Override
    public RFuture<Set<V>> addAsync(CuckooFilterAddArgs<V> args) {
        CuckooFilterAddArgsImpl<V> a = (CuckooFilterAddArgsImpl<V>) args;
        List<V> itemList = new ArrayList<>(a.getItems());

        List<Object> params = buildInsertParams(a, itemList);

        return commandExecutor.writeAsync(
                getRawName(), codec,
                new RedisCommand<>("CF.INSERT",
                        new ContainsSetDecoder<>(itemList)),
                params.toArray());
    }

    // ─── CF.ADDNX ───────────────────────────────────────────────

    @Override
    public boolean addIfAbsent(V element) {
        return get(addIfAbsentAsync(element));
    }

    @Override
    public RFuture<Boolean> addIfAbsentAsync(V element) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CF_ADDNX,
                getRawName(), encode(element));
    }

    // ─── CF.INSERTNX ────────────────────────────────────────────

    @Override
    public Set<V> addIfAbsent(CuckooFilterAddArgs<V> args) {
        return get(addIfAbsentAsync(args));
    }

    @Override
    public RFuture<Set<V>> addIfAbsentAsync(CuckooFilterAddArgs<V> args) {
        CuckooFilterAddArgsImpl<V> a = (CuckooFilterAddArgsImpl<V>) args;
        List<V> itemList = new ArrayList<>(a.getItems());

        List<Object> params = buildInsertParams(a, itemList);

        return commandExecutor.writeAsync(
                getRawName(), codec,
                new RedisCommand<>("CF.INSERTNX",
                        new ContainsSetDecoder<>(itemList)),
                params.toArray());
    }

    // ─── CF.EXISTS ───────────────────────────────────────────────

    @Override
    public boolean exists(V element) {
        return get(existsAsync(element));
    }

    @Override
    public RFuture<Boolean> existsAsync(V element) {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.CF_EXISTS,
                getRawName(), encode(element));
    }

    // ─── CF.MEXISTS ──────────────────────────────────────────────

    @Override
    public Set<V> exists(Collection<V> elements) {
        return get(existsAsync(elements));
    }

    @Override
    public RFuture<Set<V>> existsAsync(Collection<V> elements) {
        List<V> elementList = new ArrayList<>(elements);

        List<Object> params = new ArrayList<>(elementList.size() + 1);
        params.add(getRawName());
        for (V element : elementList) {
            params.add(encode(element));
        }

        return commandExecutor.readAsync(
                getRawName(), codec,
                new RedisCommand<>("CF.MEXISTS",
                        new ContainsSetDecoder<>(elementList)),
                params.toArray());
    }

    // ─── CF.DEL ──────────────────────────────────────────────────

    @Override
    public boolean remove(V element) {
        return get(removeAsync(element));
    }

    @Override
    public RFuture<Boolean> removeAsync(V element) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CF_DEL,
                getRawName(), encode(element));
    }

    // ─── CF.COUNT ────────────────────────────────────────────────

    @Override
    public long count(V element) {
        return get(countAsync(element));
    }

    @Override
    public RFuture<Long> countAsync(V element) {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.CF_COUNT,
                getRawName(), encode(element));
    }

    // ─── CF.INFO ─────────────────────────────────────────────────

    @Override
    public CuckooFilterInfo getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<CuckooFilterInfo> getInfoAsync() {
        return commandExecutor.readAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.CF_INFO,
                getRawName());
    }

    // ─── helpers ─────────────────────────────────────────────────

    private List<Object> buildInsertParams(CuckooFilterAddArgsImpl<V> a,
                                            List<V> itemList) {
        List<Object> params = new ArrayList<>();
        params.add(getRawName());

        if (a.getCapacity() != null) {
            params.add("CAPACITY");
            params.add(a.getCapacity());
        }
        if (a.isNoCreate()) {
            params.add("NOCREATE");
        }

        params.add("ITEMS");
        for (V item : itemList) {
            params.add(encode(item));
        }
        return params;
    }

}
