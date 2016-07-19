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

import java.io.IOException;
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
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.client.protocol.decoder.ListScanResultReplayDecoder;
import org.redisson.client.protocol.decoder.NestedMultiDecoder;
import org.redisson.client.protocol.decoder.ObjectListReplayDecoder;
import org.redisson.client.protocol.decoder.ObjectSetReplayDecoder;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RSet;

import io.netty.util.concurrent.Future;

/**
 * Set based Multimap Cache values holder
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetMultimapValues<V> extends RedissonExpirable implements RSet<V> {

    private static final RedisCommand<ListScanResult<Object>> EVAL_SSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", new NestedMultiDecoder(new ObjectListReplayDecoder<Object>(), new ListScanResultReplayDecoder()), 7, ValueType.MAP_KEY, ValueType.OBJECT);
    private static final RedisCommand<Integer> EVAL_SIZE = new RedisCommand<Integer>("EVAL", new IntegerReplayConvertor(), 6, ValueType.MAP_KEY);
    private static final RedisCommand<Set<Object>> EVAL_READALL = new RedisCommand<Set<Object>>("EVAL", new ObjectSetReplayDecoder<Object>(), 6, ValueType.MAP_KEY);
    private static final RedisCommand<Boolean> EVAL_CONTAINS_VALUE = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 6, Arrays.asList(ValueType.MAP_KEY, ValueType.MAP_VALUE));
    private static final RedisCommand<Boolean> EVAL_CONTAINS_ALL_WITH_VALUES = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 7, ValueType.OBJECTS);
    
    private final Object key;
    private final String timeoutSetName;
    
    public RedissonSetMultimapValues(Codec codec, CommandAsyncExecutor commandExecutor, String name, String timeoutSetName, Object key) {
        super(codec, commandExecutor, name);
        this.timeoutSetName = timeoutSetName;
        this.key = key;
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public Future<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_SIZE,
                      "local expireDate = 92233720368547758; " +
                      "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
                    + "if expireDateScore ~= false then "
                        + "expireDate = tonumber(expireDateScore) "
                    + "end; "
                    + "if expireDate <= tonumber(ARGV[1]) then "
                        + "return 0;"
                    + "end; "
                    + "return redis.call('scard', KEYS[2]);",
               Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), key);
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
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_CONTAINS_VALUE,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('sismember', KEYS[2], ARGV[3]);",
         Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), key, o);
    }

    private ListScanResult<V> scanIterator(InetSocketAddress client, long startPos) {
        Future<ListScanResult<V>> f = commandExecutor.evalReadAsync(client, getName(), codec, EVAL_SSCAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {0, {}};"
              + "end;"

              + "return redis.call('sscan', KEYS[2], ARGV[2]);", 
              Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), startPos, key);
      return get(f);
    }

    @Override
    public Iterator<V> iterator() {
        return new RedissonBaseIterator<V>() {

            @Override
            ListScanResult<V> iterator(InetSocketAddress client, long nextIterPos) {
                return scanIterator(client, nextIterPos);
            }

            @Override
            void remove(V value) {
                RedissonSetMultimapValues.this.remove(value);
            }
            
        };
    }

    @Override
    public Future<Set<V>> readAllAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_READALL,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {};"
              + "end; "
              + "return redis.call('smembers', KEYS[2]);",
              Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), key);
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
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SADD_SINGLE, getName(), e);
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
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS_VALUE,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('srem', KEYS[2], ARGV[3]) > 0 and 1 or 0;",
         Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), key, o);
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V)value));
    }

    @Override
    public Future<Boolean> moveAsync(String destination, V member) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SMOVE, getName(), destination, member);
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
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            args.add(System.currentTimeMillis());
            args.add(keyState);
            args.addAll(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return commandExecutor.evalReadAsync(getName(), codec, EVAL_CONTAINS_ALL_WITH_VALUES,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; " +
                "local s = redis.call('smembers', KEYS[2]);" +
                        "for i = 1, #s, 1 do " +
                            "for j = 2, #ARGV, 1 do "
                            + "if ARGV[j] == s[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return #ARGV == 2 and 1 or 0; ",
                   Arrays.<Object>asList(timeoutSetName, getName()), args.toArray());
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
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SADD_BOOL, args.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            args.add(System.currentTimeMillis());
            args.add(keyState);
            args.addAll(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS_ALL_WITH_VALUES,
                    "local expireDate = 92233720368547758; " +
                    "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
                  + "if expireDateScore ~= false then "
                      + "expireDate = tonumber(expireDateScore) "
                  + "end; "
                  + "if expireDate <= tonumber(ARGV[1]) then "
                      + "return 0;"
                  + "end; " +

                    "local changed = 0 " +
                    "local s = redis.call('smembers', KEYS[2]) "
                       + "local i = 1 "
                       + "while i <= #s do "
                            + "local element = s[i] "
                            + "local isInAgrs = false "
                            + "for j = 2, #ARGV, 1 do "
                                + "if ARGV[j] == element then "
                                    + "isInAgrs = true "
                                    + "break "
                                + "end "
                            + "end "
                            + "if isInAgrs == false then "
                                + "redis.call('SREM', KEYS[2], element) "
                                + "changed = 1 "
                            + "end "
                            + "i = i + 1 "
                       + "end "
                       + "return changed ",
                       Arrays.<Object>asList(timeoutSetName, getName()), args.toArray());
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        try {
            byte[] keyState = codec.getMapKeyEncoder().encode(key);
            args.add(System.currentTimeMillis());
            args.add(keyState);
            args.addAll(c);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_CONTAINS_ALL_WITH_VALUES,
                        "local expireDate = 92233720368547758; " +
                        "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
                      + "if expireDateScore ~= false then "
                          + "expireDate = tonumber(expireDateScore) "
                      + "end; "
                      + "if expireDate <= tonumber(ARGV[1]) then "
                          + "return 0;"
                      + "end; " +
                
                        "local v = 0 " +
                        "for i = 2, #ARGV, 1 do "
                            + "if redis.call('srem', KEYS[2], ARGV[i]) == 1 "
                            + "then v = 1 end "
                        +"end "
                       + "return v ",
               Arrays.<Object>asList(timeoutSetName, getName()), args.toArray());
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
    public void clear() {
        delete();
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

}
