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

import org.redisson.api.Entry;
import org.redisson.api.RFuture;
import org.redisson.api.RPriorityBlockingQueue;
import org.redisson.api.RedissonClient;
import org.redisson.client.RedisConnectionException;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.PriorityBlockingQueue}.
 *
 * <p>Queue size limited by Redis server memory amount. This is why {@link #remainingCapacity()} always
 * returns <code>Integer.MAX_VALUE</code>
 *
 * @author Nikita Koksharov
 */
public class RedissonPriorityBlockingQueue<V> extends RedissonPriorityQueue<V> implements RPriorityBlockingQueue<V> {

    protected RedissonPriorityBlockingQueue(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    protected RedissonPriorityBlockingQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
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
        CompletableFuture<V> result = new CompletableFuture<V>();
        takeAsync(result, 0, 0, RedisCommands.LPOP, getRawName());
        return new CompletableFutureWrapper<>(result);
    }

    protected <T> void takeAsync(CompletableFuture<V> result, long delay, long timeoutInMicro, RedisCommand<T> command, Object... params) {
        if (result.isDone()) {
            return;
        }

        long start = System.currentTimeMillis();
        getServiceManager().newTimeout(t -> {
            if (result.isDone()) {
                return;
            }

            RFuture<V> future = wrapLockedAsync(command, params);
            future.whenComplete((res, e) -> {
                    if (e != null && !(e instanceof RedisConnectionException)) {
                        result.completeExceptionally(e);
                        return;
                    }
                    
                    if (res != null) {
                        result.complete(res);
                        return;
                    }
                    
                    if (result.isCancelled()) {
                        return;
                    }
                    
                    long remain = 0;
                    if (timeoutInMicro > 0) {
                        remain = timeoutInMicro - ((System.currentTimeMillis() - start))*1000;
                        if (remain <= 0) {
                            result.complete(null);
                            return;
                        }
                    }
                    
                    long del = ThreadLocalRandom.current().nextInt(2000000);
                    if (timeoutInMicro > 0 && remain < 2000000) {
                        del = 0;
                    }
                    
                    takeAsync(result, del, remain, command, params);
            });
        }, delay, TimeUnit.MICROSECONDS);
    }

    @Override
    public V take() throws InterruptedException {
        return commandExecutor.getInterrupted(takeAsync());
    }

    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        CompletableFuture<V> result = new CompletableFuture<V>();
        takeAsync(result, 0, unit.toMicros(timeout), RedisCommands.LPOP, getRawName());
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return commandExecutor.getInterrupted(pollAsync(timeout, unit));
    }

    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public Entry<String, V> pollFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public Map<String, List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public Map<String, List<V>> pollLastFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public Entry<String, V> pollLastFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Map<String, List<V>>> pollFirstFromAnyAsync(Duration duration, int count, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Map<String, List<V>>> pollLastFromAnyAsync(Duration duration, int count, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        CompletableFuture<V> result = new CompletableFuture<V>();
        takeAsync(result, 0, unit.toMicros(timeout), RedisCommands.RPOPLPUSH, getRawName(), queueName);
        return new CompletableFutureWrapper<>(result);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        return commandExecutor.getInterrupted(pollLastAndOfferFirstToAsync(queueName, timeout, unit));
    }
    
    @Override
    public V takeLastAndOfferFirstTo(String queueName) throws InterruptedException {
        return commandExecutor.getInterrupted(takeLastAndOfferFirstToAsync(queueName));
    }

    @Override
    public int subscribeOnElements(Consumer<V> consumer) {
        return getServiceManager().getElementsSubscribeService()
                .subscribeOnElements(this::takeAsync, consumer);
    }

    @Override
    public int subscribeOnElements(Function<V, CompletionStage<Void>> consumer) {
        return getServiceManager().getElementsSubscribeService()
                .subscribeOnElements(this::takeAsync, consumer);
    }

    @Override
    public void unsubscribe(int listenerId) {
        getServiceManager().getElementsSubscribeService().unsubscribe(listenerId);
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

        return commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('ltrim', KEYS[1], -1, 0); " +
              "return vals", Collections.<Object>singletonList(getRawName()));
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
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                Collections.<Object>singletonList(getRawName()), maxElements);
    }

    @Override
    public RFuture<Boolean> offerAsync(V e) {
        throw new UnsupportedOperationException("use offer method");
    }

    @Override
    public RFuture<List<V>> pollAsync(int limit) {
        return wrapLockedAsync(() -> {
            return commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, RedisCommands.EVAL_LIST,
                       "local result = {};"
                     + "for i = 1, ARGV[1], 1 do " +
                           "local value = redis.call('lpop', KEYS[1]);" +
                           "if value ~= false then " +
                               "table.insert(result, value);" +
                           "else " +
                               "return result;" +
                           "end;" +
                       "end; " +
                       "return result;",
                    Collections.singletonList(getRawName()), limit);
        });
    }

    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Entry<String, V>> pollFromAnyWithNameAsync(Duration timeout, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Entry<String, V>> pollLastFromAnyWithNameAsync(Duration timeout, String... queueNames) {
        throw new UnsupportedOperationException("use poll method");
    }

    @Override
    public RFuture<Void> putAsync(V e) {
        throw new UnsupportedOperationException("use add method");
    }

    @Override
    public List<V> poll(int limit) {
        return get(pollAsync(limit));
    }
}