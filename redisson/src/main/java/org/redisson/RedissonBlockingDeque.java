/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import java.time.Duration;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.redisson.api.RBlockingDeque;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.api.queue.DequeMoveArgs;
import org.redisson.api.queue.DequeMoveParams;
import org.redisson.api.queue.DequeMoveSource;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * <p>Distributed and concurrent implementation of {@link java.util.concurrent.BlockingDeque}.
 *
 * <p>Queue size limited by Redis server memory amount. This is why {@link #remainingCapacity()} always
 * returns <code>Integer.MAX_VALUE</code>
 *
 * @author Nikita Koksharov
 */
public class RedissonBlockingDeque<V> extends RedissonDeque<V> implements RBlockingDeque<V> {

    private final RedissonBlockingQueue<V> blockingQueue;

    public RedissonBlockingDeque(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        blockingQueue = new RedissonBlockingQueue<V>(commandExecutor, name, redisson);
    }

    public RedissonBlockingDeque(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        blockingQueue = new RedissonBlockingQueue<V>(codec, commandExecutor, name, redisson);
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
        return blockingQueue.takeAsync();
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#take()
     */
    @Override
    public V take() throws InterruptedException {
        return blockingQueue.take();
    }

    @Override
    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        return blockingQueue.pollAsync(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#poll(long, java.util.concurrent.TimeUnit)
     */
    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return blockingQueue.poll(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueue#pollFromAny(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return blockingQueue.pollFromAny(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueueAsync#pollFromAnyAsync(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public RFuture<V> pollFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        return blockingQueue.pollFromAnyAsync(timeout, unit);
    }

    @Override
    public RFuture<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        return blockingQueue.pollLastAndOfferFirstToAsync(queueName, timeout, unit);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        return blockingQueue.pollLastAndOfferFirstTo(queueName, timeout, unit);
    }

    @Override
    public Map<String, List<V>> pollFirstFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        return blockingQueue.pollFirstFromAny(duration, count, queueNames);
    }

    @Override
    public Map<String, List<V>> pollLastFromAny(Duration duration, int count, String... queueNames) throws InterruptedException {
        return blockingQueue.pollLastFromAny(duration, count, queueNames);
    }

    @Override
    public RFuture<Map<String, List<V>>> pollFirstFromAnyAsync(Duration duration, int count, String... queueNames) {
        return blockingQueue.pollFirstFromAnyAsync(duration, count, queueNames);
    }

    @Override
    public RFuture<Map<String, List<V>>> pollLastFromAnyAsync(Duration duration, int count, String... queueNames) {
        return blockingQueue.pollLastFromAnyAsync(duration, count, queueNames);
    }

    @Override
    public V takeLastAndOfferFirstTo(String queueName) throws InterruptedException {
        return commandExecutor.getInterrupted(takeLastAndOfferFirstToAsync(queueName));
    }

    @Override
    public int subscribeOnElements(Consumer<V> consumer) {
        return blockingQueue.subscribeOnElements(consumer);
    }

    @Override
    public void unsubscribe(int id) {
        blockingQueue.unsubscribe(id);
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
        return blockingQueue.drainTo(c);
    }

    @Override
    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        return blockingQueue.drainToAsync(c);
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainTo(c, maxElements);
    }

    @Override
    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainToAsync(c, maxElements);
    }

    @Override
    public RFuture<Void> putFirstAsync(V e) {
        return addFirstAsync(e);
    }

    @Override
    public RFuture<Void> putLastAsync(V e) {
        return addLastAsync(e);
    }

    @Override
    public void putFirst(V e) throws InterruptedException {
        addFirst(e);
    }

    @Override
    public void putLast(V e) throws InterruptedException {
        addLast(e);
    }

    @Override
    public boolean offerFirst(V e, long timeout, TimeUnit unit) throws InterruptedException {
        addFirst(e);
        return true;
    }

    @Override
    public boolean offerLast(V e, long timeout, TimeUnit unit) throws InterruptedException {
        addLast(e);
        return true;
    }

    @Override
    public V takeFirst() throws InterruptedException {
        return commandExecutor.getInterrupted(takeFirstAsync());
    }

    @Override
    public RFuture<V> takeFirstAsync() {
        return takeAsync();
    }

    @Override
    public RFuture<V> takeLastAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BRPOP_VALUE, getRawName(), 0);
    }

    @Override
    public V takeLast() throws InterruptedException {
        return commandExecutor.getInterrupted(takeLastAsync());
    }

    @Override
    public RFuture<V> pollFirstAsync(long timeout, TimeUnit unit) {
        return pollAsync(timeout, unit);
    }

    @Override
    public V pollFirstFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollFirstFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public RFuture<V> pollFirstFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        return pollFromAnyAsync(timeout, unit, queueNames);
    }

    @Override
    public V pollLastFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return commandExecutor.getInterrupted(pollLastFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public int subscribeOnFirstElements(Consumer<V> consumer) {
        return commandExecutor.getConnectionManager().getElementsSubscribeService().subscribeOnElements(this::takeFirstAsync, consumer);
    }

    @Override
    public int subscribeOnLastElements(Consumer<V> consumer) {
        return commandExecutor.getConnectionManager().getElementsSubscribeService().subscribeOnElements(this::takeLastAsync, consumer);
    }

    @Override
    public RFuture<V> pollLastFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        return commandExecutor.pollFromAnyAsync(getRawName(), codec, RedisCommands.BRPOP_VALUE, toSeconds(timeout, unit), queueNames);
    }

    @Override
    public V pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        return commandExecutor.getInterrupted(pollFirstAsync(timeout, unit));
    }

    @Override
    public RFuture<V> pollLastAsync(long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BRPOP_VALUE, getRawName(), toSeconds(timeout, unit));
    }

    @Override
    public V pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        return commandExecutor.getInterrupted(pollLastAsync(timeout, unit));
    }

    @Override
    public V move(Duration timeout, DequeMoveArgs args) {
        return get(moveAsync(timeout, args));
    }

    @Override
    public RFuture<V> moveAsync(Duration timeout, DequeMoveArgs args) {
        DequeMoveSource source = (DequeMoveSource) args;
        DequeMoveParams pp = source.getParams();
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.BLMOVE, getRawName(),
                                                pp.getDestName(), pp.getSourceDirection(), pp.getDestDirection(),
                                                toSeconds(timeout.getSeconds(), TimeUnit.SECONDS));
    }
}