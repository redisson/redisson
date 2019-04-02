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
package org.redisson.eviction;

import java.util.Arrays;

import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class MultimapEvictionTask extends EvictionTask {

    private final String name;
    private final String timeoutSetName;
    
    public MultimapEvictionTask(String name, String timeoutSetName, CommandAsyncExecutor executor) {
        super(executor);
        this.name = name;
        this.timeoutSetName = timeoutSetName;
    }

    @Override
    String getName() {
        return name;
    }
    
    RFuture<Integer> execute() {
        return executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredKeys = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
              + "if #expiredKeys > 0 then "
                  + "redis.call('zrem', KEYS[2], unpack(expiredKeys)); "
                  
                  + "local values = redis.call('hmget', KEYS[1], unpack(expiredKeys)); "
                  + "local keys = {}; "
                  + "for i, v in ipairs(values) do "
                      + "local name = '{' .. KEYS[1] .. '}:' .. v; "
                      + "table.insert(keys, name); "
                  + "end; "
                  + "redis.call('del', unpack(keys)); "
                  
                  + "redis.call('hdel', KEYS[1], unpack(expiredKeys)); "
              + "end; "
              + "return #expiredKeys;",
              Arrays.<Object>asList(name, timeoutSetName), System.currentTimeMillis(), keysLimit);
    }
    
}
