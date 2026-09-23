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

import org.redisson.api.CountMinInfo;
import org.redisson.api.RCountMin;
import org.redisson.api.RFuture;
import org.redisson.api.countmin.CountMinInitArgs;
import org.redisson.api.countmin.CountMinInitArgsImpl;
import org.redisson.api.countmin.CountMinMergeArgs;
import org.redisson.api.countmin.CountMinMergeArgsImpl;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.CountMapDecoder;
import org.redisson.command.CommandAsyncExecutor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Distributed implementation of count-min sketch
 * based on Redis Bloom module {@code CMS.*} commands.
 *
 * @param <V> element type
 *
 * @author Nikita Koksharov
 *
 */
public class RedissonCountMin<V> extends RedissonExpirable implements RCountMin<V> {

    private final Codec codec;

    public RedissonCountMin(Codec codec,
                            CommandAsyncExecutor commandExecutor,
                            String name) {
        super(commandExecutor, name);
        this.codec = codec;
    }

    @Override
    public void init(CountMinInitArgs args) {
        get(initAsync(args));
    }

    @Override
    public RFuture<Void> initAsync(CountMinInitArgs args) {
        CountMinInitArgsImpl a = (CountMinInitArgsImpl) args;

        // The two initialization modes are separate Redis commands rather than
        // flags on one, so the args type carries which of them was asked for.
        if (a.isByDimensions()) {
            return commandExecutor.writeAsync(
                    getRawName(), codec,
                    RedisCommands.CMS_INITBYDIM,
                    getRawName(), a.getWidth(), a.getDepth());
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CMS_INITBYPROB,
                getRawName(), a.getErrorRate(), a.getProbability());
    }

    @Override
    public long add(V element) {
        return get(addAsync(element));
    }

    @Override
    public RFuture<Long> addAsync(V element) {
        // CMS.INCRBY has no single-element form, the increment is always explicit.
        return addAsync(element, 1);
    }

    @Override
    public long add(V element, long increment) {
        return get(addAsync(element, increment));
    }

    @Override
    public RFuture<Long> addAsync(V element, long increment) {
        // CMS.INCRBY parses the increment as a signed value and then rejects it,
        // so a negative one costs a round trip to learn nothing useful.
        if (increment < 0) {
            throw new IllegalArgumentException("increment can't be negative");
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                RedisCommands.CMS_INCRBY,
                getRawName(), encode(element), increment);
    }

    @Override
    public Map<V, Long> add(Map<V, Long> elements) {
        return get(addAsync(elements));
    }

    @Override
    public RFuture<Map<V, Long>> addAsync(Map<V, Long> elements) {
        List<V> elementList = new ArrayList<>(elements.size());

        List<Object> params = new ArrayList<>(elements.size() * 2 + 1);
        params.add(getRawName());
        for (Map.Entry<V, Long> entry : elements.entrySet()) {
            Long increment = entry.getValue();
            Objects.requireNonNull(increment, "increment can't be null");
            // Checked per entry, before anything is sent: the command applies
            // every pair or none, so one bad value would reject the whole batch.
            if (increment < 0) {
                throw new IllegalArgumentException(
                        "increment can't be negative for element: " + entry.getKey());
            }

            elementList.add(entry.getKey());
            params.add(encode(entry.getKey()));
            params.add(increment);
        }

        return commandExecutor.writeAsync(
                getRawName(), codec,
                new RedisCommand<>("CMS.INCRBY",
                        new CountMapDecoder<>(elementList)),
                params.toArray());
    }

    @Override
    public long count(V element) {
        return get(countAsync(element));
    }

    @Override
    public RFuture<Long> countAsync(V element) {
        return commandExecutor.readAsync(
                getRawName(), codec,
                RedisCommands.CMS_QUERY,
                getRawName(), encode(element));
    }

    @Override
    public Map<V, Long> count(Collection<V> elements) {
        return get(countAsync(elements));
    }

    @Override
    public RFuture<Map<V, Long>> countAsync(Collection<V> elements) {
        List<V> elementList = new ArrayList<>(elements);

        List<Object> params = new ArrayList<>(elementList.size() + 1);
        params.add(getRawName());
        for (V element : elementList) {
            params.add(encode(element));
        }

        return commandExecutor.readAsync(
                getRawName(), codec,
                new RedisCommand<>("CMS.QUERY",
                        new CountMapDecoder<>(elementList)),
                params.toArray());
    }

    @Override
    public void mergeWith(CountMinMergeArgs args) {
        get(mergeWithAsync(args));
    }

    @Override
    public RFuture<Void> mergeWithAsync(CountMinMergeArgs args) {
        CountMinMergeArgsImpl a = (CountMinMergeArgsImpl) args;
        String[] names = a.getNames();
        long[] weights = a.getWeights();

        List<Object> params = new ArrayList<>();
        params.add(getRawName());
        params.add(names.length);
        // Source names are mapped the same way the destination name already is,
        // so a configured NameMapper applies to every key this command touches.
        params.addAll(map(names));
        if (weights != null) {
            params.add("WEIGHTS");
            for (long weight : weights) {
                params.add(weight);
            }
        }

        return commandExecutor.writeAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.CMS_MERGE,
                params.toArray());
    }

    @Override
    public CountMinInfo getInfo() {
        return get(getInfoAsync());
    }

    @Override
    public RFuture<CountMinInfo> getInfoAsync() {
        return commandExecutor.readAsync(
                getRawName(), StringCodec.INSTANCE,
                RedisCommands.CMS_INFO,
                getRawName());
    }

}
