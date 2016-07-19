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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSet;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSet<V> extends RedissonExpirable implements RSet<V> {

    protected RedissonSet(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonSet(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SCARD_INT, getName());
    }

    @Override
    public boolean isEmpty() {
        return size() == 0;
    }

    @Override
    public boolean contains(Object o) {
        return get(containsAsync(o));
    }

    @Override
    public Future<Boolean> containsAsync(Object o) {
        return commandExecutor.readAsync(getName(o), codec, RedisCommands.SISMEMBER, getName(o), o);
    }

    protected String getName(Object o) {
        return getName();
    }

    ListScanResult<V> scanIterator(String name, InetSocketAddress client, long startPos) {
        Future<ListScanResult<V>> f = commandExecutor.readAsync(client, name, codec, RedisCommands.SSCAN, name, startPos);
        return get(f);
    }

    @Override
    public Iterator<V> iterator() {
        return new RedissonBaseIterator<V>() {

            @Override
            ListScanResult<V> iterator(InetSocketAddress client, long nextIterPos) {
                return scanIterator(getName(), client, nextIterPos);
            }

            @Override
            void remove(V value) {
                RedissonSet.this.remove(value);
            }
            
        };
    }

    @Override
    public Future<Set<V>> readAllAsync() {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SMEMBERS, getName());
    }

    @Override
    public Set<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public Object[] toArray() {
        Set<Object> res = (Set<Object>) get(readAllAsync());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        Set<Object> res = (Set<Object>) get(readAllAsync());
        return res.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return get(addAsync(e));
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        return commandExecutor.writeAsync(getName(e), codec, RedisCommands.SADD_SINGLE, getName(e), e);
    }

    @Override
    public V removeRandom() {
        return get(removeRandomAsync());
    }

    @Override
    public Future<V> removeRandomAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SPOP_SINGLE, getName());
    }

    @Override
    public V random() {
        return get(randomAsync());
    }

    @Override
    public Future<V> randomAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SRANDMEMBER_SINGLE, getName());
    }

    @Override
    public Future<Boolean> removeAsync(Object o) {
        return commandExecutor.writeAsync(getName(o), codec, RedisCommands.SREM_SINGLE, getName(o), o);
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V)value));
    }

    @Override
    public Future<Boolean> moveAsync(String destination, V member) {
        return commandExecutor.writeAsync(getName(member), codec, RedisCommands.SMOVE, getName(member), destination, member);
    }

    @Override
    public boolean move(String destination, V member) {
        return get(moveAsync(destination, member));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public Future<Boolean> containsAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(true);
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5, ValueType.OBJECTS),
                        "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                        + "local size = redis.call('sdiff', KEYS[2], KEYS[1]);"
                        + "redis.call('del', KEYS[2]); "
                        + "return #size == 0 and 1 or 0; ",
                       Arrays.<Object>asList(getName(), "redisson_temp__{" + getName() + "}"), c.toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return get(addAllAsync(c));
    }

    @Override
    public Future<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }
        
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SADD_BOOL, args.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5, ValueType.OBJECTS),
               "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                + "local prevSize = redis.call('scard', KEYS[1]); "
                + "local size = redis.call('sinterstore', KEYS[1], KEYS[1], KEYS[2]);"
                + "redis.call('del', KEYS[2]); "
                + "return size ~= prevSize and 1 or 0; ",
            Arrays.<Object>asList(getName(), "redisson_temp__{" + getName() + "}"), c.toArray());
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }
        
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SREM_SINGLE, args.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public int union(String... names) {
        return get(unionAsync(names));
    }

    @Override
    public Future<Integer> unionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SUNIONSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readUnion(String... names) {
        return get(readUnionAsync(names));
    }

    @Override
    public Future<Set<V>> readUnionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SUNION, args.toArray());
    }

    @Override
    public int diff(String... names) {
        return get(diffAsync(names));
    }

    @Override
    public Future<Integer> diffAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SDIFFSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readDiff(String... names) {
        return get(readDiffAsync(names));
    }

    @Override
    public Future<Set<V>> readDiffAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SDIFF, args.toArray());
    }

    @Override
    public int intersection(String... names) {
        return get(intersectionAsync(names));
    }

    @Override
    public Future<Integer> intersectionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SINTERSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readIntersection(String... names) {
        return get(readIntersectionAsync(names));
    }

    @Override
    public Future<Set<V>> readIntersectionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SINTER, args.toArray());
    }
    
    @Override
    public void clear() {
        delete();
    }

    @Override
    public String toString() {
        Iterator<V> it = iterator();
        if (! it.hasNext())
            return "[]";

        StringBuilder sb = new StringBuilder();
        sb.append('[');
        for (;;) {
            V e = it.next();
            sb.append(e == this ? "(this Collection)" : e);
            if (! it.hasNext())
                return sb.append(']').toString();
            sb.append(',').append(' ');
        }
    }
    
}
