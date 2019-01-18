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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.redisson.api.RCountDownLatch;
import org.redisson.api.RFuture;
import org.redisson.api.RLock;
import org.redisson.api.RPermitExpirableSemaphore;
import org.redisson.api.RReadWriteLock;
import org.redisson.api.RSemaphore;
import org.redisson.api.RSet;
import org.redisson.api.RedissonClient;
import org.redisson.api.SortOrder;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.RedisClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.decoder.ListScanResult;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.mapreduce.RedissonCollectionMapReduce;
import org.redisson.misc.Hash;
import org.redisson.misc.RedissonPromise;

import io.netty.buffer.ByteBuf;

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
        return new RedissonCollectionMapReduce<V, KOut, VOut>(this, redisson, commandExecutor.getConnectionManager());
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    @Override
    public RFuture<Integer> sizeAsync() {
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
    public RFuture<Boolean> containsAsync(Object o) {
        return commandExecutor.readAsync(getName(o), codec, RedisCommands.SISMEMBER, getName(o), encode(o));
    }

    @Override
    public ListScanResult<Object> scanIterator(String name, RedisClient client, long startPos, String pattern, int count) {
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
            protected ListScanResult<Object> iterator(RedisClient client, long nextIterPos) {
                return scanIterator(getName(), client, nextIterPos, pattern, count);
            }

            @Override
            protected void remove(Object value) {
                RedissonSet.this.remove((V)value);
            }
            
        };
    }
    
    @Override
    public Iterator<V> iterator() {
        return iterator(null);
    }

    @Override
    public RFuture<Set<V>> readAllAsync() {
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
    public RFuture<Boolean> addAsync(V e) {
        return commandExecutor.writeAsync(getName(e), codec, RedisCommands.SADD_SINGLE, getName(e), encode(e));
    }

    @Override
    public V removeRandom() {
        return get(removeRandomAsync());
    }

    @Override
    public RFuture<V> removeRandomAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SPOP_SINGLE, getName());
    }

    @Override
    public Set<V> removeRandom(int amount) {
        return get(removeRandomAsync(amount));
    }

    @Override
    public RFuture<Set<V>> removeRandomAsync(int amount) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SPOP, getName(), amount);
    }
    
    @Override
    public V random() {
        return get(randomAsync());
    }

    @Override
    public RFuture<V> randomAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SRANDMEMBER_SINGLE, getName());
    }

    @Override
    public Set<V> random(int count) {
        return get(randomAsync(count));
    }

    @Override
    public RFuture<Set<V>> randomAsync(int count) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SRANDMEMBER, getName(), count);
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        return commandExecutor.writeAsync(getName(o), codec, RedisCommands.SREM_SINGLE, getName(o), encode(o));
    }

    @Override
    public boolean remove(Object value) {
        return get(removeAsync((V)value));
    }

    @Override
    public RFuture<Boolean> moveAsync(String destination, V member) {
        return commandExecutor.writeAsync(getName(member), codec, RedisCommands.SMOVE, getName(member), destination, encode(member));
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
        
        String tempName = suffixName(getName(), "redisson_temp");
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                        "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                        + "local size = redis.call('sdiff', KEYS[2], KEYS[1]);"
                        + "redis.call('del', KEYS[2]); "
                        + "return #size == 0 and 1 or 0; ",
                       Arrays.<Object>asList(getName(), tempName), encode(c).toArray());
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
        args.add(getName());
        encode(args, c);
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SADD_BOOL, args.toArray());
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
        
        String tempName = suffixName(getName(), "redisson_temp");
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
               "redis.call('sadd', KEYS[2], unpack(ARGV)); "
                + "local prevSize = redis.call('scard', KEYS[1]); "
                + "local size = redis.call('sinterstore', KEYS[1], KEYS[1], KEYS[2]);"
                + "redis.call('del', KEYS[2]); "
                + "return size ~= prevSize and 1 or 0; ",
            Arrays.<Object>asList(getName(), tempName), 
            encode(c).toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        encode(args, c);
        
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
    public RFuture<Integer> unionAsync(String... names) {
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
    public RFuture<Set<V>> readUnionAsync(String... names) {
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
    public RFuture<Integer> diffAsync(String... names) {
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
    public RFuture<Set<V>> readDiffAsync(String... names) {
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
    public RFuture<Integer> intersectionAsync(String... names) {
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
    public RFuture<Set<V>> readIntersectionAsync(String... names) {
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
    
    @Override
    public Set<V> readSort(SortOrder order) {
        return get(readSortAsync(order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), order);
    }

    @Override
    public Set<V> readSort(SortOrder order, int offset, int count) {
        return get(readSortAsync(order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "LIMIT", offset, count, order);
    }

    @Override
    public Set<V> readSort(String byPattern, SortOrder order) {
        return get(readSortAsync(byPattern, order));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, order);
    }
    
    @Override
    public Set<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return get(readSortAsync(byPattern, order, offset, count));
    }
    
    @Override
    public RFuture<Set<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, "LIMIT", offset, count, order);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return (Collection<T>)get(readSortAsync(byPattern, getPatterns, order));
    }
    
    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return readSortAsync(byPattern, getPatterns, order, -1, -1);
    }
    
    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return (Collection<T>)get(readSortAsync(byPattern, getPatterns, order, offset, count));
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
        return (Collection<T>)get(readSortAlphaAsync(byPattern, getPatterns, order));
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return (Collection<T>)get(readSortAlphaAsync(byPattern, getPatterns, order, offset, count));
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "LIMIT", offset, count, "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, "ALPHA", order);
    }

    @Override
    public RFuture<Set<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, getName(), "BY", byPattern, "LIMIT", offset, count, "ALPHA", order);
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
        params.add(getName());
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
        
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.SORT_TO, params.toArray());
    }

    public String getLockName(Object value, String suffix) {
        ByteBuf state = encode(value);
        try {
            return suffixName(getName(value), Hash.hash128toBase64(state) + ":" + suffix);
        } finally {
            state.release();
        }
    }

    @Override
    public RPermitExpirableSemaphore getPermitExpirableSemaphore(V value) {
        String lockName = getLockName(value, "permitexpirablesemaphore");
        return new RedissonPermitExpirableSemaphore(commandExecutor, lockName, ((Redisson)redisson).getSemaphorePubSub());
    }

    @Override
    public RSemaphore getSemaphore(V value) {
        String lockName = getLockName(value, "semaphore");
        return new RedissonSemaphore(commandExecutor, lockName, ((Redisson)redisson).getSemaphorePubSub());
    }
    
    @Override
    public RCountDownLatch getCountDownLatch(V value) {
        String lockName = getLockName(value, "countdownlatch");
        return new RedissonCountDownLatch(commandExecutor, lockName);
    }
    
    @Override
    public RLock getFairLock(V value) {
        String lockName = getLockName(value, "fairlock");
        return new RedissonFairLock(commandExecutor, lockName);
    }
    
    @Override
    public RLock getLock(V value) {
        String lockName = getLockName(value, "lock");
        return new RedissonLock(commandExecutor, lockName);
    }
    
    @Override
    public RReadWriteLock getReadWriteLock(V value) {
        String lockName = getLockName(value, "rw_lock");
        return new RedissonReadWriteLock(commandExecutor, lockName);
    }

    @Override
    public RFuture<ListScanResult<Object>> scanIteratorAsync(String name, RedisClient client, long startPos,
            String pattern, int count) {
        if (pattern == null) {
            return commandExecutor.readAsync(client, name, codec, RedisCommands.SSCAN, name, startPos, "COUNT", count);
        }

        return commandExecutor.readAsync(client, name, codec, RedisCommands.SSCAN, name, startPos, "MATCH", pattern, "COUNT", count);
    }

    private <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count, boolean alpha) {
        List<Object> params = new ArrayList<Object>();
        params.add(getName());
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

        return commandExecutor.readAsync(getName(), codec, RedisCommands.SORT_SET, params.toArray());
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
