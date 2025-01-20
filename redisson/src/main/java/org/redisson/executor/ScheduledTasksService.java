/**
 * Copyright (c) 2013-2024 Nikita Koksharov
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

import org.redisson.RedissonExecutorService;
import org.redisson.api.RFuture;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.params.ScheduledParameters;
import org.redisson.remote.RemoteServiceRequest;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ScheduledTasksService extends TasksService {

    private String requestId;
    
    public ScheduledTasksService(Codec codec, String name, CommandAsyncExecutor commandExecutor, String redissonId) {
        super(codec, name, commandExecutor, redissonId);
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    @Override
    protected CompletableFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request) {
        ScheduledParameters params = (ScheduledParameters) request.getArgs()[0];

        String taskName = tasksLatchName + ":" + request.getId();

        long expireTime = 0;
        if (params.getTtl() > 0) {
            expireTime = System.currentTimeMillis() + params.getTtl();
        }
        
        String script = "";
        if (requestId != null) {
            script += "if redis.call('hget', KEYS[5], ARGV[2]) == false then "
                        + "return 0;"
                    + "end;";
        }
        
        script +=
                // check if executor service not in shutdown state
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "local retryInterval = redis.call('get', KEYS[6]); "
                    + "if retryInterval ~= false then "
                        + "local time = tonumber(ARGV[1]) + tonumber(retryInterval);"
                        + "redis.call('zadd', KEYS[3], time, 'ff:' .. ARGV[2]);"
                    + "elseif tonumber(ARGV[4]) > 0 then "
                        + "redis.call('set', KEYS[6], ARGV[4]);"
                        + "local time = tonumber(ARGV[1]) + tonumber(ARGV[4]);"
                        + "redis.call('zadd', KEYS[3], time, 'ff:' .. ARGV[2]);"
                    + "end; "

                    + "if tonumber(ARGV[5]) > 0 then "
                        + "redis.call('zadd', KEYS[7], ARGV[5], ARGV[2]);"
                    + "end; "

                    + "redis.call('zadd', KEYS[3], ARGV[1], ARGV[2]);"
                    + "redis.call('hset', KEYS[5], ARGV[2], ARGV[3]);"
                    + "redis.call('del', KEYS[8]);"
                    + "redis.call('incr', KEYS[1]);"
                    + "local v = redis.call('zrange', KEYS[3], 0, 0); "
                    // if new task added to queue head then publish its startTime
                    // to all scheduler workers
                    + "if v[1] == ARGV[2] then "
                       + "redis.call('publish', KEYS[4], ARGV[1]); "
                    + "end "
                    + "return 1;"
                + "end;"
                + "return 0;";
        
        RFuture<Boolean> f = commandExecutor.evalWriteNoRetryAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN, script,
                Arrays.asList(tasksCounterName, statusName, schedulerQueueName,
                        schedulerChannelName, tasksName, tasksRetryIntervalName, tasksExpirationTimeName, taskName),
                params.getStartTime(), request.getId(), encode(request), tasksRetryInterval, expireTime);
        return f.toCompletableFuture();
    }
    
    @Override
    protected CompletableFuture<Boolean> removeAsync(String requestQueueName, String taskId) {
        RFuture<Boolean> f = commandExecutor.evalWriteNoRetryAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "local task = redis.call('hget', KEYS[6], ARGV[1]); "
                  + "redis.call('hdel', KEYS[6], ARGV[1]); "
                  
                  + "redis.call('zrem', KEYS[2], 'ff:' .. ARGV[1]); "
                  + "redis.call('zrem', KEYS[8], ARGV[1]); "

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
                      + "return nil; "
                  + "end;"
                  + "return 0;",
              Arrays.asList(requestQueueName, schedulerQueueName, tasksCounterName, statusName,
                                terminationTopicName, tasksName, tasksRetryIntervalName, tasksExpirationTimeName),
                taskId, RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
        return f.toCompletableFuture();
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
    protected String generateRequestId(Object[] args) {
        if (requestId == null) {
            return super.generateRequestId(args);
        }
        return requestId;
    }

}
