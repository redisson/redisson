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

import java.util.Iterator;
import java.util.NoSuchElementException;

import org.redisson.api.RDeque;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListFirstObjectDecoder;
import org.redisson.command.CommandAsyncExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonDeque<V> extends RedissonQueue<V> implements RDeque<V> {

    private static final RedisCommand<Object> LRANGE_SINGLE = new RedisCommand<Object>("LRANGE", new ListFirstObjectDecoder());


    public RedissonDeque(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    public RedissonDeque(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
    }

    @Override
    public void addFirst(V e) {
        get(addFirstAsync(e));
    }

    @Override
    public RFuture<Void> addFirstAsync(V e) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LPUSH_VOID, getName(), encode(e));
    }

    @Override
    public void addLast(V e) {
        get(addLastAsync(e));
    }

    @Override
    public RFuture<Void> addLastAsync(V e) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.RPUSH_VOID, getName(), encode(e));
    }


    @Override
    public Iterator<V> descendingIterator() {
        return new Iterator<V>() {

            private int currentIndex = size();
            private boolean removeExecuted;

            @Override
            public boolean hasNext() {
                int size = size();
                return currentIndex > 0 && size > 0;
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex--;
                removeExecuted = false;
                return RedissonDeque.this.get(currentIndex);
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonDeque.this.remove(currentIndex);
                currentIndex++;
                removeExecuted = true;
            }

        };
    }

    @Override
    public RFuture<V> getLastAsync() {
        return commandExecutor.readAsync(getName(), codec, LRANGE_SINGLE, getName(), -1, -1);
    }

    @Override
    public V getLast() {
        V result = get(getLastAsync());
        if (result == null) {
            throw new NoSuchElementException();
        }
        return result;
    }

    @Override
    public boolean offerFirst(V e) {
        return get(offerFirstAsync(e));
    }

    @Override
    public RFuture<Boolean> offerFirstAsync(V e) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LPUSH_BOOLEAN, getName(), encode(e));
    }

    @Override
    public RFuture<Boolean> offerLastAsync(V e) {
        return offerAsync(e);
    }

    @Override
    public boolean offerLast(V e) {
        return get(offerLastAsync(e));
    }

    @Override
    public RFuture<V> peekFirstAsync() {
        return getAsync(0);
    }

    @Override
    public V peekFirst() {
        return get(peekFirstAsync());
    }

    @Override
    public RFuture<V> peekLastAsync() {
        return getLastAsync();
    }

    @Override
    public V peekLast() {
        return get(getLastAsync());
    }

    @Override
    public RFuture<V> pollFirstAsync() {
        return pollAsync();
    }

    @Override
    public V pollFirst() {
        return poll();
    }

    @Override
    public RFuture<V> pollLastAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.RPOP, getName());
    }


    @Override
    public V pollLast() {
        return get(pollLastAsync());
    }

    @Override
    public RFuture<V> popAsync() {
        return pollAsync();
    }

    @Override
    public V pop() {
        return removeFirst();
    }

    @Override
    public RFuture<Void> pushAsync(V e) {
        return addFirstAsync(e);
    }

    @Override
    public void push(V e) {
        addFirst(e);
    }

    @Override
    public RFuture<Boolean> removeFirstOccurrenceAsync(Object o) {
        return removeAsync(o, 1);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return remove(o, 1);
    }

    @Override
    public RFuture<V> removeFirstAsync() {
        return pollAsync();
    }

    @Override
    public RFuture<V> removeLastAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.RPOP, getName());
    }

    @Override
    public V removeLast() {
        V value = get(removeLastAsync());
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

    @Override
    public RFuture<Boolean> removeLastOccurrenceAsync(Object o) {
        return removeAsync(o, -1);
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return remove(o, -1);
    }

}
