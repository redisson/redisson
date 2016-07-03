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
package org.redisson;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RBlockingDeque;

import io.netty.util.concurrent.Future;

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

    protected RedissonBlockingDeque(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        blockingQueue = new RedissonBlockingQueue<V>(commandExecutor, name);
    }

    protected RedissonBlockingDeque(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        blockingQueue = new RedissonBlockingQueue<V>(codec, commandExecutor, name);
    }

    @Override
    public Future<Boolean> putAsync(V e) {
        return offerAsync(e);
    }

    /*
     * (non-Javadoc)
     * @see java.util.concurrent.BlockingQueue#put(java.lang.Object)
     */
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
    public Future<V> pollAsync(long timeout, TimeUnit unit) {
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
    public V pollFromAny(long timeout, TimeUnit unit, String ... queueNames) throws InterruptedException {
        return blockingQueue.pollFromAny(timeout, unit);
    }

    /*
     * (non-Javadoc)
     * @see org.redisson.core.RBlockingQueueAsync#pollFromAnyAsync(long, java.util.concurrent.TimeUnit, java.lang.String[])
     */
    @Override
    public Future<V> pollFromAnyAsync(long timeout, TimeUnit unit, String ... queueNames) {
        return blockingQueue.pollFromAnyAsync(timeout, unit);
    }

    @Override
    public Future<V> pollLastAndOfferFirstToAsync(String queueName, long timeout, TimeUnit unit) {
        return blockingQueue.pollLastAndOfferFirstToAsync(queueName, timeout, unit);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName, long timeout, TimeUnit unit) throws InterruptedException {
        return blockingQueue.pollLastAndOfferFirstTo(queueName, timeout, unit);
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
    public Future<Integer> drainToAsync(Collection<? super V> c) {
        return blockingQueue.drainToAsync(c);
    }

    @Override
    public int drainTo(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainTo(c, maxElements);
    }

    @Override
    public Future<Integer> drainToAsync(Collection<? super V> c, int maxElements) {
        return blockingQueue.drainToAsync(c, maxElements);
    }

    @Override
    public Future<Void> putFirstAsync(V e) {
        return addFirstAsync(e);
    }

    @Override
    public Future<Void> putLastAsync(V e) {
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
        Future<V> res = takeFirstAsync();
        return res.await().getNow();
    }

    @Override
    public Future<V> takeFirstAsync() {
        return takeAsync();
    }

    @Override
    public Future<V> takeLastAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BRPOP_VALUE, getName(), 0);
    }

    @Override
    public V takeLast() throws InterruptedException {
        Future<V> res = takeLastAsync();
        return res.await().getNow();
    }

    @Override
    public Future<V> pollFirstAsync(long timeout, TimeUnit unit) {
        return pollAsync(timeout, unit);
    }

    @Override
    public V pollFirstFromAny(long timeout, TimeUnit unit, String ... queueNames) throws InterruptedException {
        Future<V> res = pollFirstFromAnyAsync(timeout, unit, queueNames);
        return res.await().getNow();
    }

    @Override
    public Future<V> pollFirstFromAnyAsync(long timeout, TimeUnit unit, String ... queueNames) {
        return pollFromAnyAsync(timeout, unit, queueNames);
    }

    @Override
    public V pollLastFromAny(long timeout, TimeUnit unit, String ... queueNames) throws InterruptedException {
        Future<V> res = pollLastFromAnyAsync(timeout, unit, queueNames);
        return res.await().getNow();
    }

    @Override
    public Future<V> pollLastFromAnyAsync(long timeout, TimeUnit unit, String ... queueNames) {
        List<Object> params = new ArrayList<Object>(queueNames.length + 1);
        params.add(getName());
        for (Object name : queueNames) {
            params.add(name);
        }
        params.add(unit.toSeconds(timeout));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BRPOP_VALUE, params.toArray());
    }

    @Override
    public V pollFirst(long timeout, TimeUnit unit) throws InterruptedException {
        Future<V> res = pollFirstAsync(timeout, unit);
        return res.await().getNow();
    }

    @Override
    public Future<V> pollLastAsync(long timeout, TimeUnit unit) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.BRPOP_VALUE, getName(), unit.toSeconds(timeout));
    }

    @Override
    public V pollLast(long timeout, TimeUnit unit) throws InterruptedException {
        Future<V> res = pollLastAsync(timeout, unit);
        return res.await().getNow();
   }

}