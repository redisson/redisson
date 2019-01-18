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
import java.util.stream.Stream;

import org.redisson.api.RFuture;
import org.redisson.api.RPriorityDeque;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListFirstObjectDecoder;
import org.redisson.command.CommandExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonPriorityDeque<V> extends RedissonPriorityQueue<V> implements RPriorityDeque<V> {

    private static final RedisCommand<Object> LRANGE_SINGLE = new RedisCommand<Object>("LRANGE", new ListFirstObjectDecoder());


    protected RedissonPriorityDeque(CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
    }

    public RedissonPriorityDeque(Codec codec, CommandExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
    }

    public RFuture<Void> addFirstAsync(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }

    public RFuture<Void> addLastAsync(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }
    
    @Override
    public void addFirst(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }

    @Override
    public void addLast(V e) {
        throw new UnsupportedOperationException("use add or put method");
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
                return RedissonPriorityDeque.this.get(currentIndex);
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonPriorityDeque.this.remove(currentIndex);
                currentIndex++;
                removeExecuted = true;
            }

        };
    }

//    @Override
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
        throw new UnsupportedOperationException("use add or put method");
    }

    public RFuture<Boolean> offerFirstAsync(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }
    
    @Override
    public boolean offerLast(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }

    public RFuture<Boolean> offerLastAsync(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }
    
//    @Override
    public RFuture<V> peekFirstAsync() {
        return getAsync(0);
    }

    @Override
    public V peekFirst() {
        return get(peekFirstAsync());
    }

    @Override
    public V peekLast() {
        return get(peekLastAsync());
    }
    
    public RFuture<V> peekLastAsync() {
        return getLastAsync();
    }

    @Override
    public V pollFirst() {
        return get(pollFirstAsync());
    }
    
    public RFuture<V> pollFirstAsync() {
        return pollAsync();
    }

    public RFuture<V> pollLastAsync() {
        return pollAsync(RedisCommands.RPOP, getName());
    }

    @Override
    public V pollLast() {
        return get(pollLastAsync());
    }

//    @Override
    public RFuture<V> popAsync() {
        return pollAsync();
    }

    @Override
    public V pop() {
        return removeFirst();
    }

    @Override
    public void push(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }

    public RFuture<Void> pushAsync(V e) {
        throw new UnsupportedOperationException("use add or put method");
    }
    
//    @Override
    public RFuture<Boolean> removeFirstOccurrenceAsync(Object o) {
        return removeAsync(o, 1);
    }

    @Override
    public boolean removeFirstOccurrence(Object o) {
        return remove(o, 1);
    }

//    @Override
    public RFuture<V> removeFirstAsync() {
        return pollAsync();
    }

//    @Override
    public RFuture<V> removeLastAsync() {
        return pollLastAsync();
    }

    @Override
    public V removeLast() {
        V value = get(removeLastAsync());
        if (value == null) {
            throw new NoSuchElementException();
        }
        return value;
    }

//    @Override
    public RFuture<Boolean> removeLastOccurrenceAsync(Object o) {
        return removeAsync(o, -1);
    }

    @Override
    public boolean removeLastOccurrence(Object o) {
        return remove(o, -1);
    }

    @Override
    public Stream<V> descendingStream() {
        return toStream(descendingIterator());
    }

}
