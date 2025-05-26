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
package org.redisson.remote;

import io.netty.util.Timeout;
import org.redisson.RedissonBlockingQueue;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RFuture;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.remote.ResponseEntry.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiConsumer;

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
    private final Map<String, ResponseEntry> responses;
    final Codec codec;
    final String executorId;
    final BaseRemoteService remoteService;

    BaseRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
                    Codec codec, String executorId, BaseRemoteService remoteService) {
        super();
        this.commandExecutor = commandExecutor;
        this.name = name;
        this.responseQueueName = responseQueueName;
        this.responses = commandExecutor.getServiceManager().getResponses();
        this.codec = codec;
        this.executorId = executorId;
        this.remoteService = remoteService;
    }

    private final Map<Class<?>, String> requestQueueNameCache = new ConcurrentHashMap<>();
    
    public String getRequestQueueName(Class<?> remoteInterface) {
        return requestQueueNameCache.computeIfAbsent(remoteInterface, k -> "{" + name + ":" + k.getName() + "}");
    }
    
    protected CompletionStage<RemoteServiceAck> tryPollAckAgainAsync(RemoteInvocationOptions optionsCopy,
                                                                     String ackName, String requestId) {
        RFuture<Boolean> ackClientsFuture = commandExecutor.evalWriteNoRetryAsync(ackName, LongCodec.INSTANCE, RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('setnx', KEYS[1], 1) == 1 then " 
                        + "redis.call('pexpire', KEYS[1], ARGV[1]);"
                        + "return 0;" 
                    + "end;" 
                    + "redis.call('del', KEYS[1]);" 
                    + "return 1;",
                Arrays.asList(ackName), optionsCopy.getAckTimeoutInMillis());
        return ackClientsFuture.thenCompose(res -> {
            if (res) {
                return pollResponse(commandExecutor.getServiceManager().getConfig().getTimeout(), requestId, true);
            }
            return CompletableFuture.completedFuture(null);
        });
    }

    protected final <T extends RRemoteServiceResponse> CompletableFuture<T> pollResponse(long timeout,
                                                                                         String requestId, boolean insertFirst) {
        CompletableFuture<T> responseFuture = new CompletableFuture<T>();

        ResponseEntry e = responses.compute(responseQueueName, (key, entry) -> {
            if (entry == null) {
                entry = new ResponseEntry();
            }

            addCancelHandling(requestId, responseFuture);

            Result res = new Result(responseFuture);

            Timeout responseTimeoutFuture = createResponseTimeout(timeout, requestId, responseFuture, res);
            res.setResponseTimeoutFuture(responseTimeoutFuture);

            Map<String, List<Result>> entryResponses = entry.getResponses();
            List<Result> list = entryResponses.computeIfAbsent(requestId, k -> new ArrayList<>(3));

            if (insertFirst) {
                list.add(0, res);
            } else {
                list.add(res);
            }
            return entry;
        });

        if (e.getStarted().compareAndSet(false, true)) {
            pollResponse();
        }

        return responseFuture;
    }

    private <T extends RRemoteServiceResponse> Timeout createResponseTimeout(long timeout, String requestId,
                                                                             CompletableFuture<T> responseFuture, Result res) {
        return commandExecutor.getServiceManager().newTimeout(t -> {
                    responses.computeIfPresent(responseQueueName, (k, entry) -> {
                        RemoteServiceTimeoutException ex = new RemoteServiceTimeoutException("No response after " + timeout + "ms");
                        if (!responseFuture.completeExceptionally(ex)) {
                            return entry;
                        }

                        List<Result> list = entry.getResponses().get(requestId);
                        list.remove(res);
                        if (list.isEmpty()) {
                            entry.getResponses().remove(requestId);
                        }
                        if (entry.getResponses().isEmpty()) {
                            return null;
                        }
                        return entry;
                    });
                }, timeout, TimeUnit.MILLISECONDS);
    }

    private <T extends RRemoteServiceResponse> void addCancelHandling(String requestId, CompletableFuture<T> responseFuture) {
        responseFuture.whenComplete((res, ex) -> {
            if (!responseFuture.isCancelled()) {
                return;
            }

            responses.computeIfPresent(responseQueueName, (key, e) -> {
                List<Result> list = e.getResponses().get(requestId);
                if (list == null) {
                    return e;
                }

                for (Iterator<Result> iterator = list.iterator(); iterator.hasNext();) {
                    Result result = iterator.next();
                    if (result.getPromise() == responseFuture) {
                        result.cancelResponseTimeout();
                        iterator.remove();
                    }
                }
                if (list.isEmpty()) {
                    e.getResponses().remove(requestId);
                }

                if (e.getResponses().isEmpty()) {
                    return null;
                }
                return e;
            });
        });
    }

    private void pollResponse() {
        RBlockingQueue<RRemoteServiceResponse> queue = new RedissonBlockingQueue<>(codec, commandExecutor, responseQueueName);
        RFuture<RRemoteServiceResponse> future = queue.pollAsync(60, TimeUnit.SECONDS);
        future.whenComplete(createResponseListener());
    }

    private BiConsumer<RRemoteServiceResponse, Throwable> createResponseListener() {
        return (response, e) -> {
            if (e != null) {
                if (commandExecutor.getServiceManager().isShuttingDown(e)) {
                    return;
                }

                log.error("Can't get response from {}. Try to increase 'retryDelay' and/or 'retryAttempts' settings", responseQueueName, e);
                return;
            }

            if (response == null) {
                pollResponse();
                return;
            }

            AtomicReference<CompletableFuture<RRemoteServiceResponse>> future = new AtomicReference<>();
            responses.computeIfPresent(responseQueueName, (k, entry) -> {
                String key = response.getId();
                List<Result> list = entry.getResponses().get(key);
                if (list == null) {
                    pollResponse();
                    return entry;
                }

                Result res = list.remove(0);
                if (list.isEmpty()) {
                    entry.getResponses().remove(key);
                }

                CompletableFuture<RRemoteServiceResponse> f = res.getPromise();
                res.cancelResponseTimeout();
                future.set(f);

                if (entry.getResponses().isEmpty()) {
                    return null;
                }

                pollResponse();
                return entry;
            });

            if (future.get() != null) {
                future.get().complete(response);
            }
        };
    }
    
}
