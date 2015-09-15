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

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.redisson.client.codec.Codec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.core.RLexSortedSet;

import io.netty.util.concurrent.Future;

public class RedissonLexSortedSet extends RedissonScoredSortedSet<String> implements RLexSortedSet {

    public RedissonLexSortedSet(CommandExecutor commandExecutor, String name) {
        super(StringCodec.INSTANCE, commandExecutor, name);
    }

    @Override
    public int removeRangeByLex(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(removeRangeByLexAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public int removeRangeHeadByLex(String toElement, boolean toInclusive) {
        return get(removeRangeHeadByLexAsync(toElement, toInclusive));
    }

    @Override
    public Future<Integer> removeRangeHeadByLexAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), "-", toValue);
    }

    @Override
    public int removeRangeTailByLex(String fromElement, boolean fromInclusive) {
        return get(removeRangeTailByLexAsync(fromElement, fromInclusive));
    }

    @Override
    public Future<Integer> removeRangeTailByLexAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, "+");
    }

    @Override
    public Future<Integer> removeRangeByLexAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Collection<String> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(lexRangeAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public Collection<String> lexRangeHead(String toElement, boolean toInclusive) {
        return get(lexRangeHeadAsync(toElement, toInclusive));
    }

    @Override
    public Future<Collection<String>> lexRangeHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue);
    }

    @Override
    public Collection<String> lexRangeTail(String fromElement, boolean fromInclusive) {
        return get(lexRangeTailAsync(fromElement, fromInclusive));
    }

    @Override
    public Future<Collection<String>> lexRangeTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+");
    }


    @Override
    public Future<Collection<String>> lexRangeAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public int lexCountTail(String fromElement, boolean fromInclusive) {
        return get(lexCountTailAsync(fromElement, fromInclusive));
    }

    @Override
    public Future<Integer> lexCountTailAsync(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);

        return commandExecutor.readAsync(getName(), RedisCommands.ZLEXCOUNT, getName(), fromValue, "+");
    }

    @Override
    public int lexCountHead(String toElement, boolean toInclusive) {
        return get(lexCountHeadAsync(toElement, toInclusive));
    }

    @Override
    public Future<Integer> lexCountHeadAsync(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), RedisCommands.ZLEXCOUNT, getName(), "-", toValue);
    }

    @Override
    public int lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        return get(lexCountAsync(fromElement, fromInclusive, toElement, toInclusive));
    }

    @Override
    public Future<Integer> lexCountAsync(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readAsync(getName(), RedisCommands.ZLEXCOUNT, getName(), fromValue, toValue);
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
        return addAsync(0, e);
    }

    @Override
    public Future<Boolean> addAllAsync(Collection<? extends String> c) {
        List<Object> params = new ArrayList<Object>(2*c.size());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.ZADD, getName(), params.toArray());
    }

    @Override
    public boolean add(String e) {
        return get(addAsync(e));
    }

    @Override
    public boolean addAll(Collection<? extends String> c) {
        return get(addAllAsync(c));
    }

}
