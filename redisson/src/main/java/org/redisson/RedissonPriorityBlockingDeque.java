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
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RPriorityBlockingDeque;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;

/**
 * <p>Distributed and concurrent implementation of priority blocking deque.
 *
 * <p>Queue size limited by Redis server memory amount. This is why {@link #remainingCapacity()} always
 * returns <code>Integer.MAX_VALUE</code>
 *
 * @author Nikita Koksharov
 */
public class RedissonPriorityBlockingDeque<V> extends RedissonPriorityDeque<V> implements RPriorityBlockingDeque<V> {

    private final RedissonPriorityBlockingQueue<V> blockingQueue;
    
    protected RedissonPriorityBlockingDeque(CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        blockingQueue = (RedissonPriorityBlockingQueue<V>) redisson.getPriorityBlockingQueue(name);
    }

    protected RedissonPriorityBlockingDeque(Codec codec, CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        
        blockingQueue = (RedissonPriorityBlockingQueue<V>) redisson.getPriorityBlockingQueue(name, codec);
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
        return blockingQueue.takeAsync();
    }

    @Override
    public V take() throws InterruptedException {
        return blockingQueue.take();
    }

    public RFuture<V> pollAsync(long timeout, TimeUnit unit) {
        return blockingQueue.pollAsync(timeout, unit);
    }

    @Override
    public V poll(long timeout, TimeUnit unit) throws InterruptedException {
        return blockingQueue.poll(timeout, unit);
    }

    @Override
    public V pollFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        throw new UnsupportedOperationException("use poll method");
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
        return blockingQueue.drainTo(c);
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c) {
        return blockingQueue.drainToAsync(c);
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainTo(c, maxElements);
    }

    public RFuture<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainToAsync(c, maxElements);
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
        return get(takeFirstAsync());
    }

    @Override
    public RFuture<V> takeFirstAsync() {
        return takeAsync();
    }

    @Override
    public RFuture<V> takeLastAsync() {
        RPromise<V> result = new RedissonPromise<V>();
        blockingQueue.takeAsync(result, 0, 0, RedisCommands.RPOP, getName());
        return result;
    }

    @Override
    public V takeLast() throws InterruptedException {
        return get(takeLastAsync());
    }

    @Override
    public RFuture<V> pollFirstAsync(long timeout, TimeUnit unit) {
        return pollAsync(timeout, unit);
    }

    @Override
    public V pollFirstFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return get(pollFirstFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public RFuture<V> pollFirstFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        return pollFromAnyAsync(timeout, unit, queueNames);
    }

    @Override
    public V pollLastFromAny(long timeout, TimeUnit unit, String... queueNames) throws InterruptedException {
        return get(pollLastFromAnyAsync(timeout, unit, queueNames));
    }

    @Override
    public RFuture<V> pollLastFromAnyAsync(long timeout, TimeUnit unit, String... queueNames) {
        throw new UnsupportedOperationException();
    }

    @Override
    public V pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollFirstAsync(timeout, unit));
    }

    @Override
    public RFuture<V> pollLastAsync(long timeout, TimeUnit unit) {
        RPromise<V> result = new RedissonPromise<V>();
        blockingQueue.takeAsync(result, 0, unit.toMicros(timeout), RedisCommands.RPOP, getName());
        return result;
    }

    @Override
    public V pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        return get(pollLastAsync(timeout, unit));
    }

}