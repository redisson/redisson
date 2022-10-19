/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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

import org.redisson.api.*;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.*;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.iterator.RedissonBaseIterator;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Set based Multimap Cache values holder
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSetMultimapValues<V> extends RedissonExpirable implements RSet<V> {

    private static final RedisCommand<ListScanResult<Object>> EVAL_SSCAN = new RedisCommand<ListScanResult<Object>>("EVAL", 
                new ListMultiDecoder2(new ListScanResultReplayDecoder(), new MapValueDecoder(new ObjectListReplayDecoder())));
    
    private final RSet<V> set;
    private final Object key;
    private final String timeoutSetName;
    
    public RedissonSetMultimapValues(Codec codec, CommandAsyncExecutor commandExecutor, String name, String timeoutSetName, Object key) {
        super(codec, commandExecutor, name);
        this.timeoutSetName = timeoutSetName;
        this.key = key;
        this.set = new RedissonSet<V>(codec, commandExecutor, name, null);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }
    
    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return null;
    }
    
    @Override
    public boolean tryAdd(V... values) {
        return get(tryAddAsync(values));
    }

    @Override
    public RFuture<Boolean> tryAddAsync(V... values) {
        return set.tryAddAsync(values);
    }

    @Override
    public RFuture<Boolean> clearExpireAsync() {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }

    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit, String param, String... keys) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }
    
    @Override
    protected RFuture<Boolean> expireAtAsync(long timestamp, String param, String... keys) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }
    
    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }
    
    @Override
    public RFuture<Void> renameAsync(String newName) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }
    
    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }
    
    @Override
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; " +
                "local res = redis.call('zrem', KEYS[1], ARGV[2]); " +
                "if res > 0 then " +
                    "redis.call('del', KEYS[2]); " +
                "end; " +
                "return res; ",
                Arrays.<Object>asList(timeoutSetName, getRawName()),
                System.currentTimeMillis(), encodeMapKey(key));
    }

    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getRawName(), timeoutSetName);
        return super.sizeInMemoryAsync(keys);
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                      "local expireDate = 92233720368547758; " +
                      "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
                    + "if expireDateScore ~= false then "
                        + "expireDate = tonumber(expireDateScore) "
                    + "end; "
                    + "if expireDate <= tonumber(ARGV[1]) then "
                        + "return 0;"
                    + "end; "
                    + "return redis.call('scard', KEYS[2]);",
               Arrays.<Object>asList(timeoutSetName, getRawName()),
               System.currentTimeMillis(), encodeMapKey(key));
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
    public RFuture<Boolean> containsAsync(Object o) {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('sismember', KEYS[2], ARGV[3]);",
         Arrays.<Object>asList(timeoutSetName, getRawName()),
         System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(o));
    }

    private ListScanResult<Object> scanIterator(RedisClient client, long startPos, String pattern, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(System.currentTimeMillis());
        params.add(startPos);
        params.add(encodeMapKey(key));
        if (pattern != null) {
            params.add(pattern);
        }
        params.add(count);
        
        RFuture<ListScanResult<Object>> f = commandExecutor.evalReadAsync(client, getRawName(), codec, EVAL_SSCAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {0, {}};"
              + "end;"

              + "local res; "
              + "if (#ARGV == 5) then "
                  + "res = redis.call('sscan', KEYS[2], ARGV[2], 'match', ARGV[4], 'count', ARGV[5]); "
              + "else "
                  + "res = redis.call('sscan', KEYS[2], ARGV[2], 'count', ARGV[4]); "
              + "end;"

              + "return res;", 
              Arrays.<Object>asList(timeoutSetName, getRawName()),
              params.toArray());
      return get(f);
    }

    @Override
    public Iterator<V> iterator(int count) {
        return iterator(null, count);
    }
    
    @Override
    public Iterator<V> iterator(String pattern) {
        return iterator(pattern, 10);
    }

    @Override
    public Iterator<V> distributedIterator(final String pattern) {
        String iteratorName = "__redisson_set_cursor_{" + getRawName() + "}";
        return distributedIterator(iteratorName, pattern, 10);
    }

    @Override
    public Iterator<V> distributedIterator(final int count) {
        String iteratorName = "__redisson_set_cursor_{" + getRawName() + "}";
        return distributedIterator(iteratorName, null, count);
    }

    @Override
    public Iterator<V> distributedIterator(final String iteratorName, final String pattern, final int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return distributedScanIterator(iteratorName, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSetMultimapValues.this.remove((V) value);
            }
        };
    }

    private ScanResult<Object> distributedScanIterator(String iteratorName, String pattern, int count) {
        return get(distributedScanIteratorAsync(iteratorName, pattern, count));
    }

    private RFuture<ScanResult<Object>> distributedScanIteratorAsync(String iteratorName, String pattern, int count) {
        List<Object> args = new ArrayList<>(3);
        args.add(System.currentTimeMillis());
        if (pattern != null) {
            args.add(pattern);
        }
        args.add(count);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_SSCAN,
                "local cursor = redis.call('get', KEYS[3]); "
                + "if cursor ~= false then "
                    + "cursor = tonumber(cursor); "
                + "else"
                    + " cursor = 0;"
                + "end;"
                + "if cursor == -1 then "
                    + "return {0, {}}; "
                + "end;"
                + "local result; "
                + "if (#ARGV == 3) then "
                    + "result = redis.call('sscan', KEYS[2], cursor, 'match', ARGV[2], 'count', ARGV[3]); "
                + "else"
                    + "result = redis.call('sscan', KEYS[2], cursor, 'count', ARGV[2]); "
                + "end;"
                + "local next_cursor = result[1]"
                + "if next_cursor ~= \"0\" then "
                    + "redis.call('setex', KEYS[3], 3600, next_cursor);"
                + "else "
                    + "redis.call('setex', KEYS[3], 3600, -1);"
                + "end; "

                + "local expireDate = 92233720368547758; "
                + "local expirations = redis.call('zmscore', KEYS[1], result[2])"
                + "for i = #expirations, 1, -1 do "
                    + "if expirations[i] ~= false then "
                        + "local expireDate = tonumber(expireDateScore) "
                        + "if expireDate <= tonumber(ARGV[1]) then "
                        +   "table.remove(result[2], i);"
                        + "end; "
                    + "end; "
                + "end; "
                + "return result;",
                Arrays.<Object>asList(timeoutSetName, getRawName(), iteratorName), args.toArray());
    }

    @Override
    public Iterator<V> iterator(final String pattern, final int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ListScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(client, nextIterPos, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSetMultimapValues.this.remove((V) value);
            }
            
        };
    }
    
    @Override
    public Iterator<V> iterator() {
        return iterator(null);
    }

    @Override
    public RFuture<Set<V>> readAllAsync() {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_MAP_VALUE_SET,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {};"
              + "end; "
              + "return redis.call('smembers', KEYS[2]);",
              Arrays.<Object>asList(timeoutSetName, getRawName()),
              System.currentTimeMillis(), encodeMapKey(key));
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
        return set.add(e);
    }

    @Override
    public RFuture<Boolean> addAsync(V e) {
        return set.addAsync(e);
    }

    @Override
    public V removeRandom() {
        return set.removeRandom();
    }

    @Override
    public RFuture<V> removeRandomAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SPOP_SINGLE, getRawName());
    }

    @Override
    public Set<V> removeRandom(int amount) {
        return get(removeRandomAsync(amount));
    }

    @Override
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SPOP, getRawName(), amount);
    }
    
    @Override
    public V random() {
        return get(randomAsync());
    }

    @Override
    public RFuture<V> randomAsync() {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SRANDMEMBER_SINGLE, getRawName());
    }

    @Override
    public Set<V> random(int count) {
        return get(randomAsync(count));
    }

    @Override
    public RFuture<Set<V>> randomAsync(int count) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SRANDMEMBER, getRawName(), count);
    }
    
    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('srem', KEYS[2], ARGV[3]) > 0 and 1 or 0;",
         Arrays.<Object>asList(timeoutSetName, getRawName()),
         System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(o));
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V) value));
    }

    @Override
    public RFuture<Boolean> moveAsync(String destination, V member) {
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SMOVE, getRawName(), destination, encode(member));
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
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);
        
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
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
                   Arrays.<Object>asList(timeoutSetName, getRawName()), args.toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return false;
        }

        return get(addAllAsync(c));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getRawName());
        encodeMapValues(args, c);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SADD_BOOL, args.toArray());
    }

    @Override
    public RFuture<Integer> addAllCountedAsync(Collection<? extends V> c) {
        List<Object> args = new ArrayList<>(c.size() + 1);
        args.add(getRawName());
        encodeMapValues(args, c);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SADD, args.toArray());
    }

    @Override
    public int addAllCounted(Collection<? extends V> c) {
        return get(addAllCountedAsync(c));
    }

    @Override
    public int removeAllCounted(Collection<? extends V> c) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }

    @Override
    public RFuture<Integer> removeAllCountedAsync(Collection<? extends V> c) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values");
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
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
                       Arrays.<Object>asList(timeoutSetName, getRawName()), args.toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);
        
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
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
               Arrays.<Object>asList(timeoutSetName, getRawName()), args.toArray());
    }

    @Override
    public RCountDownLatch getCountDownLatch(V value) {
        return set.getCountDownLatch(value);
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(V value) {
        return set.getPermitExpirableSemaphore(value);
    }

    @Override
    public RSemaphore getSemaphore(V value) {
        return set.getSemaphore(value);
    }

    @Override
    public RLock getFairLock(V value) {
        return set.getFairLock(value);
    }

    @Override
    public RReadWriteLock getReadWriteLock(V value) {
        return set.getReadWriteLock(value);
    }

    @Override
    public RLock getLock(V value) {
        return set.getLock(value);
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
    public RFuture<Integer> unionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SUNIONSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readUnion(String... names) {
        return get(readUnionAsync(names));
    }

    @Override
    public RFuture<Set<V>> readUnionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SUNION, args.toArray());
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
    public RFuture<Integer> diffAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SDIFFSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readDiff(String... names) {
        return get(readDiffAsync(names));
    }

    @Override
    public RFuture<Set<V>> readDiffAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SDIFF, args.toArray());
    }

    @Override
    public int intersection(String... names) {
        return get(intersectionAsync(names));
    }

    @Override
    public RFuture<Integer> intersectionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SINTERSTORE_INT, args.toArray());
    }

    @Override
    public Set<V> readIntersection(String... names) {
        return get(readIntersectionAsync(names));
    }

    @Override
    public RFuture<Set<V>> readIntersectionAsync(String... names) {
        List<Object> args = new ArrayList<Object>(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SINTER, args.toArray());
    }

    @Override
    public Integer countIntersection(String... names) {
        return get(countIntersectionAsync(names));
    }

    @Override
    public RFuture<Integer> countIntersectionAsync(String... names) {
        return countIntersectionAsync(0, names);
    }

    @Override
    public Integer countIntersection(int limit, String... names) {
        return get(countIntersectionAsync(limit, names));
    }

    @Override
    public RFuture<Integer> countIntersectionAsync(int limit, String... names) {
        List<Object> args = new ArrayList<>(names.length + 1);
        args.add(names.length + 1);
        args.add(getRawName());
        args.addAll(Arrays.asList(names));
        if (limit > 0) {
            args.add("LIMIT");
            args.add(limit);
        }
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SINTERCARD_INT, args.toArray());
    }

    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        return set.readSortAsync(order);
    }

    @Override
    public Set<V> readSort(SortOrder order) {
        return set.readSort(order);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        return set.readSortAsync(order, offset, count);
    }

    @Override
    public Set<V> readSort(SortOrder order, int offset, int count) {
        return set.readSort(order, offset, count);
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order) {
        return set.readSort(byPattern, order);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        return set.readSortAsync(byPattern, order);
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return set.readSort(byPattern, order, offset, count);
    }

    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return set.readSortAsync(byPattern, order, offset, count);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return set.readSort(byPattern, getPatterns, order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return set.readSortAsync(byPattern, getPatterns, order);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset,
            int count) {
        return set.readSort(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order,
            int offset, int count) {
        return set.readSortAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order) {
        return set.readSortAlpha(order);
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order, int offset, int count) {
        return set.readSortAlpha(order, offset, count);
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order) {
        return set.readSortAlpha(byPattern, order);
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order, int offset, int count) {
        return set.readSortAlpha(byPattern, order, offset, count);
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order) {
        return set.readSortAlpha(byPattern, getPatterns, order);
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return set.readSortAlpha(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        return set.readSortAlphaAsync(order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return set.readSortAlphaAsync(order, offset, count);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return set.readSortAlphaAsync(byPattern, order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return set.readSortAlphaAsync(byPattern, order, offset, count);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return set.readSortAlphaAsync(byPattern, getPatterns, order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return set.readSortAlphaAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public int sortTo(String destName, SortOrder order) {
        return set.sortTo(destName, order);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order) {
        return set.sortToAsync(destName, order);
    }

    @Override
    public int sortTo(String destName, SortOrder order, int offset, int count) {
        return set.sortTo(destName, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order, int offset, int count) {
        return set.sortToAsync(destName, order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order) {
        return set.sortTo(destName, byPattern, order);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order) {
        return set.sortToAsync(destName, byPattern, order);
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order, int offset, int count) {
        return set.sortTo(destName, byPattern, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order, int offset, int count) {
        return set.sortToAsync(destName, byPattern, order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return set.sortTo(destName, byPattern, getPatterns, order);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return set.sortToAsync(destName, byPattern, getPatterns, order);
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset,
            int count) {
        return set.sortTo(destName, byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order,
            int offset, int count) {
        return set.sortToAsync(destName, byPattern, getPatterns, order, offset, count);
    }

    @Override
    public Stream<V> stream(int count) {
        return toStream(iterator(count));
    }

    @Override
    public Stream<V> stream(String pattern, int count) {
        return toStream(iterator(pattern, count));
    }

    @Override
    public Stream<V> stream(String pattern) {
        return toStream(iterator(pattern));
    }
    
}
