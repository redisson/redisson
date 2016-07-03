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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.core.RLexSortedSet;

import io.netty.util.concurrent.Future;

public class RedissonLexSortedSet extends RedissonScoredSortedSet<String> implements RLexSortedSet {

    public RedissonLexSortedSet(CommandAsyncExecutor commandExecutor, String name) {
        super(StringCodec.INSTANCE, commandExecutor, name);
    }

    @Override
    public int removeRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return removeRangeByLex(fromElement, fromInclusive, toElement, toInclusive);
    }
    
    @Override
    public int removeRangeByLex(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(removeRangeAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public int removeRangeHead(String toElement, boolean toInclusive) {
        return removeRangeHeadByLex(toElement, toInclusive);
    }
    
    @Override
    public int removeRangeHeadByLex(String toElement, boolean toInclusive) {
        return get(removeRangeHeadAsync(toElement, toInclusive));
    }

    @Override
    public Future<Integer> removeRangeHeadAsync(String toElement, boolean toInclusive) {
        return removeRangeHeadByLexAsync(toElement, toInclusive);
    }
    
    @Override
    public Future<Integer> removeRangeHeadByLexAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), "-", toValue);
    }
    
    @Override
    public int removeRangeTail(String fromElement, boolean fromInclusive) {
        return removeRangeTailByLex(fromElement, fromInclusive);
    }

    @Override
    public int removeRangeTailByLex(String fromElement, boolean fromInclusive) {
        return get(removeRangeTailAsync(fromElement, fromInclusive));
    }

    @Override
    public Future<Integer> removeRangeTailAsync(String fromElement, boolean fromInclusive) {
        return removeRangeTailByLexAsync(fromElement, fromInclusive);
    }
    
    @Override
    public Future<Integer> removeRangeTailByLexAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, "+");
    }

    @Override
    public Future<Integer> removeRangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        return removeRangeByLexAsync(fromElement, fromInclusive, toElement, toInclusive);
    }
    
    @Override
    public Future<Integer> removeRangeByLexAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return lexRange(fromElement, fromInclusive, toElement, toInclusive);
    }
    
    @Override
    public Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(rangeAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public Collection<String> rangeHead(String toElement, boolean toInclusive) {
        return lexRangeHead(toElement, toInclusive);
    }
    
    @Override
    public Collection<String> lexRangeHead(String toElement, boolean toInclusive) {
        return get(rangeHeadAsync(toElement, toInclusive));
    }

    @Override
    public Future<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive) {
        return lexRangeHeadAsync(toElement, toInclusive);
    }
    
    @Override
    public Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue);
    }
    
    @Override
    public Collection<String> rangeTail(String fromElement, boolean fromInclusive) {
        return lexRangeTail(fromElement, fromInclusive);
    }

    @Override
    public Collection<String> lexRangeTail(String fromElement, boolean fromInclusive) {
        return get(rangeTailAsync(fromElement, fromInclusive));
    }

    @Override
    public Future<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive) {
        return lexRangeTailAsync(fromElement, fromInclusive);
    }
    
    @Override
    public Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+");
    }

    @Override
    public Future<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        return lexRangeAsync(fromElement, fromInclusive, toElement, toInclusive);
    }

    @Override
    public Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Collection<String> range(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive,
            int offset, int count) {
        return lexRange(fromElement, fromInclusive, toElement, toInclusive, offset, count);
    }
    
    @Override
    public Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count) {
        return get(rangeAsync(fromElement, fromInclusive, toElement, toInclusive, offset, count));
    }

    @Override
    public Collection<String> rangeHead(String toElement, boolean toInclusive, int offset, int count) {
        return lexRangeHead(toElement, toInclusive, offset, count);
    }
    
    @Override
    public Collection<String> lexRangeHead(String toElement, boolean toInclusive, int offset, int count) {
        return get(rangeHeadAsync(toElement, toInclusive, offset, count));
    }

    @Override
    public Future<Collection<String>> rangeHeadAsync(String toElement, boolean toInclusive, int offset, int count) {
        return lexRangeHeadAsync(toElement, toInclusive, offset, count);
    }
    
    @Override
    public Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive, int offset, int count) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue, "LIMIT", offset, count);
    }

    @Override
    public Collection<String> rangeTail(String fromElement, boolean fromInclusive, int offset, int count) {
        return lexRangeTail(fromElement, fromInclusive, offset, count);
    }
    
    @Override
    public Collection<String> lexRangeTail(String fromElement, boolean fromInclusive, int offset, int count) {
        return get(rangeTailAsync(fromElement, fromInclusive, offset, count));
    }

    @Override
    public Future<Collection<String>> rangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count) {
        return lexRangeTailAsync(fromElement, fromInclusive, offset, count);
    }
    
    @Override
    public Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+", "LIMIT", offset, count);
    }

    @Override
    public Future<Collection<String>> rangeAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive, int offset, int count) {
        return lexRangeAsync(fromElement, fromInclusive, toElement, toInclusive, offset, count);
    }
    
    @Override
    public Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue, "LIMIT", offset, count);
    }
    
    @Override
    public int countTail(String fromElement, boolean fromInclusive) {
        return lexCountTail(fromElement, fromInclusive);
    }

    @Override
    public int lexCountTail(String fromElement, boolean fromInclusive) {
        return get(countTailAsync(fromElement, fromInclusive));
    }
    
    @Override
    public Future<Integer> countTailAsync(String fromElement, boolean fromInclusive) {
        return lexCountTailAsync(fromElement, fromInclusive);
    }

    @Override
    public Future<Integer> lexCountTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), fromValue, "+");
    }
    
    @Override
    public int countHead(String toElement, boolean toInclusive) {
        return lexCountHead(toElement, toInclusive);
    }

    @Override
    public int lexCountHead(String toElement, boolean toInclusive) {
        return get(countHeadAsync(toElement, toInclusive));
    }
    
    @Override
    public Future<Integer> countHeadAsync(String toElement, boolean toInclusive) {
        return lexCountHeadAsync(toElement, toInclusive);
    }

    @Override
    public Future<Integer> lexCountHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), "-", toValue);
    }

    @Override
    public int count(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return lexCount(fromElement, fromInclusive, toElement, toInclusive);
    }
    
    @Override
    public int lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(countAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public Future<Integer> countAsync(String fromElement, boolean fromInclusive, String toElement,
            boolean toInclusive) {
        return lexCountAsync(fromElement, fromInclusive, toElement, toInclusive);
    }
    
    @Override
    public Future<Integer> lexCountAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
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
    public Future<Boolean> addAsync(String e) {
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_BOOL_RAW, getName(), 0, e);
    }

    @Override
    public Future<Boolean> addAllAsync(Collection<? extends String> c) {
        if (c.isEmpty()) {
            return newSucceededFuture(false);
        }
        List<Object> params = new ArrayList<Object>(2*c.size());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_BOOL_RAW, getName(), params.toArray());
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
    public Future<Collection<String>> rangeAsync(int startIndex, int endIndex) {
        return valueRangeAsync(startIndex, endIndex);
    }
    
}
