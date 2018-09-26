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
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandReactiveExecutor;


/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonLexSortedSetReactive {

    private final RedissonScoredSortedSetReactive<String> instance;
    private final CommandReactiveExecutor commandExecutor;
    
    public RedissonLexSortedSetReactive(CommandReactiveExecutor commandExecutor, RedissonScoredSortedSetReactive<String> instance) {
        this.commandExecutor = commandExecutor;
        this.instance = instance;
    }

    public Publisher<Integer> addAll(Publisher<? extends String> c) {
        return new PublisherAdder<String>() {
    @Override
            public Publisher<Integer> add(Object e) {
                return RedissonLexSortedSetReactive.this.add(e);
            }
        }.addAll(c);
    }

    public Publisher<String> iterator() {
        return instance.iterator();
            }

    public Publisher<String> iterator(String pattern) {
        return instance.iterator(pattern);
            }

    public Publisher<String> iterator(int count) {
        return instance.iterator(count);
            }

    public Publisher<String> iterator(String pattern, int count) {
        return instance.iterator(pattern, count);
            }

    public Publisher<Integer> add(Object e) {
        return commandExecutor.writeReactive(instance.getName(), StringCodec.INSTANCE, RedisCommands.ZADD_INT, instance.getName(), 0, e);
            }

    public Publisher<Integer> addAll(Collection<? extends String> c) {
        List<Object> params = new ArrayList<Object>(2*c.size());
        params.add(instance.getName());
        for (Object param : c) {
            params.add(0);
            params.add(param);
        }
        return commandExecutor.writeReactive(instance.getName(), StringCodec.INSTANCE, RedisCommands.ZADD_INT, params.toArray());
    }

    }
