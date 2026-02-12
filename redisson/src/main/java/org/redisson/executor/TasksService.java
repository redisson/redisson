/**
 * Copyright (c) 2013-2026 Nikita Koksharov
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
import org.redisson.api.RBlockingQueueAsync;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.params.TaskParameters;
import org.redisson.misc.CompletableFutureWrapper;
import org.redisson.remote.*;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class TasksService extends BaseRemoteService {

    protected String terminationTopicName;
    protected String tasksCounterName;
    protected String statusName;
    protected String tasksName;
    protected String tasksLatchName;
    protected String schedulerQueueName;
    protected String schedulerChannelName;
    protected String tasksRetryIntervalName;
    protected String tasksExpirationTimeName;
    protected long tasksRetryInterval;
    
    public TasksService(Codec codec, String name, CommandAsyncExecutor commandExecutor, String executorId) {
        super(codec, name, commandExecutor, executorId);
    }

    public void setTasksLatchName(String tasksLatchName) {
        this.tasksLatchName = tasksLatchName;
    }

    public void setTasksExpirationTimeName(String tasksExpirationTimeName) {
        this.tasksExpirationTimeName = tasksExpirationTimeName;
    }

    public void setTasksRetryIntervalName(String tasksRetryIntervalName) {
        this.tasksRetryIntervalName = tasksRetryIntervalName;
    }
    
    public void setTasksRetryInterval(long tasksRetryInterval) {
        this.tasksRetryInterval = tasksRetryInterval;
    }
    
    public void setTerminationTopicName(String terminationTopicName) {
        this.terminationTopicName = terminationTopicName;
    }
    
    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }
    
    public void setTasksCounterName(String tasksCounterName) {
        this.tasksCounterName = tasksCounterName;
    }
    
    public void setTasksName(String tasksName) {
        this.tasksName = tasksName;
    }
    
    public void setSchedulerChannelName(String schedulerChannelName) {
        this.schedulerChannelName = schedulerChannelName;
    }
    
    public void setSchedulerQueueName(String scheduledQueueName) {
        this.schedulerQueueName = scheduledQueueName;
    }

    @Override
    protected final CompletableFuture<Boolean> addAsync(String requestQueueName,
                                                        RemoteServiceRequest request, RemotePromise<Object> result) {
        CompletableFuture<Boolean> future = addAsync(requestQueueName, request);
        result.setAddFuture(future);
        
        return future.thenApply(res -> {
            if (!res) {
                throw new IllegalStateException("Task hasn't been added. Check if executorService exists and task id is unique");
            }

            return true;
        });
    }

    protected CommandAsyncExecutor getAddCommandExecutor() {
        return commandExecutor;
    }
    
    protected CompletableFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request) {
        TaskParameters params = (TaskParameters) request.getArgs()[0];

        String taskName = tasksLatchName + ":" + request.getId();

        long retryStartTime = 0;
        if (tasksRetryInterval > 0) {
            retryStartTime = System.currentTimeMillis() + tasksRetryInterval;
        }
        long expireTime = 0;
        if (params.getTtl() > 0) {
            expireTime = System.currentTimeMillis() + params.getTtl();
        }

        RFuture<Boolean> f = getAddCommandExecutor().evalWriteNoRetryAsync(name, StringCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                        // check if executor service not in shutdown state
                        "if redis.call('exists', KEYS[2]) == 0 then "
                            + "redis.call('hset', KEYS[5], ARGV[2], ARGV[3]);"
                            + "redis.call('del', KEYS[9]);"
                            + "redis.call('rpush', KEYS[6], ARGV[2]); "
                            + "redis.call('incr', KEYS[1]);"

                            + "if tonumber(ARGV[5]) > 0 then "
                                + "redis.call('zadd', KEYS[8], ARGV[5], ARGV[2]);"
                            + "end; "

                            + "if tonumber(ARGV[1]) > 0 then "
                                + "local scheduledName = 'ff:' .. ARGV[2];"
                                + "redis.call('set', KEYS[7], ARGV[4]);"
                                + "redis.call('zadd', KEYS[3], ARGV[1], scheduledName);"
                                + "local v = redis.call('zrange', KEYS[3], 0, 0); "
                                // if new task added to queue head then publish its startTime
                                // to all scheduler workers
                                + "if v[1] == scheduledName then "
                                    + "redis.call('publish', KEYS[4], ARGV[1]); "
                                + "end; "
                            + "end;"
                            + "return 1;"
                        + "end;"
                        + "return 0;",
                        Arrays.asList(tasksCounterName, statusName, schedulerQueueName, schedulerChannelName,
                                            tasksName, requestQueueName, tasksRetryIntervalName, tasksExpirationTimeName, taskName),
                        retryStartTime, request.getId(), encode(request), tasksRetryInterval, expireTime);
        return f.toCompletableFuture();
    }
    
    @Override
    protected CompletableFuture<Boolean> removeAsync(String requestQueueName, String taskId) {
        RFuture<Boolean> f = commandExecutor.evalWriteNoRetryAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
          "if redis.call('exists', KEYS[3]) == 0 then " +
                    "return nil;" +
                "end;" +

                "redis.call('zrem', KEYS[2], 'ff:' .. ARGV[1]); "
              + "redis.call('zrem', KEYS[8], ARGV[1]); "
              + "local task = redis.call('hget', KEYS[6], ARGV[1]); "
              + "redis.call('hdel', KEYS[6], ARGV[1]); "

              + "local removed = redis.call('lrem', KEYS[1], 1, ARGV[1]); "

               // remove from executor queue
              + "if task ~= false and removed > 0 then "
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
          Arrays.asList(requestQueueName, schedulerQueueName, tasksCounterName, statusName, terminationTopicName,
                                tasksName, tasksRetryIntervalName, tasksExpirationTimeName),
          taskId, RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
        return f.toCompletableFuture();
    }

    @Override
    protected String generateRequestId(Object[] args) {
        TaskParameters params = (TaskParameters) args[0];
        return params.getRequestId();
    }

    public RFuture<Boolean> cancelExecutionAsync(String requestId) {
        String requestQueueName = getRequestQueueName(RemoteExecutorService.class);
        CompletableFuture<Boolean> removeFuture = removeAsync(requestQueueName, requestId);
        CompletableFuture<Boolean> f = removeFuture.thenCompose(res -> {
            if (res == null) {
                return CompletableFuture.completedFuture(null);
            }
            if (res) {
                return CompletableFuture.completedFuture(true);
            }

            RMap<String, RemoteServiceCancelRequest> canceledRequests = getMap(cancelRequestMapName);
            canceledRequests.putAsync(requestId, new RemoteServiceCancelRequest(true, true));
            canceledRequests.expireAsync(60, TimeUnit.SECONDS);

            CompletableFuture<RemoteServiceCancelResponse> response = scheduleCancelResponseCheck(cancelResponseMapName, requestId);
            return response.thenApply(r -> {
                if (r == null) {
                    return false;
                }
                return r.isCanceled();
            });
        });

        removeFuture.thenAccept(r -> {
            commandExecutor.getServiceManager().newTimeout(timeout -> {
                f.complete(false);
            }, 60, TimeUnit.SECONDS);
        });

        return new CompletableFutureWrapper<>(f);
    }

    private CompletableFuture<RemoteServiceCancelResponse> scheduleCancelResponseCheck(String mapName, String requestId) {
        CompletableFuture<RemoteServiceCancelResponse> cancelResponse = new CompletableFuture<>();

        commandExecutor.getServiceManager().newTimeout(timeout -> {
            if (cancelResponse.isDone()) {
                return;
            }

            RMap<String, RemoteServiceCancelResponse> canceledResponses = getMap(mapName);
            RFuture<RemoteServiceCancelResponse> removeFuture = canceledResponses.removeAsync(requestId);
            CompletableFuture<RemoteServiceCancelResponse> future = removeFuture.thenCompose(response -> {
                if (response == null) {
                    RFuture<Boolean> f = hasTaskAsync(requestId);
                    return f.thenCompose(r -> {
                        if (r) {
                            return scheduleCancelResponseCheck(mapName, requestId);
                        }

                        RemoteServiceCancelResponse resp = new RemoteServiceCancelResponse(requestId, false);
                        return CompletableFuture.completedFuture(resp);
                    });
                }

                RBlockingQueueAsync<RRemoteServiceResponse> queue = getBlockingQueue(responseQueueName, codec);
                return queue.removeAsync(response).thenApply(r -> response);
            }).whenComplete((r, ex) -> {
                if (ex != null) {
                    scheduleCancelResponseCheck(mapName, requestId);
                }
            }).toCompletableFuture();

            commandExecutor.transfer(future, cancelResponse);
        }, 3000, TimeUnit.MILLISECONDS);
        return cancelResponse;
    }

    public RFuture<Boolean> hasTaskAsync(String taskId) {
        return commandExecutor.writeAsync(tasksName, LongCodec.INSTANCE, RedisCommands.HEXISTS, tasksName, taskId);
    }

}
