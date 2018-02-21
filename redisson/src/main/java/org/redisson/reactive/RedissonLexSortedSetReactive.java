/**
 * Copyright 2018 Nikita Koksharov
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
import java.util.function.Supplier;

import org.reactivestreams.Publisher;
import org.redisson.RedissonLexSortedSet;
import org.redisson.api.RFuture;
import org.redisson.api.RLexSortedSetAsync;
import org.redisson.api.RLexSortedSetReactive;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;


/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLexSortedSetReactive extends RedissonScoredSortedSetReactive<String> implements RLexSortedSetReactive {

    private final RLexSortedSetAsync instance;
    
    public RedissonLexSortedSetReactive(CommandReactiveExecutor commandExecutor, String name) {
        super(StringCodec.INSTANCE, commandExecutor, name);
        instance = new RedissonLexSortedSet(commandExecutor, name, null);
    }

    @Override
    public Publisher<Integer> addAll(Publisher<? extends String> c) {
        return new PublisherAdder<String>(this).addAll(c);
    }

    @Override
    public Publisher<Integer> removeRangeHead(final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.removeRangeHeadAsync(toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Integer> removeRangeTail(final String fromElement, final boolean fromInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.removeRangeTailAsync(fromElement, fromInclusive);
            }
        });
    }

    @Override
    public Publisher<Integer> removeRange(final String fromElement, final boolean fromInclusive, final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.removeRangeAsync(fromElement, fromInclusive, toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> rangeHead(final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeHeadAsync(toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> rangeTail(final String fromElement, final boolean fromInclusive) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeTailAsync(fromElement, fromInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> range(final String fromElement, final boolean fromInclusive, final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeAsync(fromElement, fromInclusive, toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> rangeHead(final String toElement, final boolean toInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeHeadAsync(toElement, toInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> rangeTail(final String fromElement, final boolean fromInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeTailAsync(fromElement, fromInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Collection<String>> range(final String fromElement, final boolean fromInclusive, final String toElement, final boolean toInclusive, final int offset, final int count) {
        return reactive(new Supplier<RFuture<Collection<String>>>() {
            @Override
            public RFuture<Collection<String>> get() {
                return instance.rangeAsync(fromElement, fromInclusive, toElement, toInclusive, offset, count);
            }
        });
    }

    @Override
    public Publisher<Integer> countTail(final String fromElement, final boolean fromInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.countTailAsync(fromElement, fromInclusive);
            }
        });
    }

    @Override
    public Publisher<Integer> countHead(final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.countHeadAsync(toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Integer> count(final String fromElement, final boolean fromInclusive, final String toElement, final boolean toInclusive) {
        return reactive(new Supplier<RFuture<Integer>>() {
            @Override
            public RFuture<Integer> get() {
                return instance.countAsync(fromElement, fromInclusive, toElement, toInclusive);
            }
        });
    }

    @Override
    public Publisher<Integer> add(final String e) {
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_INT, getName(), 0, e);
    }

    @Override
    public Publisher<Integer> addAll(Collection<? extends String> c) {
        List<Object> params = new ArrayList<Object>(2*c.size());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeReactive(getName(), StringCodec.INSTANCE, RedisCommands.ZADD_INT, getName(), params.toArray());
    }

    @Override
    public Publisher<Collection<String>> range(int startIndex, int endIndex) {
        return valueRange(startIndex, endIndex);
    }

}
