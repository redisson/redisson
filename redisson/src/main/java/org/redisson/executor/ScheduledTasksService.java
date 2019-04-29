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
package org.redisson.executor;

import java.util.Arrays;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ThreadLocalRandom;

import org.redisson.RedissonExecutorService;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.executor.params.ScheduledParameters;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ScheduledTasksService extends TasksService {

    private RequestId requestId;
    
    public ScheduledTasksService(Codec codec, String name, CommandExecutor commandExecutor, String redissonId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, name, commandExecutor, redissonId, responses);
    }
    
    public void setRequestId(RequestId requestId) {
        this.requestId = requestId;
    }
    
    @Override
    protected RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request) {
        ScheduledParameters params = (ScheduledParameters) request.getArgs()[0];
        params.setRequestId(request.getId());
        
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // check if executor service not in shutdown state
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "local retryInterval = redis.call('get', KEYS[6]); "
                    + "if retryInterval ~= false then "
                        + "local time = tonumber(ARGV[1]) + tonumber(retryInterval);"
                        + "redis.call('zadd', KEYS[3], time, 'ff' .. ARGV[2]);"
                    + "elseif tonumber(ARGV[4]) > 0 then "
                        + "redis.call('set', KEYS[6], ARGV[4]);"
                        + "local time = tonumber(ARGV[1]) + tonumber(ARGV[4]);"
                        + "redis.call('zadd', KEYS[3], time, 'ff' .. ARGV[2]);"
                    + "end; "

                    + "redis.call('zadd', KEYS[3], ARGV[1], ARGV[2]);"
                    + "redis.call('hset', KEYS[5], ARGV[2], ARGV[3]);"
                    + "redis.call('incr', KEYS[1]);"
                    + "local v = redis.call('zrange', KEYS[3], 0, 0); "
                    // if new task added to queue head then publish its startTime 
                    // to all scheduler workers 
                    + "if v[1] == ARGV[2] then "
                       + "redis.call('publish', KEYS[4], ARGV[1]); "
                    + "end "
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounterName, statusName, schedulerQueueName, schedulerChannelName, tasksName, tasksRetryIntervalName),
                params.getStartTime(), request.getId(), encode(request), tasksRetryInterval);
    }
    
    @Override
    protected RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId) {
        return commandExecutor.evalWriteAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                   // remove from scheduler queue
                    "if redis.call('exists', KEYS[3]) == 0 then "
                      + "return 1;"
                  + "end;"
                      
                  + "local task = redis.call('hget', KEYS[6], ARGV[1]); "
                  + "redis.call('hdel', KEYS[6], ARGV[1]); "
                  
                  + "redis.call('zrem', KEYS[2], 'ff' .. ARGV[1]); "
                  + "local removedScheduled = redis.call('zrem', KEYS[2], ARGV[1]); "
                  + "local removed = redis.call('lrem', KEYS[1], 1, ARGV[1]); "

                  // remove from executor queue
                  + "if task ~= false and (removed > 0 or removedScheduled > 0) then "
                      + "if redis.call('decr', KEYS[3]) == 0 then "
                         + "redis.call('del', KEYS[3]);"
                         + "if redis.call('get', KEYS[4]) == ARGV[2] then "
                            + "redis.call('del', KEYS[7]);"
                            + "redis.call('set', KEYS[4], ARGV[3]);"
                            + "redis.call('publish', KEYS[5], ARGV[3]);"
                         + "end;"
                      + "end;"
                      + "return 1;"
                  + "end;"
                  + "if task == false then "
                      + "return 1; "
                  + "end;"
                  + "return 0;",
              Arrays.<Object>asList(requestQueueName, schedulerQueueName, tasksCounterName, statusName, terminationTopicName, tasksName, tasksRetryIntervalName), 
              taskId.toString(), RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }
    
    @Override
    protected long getTimeout(Long executionTimeoutInMillis, RemoteServiceRequest request) {
        if (request.getArgs()[0].getClass() == ScheduledParameters.class) {
            ScheduledParameters params = (ScheduledParameters) request.getArgs()[0];
            return executionTimeoutInMillis + params.getStartTime() - System.currentTimeMillis();
        }
        return executionTimeoutInMillis;
    }
    
    @Override
    protected RequestId generateRequestId() {
        if (requestId == null) {
            byte[] id = new byte[17];
            ThreadLocalRandom.current().nextBytes(id);
            id[0] = 1;
            return new RequestId(id);
        }
        return requestId;
    }    

}
