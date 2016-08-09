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
package org.redisson.reactive;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import org.reactivestreams.Publisher;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;

public class RedissonLexSortedSetReactive extends RedissonScoredSortedSetReactive<String> implements RLexSortedSetReactive {

    public RedissonLexSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(StringCodec.INSTANCE, commandExecutor, name);
    }

    @Override
    public Publisher<Long> addAll(Publisher<? extends String> c) {
        return new PublisherAdder<String>(this).addAll(c);
    }

    @Override
    public Publisher<Integer> removeRangeHeadByLex(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), "-", toValue);
    }

    @Override
    public Publisher<Integer> removeRangeTailByLex(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, "+");
    }

    @Override
    public Publisher<Integer> removeRangeByLex(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZREMRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Publisher<Collection<String>> lexRangeHead(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue);
    }

    @Override
    public Publisher<Collection<String>> lexRangeTail(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+");
    }


    @Override
    public Publisher<Collection<String>> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue);
    }

    @Override
    public Publisher<Collection<String>> lexRangeHead(String toElement, boolean toInclusive, int offset, int count) {
        String toValue = value(toElement, toInclusive);
        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), "-", toValue, "LIMIT", offset, count);
    }

    @Override
    public Publisher<Collection<String>> lexRangeTail(String fromElement, boolean fromInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, "+", "LIMIT", offset, count);
    }

    @Override
    public Publisher<Collection<String>> lexRange(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive, int offset, int count) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZRANGEBYLEX, getName(), fromValue, toValue, "LIMIT", offset, count);
    }

    @Override
    public Publisher<Integer> lexCountTail(String fromElement, boolean fromInclusive) {
        String fromValue = value(fromElement, fromInclusive);

        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), fromValue, "+");
    }

    @Override
    public Publisher<Integer> lexCountHead(String toElement, boolean toInclusive) {
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), "-", toValue);
    }

    @Override
    public Publisher<Integer> lexCount(String fromElement, boolean fromInclusive, String toElement, boolean toInclusive) {
        String fromValue = value(fromElement, fromInclusive);
        String toValue = value(toElement, toInclusive);

        return commandExecutor.readReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZLEXCOUNT, getName(), fromValue, toValue);
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
    public Publisher<Long> add(String e) {
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_RAW, getName(), 0, e);
    }

    @Override
    public Publisher<Long> addAll(Collection<? extends String> c) {
        List<Object> params = new ArrayList<Object>(2*c.size());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_RAW, getName(), params.toArray());
    }

}
