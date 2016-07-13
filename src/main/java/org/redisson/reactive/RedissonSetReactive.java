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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.reactivestreams.Publisher;
import org.redisson.RedissonSet;
import org.redisson.api.RSetReactive;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandReactiveExecutor;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> extends RedissonExpirableReactive implements RSetReactive<V> {

    private final RedissonSet<V> instance;

    public RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
        instance = new RedissonSet<V>(commandExecutor.getConnectionManager().getCodec(), commandExecutor, name);
    }

    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
        instance = new RedissonSet<V>(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Long> addAll(Publisher<? extends V> c) {
        return new PublisherAdder<V>(this).addAll(c);
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readReactive(getName(), codec, RedisCommands.SCARD, getName());
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return reactive(instance.containsAsync(o));
    }

    private Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long startPos) {
        return commandExecutor.readReactive(client, getName(), codec, RedisCommands.SSCAN, getName(), startPos);
    }

    @Override
    public Publisher<Long> add(V e) {
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, getName(), e);
    }

    @Override
    public Publisher<V> removeRandom() {
        return reactive(instance.removeRandomAsync());
    }

    @Override
    public Publisher<V> random() {
        return reactive(instance.randomAsync());
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        return reactive(instance.removeAsync(o));
    }

    @Override
    public Publisher<Boolean> move(String destination, V member) {
        return reactive(instance.moveAsync(destination, member));
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return reactive(instance.containsAllAsync(c));
    }

    @Override
    public Publisher<Long> addAll(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SADD, args.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return reactive(instance.retainAllAsync(c));
    }

    @Override
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return reactive(instance.removeAllAsync(c));
    }

    @Override
    public Publisher<Set<V>> readIntersection(String... names) {
        return reactive(instance.readIntersectionAsync(names));
    }
    
    @Override
    public Publisher<Long> intersection(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SINTERSTORE, args.toArray());
    }
    
    @Override
    public Publisher<Long> diff(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SDIFFSTORE, args.toArray());
    }
    
    @Override
    public Publisher<Long> union(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeReactive(getName(), codec, RedisCommands.SUNIONSTORE, args.toArray());
    }

    @Override
    public Publisher<Set<V>> readUnion(String... names) {
        return reactive(instance.readUnionAsync(names));
    }

    @Override
    public Publisher<V> iterator() {
        return new SetReactiveIterator<V>() {
            @Override
            protected Publisher<ListScanResult<V>> scanIteratorReactive(InetSocketAddress client, long nextIterPos) {
                return RedissonSetReactive.this.scanIteratorReactive(client, nextIterPos);
            }
        };
    }

}
