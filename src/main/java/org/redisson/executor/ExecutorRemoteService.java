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
package org.redisson.executor;

import java.util.Arrays;

import org.redisson.Redisson;
import org.redisson.RedissonRemoteService;
import org.redisson.api.RAtomicLong;
import org.redisson.api.RBlockingQueue;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.remote.RemoteServiceRequest;

import io.netty.util.concurrent.Future;

public class ExecutorRemoteService extends RedissonRemoteService {

    private final RAtomicLong tasksCounter;
    private final RAtomicLong status;
    
    public ExecutorRemoteService(Codec codec, Redisson redisson, String name, CommandExecutor commandExecutor) {
        super(codec, redisson, name, commandExecutor);
        
        String objectName = name + ":{"+ RemoteExecutorService.class.getName() + "}";
        tasksCounter = redisson.getAtomicLong(objectName + ":counter");
        status = redisson.getAtomicLong(objectName + ":status");
    }

    @Override
    protected Future<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue,
            RemoteServiceRequest request) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "redis.call('rpush', KEYS[3], ARGV[1]); "
                    + "redis.call('incr', KEYS[1]);"
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounter.getName(), status.getName(), requestQueue.getName()),
                encode(request));
    }

}
