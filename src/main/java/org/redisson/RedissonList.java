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

import static org.redisson.client.protocol.RedisCommands.EVAL_OBJECT;
import static org.redisson.client.protocol.RedisCommands.LINDEX;
import static org.redisson.client.protocol.RedisCommands.LLEN_INT;
import static org.redisson.client.protocol.RedisCommands.LPOP;
import static org.redisson.client.protocol.RedisCommands.LPUSH_BOOLEAN;
import static org.redisson.client.protocol.RedisCommands.LRANGE;
import static org.redisson.client.protocol.RedisCommands.LREM_SINGLE;
import static org.redisson.client.protocol.RedisCommands.RPUSH_BOOLEAN;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;
import java.util.NoSuchElementException;

import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommand.ValueType;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanNumberReplayConvertor;
import org.redisson.client.protocol.convertor.BooleanReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RList;

import io.netty.util.concurrent.Future;

/**
 * Distributed and concurrent implementation of {@link java.util.List}
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonList<V> extends RedissonExpirable implements RList<V> {

    public static final RedisCommand<Boolean> EVAL_BOOLEAN_ARGS2 = new RedisCommand<Boolean>("EVAL", new BooleanReplayConvertor(), 5, ValueType.OBJECTS);

    public RedissonList(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonList(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    public Future<Integer> sizeAsync() {
        return commandExecutor.readAsync(getName(), codec, LLEN_INT, getName());
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
    public Iterator<V> iterator() {
        return listIterator();
    }

    @Override
    public Object[] toArray() {
        List<V> list = readAll();
        return list.toArray();
    }

    @Override
    public List<V> readAll() {
        return get(readAllAsync());
    }

    @Override
    public Future<List<V>> readAllAsync() {
        return commandExecutor.readAsync(getName(), codec, LRANGE, getName(), 0, -1);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> list = readAll();
        return list.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return get(addAsync(e));
    }

    @Override
    public Future<Boolean> addAsync(V e) {
        return commandExecutor.writeAsync(getName(), codec, RPUSH_BOOLEAN, getName(), e);
    }

    @Override
    public boolean remove(Object o) {
        return get(removeAsync(o));
    }

    @Override
    public Future<Boolean> removeAsync(Object o) {
        return removeAsync(o, 1);
    }

    protected Future<Boolean> removeAsync(Object o, int count) {
        return commandExecutor.writeAsync(getName(), codec, LREM_SINGLE, getName(), count, o);
    }

    protected boolean remove(Object o, int count) {
        return get(removeAsync(o, count));
    }

    @Override
    public Future<Boolean> containsAllAsync(Collection<?> c) {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                "local items = redis.call('lrange', KEYS[1], 0, -1) " +
                "for i=1, #items do " +
                    "for j = 1, #ARGV, 1 do " +
                        "if items[i] == ARGV[j] then " +
                            "table.remove(ARGV, j) " +
                        "end " +
                    "end " +
                "end " +
                "return #ARGV == 0 and 1 or 0",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return get(addAllAsync(c));
    }

    @Override
    public Future<Boolean> addAllAsync(final Collection<? extends V> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }

        List<Object> args = new ArrayList<Object>(c.size() + 1);
        args.add(getName());
        args.addAll(c);
        return commandExecutor.writeAsync(getName(), codec, RPUSH_BOOLEAN, args.toArray());
    }

    public Future<Boolean> addAllAsync(int index, Collection<? extends V> coll) {
        if (index < 0) {
            throw new IndexOutOfBoundsException("index: " + index);
        }

        if (coll.isEmpty()) {
            return newSucceededFuture(false);
        }

        if (index == 0) { // prepend elements to list
            List<Object> elements = new ArrayList<Object>(coll);
            Collections.reverse(elements);
            elements.add(0, getName());

            return commandExecutor.writeAsync(getName(), codec, LPUSH_BOOLEAN, elements.toArray());
        }

        List<Object> args = new ArrayList<Object>(coll.size() + 1);
        args.add(index);
        args.addAll(coll);
        return commandExecutor.evalWriteAsync(getName(), codec, EVAL_BOOLEAN_ARGS2,
                "local ind = table.remove(ARGV, 1); " + // index is the first parameter
                        "local size = redis.call('llen', KEYS[1]); " +
                        "assert(tonumber(ind) <= size, 'index: ' .. ind .. ' but current size: ' .. size); " +
                        "local tail = redis.call('lrange', KEYS[1], ind, -1); " +
                        "redis.call('ltrim', KEYS[1], 0, ind - 1); " +
                        "for i=1, #ARGV, 5000 do "
                            + "redis.call('rpush', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); "
                        + "end " +
                        "if #tail > 0 then " +
                            "for i=1, #tail, 5000 do "
                                + "redis.call('rpush', KEYS[1], unpack(tail, i, math.min(i+4999, #tail))); "
                          + "end "
                      + "end;" +
                        "return 1;",
                Collections.<Object>singletonList(getName()), args.toArray());
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> coll) {
        return get(addAllAsync(index, coll));
    }

    @Override
    public Future<Boolean> removeAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                        "local v = 0 " +
                        "for i = 1, #ARGV, 1 do "
                            + "if redis.call('lrem', KEYS[1], 0, ARGV[i]) == 1 "
                            + "then v = 1 end "
                        +"end "
                       + "return v ",
                Collections.<Object>singletonList(getName()), c.toArray());
    }

    @Override
    public boolean removeAll(Collection<?> c) {
        return get(removeAllAsync(c));
    }

    @Override
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public Future<Boolean> retainAllAsync(Collection<?> c) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN_WITH_VALUES,
                "local changed = 0 " +
                "local items = redis.call('lrange', KEYS[1], 0, -1) "
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
                Collections.<Object>singletonList(getName()), c.toArray());
    }


    @Override
    public void clear() {
        delete();
    }

    @Override
    public Future<V> getAsync(int index) {
        return commandExecutor.readAsync(getName(), codec, LINDEX, getName(), index);
    }

    @Override
    public V get(int index) {
        checkIndex(index);
        return getValue(index);
    }

    V getValue(int index) {
        return get(getAsync(index));
    }

    private void checkIndex(int index) {
        int size = size();
        if (!isInRange(index, size))
            throw new IndexOutOfBoundsException("index: " + index + " but current size: "+ size);
    }

    private boolean isInRange(int index, int size) {
        return index >= 0 && index < size;
    }

    @Override
    public V set(int index, V element) {
        checkIndex(index);
        return get(setAsync(index, element));
    }

    @Override
    public Future<V> setAsync(int index, V element) {
        return commandExecutor.evalWriteAsync(getName(), codec, new RedisCommand<Object>("EVAL", 5),
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                        "redis.call('lset', KEYS[1], ARGV[1], ARGV[2]); " +
                        "return v",
                Collections.<Object>singletonList(getName()), index, element);
    }

    @Override
    public void fastSet(int index, V element) {
        get(fastSetAsync(index, element));
    }

    @Override
    public Future<Void> fastSetAsync(int index, V element) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LSET, getName(), index, element);
    }

    @Override
    public void add(int index, V element) {
        addAll(index, Collections.singleton(element));
    }

    @Override
    public V remove(int index) {
        if (index == 0) {
            Future<V> f = commandExecutor.writeAsync(getName(), codec, LPOP, getName());
            return get(f);
        }

        Future<V> f = commandExecutor.evalWriteAsync(getName(), codec, EVAL_OBJECT,
                "local v = redis.call('lindex', KEYS[1], ARGV[1]); " +
                "redis.call('lset', KEYS[1], ARGV[1], 'DELETED_BY_REDISSON');" +
                "redis.call('lrem', KEYS[1], 1, 'DELETED_BY_REDISSON');" +
                "return v",
                Collections.<Object>singletonList(getName()), index);
        return get(f);
    }
    
    @Override
    public void fastRemove(int index) {
        get(fastRemoveAsync(index));
    }
    
    @Override
    public Future<Void> fastRemoveAsync(int index) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_VOID,
                "redis.call('lset', KEYS[1], ARGV[1], 'DELETED_BY_REDISSON');" +
                "redis.call('lrem', KEYS[1], 1, 'DELETED_BY_REDISSON');",
                Collections.<Object>singletonList(getName()), index);
    }
    
    @Override
    public int indexOf(Object o) {
        return get(indexOfAsync(o));
    }

    @Override
    public Future<Boolean> containsAsync(Object o) {
        return indexOfAsync(o, new BooleanNumberReplayConvertor(-1L));
    }

    private <R> Future<R> indexOfAsync(Object o, Convertor<R> convertor) {
        return commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<R>("EVAL", convertor, 4),
                "local key = KEYS[1] " +
                "local obj = ARGV[1] " +
                "local items = redis.call('lrange', key, 0, -1) " +
                "for i=1,#items do " +
                    "if items[i] == obj then " +
                        "return i - 1 " +
                    "end " +
                "end " +
                "return -1",
                Collections.<Object>singletonList(getName()), o);
    }

    @Override
    public Future<Integer> indexOfAsync(Object o) {
        return indexOfAsync(o, new IntegerReplayConvertor());
    }

    @Override
    public int lastIndexOf(Object o) {
        return get(lastIndexOfAsync(o));
    }

    @Override
    public Future<Integer> lastIndexOfAsync(Object o) {
        return commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<Integer>("EVAL", new IntegerReplayConvertor(), 4),
                "local key = KEYS[1] " +
                "local obj = ARGV[1] " +
                "local items = redis.call('lrange', key, 0, -1) " +
                "for i = #items, 1, -1 do " +
                    "if items[i] == obj then " +
                        "return i - 1 " +
                    "end " +
                "end " +
                "return -1",
                Collections.<Object>singletonList(getName()), o);
    }

    @Override
    public void trim(int fromIndex, int toIndex) {
        get(trimAsync(fromIndex, toIndex));
    }

    @Override
    public Future<Void> trimAsync(int fromIndex, int toIndex) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LTRIM, getName(), fromIndex, toIndex);
    }

    @Override
    public ListIterator<V> listIterator() {
        return listIterator(0);
    }

    @Override
    public ListIterator<V> listIterator(final int ind) {
        return new ListIterator<V>() {

            private V prevCurrentValue;
            private V nextCurrentValue;
            private V currentValueHasRead;
            private int currentIndex = ind - 1;
            private boolean hasBeenModified = true;

            @Override
            public boolean hasNext() {
                V val = RedissonList.this.getValue(currentIndex+1);
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
                RedissonList.this.remove(currentIndex);
                currentIndex--;
                hasBeenModified = true;
                currentValueHasRead = null;
            }

            @Override
            public boolean hasPrevious() {
                if (currentIndex < 0) {
                    return false;
                }
                V val = RedissonList.this.getValue(currentIndex);
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

                RedissonList.this.fastSet(currentIndex, e);
            }

            @Override
            public void add(V e) {
                RedissonList.this.add(currentIndex+1, e);
                currentIndex++;
                hasBeenModified = true;
            }
        };
    }

    @Override
    public RList<V> subList(int fromIndex, int toIndex) {
        int size = size();
        if (fromIndex < 0 || toIndex > size) {
            throw new IndexOutOfBoundsException("fromIndex: " + fromIndex + " toIndex: " + toIndex + " size: " + size);
        }
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException("fromIndex: " + fromIndex + " toIndex: " + toIndex);
        }

        return new RedissonSubList<V>(codec, commandExecutor, getName(), fromIndex, toIndex);
    }

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
    public int hashCode() {
        int hashCode = 1;
        for (V e : this) {
            hashCode = 31*hashCode + (e==null ? 0 : e.hashCode());
        }
        return hashCode;
    }

    @Override
    public Future<Integer> addAfterAsync(V elementToFind, V element) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LINSERT_INT, getName(), "AFTER", elementToFind, element);
    }

    @Override
    public Future<Integer> addBeforeAsync(V elementToFind, V element) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.LINSERT_INT, getName(), "BEFORE", elementToFind, element);
    }

    @Override
    public Integer addAfter(V elementToFind, V element) {
        return get(addAfterAsync(elementToFind, element));
    }

    @Override
    public Integer addBefore(V elementToFind, V element) {
        return get(addBeforeAsync(elementToFind, element));
    }

}
