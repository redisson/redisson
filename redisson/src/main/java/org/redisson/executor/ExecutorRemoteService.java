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

    @Override
    protected final RFuture<Boolean> addAsync(RBlockingQueue<RemoteServiceRequest> requestQueue,
            RemoteServiceRequest request, RemotePromise<Object> result) {
        final RPromise<Boolean> promise = commandExecutor.getConnectionManager().newPromise();
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
                    + "redis.call('rpush', KEYS[3], ARGV[1]); "
                    + "redis.call('incr', KEYS[1]);"
                    + "return 1;"
                + "end;"
                + "return 0;", 
                Arrays.<Object>asList(tasksCounterName, statusName, requestQueue.getName()),
                encode(request));
    }
    
    @Override
    protected boolean remove(RBlockingQueue<RemoteServiceRequest> requestQueue, RemoteServiceRequest request) {
        byte[] encodedRequest = encode(request);
        
        return commandExecutor.evalWrite(name, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('lrem', KEYS[1], 1, ARGV[1]) > 0 then "
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
              Arrays.<Object>asList(requestQueue.getName(), tasksCounterName, statusName, terminationTopicName), 
              encodedRequest, RedissonExecutorService.SHUTDOWN_STATE, RedissonExecutorService.TERMINATED_STATE);
    }

}
