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
import java.util.concurrent.ConcurrentMap;

import org.redisson.RedissonExecutorService;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
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
    private String schedulerQueueName;
    private String schedulerChannelName;
    
    public ScheduledTasksService(Codec codec, RedissonClient redisson, String name, CommandExecutor commandExecutor, String redissonId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, redisson, name, commandExecutor, redissonId, responses);
    }
    
    public void setRequestId(RequestId requestId) {
        this.requestId = requestId;
    }
    
    public void setSchedulerChannelName(String schedulerChannelName) {
        this.schedulerChannelName = schedulerChannelName;
    }
    
    public void setSchedulerQueueName(String scheduledQueueName) {
        this.schedulerQueueName = scheduledQueueName;
    }
    
    @Override
    protected RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request) {
        int requestIndex = 0;
        if ("scheduleCallable".equals(request.getMethodName())
                || "scheduleRunnable".equals(request.getMethodName())) {
            requestIndex = 4;
        }
        if ("scheduleAtFixedRate".equals(request.getMethodName())
                || "scheduleWithFixedDelay".equals(request.getMethodName())
                    || "schedule".equals(request.getMethodName())) {
            requestIndex = 6;
        }

        request.getArgs()[requestIndex] = request.getId();
        Long startTime = (Long)request.getArgs()[3];
        
        if (requestId != null) {
            return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    // check if executor service not in shutdown state and previous task exists
                    "if redis.call('exists', KEYS[2]) == 0 and redis.call('hexists', KEYS[5], ARGV[2]) == 1 then "
                        + "redis.call('zadd', KEYS[3], ARGV[1], ARGV[2]);"
                        + "redis.call('hset', KEYS[5], ARGV[2], ARGV[3]);"
                        + "redis.call('incr', KEYS[1]);"
                        // if new task added to queue head then publish its startTime 
                        // to all scheduler workers 
                        + "local v = redis.call('zrange', KEYS[3], 0, 0); "
                        + "if v[1] == ARGV[2] then "
                           + "redis.call('publish', KEYS[4], ARGV[1]); "
                        + "end "
                        + "return 1;"
                    + "end;"
                    + "return 0;", 
                    Arrays.<Object>asList(tasksCounterName, statusName, schedulerQueueName, schedulerChannelName, tasksName),
                    startTime, request.getId(), encode(request));
        }
        
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                // check if executor service not in shutdown state
                "if redis.call('exists', KEYS[2]) == 0 then "
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
                Arrays.<Object>asList(tasksCounterName, statusName, schedulerQueueName, schedulerChannelName, tasksName),
                startTime, request.getId(), encode(request));
    }
    
    @Override
    protected RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                   // remove from scheduler queue
                    "if redis.call('zrem', KEYS[2], ARGV[1]) > 0 then "
                      + "redis.call('hdel', KEYS[6], ARGV[1]); "
                      + "if redis.call('decr', KEYS[3]) == 0 then "
                         + "redis.call('del', KEYS[3]);"
                         + "if redis.call('get', KEYS[4]) == ARGV[2] then "
                            + "redis.call('set', KEYS[4], ARGV[3]);"
                            + "redis.call('publish', KEYS[5], ARGV[3]);"
                         + "end;"
                      + "end;"
                      + "return 1;"
                  + "end;"
                  + "local task = redis.call('hget', KEYS[6], ARGV[1]); "
                   // remove from executor queue
                  + "if task ~= false and redis.call('lrem', KEYS[1], 1, ARGV[1]) > 0 then "
                      + "redis.call('hdel', KEYS[6], ARGV[1]); "
                      + "if redis.call('decr', KEYS[3]) == 0 then "
                         + "redis.call('del', KEYS[3]);"
                         + "if redis.call('get', KEYS[4]) == ARGV[2] then "
                            + "redis.call('set', KEYS[4], ARGV[3]);"
                            + "redis.call('publish', KEYS[5], ARGV[3]);"
                         + "end;"
                      + "end;"
                      + "return 1;"
                  + "end;"
                   // delete scheduled task
                  + "redis.call('hdel', KEYS[6], ARGV[1]); "
                  + "return 0;",
              Arrays.<Object>asList(requestQueueName, schedulerQueueName, tasksCounterName, statusName, terminationTopicName, tasksName), 
              taskId.toString(), RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }
    
    @Override
    protected RequestId generateRequestId() {
        if (requestId == null) {
            return super.generateRequestId();
        }
        return requestId;
    }    

}
