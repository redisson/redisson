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
public class MapCacheEvictionTask extends EvictionTask {

    private final String name;
    private final String timeoutSetName;
    private final String maxIdleSetName;
    
    public MapCacheEvictionTask(String name, String timeoutSetName, String maxIdleSetName, CommandAsyncExecutor executor) {
        super(executor);
        this.name = name;
        this.timeoutSetName = timeoutSetName;
        this.maxIdleSetName = maxIdleSetName;
    }

    @Override
    RFuture<Integer> execute() {
        return executor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_INTEGER,
                "local expiredKeys1 = redis.call('zrangebyscore', KEYS[2], 0, ARGV[1], 'limit', 0, ARGV[2]); "
              + "if #expiredKeys1 > 0 then "
                  + "redis.call('zrem', KEYS[3], unpack(expiredKeys1)); "
                  + "redis.call('zrem', KEYS[2], unpack(expiredKeys1)); "
                  + "redis.call('hdel', KEYS[1], unpack(expiredKeys1)); "
              + "end; "
              + "local expiredKeys2 = redis.call('zrangebyscore', KEYS[3], 0, ARGV[1], 'limit', 0, ARGV[2]); "
              + "if #expiredKeys2 > 0 then "
                  + "redis.call('zrem', KEYS[3], unpack(expiredKeys2)); "
                  + "redis.call('zrem', KEYS[2], unpack(expiredKeys2)); "
                  + "redis.call('hdel', KEYS[1], unpack(expiredKeys2)); "
              + "end; "
              + "return #expiredKeys1 + #expiredKeys2;",
              Arrays.<Object>asList(name, timeoutSetName, maxIdleSetName), System.currentTimeMillis(), keysLimit);
    }
    
}
