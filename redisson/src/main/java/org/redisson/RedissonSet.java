/**
 * Copyright (c) 2013-2021 Nikita Koksharov
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
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.iterator.RedissonBaseIterator;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.RedissonPromise;

import java.util.*;
import java.util.stream.Stream;

/**
 * Distributed and concurrent implementation of {@link java.util.Set}
 *
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonSet<V> extends RedissonExpirable implements RSet<V>, ScanIterator {

    RedissonClient redisson;
    
    public RedissonSet(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name);
        this.redisson = redisson;
    }

    public RedissonSet(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name);
        this.redisson = redisson;
    }

    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return new RedissonCollectionMapReduce<V, KOut, VOut>(this, redisson, commandExecutor);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SCARD_INT, getRawName());
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
        String name = getRawName(o);
        return commandExecutor.readAsync(name, codec, RedisCommands.SISMEMBER, name, encode(o));
    }

    @Override
    public ScanResult<Object> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
        return get(scanIteratorAsync(name, client, startPos, pattern, count));
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
    public Iterator<V> iterator(final String pattern, final int count) {
        return new RedissonBaseIterator<V>() {

            @Override
            protected ScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(getRawName(), client, nextIterPos, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSet.this.remove((V) value);
            }
            
        };
    }
    
    @Override
    public Iterator<V> iterator() {
        return iterator(null);
    }

    @Override
    public RFuture<Set<V>> readAllAsync() {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SMEMBERS, getRawName());
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
    public RFuture<Boolean> addAsync(V e) {
        String name = getRawName(e);
        return commandExecutor.writeAsync(name, codec, RedisCommands.SADD_SINGLE, name, encode(e));
    }

    @Override
    public V removeRandom() {
        return get(removeRandomAsync());
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
        String name = getRawName(o);
        return commandExecutor.writeAsync(name, codec, RedisCommands.SREM_SINGLE, name, encode(o));
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V) value));
    }

    @Override
    public RFuture<Boolean> moveAsync(String destination, V member) {
        String name = getRawName(member);
        return commandExecutor.writeAsync(name, codec, RedisCommands.SMOVE, name, destination, encode(member));
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
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(true);
        }
        
        String tempName = suffixName(getRawName(), "redisson_temp");
        
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                        + "local size = redis.call('sdiff', KEYS[2], KEYS[1]);"
                        + "redis.call('del', KEYS[2]); "
                        + "return #size == 0 and 1 or 0; ",
                       Arrays.<Object>asList(getRawName(), tempName), encode(c).toArray());
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return get(addAllAsync(c));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getRawName());
        encode(args, c);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SADD_BOOL, args.toArray());
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return deleteAsync();
        }
        
        String tempName = suffixName(getRawName(), "redisson_temp");
        
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
               "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                + "local prevSize = redis.call('scard', KEYS[1]); "
                + "local size = redis.call('sinterstore', KEYS[1], KEYS[1], KEYS[2]);"
                + "redis.call('del', KEYS[2]); "
                + "return size ~= prevSize and 1 or 0; ",
            Arrays.<Object>asList(getRawName(), tempName),
            encode(c).toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getRawName());
        encode(args, c);
        
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SREM_SINGLE, args.toArray());
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
    public void clear() {
        delete();
    }

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
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
    
    @Override
    public Set<V> readSort(SortOrder order) {
        return get(readSortAsync(order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), order);
    }

    @Override
    public Set<V> readSort(SortOrder order, int offset, int count) {
        return get(readSortAsync(order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "LIMIT", offset, count, order);
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order) {
        return get(readSortAsync(byPattern, order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "BY", byPattern, order);
    }
    
    @Override
    public Set<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAsync(byPattern, order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "BY", byPattern, "LIMIT", offset, count, order);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return (Collection<T>) get(readSortAsync(byPattern, getPatterns, order));
    }
    
    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAsync(byPattern, getPatterns, order, -1, -1);
    }
    
    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return (Collection<T>) get(readSortAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, getPatterns, order, offset, count, false);
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order) {
        return get(readSortAlphaAsync(order));
    }

    @Override
    public Set<V> readSortAlpha(SortOrder order, int offset, int count) {
        return get(readSortAlphaAsync(order, offset, count));
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order) {
        return get(readSortAlphaAsync(byPattern, order));
    }

    @Override
    public Set<V> readSortAlpha(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAlphaAsync(byPattern, order, offset, count));
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order) {
        return (Collection<T>) get(readSortAlphaAsync(byPattern, getPatterns, order));
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return (Collection<T>) get(readSortAlphaAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "LIMIT", offset, count, "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "BY", byPattern, "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, getRawName(), "BY", byPattern, "LIMIT", offset, count, "ALPHA", order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAlphaAsync(byPattern, getPatterns, order, -1, -1);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return readSortAsync(byPattern, getPatterns, order, offset, count, true);
    }

    @Override
    public int sortTo(String destName, SortOrder order) {
        return get(sortToAsync(destName, order));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order) {
        return sortToAsync(destName, null, Collections.<String>emptyList(), order, -1, -1);
    }
    
    @Override
    public int sortTo(String destName, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, order, offset, count));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order, int offset, int count) {
        return sortToAsync(destName, null, Collections.<String>emptyList(), order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, order, offset, count));
    }
    
    @Override
    public int sortTo(String destName, String byPattern, SortOrder order) {
        return get(sortToAsync(destName, byPattern, order));
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order) {
        return sortToAsync(destName, byPattern, Collections.<String>emptyList(), order, -1, -1);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order, int offset, int count) {
        return sortToAsync(destName, byPattern, Collections.<String>emptyList(), order, offset, count);
    }

    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return get(sortToAsync(destName, byPattern, getPatterns, order));
    }
    
    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return sortToAsync(destName, byPattern, getPatterns, order, -1, -1);
    }
    
    @Override
    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return get(sortToAsync(destName, byPattern, getPatterns, order, offset, count));
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        if (byPattern != null) {
            params.add("BY");
            params.add(byPattern);
        }
        if (offset != -1 && count != -1) {
            params.add("LIMIT");
        }
        if (offset != -1) {
            params.add(offset);
        }
        if (count != -1) {
            params.add(count);
        }
        for (String pattern : getPatterns) {
            params.add("GET");
            params.add(pattern);
        }
        params.add(order);
        params.add("STORE");
        params.add(destName);
        
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.SORT_TO, params.toArray());
    }

    @Override
    public boolean tryAdd(V... values) {
        return get(tryAddAsync(values));
    }

    @Override
    public RFuture<Boolean> tryAddAsync(V... values) {
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                  "for i, v in ipairs(ARGV) do " +
                            "if redis.call('sismember', KEYS[1], v) == 1 then " +
                                "return 0; " +
                            "end; " +
                        "end; " +

                        "for i=1, #ARGV, 5000 do " +
                            "redis.call('sadd', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); " +
                        "end; " +
                        "return 1; ",
                       Arrays.asList(getRawName()), encode(values).toArray());
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(V value) {
        String lockName = getLockByValue(value, "permitexpirablesemaphore");
        return new RedissonPermitExpirableSemaphore(commandExecutor, lockName);
    }

    @Override
    public RSemaphore getSemaphore(V value) {
        String lockName = getLockByValue(value, "semaphore");
        return new RedissonSemaphore(commandExecutor, lockName);
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(V value) {
        String lockName = getLockByValue(value, "countdownlatch");
        return new RedissonCountDownLatch(commandExecutor, lockName);
    }
    
    @Override
    public RLock getFairLock(V value) {
        String lockName = getLockByValue(value, "fairlock");
        return new RedissonFairLock(commandExecutor, lockName);
    }
    
    @Override
    public RLock getLock(V value) {
        String lockName = getLockByValue(value, "lock");
        return new RedissonLock(commandExecutor, lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(V value) {
        String lockName = getLockByValue(value, "rw_lock");
        return new RedissonReadWriteLock(commandExecutor, lockName);
    }

    @Override
    public RFuture<ScanResult<Object>> scanIteratorAsync(String name, RedisClient client, long startPos,
            String pattern, int count) {
        if (pattern == null) {
            return commandExecutor.readAsync(client, name, codec, RedisCommands.SSCAN, name, startPos, "COUNT", count);
        }

        return commandExecutor.readAsync(client, name, codec, RedisCommands.SSCAN, name, startPos, "MATCH", pattern, "COUNT", count);
    }

    private <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count, boolean alpha) {
        List<Object> params = new ArrayList<Object>();
        params.add(getRawName());
        if (byPattern != null) {
            params.add("BY");
            params.add(byPattern);
        }
        if (offset != -1 && count != -1) {
            params.add("LIMIT");
        }
        if (offset != -1) {
            params.add(offset);
        }
        if (count != -1) {
            params.add(count);
        }
        if (getPatterns != null) {
            for (String pattern : getPatterns) {
                params.add("GET");
                params.add(pattern);
            }
        }
        if (alpha) {
            params.add("ALPHA");
        }
        if (order != null) {
            params.add(order);
        }

        return commandExecutor.readAsync(getRawName(), codec, RedisCommands.SORT_SET, params.toArray());
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
