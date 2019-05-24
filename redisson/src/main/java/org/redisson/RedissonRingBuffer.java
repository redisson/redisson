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
import org.redisson.api.RRingBuffer;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.client.protocol.RedisStrictCommand;
import org.redisson.client.protocol.convertor.IntegerReplayConvertor;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RedissonPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 * @param <V> value type
 */
public class RedissonRingBuffer<V> extends RedissonQueue<V> implements RRingBuffer<V> {

    private static final RedisStrictCommand<Integer> GET_INTEGER = new RedisStrictCommand<Integer>("GET", new IntegerReplayConvertor(0));
    
    private final String settingsName;
    
    public RedissonRingBuffer(CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(commandExecutor, name, redisson);
        settingsName = prefixName("redisson_rb", getName());
    }
    
    public RedissonRingBuffer(Codec codec, CommandAsyncExecutor commandExecutor, String name, RedissonClient redisson) {
        super(codec, commandExecutor, name, redisson);
        settingsName = prefixName("redisson_rb", getName());
    }

    @Override
    public RFuture<Boolean> trySetCapacityAsync(int capacity) {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, RedisCommands.SETNX, settingsName, capacity);
    }
    
    @Override
    public boolean trySetCapacity(int capacity) {
        return get(trySetCapacityAsync(capacity));
    }
    
    @Override
    public RFuture<Boolean> addAsync(V e) {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local limit = redis.call('get', KEYS[2]); "
              + "assert(limit ~= false, 'RingBuffer capacity is not defined'); "
              + "local size = redis.call('rpush', KEYS[1], ARGV[1]); "
              
              + "if size > tonumber(limit) then "
                  + "redis.call('lpop', KEYS[1]); "
              + "end; "
              + "return 1; ",
             Arrays.asList(getName(), settingsName), encode(e));
    }

    @Override
    public RFuture<Boolean> addAllAsync(Collection<? extends V> c) {
        if (c.isEmpty()) {
            return RedissonPromise.newSucceededFuture(false);
        }

        List<Object> args = new ArrayList<>(c.size());
        encode(args, c);
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local limit = redis.call('get', KEYS[2]); "
              + "assert(limit ~= false, 'RingBuffer capacity is not defined'); "

              + "local size = 0; "
              + "for i=1, #ARGV,5000 do "
                 + "size = redis.call('rpush', KEYS[1], unpack(ARGV, i, math.min(i+4999, #ARGV))); "
              + "end; "
              
              + "local extraSize = size - tonumber(limit); "
              + "if extraSize > 0 then "
                  + "redis.call('ltrim', KEYS[1], extraSize, -1); "
              + "end; "
              + "return 1; ",
             Arrays.asList(getName(), settingsName), args.toArray());
    }
    
    @Override
    public int remainingCapacity() {
        return get(remainingCapacityAsync());
    }

    @Override
    public RFuture<Integer> remainingCapacityAsync() {
        return commandExecutor.evalWriteAsync(getName(), LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local limit = redis.call('get', KEYS[2]); "
              + "assert(limit ~= false, 'RingBuffer capacity is not defined'); "
              + "local size = redis.call('llen', KEYS[1]); "
              + "return math.max(tonumber(limit) - size, 0); ",
             Arrays.asList(getName(), settingsName));
        
    }

    @Override
    public RFuture<Integer> capacityAsync() {
        return commandExecutor.writeAsync(getName(), LongCodec.INSTANCE, GET_INTEGER, settingsName);
    }

    @Override
    public int capacity() {
        return get(capacityAsync());
    }
    
}
