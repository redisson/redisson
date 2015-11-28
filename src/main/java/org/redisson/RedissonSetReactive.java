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

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.NoSuchElementException;

import org.reactivestreams.Publisher;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.core.RSet;
import org.redisson.core.RSetReactive;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetReactive<V> extends RedissonExpirableReactive implements RSetReactive<V> {

    private static final RedisCommand<Boolean> EVAL_OBJECTS = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 4);

    protected RedissonSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonSetReactive(Codec codec, CommandReactiveExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public Publisher<Long> size() {
        return commandExecutor.readObservable(getName(), codec, RedisCommands.SCARD, getName());
    }

    @Override
    public Publisher<Boolean> contains(Object o) {
        return commandExecutor.readObservable(getName(), codec, RedisCommands.SISMEMBER, getName(), o);
    }

//    private ListScanResult<V> scanIterator(InetSocketAddress client, long startPos) {
//        Publisher<ListScanResult<V>> f = commandExecutor.readObservable(client, getName(), codec, RedisCommands.SSCAN, getName(), startPos);
//        return get(f);
//    }
//
//    @Override
//    public Iterator<V> iterator() {
//        return new Iterator<V>() {
//
//            private List<V> firstValues;
//            private Iterator<V> iter;
//            private InetSocketAddress client;
//            private long nextIterPos;
//
//            private boolean currentElementRemoved;
//            private boolean removeExecuted;
//            private V value;
//
//            @Override
//            public boolean hasNext() {
//                if (iter == null || !iter.hasNext()) {
//                    if (nextIterPos == -1) {
//                        return false;
//                    }
//                    long prevIterPos = nextIterPos;
//                    ListScanResult<V> res = scanIterator(client, nextIterPos);
//                    client = res.getRedisClient();
//                    if (nextIterPos == 0 && firstValues == null) {
//                        firstValues = res.getValues();
//                    } else if (res.getValues().equals(firstValues)) {
//                        return false;
//                    }
//                    iter = res.getValues().iterator();
//                    nextIterPos = res.getPos();
//                    if (prevIterPos == nextIterPos && !removeExecuted) {
//                        nextIterPos = -1;
//                    }
//                }
//                return iter.hasNext();
//            }
//
//            @Override
//            public V next() {
//                if (!hasNext()) {
//                    throw new NoSuchElementException("No such element at index");
//                }
//
//                value = iter.next();
//                currentElementRemoved = false;
//                return value;
//            }
//
//            @Override
//            public void remove() {
//                if (currentElementRemoved) {
//                    throw new IllegalStateException("Element been already deleted");
//                }
//                if (iter == null) {
//                    throw new IllegalStateException();
//                }
//
//                iter.remove();
//                RedissonSetReactive.this.remove(value);
//                currentElementRemoved = true;
//                removeExecuted = true;
//            }
//
//        };
//    }

    @Override
    public Publisher<Long> add(V e) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SADD_SINGLE, getName(), e);
    }

    @Override
    public Publisher<V> removeRandom() {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SPOP_SINGLE, getName());
    }

    @Override
    public Publisher<Boolean> remove(Object o) {
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SREM_SINGLE, getName(), o);
    }

    @Override
    public Publisher<Boolean> containsAll(Collection<?> c) {
        return commandExecutor.evalReadObservable(getName(), codec, EVAL_OBJECTS,
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
    public Publisher<Long> addAll(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeObservable(getName(), codec, RedisCommands.SADD, args.toArray());
    }

    @Override
    public Publisher<Boolean> retainAll(Collection<?> c) {
        return commandExecutor.evalWriteObservable(getName(), codec, EVAL_OBJECTS,
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
    public Publisher<Boolean> removeAll(Collection<?> c) {
        return commandExecutor.evalWriteObservable(getName(), codec, EVAL_OBJECTS,
                        "local v = false " +
                        "for i = 0, table.getn(ARGV), 1 do "
                            + "if redis.call('srem', KEYS[1], ARGV[i]) == 1 "
                            + "then v = true end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public Publisher<V> iterator() {
        // TODO Auto-generated method stub
        return null;
    }

}
