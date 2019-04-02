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

import org.redisson.api.RFuture;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ScoredSetEvictionTask extends EvictionTask {

    private final String name;
    private final long shiftInMilliseconds;
    
    public ScoredSetEvictionTask(String name, CommandAsyncExecutor executor, long shiftInMilliseconds) {
        super(executor);
        this.name = name;
        this.shiftInMilliseconds = shiftInMilliseconds;
    }

    @Override
    String getName() {
        return name;
    }
    
    @Override
    RFuture<Integer> execute() {
        return executor.writeAsync(name, LongCodec.INSTANCE, RedisCommands.ZREMRANGEBYSCORE, name, 0, System.currentTimeMillis() - shiftInMilliseconds);
    }
    
}
