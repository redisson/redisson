/**
 * Copyright (c) 2013-2019 Nikita Koksharov
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
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;
import io.netty.util.internal.PlatformDependent;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.PriorityBlockingQueue}.
 *
 * <p>Queue size limited by Redis server memory amount. This is why {@link #remainingCapacity()} always
 * returns <code>Integer.MAX_VALUE</code>
 *
 * @author Nikita Koksharov
 */
public class RedissonPriorityBlockingQueue<V> extends RedissonPriorityQueue<V> implements RPriorityBlockingQueue<V> {

    protected RedissonPriorityBlockingQueue(CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    protected RedissonPriorityBlockingQueue(Codec codec, CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
    }

    @Override
    public void put(V e) throws InterruptedException {
        add(e);
    }

    @Override
    public boolean offer(V e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    @Override
    public RFuture<V> takeAsync() {
        RPromise<V> result = new RedissonPromise<V>();
        takeAsync(result, 0, 0, RedisCommands.LPOP, getName());
        return result;
    }

    protected <T> void takeAsync(final RPromise<V> result, final long delay, final long timeoutInMicro, final RedisCommand<T> command, final Object ... params) {
        final long start = System.currentTimeMillis();
        commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
            @Override
            public void run() {
                RFuture<V> future = pollAsync(command, params);
                future.addListener(new FutureListener<V>() {
                    @Override
                    public void operationComplete(Future<V> future) throws Exception {
                        if (!future.isSuccess() && !(future.cause() instanceof RedisConnectionException)) {
                            result.tryFailure(future.cause());
                            return;
                        }
                        
                        if (future.getNow() != null) {
                            result.trySuccess(future.getNow());
                            return;
                        }
                        
                        if (result.isCancelled()) {
                            return;
                        }
                        
                        long remain = 0;
                        if (timeoutInMicro > 0) {
                            remain = timeoutInMicro - ((System.currentTimeMillis() - start))*1000;
                            if (remain <= 0) {
                                result.trySuccess(null);
                                return;
                            }
                        }
                        
                        long delay = PlatformDependent.threadLocalRandom().nextInt(2000000);
                        if (timeoutInMicro > 0 && remain < 2000000) {
                            delay = 0;
                        }
                        
                        takeAsync(result, delay, remain, command, params);
                    }
                });
            }
        }, delay, TimeUnit.MICROSECONDS);
    }

    @Override
    public V take() throws InterruptedException {
        return get(takeAsync());
    }

    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        RPromise<V> result = new RedissonPromise<V>();
        takeAsync(result, 0, unit.toMicros(timeout), RedisCommands.LPOP, getName());
        return result;
    }

    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollAsync(timeout, unit));
    }

    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String ... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        RPromise<V> result = new RedissonPromise<V>();
        takeAsync(result, 0, unit.toMicros(timeout), RedisCommands.RPOPLPUSH, getName(), queueName);
        return result;
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollLastAndOfferFirstToAsync(queueName, timeout, unit));
    }
    
    @Override
    public V takeLastAndOfferFirstTo(String queueName) throws InterruptedException {
        return get(takeLastAndOfferFirstToAsync(queueName));
    }
    
    public RFuture<V> takeLastAndOfferFirstToAsync(String queueName) {
        return pollLastAndOfferFirstToAsync(queueName, 0, TimeUnit.SECONDS);
    }

    @Override
    public int remainingCapacity() {
        return Integer.MAX_VALUE;
    }

    @Override
    public int drainTo(Collection<? super V> c) {
        lock.lock();
        
        try {
            return get(drainToAsync(c));
        } finally {
            lock.unlock();
        }
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('ltrim', KEYS[1], -1, 0); " +
              "return vals", Collections.<Object>singletonList(getName()));
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }

        lock.lock();
        
        try {
            return get(drainToAsync(c, maxElements));
        } finally {
            lock.unlock();
        }
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                Collections.<Object>singletonList(getName()), maxElements);
    }

    @Override
    public RFuture<Boolean> offerAsync(V e) {
        throw new UnsupportedOperationException("use offer method");
    }

    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Void> putAsync(V e) {
        throw new UnsupportedOperationException("use add method");
    }
}