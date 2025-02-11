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

import org.redisson.api.Entry;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.connection.decoder.ListDrainToDecoder;
import org.redisson.misc.CompletableFutureWrapper;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.BlockingQueue}.
 *
 * <p>Queue size limited by Redis server memory amount. This is why {@link #remainingCapacity()} always
 * returns <code>Integer.MAX_VALUE</code>
 *
 * @author pdeschen@gmail.com
 * @author Nikita Koksharov
 */
public class RedissonBlockingQueue<V> extends RedissonQueue<V> implements RBlockingQueue<V> {

    public RedissonBlockingQueue(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    public RedissonBlockingQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
    }

    public RedissonBlockingQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name, null);
        this.name = name;
    }

    @Override
    public RFuture<Void> putAsync(V e) {
        return addAsync(e, RedisCommands.RPUSH_VOID);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#put(java.lang.Object)
     */
    @Override
    public void put(V e) throws InterruptedException {
        get(putAsync(e));
    }

    @Override
    public boolean offer(V e, long timeout, TimeUnit unit) throws InterruptedException {
        return offer(e);
    }

    @Override
    public RFuture<V> takeAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BLPOP_VALUE, getRawName(), 0);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#take()
     */
    @Override
    public V take() throws InterruptedException {
        return commandExecutor.getInterrupted(takeAsync());
    }

    @Override
    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        if (timeout < 0) {
            return new CompletableFutureWrapper<>((V) null);
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BLPOP_VALUE, getRawName(), toSeconds(timeout, unit));
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return commandExecutor.getInterrupted(pollAsync(timeout, unit));
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueue#pollFromAny(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public Entry<String, V> pollFromAnyWithName(Duration timeout, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollFromAnyWithNameAsync(timeout, queueNames));
    }

    @Override
    public RFuture<Entry<String, V>> pollFromAnyWithNameAsync(Duration timeout, String... queueNames) {
        if (timeout.toMillis() < 0) {
            return new CompletableFutureWrapper<>((Entry) null);
        }

        return commandExecutor.pollFromAnyAsync(getRawName(), codec, RedisCommands.BLPOP_NAME,
                toSeconds(timeout.toMillis(), TimeUnit.MILLISECONDS), queueNames);
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueueAsync#pollFromAnyAsync(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        if (timeout < 0) {
            return new CompletableFutureWrapper<>((V) null);
        }

        return commandExecutor.pollFromAnyAsync(getRawName(), codec, RedisCommands.BLPOP_VALUE,
                                    toSeconds(timeout, unit), queueNames);
    }

    @Override
    public Map<String, List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollFirstFromAnyAsync(duration, count, queueNames));
    }

    @Override
    public RFuture<Map<String, List<V>>> pollFirstFromAnyAsync(Duration duration, int count, String... queueNames) {
        List<String> mappedNames = map(queueNames);
        List<Object> params = new ArrayList<>();
        params.add(toSeconds(duration.getSeconds(), TimeUnit.SECONDS));
        params.add(queueNames.length + 1);
        params.add(getRawName());
        params.addAll(mappedNames);
        params.add("LEFT");
        params.add("COUNT");
        params.add(count);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BLMPOP, params.toArray());
    }

    @Override
    public Map<String, List<V>> pollLastFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollLastFromAnyAsync(duration, count, queueNames));
    }

    @Override
    public RFuture<Map<String, List<V>>> pollLastFromAnyAsync(Duration duration, int count, String... queueNames) {
        List<String> mappedNames = map(queueNames);
        List<Object> params = new ArrayList<>();
        params.add(toSeconds(duration.getSeconds(), TimeUnit.SECONDS));
        params.add(queueNames.length + 1);
        params.add(getRawName());
        params.addAll(mappedNames);
        params.add("RIGHT");
        params.add("COUNT");
        params.add(count);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BLMPOP, params.toArray());
    }

    @Override
    public Entry<String, V> pollLastFromAnyWithName(Duration duration, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollLastFromAnyWithNameAsync(duration, queueNames));
    }

    @Override
    public RFuture<Entry<String, V>> pollLastFromAnyWithNameAsync(Duration timeout, String... queueNames) {
        if (timeout.toMillis() < 0) {
            return new CompletableFutureWrapper<>((Entry) null);
        }
        return commandExecutor.pollFromAnyAsync(getRawName(), codec, RedisCommands.BRPOP_NAME,
                toSeconds(timeout.toMillis(), TimeUnit.MILLISECONDS), queueNames);
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        if (timeout < 0) {
            return new CompletableFutureWrapper<>((V) null);
        }

        String mappedName = mapName(queueName);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BRPOPLPUSH, getRawName(), mappedName, toSeconds(timeout, unit));
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
    public RFuture<V> takeLastAndOfferFirstToAsync(String queueName) {
        return pollLastAndOfferFirstToAsync(queueName, 0, TimeUnit.SECONDS);
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
    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        if (c == null) {
            throw new NullPointerException();
        }

        return commandExecutor.evalWriteAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
              "local vals = redis.call('lrange', KEYS[1], 0, -1); " +
              "redis.call('del', KEYS[1]); " +
              "return vals", Collections.<Object>singletonList(getRawName()));
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        if (maxElements <= 0) {
            return 0;
        }

        return get(drainToAsync(c, maxElements));
    }

    @Override
    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        if (c == null) {
            throw new NullPointerException();
        }
        return commandExecutor.evalWriteAsync(getRawName(), codec, new RedisCommand<Object>("EVAL", new ListDrainToDecoder(c)),
                "local elemNum = math.min(ARGV[1], redis.call('llen', KEYS[1])) - 1;" +
                        "local vals = redis.call('lrange', KEYS[1], 0, elemNum); " +
                        "redis.call('ltrim', KEYS[1], elemNum + 1, -1); " +
                        "return vals",
                Collections.<Object>singletonList(getRawName()), maxElements);
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

}