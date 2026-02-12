/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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

import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.CompletableFutureWrapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.redisson.client.protocol.RedisCommands.*;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonSubList<V> extends RedissonList<V> implements RList<V> {

    final int fromIndex;
    AtomicInteger toIndex = new AtomicInteger();
    int size = -1;

    protected RedissonSubList(Codec codec, CommandAsyncExecutor commandExecutor, String name, int fromIndex, int toIndex) {
        super(codec, commandExecutor, name, null);
        this.fromIndex = fromIndex;
        this.toIndex.set(toIndex);
    }

    public RFuture<Integer> sizeAsync() {
        if (size != -1) {
            return new CompletableFutureWrapper<>(size);
        }
        return commandExecutor.readAsync(getRawName(), codec, new RedisStrictCommand<Integer>("LLEN", new IntegerReplayConvertor() {
            @Override
            public Integer convert(Object obj) {
                int size = ((Long) obj).intValue();
                int subListLen = Math.min(size, toIndex.get()) - fromIndex;
                return Math.max(subListLen, 0);
            }
        }), getRawName());
    }

    @Override
    public RFuture<List<V>> readAllAsync() {
        return commandExecutor.readAsync(getRawName(), codec, LRANGE, getRawName(), fromIndex, toIndex.get()-1);
    }

    @Override
    public RFuture<Boolean> addAsync(V e) {
        return addAllAsync(toIndex.get() - fromIndex, Collections.singleton(e));
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        return removeAllAsync(Collections.singleton(o), 1);
    }

    @Override
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        List<Object> params = new ArrayList<Object>();
        params.add(fromIndex);
        params.add(toIndex.get() - 1);
        encode(params, c);
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local fromIndex = table.remove(ARGV, 1);" +
                "local toIndex = table.remove(ARGV, 1);" +
                "local items = redis.call('lrange', KEYS[1], tonumber(fromIndex), tonumber(toIndex)) " +
                "for i=1, #items do " +
                    "for j = #ARGV, 1, -1 do " +
                        "if items[i] == ARGV[j] then " +
                            "table.remove(ARGV, j) " +
                        "end " +
                    "end " +
                "end " +
                "return #ARGV == 0 and 1 or 0",
                Collections.<Object>singletonList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        return addAllAsync(toIndex.get() - fromIndex, c);
    }

    @Override
    public RFuture<Boolean> addAllAsync(int index, Collection<? extends V> coll) {
        checkIndex(index);

        if (coll.isEmpty()) {
            return new CompletableFutureWrapper<>(false);
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>();
            encode(elements, coll);
            Collections.reverse(elements);
            elements.add(0, getRawName());

            return commandExecutor.writeAsync(getRawName(), codec, LPUSH_BOOLEAN, elements.toArray());
        }

        List<Object> args = new ArrayList<Object>(coll.size() + 1);
        args.add(index);
        encode(args, coll);
        
        return commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                        "local size = redis.call('llen', KEYS[1]); " +
                        "assert(tonumber(ind) <= size, 'index: ' .. ind .. ' but current size: ' .. size); " +
                        "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                        "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                        "for i=1, #ARGV, 5000 do "
                          + "redis.call('rpush', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); "
                      + "end; " +
                        "if #tail > 0 then " +
                            "for i=1, #tail, 5000 do "
                                + "redis.call('rpush', KEYS[1], unpack(tail, i, math.min(i+4999, #tail))); "
                          + "end "
                      + "end;" +
                        "return 1;",
                Collections.<Object>singletonList(getRawName()), args.toArray());
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        return removeAllAsync(c, 0);
    }

    private RFuture<Boolean> removeAllAsync(Collection<?> c, int count) {
        List<Object> params = new ArrayList<Object>();
        params.add(fromIndex);
        params.add(toIndex.get() - 1);
        params.add(count);
        encode(params, c);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local v = 0; " +
                "local fromIndex = table.remove(ARGV, 1);" +
                "local toIndex = table.remove(ARGV, 1);" +
                "local count = table.remove(ARGV, 1);" +
                "local items = redis.call('lrange', KEYS[1], fromIndex, toIndex); " +

                "for i=1, #items do " +
                    "for j = 1, #ARGV, 1 do " +
                        "if items[i] == ARGV[j] then " +
                            "redis.call('lrem', KEYS[1], count, ARGV[j]); " +
                            "v = 1; " +
                        "end; " +
                    "end; " +
                "end; " +
                "return v; ",
                Collections.<Object>singletonList(getRawName()), params.toArray());
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        List<Object> params = new ArrayList<Object>();
        params.add(fromIndex);
        params.add(toIndex.get() - 1);
        encode(params, c);

        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local changed = 0 " +
                "local fromIndex = table.remove(ARGV, 1);" +
                "local toIndex = table.remove(ARGV, 1);" +
                "local items = redis.call('lrange', KEYS[1], fromIndex, toIndex) "
                   + "local i = 1 "
                   + "while i <= #items do "
                        + "local element = items[i] "
                        + "local isInAgrs = false "
                        + "for j = 1, #ARGV, 1 do "
                            + "if ARGV[j] == element then "
                                + "isInAgrs = true "
                                + "break "
                            + "end "
                        + "end "
                        + "if isInAgrs == false then "
                            + "redis.call('LREM', KEYS[1], 0, element) "
                            + "changed = 1 "
                        + "end "
                        + "i = i + 1 "
                   + "end "
                   + "return changed ",
                Collections.<Object>singletonList(getRawName()), params.toArray());
    }


    @Override
    public void clear() {
        if (fromIndex == 0) {
            get(commandExecutor.writeAsync(getRawName(), codec, RedisCommands.LTRIM, getRawName(), toIndex, -1));
            size = 0;
            return;
        }

        get(commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, RedisCommands.EVAL_VOID,
                        "local tail = redis.call('lrange', KEYS[1], ARGV[2], -1); " +
                        "redis.call('ltrim', KEYS[1], 0, ARGV[1] - 1); " +
                        "if #tail > 0 then " +
                            "for i=1, #tail, 5000 do "
                                + "redis.call('rpush', KEYS[1], unpack(tail, i, math.min(i+4999, #tail))); "
                          + "end "
                      + "end;",
                Collections.<Object>singletonList(getRawName()), fromIndex, toIndex));
        size = 0;
    }

    @Override
    public RFuture<V> getAsync(int index) {
        checkIndex(index);
        return commandExecutor.readAsync(getRawName(), codec, LINDEX, getRawName(), index);
    }

    @Override
    public V get(int index) {
        return getValue(index);
    }

    V getValue(int index) {
        return get(getAsync(index));
    }

    private void checkIndex(int index) {
        if (!(index >= fromIndex && index < toIndex.get())) {
            throw new IndexOutOfBoundsException("index: " + index + " but current fromIndex: "+ fromIndex + " toIndex: " + toIndex);
        }
    }

    @Override
    public V set(int index, V element) {
        return get(setAsync(index, element));
    }

    @Override
    public RFuture<V> setAsync(int index, V element) {
        checkIndex(index);
        return commandExecutor.evalWriteAsync(getRawName(), codec, RedisCommands.EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(getRawName()), index, encode(element));
    }

    @Override
    public void fastSet(int index, V element) {
        get(fastSetAsync(index, element));
    }

    @Override
    public RFuture<Void> fastSetAsync(int index, V element) {
        checkIndex(index);
        return commandExecutor.writeAsync(getRawName(), codec, RedisCommands.LSET, getRawName(), index, encode(element));
    }

    @Override
    public void add(int index, V element) {
        addAll(index, Collections.singleton(element));
    }

    @Override
    public V remove(int index) {
        checkIndex(index);

        V v = removeInner(index);
        toIndex.decrementAndGet();
        return v;
    }

    private V removeInner(int index) {
        if (index == 0) {
            RFuture<V> f = commandExecutor.writeAsync(getRawName(), codec, LPOP, getRawName());
            return get(f);
        }

        RFuture<V> f = commandExecutor.evalWriteNoRetryAsync(getRawName(), codec, EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "local tail = redis.call('lrange', KEYS[1], ARGV[1] + 1, -1);" +
                        "redis.call('ltrim', KEYS[1], 0, ARGV[1] - 1);" +
                        "if #tail > 0 then " +
                            "for i=1, #tail, 5000 do "
                                + "redis.call('rpush', KEYS[1], unpack(tail, i, math.min(i+4999, #tail))); "
                          + "end "
                      + "end;" +
                        "return v",
                Collections.<Object>singletonList(getRawName()), fromIndex + index);
        return get(f);
    }

    public <R> RFuture<R> indexOfAsync(Object o, Convertor<R> convertor) {
        return commandExecutor.evalReadAsync(getRawName(), codec, new RedisCommand<R>("EVAL", convertor),
                "local items = redis.call('lrange', KEYS[1], tonumber(ARGV[2]), tonumber(ARGV[3])) " +
                "for i=1,#items do " +
                    "if items[i] == ARGV[1] then " +
                        "return tonumber(ARGV[2]) + i - 1; " +
                    "end; " +
                "end; " +
                "return -1; ",
                Collections.<Object>singletonList(getRawName()), encode(o), fromIndex, toIndex.get()-1);
    }

    @Override
    public RFuture<Integer> lastIndexOfAsync(Object o) {
        return commandExecutor.evalReadAsync(getRawName(), codec, RedisCommands.EVAL_INTEGER,
                "local key = KEYS[1] " +
                "local obj = table.remove(ARGV, 1);" +
                "local fromIndex = table.remove(ARGV, 1);" +
                "local toIndex = table.remove(ARGV, 1);" +
                "local items = redis.call('lrange', key, fromIndex, toIndex) " +
                "for i = #items, 1, -1 do " +
                    "if items[i] == obj then " +
                        "return tonumber(fromIndex) + i - 1; " +
                    "end; " +
                "end; " +
                "return -1; ",
                Collections.<Object>singletonList(getRawName()), encode(o), fromIndex, toIndex.get()-1);
    }

    @Override
    public ListIterator<V> listIterator() {
        return listIterator(fromIndex);
    }

    @Override
    public ListIterator<V> listIterator(final int fromIndex) {
        checkIndex(fromIndex);
        return new ListIterator<V>() {

            private V prevCurrentValue;
            private V nextCurrentValue;
            private V currentValueHasRead;
            private int currentIndex = fromIndex - 1;
            private boolean hasBeenModified = true;

            @Override
            public boolean hasNext() {
                if (currentIndex == toIndex.get()-1) {
                    return false;
                }
                V val = RedissonSubList.this.getValue(currentIndex+1);
                if (val != null) {
                    nextCurrentValue = val;
                }
                return val != null;
            }

            @Override
            public V next() {
                if (nextCurrentValue == null && !hasNext()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex++;
                currentValueHasRead = nextCurrentValue;
                nextCurrentValue = null;
                hasBeenModified = false;
                return currentValueHasRead;
            }

            @Override
            public void remove() {
                if (currentValueHasRead == null) {
                    throw new IllegalStateException("Neither next nor previous have been called");
                }
                if (hasBeenModified) {
                    throw new IllegalStateException("Element been already deleted");
                }
                RedissonSubList.this.removeInner(currentIndex - fromIndex);
                toIndex.decrementAndGet();
                hasBeenModified = true;
                currentValueHasRead = null;
            }

            @Override
            public boolean hasPrevious() {
                if (currentIndex <= fromIndex - 1) {
                    return false;
                }
                V val = RedissonSubList.this.getValue(currentIndex);
                if (val != null) {
                    prevCurrentValue = val;
                }
                return val != null;
            }

            @Override
            public V previous() {
                if (prevCurrentValue == null && !hasPrevious()) {
                    throw new NoSuchElementException("No such element at index " + currentIndex);
                }
                currentIndex--;
                hasBeenModified = false;
                currentValueHasRead = prevCurrentValue;
                prevCurrentValue = null;
                return currentValueHasRead;
            }

            @Override
            public int nextIndex() {
                return currentIndex + 1;
            }

            @Override
            public int previousIndex() {
                return currentIndex;
            }

            @Override
            public void set(V e) {
                if (hasBeenModified) {
                    throw new IllegalStateException();
                }
                RedissonSubList.this.set(currentIndex, e);
            }

            @Override
            public void add(V e) {
                RedissonSubList.this.add(currentIndex+1, e);
                currentIndex++;
                hasBeenModified = true;
            }
        };
    }

    @Override
    public RList<V> subList(int fromIndex, int toIndex) {
        if (fromIndex < this.fromIndex || toIndex >= this.toIndex.get()) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }

        return new RedissonSubList<V>(codec, commandExecutor, getRawName(), fromIndex, toIndex);
    }

    @Override
    public RFuture<Void> trimAsync(int fromIndex, int toIndex) {
        if (fromIndex < this.fromIndex || toIndex >= this.toIndex.get()) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }

        return super.trimAsync(fromIndex, toIndex);
    }

    @Override
    public void trim(int fromIndex, int toIndex) {
        get(trimAsync(fromIndex, toIndex));
    }

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
    @SuppressWarnings("AvoidInlineConditionals")
    public boolean equals(Object o) {
        if (o == this)
            return true;
        if (!(o instanceof List))
            return false;

        Iterator<V> e1 = iterator();
        Iterator<?> e2 = ((List<?>) o).iterator();
        while (e1.hasNext() && e2.hasNext()) {
            V o1 = e1.next();
            Object o2 = e2.next();
            if (!(o1==null ? o2==null : o1.equals(o2)))
                return false;
        }
        return !(e1.hasNext() || e2.hasNext());
    }

    @Override
    @SuppressWarnings("AvoidInlineConditionals")
    public int hashCode() {
        int hashCode = 1;
        for (V e : this) {
            hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
        }
        return hashCode;
    }

}
