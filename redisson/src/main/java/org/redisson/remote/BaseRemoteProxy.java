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
package org.redisson.remote;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;
import java.util.function.BiConsumer;

import org.redisson.RedissonBlockingQueue;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.misc.RPromise;
import org.redisson.misc.RedissonPromise;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.netty.util.concurrent.ScheduledFuture;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public abstract class BaseRemoteProxy {

    private final Logger log = LoggerFactory.getLogger(getClass());
    
    final CommandAsyncExecutor commandExecutor;
    private final String name;
    final String responseQueueName;
    private final ConcurrentMap<String, ResponseEntry> responses;
    final Codec codec;
    final String executorId;
    final BaseRemoteService remoteService;
    
    BaseRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
            ConcurrentMap<String, ResponseEntry> responses, Codec codec, String executorId, BaseRemoteService remoteService) {
        super();
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.responseQueueName = responseQueueName;
        this.responses = responses;
        this.codec = codec;
        this.executorId = executorId;
        this.remoteService = remoteService;
    }

    private final Map<Class<?>, String> requestQueueNameCache = new ConcurrentHashMap<>();
    
    public String getRequestQueueName(Class<?> remoteInterface) {
        String str = requestQueueNameCache.get(remoteInterface);
        if (str == null) {
            str = "{" + name + ":" + remoteInterface.getName() + "}";
            requestQueueNameCache.put(remoteInterface, str);
        }
        return str;
    }
    
    protected RFuture<RemoteServiceAck> tryPollAckAgainAsync(RemoteInvocationOptions optionsCopy,
            String ackName, RequestId requestId) {
        RPromise<RemoteServiceAck> promise = new RedissonPromise<RemoteServiceAck>();
        RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                        + "redis.call('pexpire', KEYS[1], ARGV[1]);"
                        + "return 0;" 
                    + "end;" 
                    + "redis.call('del', KEYS[1]);" 
                    + "return 1;",
                Arrays.<Object> asList(ackName), optionsCopy.getAckTimeoutInMillis());
        ackClientsFuture.onComplete((res, e) -> {
            if (e != null) {
                promise.tryFailure(e);
                return;
            }

            if (res) {
                RPromise<RemoteServiceAck> ackFuture = pollResponse(commandExecutor.getConnectionManager().getConfig().getTimeout(), requestId, true);
                ackFuture.onComplete((r, ex) -> {
                    if (ex != null) {
                        promise.tryFailure(ex);
                        return;
                    }

                    promise.trySuccess(r);
                });
            } else {
                promise.trySuccess(null);
            }
        });
        return promise;
    }

    protected <T extends RRemoteServiceResponse> RPromise<T> pollResponse(long timeout,
            RequestId requestId, boolean insertFirst) {
        RPromise<T> responseFuture = new RedissonPromise<T>();

        ResponseEntry entry;
        synchronized (responses) {
            entry = responses.get(responseQueueName);
            if (entry == null) {
                entry = new ResponseEntry();
                ResponseEntry oldEntry = responses.putIfAbsent(responseQueueName, entry);
                if (oldEntry != null) {
                    entry = oldEntry;
                }
            }
            
            responseFuture.onComplete((res, ex) -> {
                if (responseFuture.isCancelled()) {
                    synchronized (responses) {
                        ResponseEntry e = responses.get(responseQueueName);
                        List<Result> list = e.getResponses().get(requestId);
                        if (list == null) {
                            return;
                        }
                        
                        for (Iterator<Result> iterator = list.iterator(); iterator.hasNext();) {
                            Result result = iterator.next();
                            if (result.getPromise() == responseFuture) {
                                result.getScheduledFuture().cancel(true);
                                iterator.remove();
                            }
                        }
                        if (list.isEmpty()) {
                            e.getResponses().remove(requestId);
                        }

                        if (e.getResponses().isEmpty()) {
                            responses.remove(responseQueueName, e);
                        }
                    }
                }
            });
            
            ScheduledFuture<?> future = commandExecutor.getConnectionManager().getGroup().schedule(new Runnable() {
                @Override
                public void run() {
                    synchronized (responses) {
                        ResponseEntry entry = responses.get(responseQueueName);
                        if (entry == null) {
                            return;
                        }
                        
                        RemoteServiceTimeoutException ex = new RemoteServiceTimeoutException("No response after " + timeout + "ms");
                        if (responseFuture.tryFailure(ex)) {
                            List<Result> list = entry.getResponses().get(requestId);
                            list.remove(0);
                            if (list.isEmpty()) {
                                entry.getResponses().remove(requestId);
                            }
                            if (entry.getResponses().isEmpty()) {
                                responses.remove(responseQueueName, entry);
                            }
                        }
                    }
                }
            }, timeout, TimeUnit.MILLISECONDS);

            Map<RequestId, List<Result>> entryResponses = entry.getResponses();
            List<Result> list = entryResponses.get(requestId);
            if (list == null) {
                list = new ArrayList<>(3);
                entryResponses.put(requestId, list);
            }
            
            Result res = new Result(responseFuture, future);
            if (insertFirst) {
                list.add(0, res);
            } else {
                list.add(res);
            }
        }

        
        pollResponse(entry);
        return responseFuture;
    }

    private <V> RBlockingQueue<V> getBlockingQueue(String name, Codec codec) {
        return new RedissonBlockingQueue<V>(codec, commandExecutor, name, null);
    }
    
    private void pollResponse(ResponseEntry ent) {
        if (!ent.getStarted().compareAndSet(false, true)) {
            return;
        }
        
        RBlockingQueue<RRemoteServiceResponse> queue = getBlockingQueue(responseQueueName, codec);
        RFuture<RRemoteServiceResponse> future = queue.takeAsync();
        future.onComplete(createResponseListener());
    }

    private BiConsumer<RRemoteServiceResponse, Throwable> createResponseListener() {
        return (response, e) -> {
            if (e != null) {
                log.error("Can't get response from " + responseQueueName, e);
                return;
            }
            
            RPromise<RRemoteServiceResponse> promise;
            synchronized (responses) {
                ResponseEntry entry = responses.get(responseQueueName);
                if (entry == null) {
                    return;
                }
                
                RequestId key = new RequestId(response.getId());
                List<Result> list = entry.getResponses().get(key);
                if (list == null) {
                    RBlockingQueue<RRemoteServiceResponse> responseQueue = getBlockingQueue(responseQueueName, codec);
                    responseQueue.takeAsync().onComplete(createResponseListener());
                    return;
                }
                
                Result res = list.remove(0);
                if (list.isEmpty()) {
                    entry.getResponses().remove(key);
                }

                promise = res.getPromise();
                res.getScheduledFuture().cancel(true);
                
                if (entry.getResponses().isEmpty()) {
                    responses.remove(responseQueueName, entry);
                } else {
                    RBlockingQueue<RRemoteServiceResponse> responseQueue = getBlockingQueue(responseQueueName, codec);
                    responseQueue.takeAsync().onComplete(createResponseListener());
                }
            }

            if (promise != null) {
                promise.trySuccess(response);
            }
        };
    }
    
}
