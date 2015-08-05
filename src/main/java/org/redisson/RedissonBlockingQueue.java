/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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

import java.util.Collection;
import java.util.Collections;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.core.RBlockingQueue;

import io.netty.util.concurrent.Future;

/**
 * Offers blocking queue facilities through an intermediary
 * {@link LinkedBlockingQueue} where items are added as soon as
 * <code>blpop</code> returns. All {@link BlockingQueue} methods are actually
 * delegated to this intermediary queue.
 *
 * @author pdeschen@gmail.com
 * @author Nikita Koksharov
 */
public class RedissonBlockingQueue<V> extends RedissonQueue<V> implements RBlockingQueue<V> {

    protected RedissonBlockingQueue(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public Future<Boolean> putAsync(V e) {
        return offerAsync(e);
    }

    @Override
    public void put(V e) throws InterruptedException {
        offer(e);
    }

    @Override
    public boolean offer(V e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    @Override
    public Future<V> takeAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.BLPOP_VALUE, getName(), 0);
    }

    @Override
    public V take() throws InterruptedException {
        Future<V> res = takeAsync();
        return res.await().getNow();
    }

    @Override
    public Future<V> pollAsync(long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getName(), RedisCommands.BLPOP_VALUE, getName(), unit.toSeconds(timeout));
    }

    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        Future<V> res = pollAsync(timeout, unit);
        return res.await().getNow();
    }

    @Override
    public V pollLastAndOfferFirstTo(RBlockingQueue<V> queue, long timeout, TimeUnit unit)
            throws InterruptedException {
        return pollLastAndOfferFirstTo(queue.getName(), timeout, unit);
    }

    @Override
    public Future<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getName(), RedisCommands.BRPOPLPUSH, getName(), queueName, unit.toSeconds(timeout));
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        Future<V> res = pollLastAndOfferFirstToAsync(queueName, timeout, unit);
        return res.await().getNow();
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        return get(drainToAsync(c));
    }

    @Override
    public Future<Integer> drainToAsync(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return commandExecutor.evalWriteAsync(getName(), new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('ltrim', KEYS[1], -1, 0); " +
              "return vals", Collections.<Object>singletonList(getName()));
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }

        return get(drainToAsync(c, maxElements));
    }

    @Override
    public Future<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        return commandExecutor.evalWriteAsync(getName(), new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                Collections.<Object>singletonList(getName()), maxElements);
    }
}