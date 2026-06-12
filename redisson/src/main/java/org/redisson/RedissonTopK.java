/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.RTopK;
import org.redisson.api.TopKInfo;
import org.redisson.api.topk.TopKInitArgs;
import org.redisson.api.topk.TopKInitArgsImpl;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Distributed implementation of Top-K
 * based on Redis Bloom module {@code TOPK.*} commands.
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonTopK<V> extends RedissonExpirable implements RTopK<V> {

    private static final long DEFAULT_WIDTH = 8;
    private static final long DEFAULT_DEPTH = 7;
    private static final double DEFAULT_DECAY = 0.9;

    public RedissonTopK(Codec codec,
                        CommandAsyncExecutor commandExecutor,
                        String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public void init(int topK) {
        get(initAsync(topK));
    }

    @Override
    public RFuture<Void> initAsync(int topK) {
        return commandExecutor.writeAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.TOPK_RESERVE,
                getRawName(), topK);
    }

    @Override
    public void init(TopKInitArgs args) {
        get(initAsync(args));
    }

    @Override
    public RFuture<Void> initAsync(TopKInitArgs args) {
        TopKInitArgsImpl a = (TopKInitArgsImpl) args;

        List<Object> params = new ArrayList<>(5);
        params.add(getRawName());
        params.add(a.getTopK());

        // TOPK.RESERVE accepts width, depth and decay only together, so when
        // any of them is supplied the remaining ones fall back to the defaults.
        if (a.getWidth() != null || a.getDepth() != null || a.getDecay() != null) {
            params.add(a.getWidth() != null ? a.getWidth() : DEFAULT_WIDTH);
            params.add(a.getDepth() != null ? a.getDepth() : DEFAULT_DEPTH);
            params.add(a.getDecay() != null ? a.getDecay() : DEFAULT_DECAY);
        }

        return commandExecutor.writeAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.TOPK_RESERVE,
                params.toArray());
    }

    @Override
    public V add(V item) {
        return get(addAsync(item));
    }

    @Override
    public RFuture<V> addAsync(V item) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.TOPK_ADD_SINGLE,
                getRawName(), encode(item));
    }

    @Override
    public List<V> add(List<V> items) {
        return get(addAsync(items));
    }

    @Override
    public RFuture<List<V>> addAsync(List<V> items) {
        List<Object> params = new ArrayList<>(items.size() + 1);
        params.add(getRawName());
        for (V item : items) {
            params.add(encode(item));
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.TOPK_ADD,
                params.toArray());
    }

    @Override
    public V incrementBy(V item, int increment) {
        return get(incrementByAsync(item, increment));
    }

    @Override
    public RFuture<V> incrementByAsync(V item, int increment) {
        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.TOPK_INCRBY_SINGLE,
                getRawName(), encode(item), increment);
    }

    @Override
    public List<V> incrementBy(Map<V, Integer> itemIncrements) {
        return get(incrementByAsync(itemIncrements));
    }

    @Override
    public RFuture<List<V>> incrementByAsync(Map<V, Integer> itemIncrements) {
        List<Object> params = new ArrayList<>(itemIncrements.size() * 2 + 1);
        params.add(getRawName());
        for (Map.Entry<V, Integer> entry : itemIncrements.entrySet()) {
            params.add(encode(entry.getKey()));
            params.add(entry.getValue());
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.TOPK_INCRBY,
                params.toArray());
    }

    @Override
    public boolean contains(V item) {
        return get(containsAsync(item));
    }

    @Override
    public RFuture<Boolean> containsAsync(V item) {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_QUERY_SINGLE,
                getRawName(), encode(item));
    }

    @Override
    public List<Boolean> contains(List<V> items) {
        return get(containsAsync(items));
    }

    @Override
    public RFuture<List<Boolean>> containsAsync(List<V> items) {
        List<Object> params = new ArrayList<>(items.size() + 1);
        params.add(getRawName());
        for (V item : items) {
            params.add(encode(item));
        }

        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_QUERY,
                params.toArray());
    }

    @Override
    public long count(V item) {
        return get(countAsync(item));
    }

    @Override
    public RFuture<Long> countAsync(V item) {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_COUNT_SINGLE,
                getRawName(), encode(item));
    }

    @Override
    public List<Long> count(List<V> items) {
        return get(countAsync(items));
    }

    @Override
    public RFuture<List<Long>> countAsync(List<V> items) {
        List<Object> params = new ArrayList<>(items.size() + 1);
        params.add(getRawName());
        for (V item : items) {
            params.add(encode(item));
        }

        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_COUNT,
                params.toArray());
    }

    @Override
    public List<V> list() {
        return get(listAsync());
    }

    @Override
    public RFuture<List<V>> listAsync() {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_LIST,
                getRawName());
    }

    @Override
    public Map<V, Long> listWithCount() {
        return get(listWithCountAsync());
    }

    @Override
    public RFuture<Map<V, Long>> listWithCountAsync() {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.TOPK_LIST_WITHCOUNT,
                getRawName(), "WITHCOUNT");
    }

    @Override
    public TopKInfo getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<TopKInfo> getInfoAsync() {
        return commandExecutor.readAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.TOPK_INFO,
                getRawName());
    }

}
