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
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.pubsub.SemaphorePubSub;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonQueueSemaphore extends RedissonSemaphore {

    private String queueName;
    private Object value;
    private Collection<?> values;
    
    public RedissonQueueSemaphore(CommandExecutor commandExecutor, String name, 
            SemaphorePubSub semaphorePubSub) {
        super(commandExecutor, name, semaphorePubSub);
    }
    
    public void setQueueName(String queueName) {
        this.queueName = queueName;
    }
    
    public void setValues(Collection<?> values) {
        this.values = values;
    }
    
    public void setValue(Object value) {
        this.value = value;
    }

    public RFuture<Boolean> tryAcquireAsync(int permits) {
        List<Object> params;
        if (values != null) {
            params = new ArrayList<Object>(values.size() + 1);
            params.add(values.size());
            for (Object value : values) {
                params.add(encode(value));
            }
        } else {
            params = new ArrayList<Object>(2);
            params.add(1);
            params.add(encode(value));
        }
        return commandExecutor.evalWriteAsync(getName(), codec, RedisCommands.EVAL_BOOLEAN,
                "local value = redis.call('get', KEYS[1]); " +
                    "assert(value ~= false, 'Capacity of queue ' .. KEYS[1] .. ' has not been set'); " +
                    "if (tonumber(value) >= tonumber(ARGV[1])) then " +
                        "redis.call('decrby', KEYS[1], ARGV[1]); " + 
                        "redis.call('rpush', KEYS[2], unpack(ARGV, 2, #ARGV));" +
                        "return 1; " +
                    "end; " +
                    "return 0;",
                    Arrays.<Object>asList(getName(), queueName), params.toArray());
    }

    
}
