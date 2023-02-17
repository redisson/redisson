/**
 * Copyright (c) 2013-2022 Nikita Koksharov
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
import org.redisson.RedissonObject;
import org.redisson.RedissonRemoteService;
import org.redisson.RedissonShutdownException;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.executor.*;
import org.redisson.client.codec.Codec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.remote.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class RedissonExecutorRemoteService extends RedissonRemoteService {

    private static final Logger log = LoggerFactory.getLogger(RedissonExecutorRemoteService.class);

    private String tasksExpirationTimeName;
    private String tasksCounterName;
    private String statusName;
    private String tasksRetryIntervalName;
    private String terminationTopicName;
    private String schedulerQueueName;
    private long taskTimeout;
    private List<TaskStartedListener> startedListeners;
    private List<TaskFinishedListener> finishedListeners;
    private List<TaskFailureListener> failureListeners;
    private List<TaskSuccessListener> successListeners;

    public RedissonExecutorRemoteService(Codec codec, String name,
                                         CommandAsyncExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, name, commandExecutor, executorId, responses);
    }

    @Override
    protected RFuture<RemoteServiceRequest> getTask(String requestId, RMap<String, RemoteServiceRequest> tasks) {
        return commandExecutor.evalWriteNoRetryAsync(((RedissonObject) tasks).getRawName(), codec, RedisCommands.EVAL_OBJECT,
                  "local value = redis.call('zscore', KEYS[2], ARGV[1]); " +
                  "if (value ~= false and tonumber(value) < tonumber(ARGV[2])) then "
                    + "redis.call('zrem', KEYS[2], ARGV[1]); "

                    + "redis.call('zrem', KEYS[7], ARGV[1]); "
                    + "redis.call('zrem', KEYS[7], 'ff:' .. ARGV[1]);"

                    + "redis.call('hdel', KEYS[1], ARGV[1]); "
                    + "if redis.call('decr', KEYS[3]) == 0 then "
                        + "redis.call('del', KEYS[3]);"
                        + "if redis.call('get', KEYS[4]) == ARGV[3] then "
                            + "redis.call('del', KEYS[5]);"
                            + "redis.call('set', KEYS[4], ARGV[4]);"
                            + "redis.call('publish', KEYS[6], ARGV[4]);"
                        + "end;"
                    + "end;"

                    + "return nil;"
                + "end;"
                + "return redis.call('hget', KEYS[1], ARGV[1]); ",
        Arrays.asList(((RedissonObject) tasks).getRawName(), tasksExpirationTimeName, tasksCounterName, statusName,
                            tasksRetryIntervalName, terminationTopicName, schedulerQueueName),
        requestId, System.currentTimeMillis(), RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

    @Override
    protected <T> void invokeMethod(RemoteServiceRequest request, RemoteServiceMethod method,
                                    CompletableFuture<RemoteServiceCancelRequest> cancelRequestFuture,
                                    CompletableFuture<RRemoteServiceResponse> responsePromise) {
        startedListeners.forEach(l -> l.onStarted(request.getId()));

        if (taskTimeout > 0) {
            commandExecutor.getServiceManager().getGroup().schedule(() -> {
                cancelRequestFuture.complete(new RemoteServiceCancelRequest(true, false));
            }, taskTimeout, TimeUnit.MILLISECONDS);
        }

        try {
            Object result = method.getMethod().invoke(method.getBean(), request.getArgs());

            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), result);
            responsePromise.complete(response);
        } catch (Exception e) {
            if (e instanceof InvocationTargetException
                && e.getCause() instanceof RedissonShutdownException) {
                if (cancelRequestFuture != null) {
                    cancelRequestFuture.cancel(false);
                }
                return;
            }
            RemoteServiceResponse response = new RemoteServiceResponse(request.getId(), e.getCause());
            responsePromise.complete(response);
            log.error("Can't execute: {}", request, e);
        }

        if (cancelRequestFuture != null) {
            cancelRequestFuture.cancel(false);
        }

        if (commandExecutor.getNow(responsePromise) instanceof RemoteServiceResponse) {
            RemoteServiceResponse response = (RemoteServiceResponse) commandExecutor.getNow(responsePromise);
            if (response.getError() == null) {
                successListeners.forEach(l -> l.onSucceeded(request.getId(), response.getResult()));
            } else {
                failureListeners.forEach(l -> l.onFailed(request.getId(), response.getError()));
            }
        } else {
            failureListeners.forEach(l -> l.onFailed(request.getId(), null));
        }

        finishedListeners.forEach(l -> l.onFinished(request.getId()));
    }

    public void setListeners(List<TaskListener> listeners) {
        startedListeners = listeners.stream()
                                .filter(x -> x instanceof TaskStartedListener)
                                .map(x -> (TaskStartedListener) x)
                                .collect(Collectors.toList());

        finishedListeners = listeners.stream()
                                .filter(x -> x instanceof TaskFinishedListener)
                                .map(x -> (TaskFinishedListener) x)
                                .collect(Collectors.toList());

        failureListeners = listeners.stream()
                                .filter(x -> x instanceof TaskFailureListener)
                                .map(x -> (TaskFailureListener) x)
                                .collect(Collectors.toList());

        successListeners = listeners.stream()
                                .filter(x -> x instanceof TaskSuccessListener)
                                .map(x -> (TaskSuccessListener) x)
                                .collect(Collectors.toList());
    }

    public void setTaskTimeout(long taskTimeout) {
        this.taskTimeout = taskTimeout;
    }

    public void setSchedulerQueueName(String schedulerQueueName) {
        this.schedulerQueueName = schedulerQueueName;
    }

    public void setTasksExpirationTimeName(String tasksExpirationTimeName) {
        this.tasksExpirationTimeName = tasksExpirationTimeName;
    }

    public void setTasksCounterName(String tasksCounterName) {
        this.tasksCounterName = tasksCounterName;
    }

    public void setStatusName(String statusName) {
        this.statusName = statusName;
    }

    public void setTasksRetryIntervalName(String tasksRetryIntervalName) {
        this.tasksRetryIntervalName = tasksRetryIntervalName;
    }

    public void setTerminationTopicName(String terminationTopicName) {
        this.terminationTopicName = terminationTopicName;
    }
}
