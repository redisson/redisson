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

import java.util.NoSuchElementException;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RQueue;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonQueue<V> extends RedissonList<V> implements RQueue<V> {

    protected RedissonQueue(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    protected RedissonQueue(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public boolean offer(V e) {
        return add(e);
    }

    @Override
    public Future<Boolean> offerAsync(V e) {
        return addAsync(e);
    }

    public V getFirst() {
        V value = getValue(0);
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    public V removeFirst() {
        V value = poll();
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public V remove() {
        return removeFirst();
    }

    @Override
    public Future<V> pollAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LPOP, getName());
    }

    @Override
    public V poll() {
        return get(pollAsync());
    }

    @Override
    public V element() {
        return getFirst();
    }

    @Override
    public Future<V> peekAsync() {
        return getAsync(0);
    }

    @Override
    public V peek() {
        return getValue(0);
    }

    @Override
    public V pollLastAndOfferFirstTo(String queueName) {
        return get(pollLastAndOfferFirstToAsync(queueName));
    }

    @Override
    public Future<V> pollLastAndOfferFirstToAsync(String queueName) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.RPOPLPUSH, getName(), queueName);
    }

    @Override
    public Future<V> pollLastAndOfferFirstToAsync(RQueue<V> queue) {
        return pollLastAndOfferFirstToAsync(queue.getName());
    }

    @Override
    public V pollLastAndOfferFirstTo(RQueue<V> queue) {
        return pollLastAndOfferFirstTo(queue.getName());
    }

}
