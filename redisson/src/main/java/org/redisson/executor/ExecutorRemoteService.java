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
import java.util.concurrent.TimeUnit;

import org.redisson.BaseRemoteService;
import org.redisson.RedissonExecutorService;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.RemoteServiceCancelRequest;
import org.redisson.remote.RemoteServiceCancelResponse;
import org.redisson.remote.RemoteServiceRequest;

import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.FutureListener;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class ExecutorRemoteService extends BaseRemoteService {

    protected String terminationTopicName;
    protected String tasksCounterName;
    protected String statusName;
    protected String tasksName;
    
    public ExecutorRemoteService(Codec codec, RedissonClient redisson, String name, CommandExecutor commandExecutor) {
        super(codec, redisson, name, commandExecutor);
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
    protected final RFuture<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue,
            RemoteServiceRequest request, RemotePromise<Object> result) {
        final RPromise<Boolean> promise = new RedissonPromise<Boolean>();
        RFuture<Boolean> future = addAsync(requestQueue, request);
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

    protected RFuture<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request) {
        return commandExecutor.evalWriteAsync(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                "if redis.call('exists', KEYS[2]) == 0 then "
                    + "redis.call('rpush', KEYS[3], ARGV[2]); "
                    + "redis.call('hset', KEYS[4], ARGV[1], ARGV[2]);"
                    + "redis.call('incr', KEYS[1]);"
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounterName, statusName, requestQueue.getName(), tasksName),
                request.getRequestId(), encode(request));
    }
    
    @Override
    protected boolean remove(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request) {
        return commandExecutor.evalWrite(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "local task = redis.call('hget', KEYS[5], ARGV[1]); " + 
                    "if task ~= false and redis.call('lrem', KEYS[1], 1, task) > 0 then "
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
                  + "redis.call('hdel', KEYS[5], ARGV[1]); "
                  + "return 0;",
              Arrays.<Object>asList(requestQueue.getName(), tasksCounterName, statusName, terminationTopicName, tasksName), 
              request.getRequestId(), RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

    public RFuture<Boolean> cancelExecutionAsync(String requestId) {
        Class<?> syncInterface = RemoteExecutorService.class;
        String requestQueueName = getRequestQueueName(syncInterface);
        String cancelRequestName = getCancelRequestQueueName(syncInterface, requestId);

        if (!redisson.getMap(tasksName, LongCodec.INSTANCE).containsKey(requestId)) {
            return RedissonPromise.newSucceededFuture(false);
        }
        
        RBlockingQueue<RemoteServiceRequest> requestQueue = redisson.getBlockingQueue(requestQueueName, getCodec());

        RemoteServiceRequest request = new RemoteServiceRequest(requestId);
        if (remove(requestQueue, request)) {
            return RedissonPromise.newSucceededFuture(true);
        }
        
        RBlockingQueue<RemoteServiceCancelRequest> cancelRequestQueue = redisson.getBlockingQueue(cancelRequestName, getCodec());
        cancelRequestQueue.putAsync(new RemoteServiceCancelRequest(true, requestId + ":cancel-response"));
        cancelRequestQueue.expireAsync(60, TimeUnit.SECONDS);
        
        final RPromise<Boolean> result = new RedissonPromise<Boolean>();
        String responseQueueName = getResponseQueueName(syncInterface, requestId + ":cancel-response");
        RBlockingQueue<RemoteServiceCancelResponse> responseQueue = redisson.getBlockingQueue(responseQueueName, getCodec());
        final RFuture<RemoteServiceCancelResponse> response = responseQueue.pollAsync(60, TimeUnit.SECONDS);
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
        return result;
    }
    
}
