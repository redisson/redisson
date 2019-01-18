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
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.SortedSet;

import org.redisson.api.RFuture;
import org.redisson.api.RLexSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RedissonPromise;

/**
 * Sorted set contained values of String type
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLexSortedSet extends RedissonScoredSortedSet<String> implements RLexSortedSet {

    public RedissonLexSortedSet(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(StringCodec.INSTANCE, commandExecutor, name, redisson);
    }

    @Override
    public int removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(removeRangeAsync(fromElement, fromInclusive, toElement, toInclusive));
    }
    
    @Override
    public int removeRangeHead(String toElement, boolean toInclusive) {
        return get(removeRangeHeadAsync(toElement, toInclusive));
    }
    
    @Override
    public RFuture<Integer> removeRangeHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), "-", toValue);
    }
    
    @Override
    public int removeRangeTail(String fromElement, boolean fromInclusive) {
        return get(removeRangeTailAsync(fromElement, fromInclusive));
    }

    @Override
    public RFuture<Integer> removeRangeTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, "+");
    }
    
    @Override
    public RFuture<Integer> removeRangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, toValue);
    }
    
    @Override
    public Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(rangeAsync(fromElement, fromInclusive, toElement, toInclusive));
    }
    
    @Override
    public Collection<String> rangeHead(String toElement, boolean toInclusive) {
        return get(rangeHeadAsync(toElement, toInclusive));
    }
    
    @Override
    public RFuture<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue);
    }
    
    @Override
    public Collection<String> rangeTail(String fromElement, boolean fromInclusive) {
        return get(rangeTailAsync(fromElement, fromInclusive));
    }

    @Override
    public RFuture<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+");
    }
    
    @Override
    public RFuture<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive,
            int offset, int count) {
        return get(rangeAsync(fromElement, fromInclusive, toElement, toInclusive, offset, count));
    }
    
    @Override
    public Collection<String> rangeHead(String toElement, boolean toInclusive, int offset, int count) {
        return get(rangeHeadAsync(toElement, toInclusive, offset, count));
    }
    
    @Override
    public RFuture<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive, int offset, int count) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue, "LIMIT", offset, count);
    }

    @Override
    public Collection<String> rangeTail(String fromElement, boolean fromInclusive, int offset, int count) {
        return get(rangeTailAsync(fromElement, fromInclusive, offset, count));
    }
    
    @Override
    public RFuture<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+", "LIMIT", offset, count);
    }
    
    @Override
    public RFuture<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue, "LIMIT", offset, count);
    }
    
    @Override
    public Collection<String> rangeTailReversed(String fromElement, boolean fromInclusive) {
        return get(rangeTailReversedAsync(fromElement, fromInclusive));
    }

    @Override
    public Collection<String> rangeHeadReversed(String toElement, boolean toInclusive) {
        return get(rangeHeadReversedAsync(toElement, toInclusive));
    }

    @Override
    public Collection<String> rangeReversed(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        return get(rangeReversedAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public Collection<String> rangeTailReversed(String fromElement, boolean fromInclusive, int offset, int count) {
        return get(rangeTailReversedAsync(fromElement, fromInclusive, offset, count));
    }

    @Override
    public Collection<String> rangeHeadReversed(String toElement, boolean toInclusive, int offset, int count) {
        return get(rangeHeadReversedAsync(toElement, toInclusive, offset, count));
    }

    @Override
    public Collection<String> rangeReversed(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive, int offset, int count) {
        return get(rangeReversedAsync(fromElement, fromInclusive, toElement, toInclusive, offset, count));
    }

    @Override
    public RFuture<Collection<String>> rangeTailReversedAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), "+", fromValue);
    }

    @Override
    public RFuture<Collection<String>> rangeHeadReversedAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), toValue, "-");
    }

    @Override
    public RFuture<Collection<String>> rangeReversedAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), toValue, fromValue);
    }

    @Override
    public RFuture<Collection<String>> rangeTailReversedAsync(String fromElement, boolean fromInclusive, int offset,
            int count) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), "+", fromValue, "LIMIT", offset, count);
    }

    @Override
    public RFuture<Collection<String>> rangeHeadReversedAsync(String toElement, boolean toInclusive, int offset,
            int count) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), toValue, "-", "LIMIT", offset, count);
    }

    @Override
    public RFuture<Collection<String>> rangeReversedAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREVRANGEBYLEX, getName(), toValue, fromValue, "LIMIT", offset, count);
    }

    @Override
    public int countTail(String fromElement, boolean fromInclusive) {
        return get(countTailAsync(fromElement, fromInclusive));
    }
    
    @Override
    public RFuture<Integer> countTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), fromValue, "+");
    }

    @Override
    public int countHead(String toElement, boolean toInclusive) {
        return get(countHeadAsync(toElement, toInclusive));
    }

    @Override
    public RFuture<Integer> countHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), "-", toValue);
    }

    @Override
    public int count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(countAsync(fromElement, fromInclusive, toElement, toInclusive));
    }
    
    @Override
    public RFuture<Integer> countAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);
        
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), fromValue, toValue);
    }
    
    private String value(String fromElement, boolean fromInclusive) {
        String fromValue = fromElement.toString();
        if (fromInclusive) {
            fromValue = "[" + fromValue;
        } else {
            fromValue = "(" + fromValue;
        }
        return fromValue;
    }

    @Override
    public RFuture<Boolean> addAsync(String e) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_BOOL_RAW, getName(), 0, e);
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends String> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }
        List<Object> params = new ArrayList<Object>(2*c.size());
        params.add(getName());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_BOOL_RAW, params.toArray());
    }

    @Override
    public boolean add(String e) {
        return get(addAsync(e));
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        return get(addAllAsync(c));
    }

    @Override
    public Collection<String> range(int startIndex, int endIndex) {
        return valueRange(startIndex, endIndex);
    }
    
    @Override
    public RFuture<Collection<String>> rangeAsync(int startIndex, int endIndex) {
        return valueRangeAsync(startIndex, endIndex);
    }

    @Override
    public boolean trySetComparator(Comparator<? super String> comparator) {
        throw new UnsupportedOperationException();
    }

    @Override
    public Comparator<? super String> comparator() {
        return null;
    }

    @Override
    public SortedSet<String> subSet(String fromElement, String toElement) {
        throw new UnsupportedOperationException();
    }

    @Override
    public SortedSet<String> headSet(String toElement) {
        return subSet(null, toElement);
    }

    @Override
    public SortedSet<String> tailSet(String fromElement) {
        return subSet(fromElement, null);
    }

}
