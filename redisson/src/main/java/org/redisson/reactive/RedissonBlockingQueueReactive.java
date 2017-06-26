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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.reactivestreams.Publisher;
import org.redisson.api.RBlockingQueueReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.BlockingQueue}.
 *
 * <p>Queue size limited by Redis server memory amount.
 *
 * @author pdeschen@gmail.com
 * @author Nikita Koksharov
 */
public class RedissonBlockingQueueReactive<V> extends RedissonQueueReactive<V> implements RBlockingQueueReactive<V> {

    public RedissonBlockingQueueReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonBlockingQueueReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Integer> put(V e) {
        return offer(e);
    }

    @Override
    public Publisher<V> take() {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.BLPOP_VALUE, getName(), 0);
    }

    @Override
    public Publisher<V> poll(long timeout, TimeUnit unit) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.BLPOP_VALUE, getName(), unit.toSeconds(timeout));
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueueAsync#pollFromAnyAsync(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public Publisher<V> pollFromAny(long timeout, TimeUnit unit, String ... queueNames) {
        List<Object> params = new ArrayList<Object>(queueNames.length + 1);
        params.add(getName());
        for (Object name : queueNames) {
            params.add(name);
        }
        params.add(unit.toSeconds(timeout));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.BLPOP_VALUE, params.toArray());
    }

    @Override
    public Publisher<V> pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.BRPOPLPUSH, getName(), queueName, unit.toSeconds(timeout));
    }

    @Override
    public Publisher<Integer> drainTo(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('ltrim', KEYS[1], -1, 0); " +
              "return vals", Collections.<Object>singletonList(getName()));
    }

    @Override
    public Publisher<Integer> drainTo(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        return commandExecutor.evalWriteReactive(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                Collections.<Object>singletonList(getName()), maxElements);
    }
}