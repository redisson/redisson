/**
 * Copyright 2014 Nikita Koksharov, Nickolay Borbit
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
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
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

    protected RedissonSet(CommandExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), RedisCommands.SCARD, getName());
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
        return commandExecutor.readAsync(getName(), RedisCommands.SISMEMBER, getName(), o);
    }

    private ListScanResult<V> scanIterator(long startPos) {
        return commandExecutor.read(getName(), RedisCommands.SSCAN, getName(), startPos);
    }

    @Override
    public Iterator<V> iterator() {
        return new Iterator<V>() {

            private Iterator<V> iter;
            private Long iterPos;

            private boolean removeExecuted;
            private V value;

            @Override
            public boolean hasNext() {
                if (iter == null) {
                    ListScanResult<V> res = scanIterator(0);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                } else if (!iter.hasNext() && iterPos != 0) {
                    ListScanResult<V> res = scanIterator(iterPos);
                    iter = res.getValues().iterator();
                    iterPos = res.getPos();
                }
                return iter.hasNext();
            }

            @Override
            public V next() {
                if (!hasNext()) {
                    throw new NoSuchElementException("No such element at index");
                }

                value = iter.next();
                removeExecuted = false;
                return value;
            }

            @Override
            public void remove() {
                if (removeExecuted) {
                    throw new IllegalStateException("Element been already deleted");
                }

                // lazy init iterator
//                hasNext();
                iter.remove();
                RedissonSet.this.remove(value);
                removeExecuted = true;
            }

        };
    }

    @Override
    public Future<Collection<V>> readAllAsync() {
        return commandExecutor.readAsync(getName(), RedisCommands.SMEMBERS, getName());
    }

    @Override
    public Object[] toArray() {
        List<Object> res = (List<Object>) get(readAllAsync());
        return res.toArray();
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<Object> res = (List<Object>) get(readAllAsync());
        return res.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return get(addAsync(e));
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        return commandExecutor.writeAsync(getName(), RedisCommands.SADD_SINGLE, getName(), e);
    }

    @Override
    public V removeRandom() {
        return get(removeRandomAsync());
    }

    @Override
    public Future<V> removeRandomAsync() {
        return commandExecutor.writeAsync(getName(), RedisCommands.SPOP_SINGLE, getName());
    }

    @Override
    public Future<Boolean> removeAsync(Object o) {
        return commandExecutor.writeAsync(getName(), RedisCommands.SREM_SINGLE, getName(), o);
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V)value));
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public Future<Boolean> containsAllAsync(Collection<?> c) {
        return commandExecutor.evalReadAsync(getName(), new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                "local s = redis.call('smembers', KEYS[1]);" +
                        "for i = 0, table.getn(s), 1 do " +
                            "for j = 0, table.getn(ARGV), 1 do "
                            + "if ARGV[j] == s[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return table.getn(ARGV) == 0; ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return false;
        }

        return get(addAllAsync(c));
    }

    @Override
    public Future<Boolean> addAllAsync(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeAsync(getName(), RedisCommands.SADD, args.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                    "local changed = false " +
                    "local s = redis.call('smembers', KEYS[1]) "
                       + "local i = 0 "
                       + "while i <= table.getn(s) do "
                            + "local element = s[i] "
                            + "local isInAgrs = false "
                            + "for j = 0, table.getn(ARGV), 1 do "
                                + "if ARGV[j] == element then "
                                    + "isInAgrs = true "
                                    + "break "
                                + "end "
                            + "end "
                            + "if isInAgrs == false then "
                                + "redis.call('SREM', KEYS[1], element) "
                                + "changed = true "
                            + "end "
                            + "i = i + 1 "
                       + "end "
                       + "return changed ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4),
                        "local v = false " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('srem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = true end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public void clear() {
        delete();
    }

}
