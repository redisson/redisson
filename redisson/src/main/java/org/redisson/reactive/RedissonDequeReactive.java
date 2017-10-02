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
package org.redisson.reactive;

import org.reactivestreams.Publisher;
import org.redisson.api.RDequeReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListFirstObjectDecoder;
import org.redisson.command.CommandReactiveExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Queue}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonDequeReactive<V> extends RedissonQueueReactive<V> implements RDequeReactive<V> {

    private static final RedisCommand<Object> LRANGE_SINGLE = new RedisCommand<Object>("LRANGE", new ListFirstObjectDecoder());

    public RedissonDequeReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonDequeReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Void> addFirst(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.LPUSH_VOID, getName(), encode(e));
    }

    @Override
    public Publisher<Void> addLast(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.RPUSH_VOID, getName(), encode(e));
    }

    @Override
    public Publisher<V> getLast() {
        return commandExecutor.readReactive(getName(), codec, LRANGE_SINGLE, getName(), -1, -1);
    }

    @Override
    public Publisher<Boolean> offerFirst(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.LPUSH_BOOLEAN, getName(), encode(e));
    }

    @Override
    public Publisher<Integer> offerLast(V e) {
        return offer(e);
    }

    @Override
    public Publisher<V> peekFirst() {
        return get(0);
    }

    @Override
    public Publisher<V> peekLast() {
        return getLast();
    }

    @Override
    public Publisher<V> pollFirst() {
        return poll();
    }

    @Override
    public Publisher<V> pollLast() {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.RPOP, getName());
    }

    @Override
    public Publisher<V> pop() {
        return poll();
    }

    @Override
    public Publisher<Void> push(V e) {
        return addFirst(e);
    }

    @Override
    public Publisher<Boolean> removeFirstOccurrence(Object o) {
        return remove(o, 1);
    }

    @Override
    public Publisher<V> removeFirst() {
        return poll();
    }

    @Override
    public Publisher<V> removeLast() {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.RPOP, getName());
    }

    @Override
    public Publisher<Boolean> removeLastOccurrence(Object o) {
        return remove(o, -1);
    }

}
