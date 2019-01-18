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
import java.util.List;

import org.redisson.api.RFuture;
import org.redisson.api.RHyperLogLog;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value
 */
public class RedissonHyperLogLog<V> extends RedissonExpirable implements RHyperLogLog<V> {

    public RedissonHyperLogLog(CommandAsyncExecutor commandExecutor, String name) {
        super(commandExecutor, name);
    }

    public RedissonHyperLogLog(Codec codec, CommandAsyncExecutor commandExecutor, String name) {
        super(codec, commandExecutor, name);
    }

    @Override
    public boolean add(V obj) {
        return get(addAsync(obj));
    }

    @Override
    public boolean addAll(Collection<V> objects) {
        return get(addAllAsync(objects));
    }

    @Override
    public long count() {
        return get(countAsync());
    }

    @Override
    public long countWith(String... otherLogNames) {
        return get(countWithAsync(otherLogNames));
    }

    @Override
    public void mergeWith(String... otherLogNames) {
        get(mergeWithAsync(otherLogNames));
    }

    @Override
    public RFuture<Boolean> addAsync(V obj) {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PFADD, getName(), encode(obj));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<V> objects) {
        List<Object> args = new ArrayList<Object>(objects.size() + 1);
        args.add(getName());
        encode(args, objects);
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PFADD, args.toArray());
    }

    @Override
    public RFuture<Long> countAsync() {
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PFCOUNT, getName());
    }

    @Override
    public RFuture<Long> countWithAsync(String... otherLogNames) {
        List<Object> args = new ArrayList<Object>(otherLogNames.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(otherLogNames));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PFCOUNT, args.toArray());
    }

    @Override
    public RFuture<Void> mergeWithAsync(String... otherLogNames) {
        List<Object> args = new ArrayList<Object>(otherLogNames.length + 1);
        args.add(getName());
        args.addAll(Arrays.asList(otherLogNames));
        return commandExecutor.writeAsync(getName(), codec, RedisCommands.PFMERGE, args.toArray());
    }

}
