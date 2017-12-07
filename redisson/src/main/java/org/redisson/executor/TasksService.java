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
import java.util.concurrent.TimeUnit;

import org.redisson.BaseRemoteService;
import org.redisson.RedissonExecutorService;
import org.redisson.api.RFuture;
import org.redisson.api.RMap;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceRequest;
import org.redisson.remote.RequestId;
import org.redisson.remote.ResponseEntry;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

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
    
    public TasksService(Codec codec, RedissonClient redisson, String name, CommandAsyncExecutor commandExecutor, String executorId, ConcurrentMap<String, ResponseEntry> responses) {
        super(codec, redisson, name, commandExecutor, executorId, responses);
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

    @Override
    protected final RFuture<Boolean> addAsync(String requestQueueName,
            RemoteServiceRequest request, RemotePromise<Object> result) {
        final RPromise<Boolean> promise = new RedissonPromise<Boolean>();
        RFuture<Boolean> future = addAsync(requestQueueName, request);
        result.setAddFuture(future);
        
        future.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    promise.tryFailure(future.cause());
                    return;
                }
                
                if (!future.getNow()) {
                    promise.cancel(true);
                    return;
                }
                
                promise.trySuccess(true);
            }
        });
        
        return promise;
    }

    protected CommandAsyncExecutor getAddCommandExecutor() {
        return commandExecutor;
    }
    
    protected RFuture<Boolean> addAsync(String requestQueueName, RemoteServiceRequest request) {
        request.getArgs()[3] = request.getId();
        
        return getAddCommandExecutor().evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "redis.call('hset', KEYS[4], ARGV[1], ARGV[2]);"
                    + "redis.call('rpush', KEYS[3], ARGV[1]); "
                    + "redis.call('incr', KEYS[1]);"
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounterName, statusName, requestQueueName, tasksName),
                request.getId(), encode(request));
    }
    
    @Override
    protected RFuture<Boolean> removeAsync(String requestQueueName, RequestId taskId) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "local task = redis.call('hget', KEYS[5], ARGV[1]); " + 
                    "if task ~= false and redis.call('lrem', KEYS[1], 1, ARGV[1]) > 0 then "
                      + "redis.call('hdel', KEYS[5], ARGV[1]); "
                      + "if redis.call('decr', KEYS[2]) == 0 then "
                          + "redis.call('del', KEYS[2]);"
                          + "if redis.call('get', KEYS[3]) == ARGV[2] then "
                           + "redis.call('set', KEYS[3], ARGV[3]);"
                           + "redis.call('publish', KEYS[4], ARGV[3]);"
                          + "end;"
                      + "end;"
                      + "return 1;"
                  + "end;"
                  + "return 0;",
              Arrays.<Object>asList(requestQueueName, tasksCounterName, statusName, terminationTopicName, tasksName), 
              taskId.toString(), RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

    public RFuture<Boolean> cancelExecutionAsync(final RequestId requestId) {
        final Class<?> syncInterface = RemoteExecutorService.class;

        if (!redisson.getMap(tasksName, LongCodec.INSTANCE).containsKey(requestId)) {
            return RedissonPromise.newSucceededFuture(false);
        }

        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        
        String requestQueueName = getRequestQueueName(syncInterface);
        RFuture<Boolean> removeFuture = removeAsync(requestQueueName, requestId);
        removeFuture.addListener(new FutureListener<Boolean>() {
            @Override
            public void operationComplete(Future<Boolean> future) throws Exception {
                if (!future.isSuccess()) {
                    result.tryFailure(future.cause());
                    return;
                }
                
                if (future.getNow()) {
                    result.trySuccess(true);
                } else {
                    RMap<String, RemoteServiceCancelRequest> canceledRequests = redisson.getMap(cancelRequestMapName, codec);
                    canceledRequests.putAsync(requestId.toString(), new RemoteServiceCancelRequest(true, true));
                    canceledRequests.expireAsync(60, TimeUnit.SECONDS);
                    
                    final RPromise<RemoteServiceCancelResponse> response = new RedissonPromise<RemoteServiceCancelResponse>();
                    scheduleCheck(cancelResponseMapName, requestId, response);
                    response.addListener(new FutureListener<RemoteServiceCancelResponse>() {
                        @Override
                        public void operationComplete(Future<RemoteServiceCancelResponse> future) throws Exception {
                            if (!future.isSuccess()) {
                                result.tryFailure(future.cause());
                                return;
                            }
                            
                            if (response.getNow() == null) {
                                result.trySuccess(false);
                                return;
                            }
                            result.trySuccess(response.getNow().isCanceled());
                        }
                    });
                }
            }
        });

        return result;
    }
    
}
