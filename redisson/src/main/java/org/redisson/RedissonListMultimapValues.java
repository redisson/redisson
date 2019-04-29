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
import java.util.ListIterator;
import java.util.NoSuchElementException;
import java.util.concurrent.TimeUnit;

import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.SortOrder;
import org.redisson.api.mapreduce.RCollectionMapReduce;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommand;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.convertor.BooleanNumberReplayConvertor;
import org.redisson.client.protocol.convertor.Convertor;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;

/**
 * List based Multimap Cache values holder
 *
 * @author Nikita Koksharov
 *
 * @param <V> the type of elements held in this collection
 */
public class RedissonListMultimapValues<V> extends RedissonExpirable implements RList<V> {

    private final RList<V> list;
    private final Object key;
    private final String timeoutSetName;

    public RedissonListMultimapValues(Codec codec, CommandAsyncExecutor commandExecutor, String name, String timeoutSetName, Object key) {
        super(codec, commandExecutor, name);
        this.timeoutSetName = timeoutSetName;
        this.key = key;
        this.list = new RedissonList<V>(codec, commandExecutor, name, null);
    }
    
    @Override
    public <KOut, VOut> RCollectionMapReduce<V, KOut, VOut> mapReduce() {
        return null;
    }
    
    @Override
    public RFuture<Boolean> clearExpireAsync() {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Boolean> expireAsync(long timeToLive, TimeUnit timeUnit) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Boolean> expireAtAsync(long timestamp) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Long> remainTimeToLiveAsync() {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Void> renameAsync(String newName) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Boolean> renamenxAsync(String newName) {
        throw new UnsupportedOperationException("This operation is not supported for SetMultimap values Set");
    }
    
    @Override
    public RFuture<Long> sizeInMemoryAsync() {
        List<Object> keys = Arrays.<Object>asList(getName(), timeoutSetName);
        return super.sizeInMemoryAsync(keys);
    }
    
    public RFuture<Boolean> deleteAsync() {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
                Arrays.<Object>asList(timeoutSetName, getName()), System.currentTimeMillis(), key);
    }

    @Override
    public int size() {
        return get(sizeAsync());
    }

    public RFuture<Integer> sizeAsync() {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('llen', KEYS[2]);",
         Arrays.<Object>asList(timeoutSetName, getName()), 
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
    public RFuture<List<V>> readAllAsync() {
        return rangeAsync(0, -1);
    }

    @Override
    public <T> T[] toArray(T[] a) {
        List<V> list = readAll();
        return list.toArray(a);
    }

    @Override
    public boolean add(V e) {
        return list.add(e);
    }

    @Override
    public RFuture<Boolean> addAsync(V e) {
        return list.addAsync(e);
    }
    
    @Override
    public RFuture<Boolean> addAsync(int index, V element) {
        return list.addAsync(index, element);
    }

    @Override
    public boolean remove(Object o) {
        return get(removeAsync(o));
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o) {
        return removeAsync(o, 1);
    }

    @Override
    public RFuture<Boolean> removeAsync(Object o, int count) {
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; "
              + "return redis.call('lrem', KEYS[2], ARGV[2], ARGV[4]) > 0 and 1 or 0;",
         Arrays.<Object>asList(timeoutSetName, getName()), 
         System.currentTimeMillis(), count, encodeMapKey(key), encodeMapValue(o));
    }

    @Override
    public boolean remove(Object o, int count) {
        return get(removeAsync(o, count));
    }

    @Override
    public RFuture<Boolean> containsAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);
        
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return 0;"
              + "end; " +
                "local items = redis.call('lrange', KEYS[2], 0, -1);" +
                        "for i = 1, #items, 1 do " +
                            "for j = 2, #ARGV, 1 do "
                            + "if ARGV[j] == items[i] "
                            + "then table.remove(ARGV, j) end "
                        + "end; "
                       + "end;"
                       + "return #ARGV == 2 and 1 or 0; ",
                   Arrays.<Object>asList(timeoutSetName, getName()), args.toArray());
        
    }

    @Override
    public boolean containsAll(Collection<?> c) {
        return get(containsAllAsync(c));
    }

    @Override
    public boolean addAll(Collection<? extends V> c) {
        return list.addAll(c);
    }

    @Override
    public RFuture<Boolean> addAllAsync(final Collection<? extends V> c) {
        return list.addAllAsync(c);
    }

    @Override
    public RFuture<Boolean> addAllAsync(int index, Collection<? extends V> coll) {
        return list.addAllAsync(index, coll);
    }

    @Override
    public boolean addAll(int index, Collection<? extends V> coll) {
        return list.addAll(index, coll);
    }

    @Override
    public RFuture<Boolean> removeAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);
        
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
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
                            + "if redis.call('lrem', KEYS[2], 0, ARGV[i]) == 1 "
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
    public boolean retainAll(Collection<?> c) {
        return get(retainAllAsync(c));
    }

    @Override
    public RFuture<Boolean> retainAllAsync(Collection<?> c) {
        List<Object> args = new ArrayList<Object>(c.size() + 2);
        args.add(System.currentTimeMillis());
        args.add(encodeMapKey(key));
        encodeMapValues(args, c);

        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                    "local expireDate = 92233720368547758; " +
                    "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
                  + "if expireDateScore ~= false then "
                      + "expireDate = tonumber(expireDateScore) "
                  + "end; "
                  + "if expireDate <= tonumber(ARGV[1]) then "
                      + "return 0;"
                  + "end; " +

                    "local changed = 0; " +
                    "local s = redis.call('lrange', KEYS[2], 0, -1); "
                       + "local i = 1; "
                       + "while i <= #s do "
                            + "local element = s[i]; "
                            + "local isInAgrs = false; "
                            + "for j = 2, #ARGV, 1 do "
                                + "if ARGV[j] == element then "
                                    + "isInAgrs = true; "
                                    + "break; "
                                + "end; "
                            + "end; "
                            + "if isInAgrs == false then "
                                + "redis.call('lrem', KEYS[2], 0, element); "
                                + "changed = 1; "
                            + "end; "
                            + "i = i + 1; "
                       + "end; "
                       + "return changed; ",
                       Arrays.<Object>asList(timeoutSetName, getName()), args.toArray());
    }


    @Override
    public void clear() {
        delete();
    }

    @Override
    public List<V> get(int...indexes) {
        return get(getAsync(indexes));
    }
    
    @Override
    public RFuture<List<V>> getAsync(int...indexes) {
        List<Object> params = new ArrayList<Object>();
        params.add(System.currentTimeMillis());
        params.add(encodeMapKey(key));
        for (Integer index : indexes) {
            params.add(index);
        }
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_LIST,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return nil;"
              + "end; " +
                
                "local result = {}; " + 
                "for i = 3, #ARGV, 1 do "
                    + "local value = redis.call('lindex', KEYS[1], ARGV[i]);"
                    + "table.insert(result, value);" + 
                "end; " +
                "return result;",
                Collections.<Object>singletonList(getName()), params.toArray());
    }
    
    @Override
    public RFuture<V> getAsync(int index) {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[3]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return nil;"
              + "end; "
              + "return redis.call('lindex', KEYS[2], ARGV[2]);",
         Arrays.<Object>asList(timeoutSetName, getName()), 
         System.currentTimeMillis(), index, encodeMapKey(key));
    }

    @Override
    public V get(int index) {
        return getValue(index);
    }

    V getValue(int index) {
        return get(getAsync(index));
    }

    public V set(int index, V element) {
        return list.set(index, element);
    }

    @Override
    public RFuture<V> setAsync(int index, V element) {
        return list.setAsync(index, element);
    }

    @Override
    public void fastSet(int index, V element) {
        list.fastSet(index, element);
    }

    @Override
    public RFuture<Void> fastSetAsync(int index, V element) {
        return list.fastSetAsync(index, element);
    }

    @Override
    public void add(int index, V element) {
        addAll(index, Collections.singleton(element));
    }

    @Override
    public V remove(int index) {
        return list.remove(index);
    }
    
    @Override
    public RFuture<V> removeAsync(int index) {
        return list.removeAsync(index);
    }

    @Override
    public void fastRemove(int index) {
        list.fastRemove(index);
    }
    
    @Override
    public RFuture<Void> fastRemoveAsync(int index) {
        return list.fastRemoveAsync(index);
    }
    
    @Override
    public int indexOf(Object o) {
        return get(indexOfAsync(o));
    }

    @Override
    public RFuture<Boolean> containsAsync(Object o) {
        return indexOfAsync(o, new BooleanNumberReplayConvertor(-1L));
    }

    private <R> RFuture<R> indexOfAsync(Object o, Convertor<R> convertor) {
        return commandExecutor.evalReadAsync(getName(), codec, new RedisCommand<R>("EVAL", convertor),
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return -1;"
              + "end; " +

                "local items = redis.call('lrange', KEYS[2], 0, -1); " +
                "for i=1,#items do " +
                    "if items[i] == ARGV[3] then " +
                        "return i - 1; " +
                    "end; " +
                "end; " +
                "return -1;",
                Arrays.<Object>asList(timeoutSetName, getName()), 
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(o));
    }

    @Override
    public RFuture<Integer> indexOfAsync(Object o) {
        return indexOfAsync(o, new IntegerReplayConvertor());
    }

    @Override
    public int lastIndexOf(Object o) {
        return get(lastIndexOfAsync(o));
    }

    @Override
    public RFuture<Integer> lastIndexOfAsync(Object o) {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_INTEGER,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore); "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return -1;"
              + "end; " +
                
                "local items = redis.call('lrange', KEYS[1], 0, -1) " +
                "for i = #items, 1, -1 do " +
                    "if items[i] == ARGV[1] then " +
                        "return i - 1 " +
                    "end " +
                "end " +
                "return -1",
                Arrays.<Object>asList(timeoutSetName, getName()), 
                System.currentTimeMillis(), encodeMapKey(key), encodeMapValue(o));
    }

    @Override
    public void trim(int fromIndex, int toIndex) {
        list.trim(fromIndex, toIndex);
    }

    @Override
    public RFuture<Void> trimAsync(int fromIndex, int toIndex) {
        return list.trimAsync(fromIndex, toIndex);
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
                V val = RedissonListMultimapValues.this.getValue(currentIndex+1);
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
                RedissonListMultimapValues.this.remove(currentIndex);
                currentIndex--;
                hasBeenModified = true;
                currentValueHasRead = null;
            }

            @Override
            public boolean hasPrevious() {
                if (currentIndex < 0) {
                    return false;
                }
                V val = RedissonListMultimapValues.this.getValue(currentIndex);
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

                RedissonListMultimapValues.this.fastSet(currentIndex, e);
            }

            @Override
            public void add(V e) {
                RedissonListMultimapValues.this.add(currentIndex+1, e);
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

    @Override
    public RFuture<Integer> addAfterAsync(V elementToFind, V element) {
        return list.addAfterAsync(elementToFind, element);
    }

    @Override
    public RFuture<Integer> addBeforeAsync(V elementToFind, V element) {
        return list.addBeforeAsync(elementToFind, element);
    }

    @Override
    public int addAfter(V elementToFind, V element) {
        return list.addAfter(elementToFind, element);
    }

    @Override
    public int addBefore(V elementToFind, V element) {
        return list.addBefore(elementToFind, element);
    }

    @Override
    public RFuture<List<V>> readSortAsync(SortOrder order) {
        return list.readSortAsync(order);
    }

    @Override
    public List<V> readSort(SortOrder order) {
        return list.readSort(order);
    }

    @Override
    public RFuture<List<V>> readSortAsync(SortOrder order, int offset, int count) {
        return list.readSortAsync(order, offset, count);
    }

    @Override
    public List<V> readSort(SortOrder order, int offset, int count) {
        return list.readSort(order, offset, count);
    }

    @Override
    public List<V> readSort(String byPattern, SortOrder order, int offset, int count) {
        return list.readSort(byPattern, order, offset, count);
    }

    @Override
    public RFuture<List<V>> readSortAsync(String byPattern, SortOrder order, int offset, int count) {
        return list.readSortAsync(byPattern, order, offset, count);
    }

    @Override
    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return list.readSort(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset,
            int count) {
        return list.readSortAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public List<V> readSortAlpha(SortOrder order) {
        return list.readSortAlpha(order);
    }

    @Override
    public List<V> readSortAlpha(SortOrder order, int offset, int count) {
        return list.readSortAlpha(order, offset, count);
    }

    @Override
    public List<V> readSortAlpha(String byPattern, SortOrder order) {
        return list.readSortAlpha(byPattern, order);
    }

    @Override
    public List<V> readSortAlpha(String byPattern, SortOrder order, int offset, int count) {
        return list.readSortAlpha(byPattern, order, offset, count);
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order) {
        return list.readSortAlpha(byPattern, getPatterns, order);
    }

    @Override
    public <T> Collection<T> readSortAlpha(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return list.readSortAlpha(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<List<V>> readSortAlphaAsync(SortOrder order) {
        return list.readSortAlphaAsync(order);
    }

    @Override
    public RFuture<List<V>> readSortAlphaAsync(SortOrder order, int offset, int count) {
        return list.readSortAlphaAsync(order, offset, count);
    }

    @Override
    public RFuture<List<V>> readSortAlphaAsync(String byPattern, SortOrder order) {
        return list.readSortAlphaAsync(byPattern, order);
    }

    @Override
    public RFuture<List<V>> readSortAlphaAsync(String byPattern, SortOrder order, int offset, int count) {
        return list.readSortAlphaAsync(byPattern, order, offset, count);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return list.readSortAlphaAsync(byPattern, getPatterns, order);
    }

    @Override
    public <T> RFuture<Collection<T>> readSortAlphaAsync(String byPattern, List<String> getPatterns, SortOrder order, int offset, int count) {
        return list.readSortAlphaAsync(byPattern, getPatterns, order, offset, count);
    }

    @Override
    public int sortTo(String destName, SortOrder order) {
        return list.sortTo(destName, order);
    }

    @Override
    public RFuture<Integer> sortToAsync(String destName, SortOrder order) {
        return list.sortToAsync(destName, order);
    }

    public List<V> readSort(String byPattern, SortOrder order) {
        return list.readSort(byPattern, order);
    }

    public RFuture<List<V>> readSortAsync(String byPattern, SortOrder order) {
        return list.readSortAsync(byPattern, order);
    }

    public <T> Collection<T> readSort(String byPattern, List<String> getPatterns, SortOrder order) {
        return list.readSort(byPattern, getPatterns, order);
    }

    public <T> RFuture<Collection<T>> readSortAsync(String byPattern, List<String> getPatterns, SortOrder order) {
        return list.readSortAsync(byPattern, getPatterns, order);
    }

    public int sortTo(String destName, SortOrder order, int offset, int count) {
        return list.sortTo(destName, order, offset, count);
    }

    public int sortTo(String destName, String byPattern, SortOrder order) {
        return list.sortTo(destName, byPattern, order);
    }

    public RFuture<Integer> sortToAsync(String destName, SortOrder order, int offset, int count) {
        return list.sortToAsync(destName, order, offset, count);
    }

    public int sortTo(String destName, String byPattern, SortOrder order, int offset, int count) {
        return list.sortTo(destName, byPattern, order, offset, count);
    }

    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order) {
        return list.sortToAsync(destName, byPattern, order);
    }

    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order) {
        return list.sortTo(destName, byPattern, getPatterns, order);
    }

    public RFuture<Integer> sortToAsync(String destName, String byPattern, SortOrder order, int offset,
            int count) {
        return list.sortToAsync(destName, byPattern, order, offset, count);
    }

    public int sortTo(String destName, String byPattern, List<String> getPatterns, SortOrder order, int offset,
            int count) {
        return list.sortTo(destName, byPattern, getPatterns, order, offset, count);
    }

    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns,
            SortOrder order) {
        return list.sortToAsync(destName, byPattern, getPatterns, order);
    }

    public RFuture<Integer> sortToAsync(String destName, String byPattern, List<String> getPatterns,
            SortOrder order, int offset, int count) {
        return list.sortToAsync(destName, byPattern, getPatterns, order, offset, count);
    }

    @Override
    public RFuture<List<V>> rangeAsync(int toIndex) {
        return rangeAsync(0, toIndex);
    }

    @Override
    public RFuture<List<V>> rangeAsync(int fromIndex, int toIndex) {
        return commandExecutor.evalReadAsync(getName(), codec, RedisCommands.EVAL_MAP_VALUE_LIST,
                "local expireDate = 92233720368547758; " +
                "local expireDateScore = redis.call('zscore', KEYS[1], ARGV[2]); "
              + "if expireDateScore ~= false then "
                  + "expireDate = tonumber(expireDateScore) "
              + "end; "
              + "if expireDate <= tonumber(ARGV[1]) then "
                  + "return {};"
              + "end; "
              + "return redis.call('lrange', KEYS[2], ARGV[3], ARGV[4]);",
              Arrays.<Object>asList(timeoutSetName, getName()), 
              System.currentTimeMillis(), encodeMapKey(key), fromIndex, toIndex);
    }

    @Override
    public List<V> range(int toIndex) {
        return get(rangeAsync(toIndex));
    }

    @Override
    public List<V> range(int fromIndex, int toIndex) {
        return get(rangeAsync(fromIndex, toIndex));
    }

    
    
}
