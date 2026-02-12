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
package org.redisson.remote;

import org.redisson.RedissonBucket;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Optional;
import java.util.concurrent.*;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SyncRemoteProxy extends BaseRemoteProxy {

    public SyncRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
                            Codec codec, String executorId, BaseRemoteService remoteService) {
        super(commandExecutor, name, responseQueueName, codec, executorId, remoteService);
    }

    public <T> T create(Class<T> remoteInterface, RemoteInvocationOptions options) {
        // local copy of the options, to prevent mutation
        RemoteInvocationOptions optionsCopy = new RemoteInvocationOptions(options);
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("toString")) {
                    return proxy.getClass().getName() + "-" + remoteInterface.getName();
                } else if (method.getName().equals("equals")) {
                    return proxy == args[0];
                } else if (method.getName().equals("hashCode")) {
                    return (proxy.getClass().getName() + "-" + remoteInterface.getName()).hashCode();
                }

                if (!optionsCopy.isResultExpected()
                        && !(method.getReturnType().equals(Void.class) || method.getReturnType().equals(Void.TYPE))) {
                    throw new IllegalArgumentException("The noResult option only supports void return value");
                }

                String requestId = remoteService.generateRequestId(args);
                String requestQueueName = getRequestQueueName(remoteInterface);
                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId, method.getName(),
                                                        remoteService.getMethodSignature(method), args, optionsCopy, System.currentTimeMillis());
                
                CompletableFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = pollResponse(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }

                CompletableFuture<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    long timeout = remoteService.getTimeout(optionsCopy.getExecutionTimeoutInMillis(), request);
                    responseFuture = pollResponse(timeout, requestId, false);
                } else {
                    responseFuture = null;
                }
                
                RemotePromise<Object> addPromise = new RemotePromise<Object>(requestId);
                CompletableFuture<Boolean> futureAdd = remoteService.addAsync(requestQueueName, request, addPromise);
                Boolean res;
                try {
                    res = futureAdd.join();
                } catch (Exception e) {
                    if (responseFuture != null) {
                        responseFuture.cancel(false);
                    }
                    if (ackFuture != null) {
                        ackFuture.cancel(false);
                    }
                    throw e.getCause();
                }

                if (!res) {
                    if (responseFuture != null) {
                        responseFuture.cancel(false);
                    }
                    if (ackFuture != null) {
                        ackFuture.cancel(false);
                    }
                    throw new RedisException("Task hasn't been added");
                }
                
                // poll for the ack only if expected
                if (ackFuture != null) {
                    String ackName = remoteService.getAckName(requestId);
                    RemoteServiceAck ack = null;
                    try {
                        ack = ackFuture.toCompletableFuture().get(optionsCopy.getAckTimeoutInMillis(), TimeUnit.MILLISECONDS);
                    } catch (ExecutionException | TimeoutException e) {
                        // skip
                    }
                    if (ack == null) {
                        CompletionStage<RemoteServiceAck> ackFutureAttempt =
                                tryPollAckAgainAsync(optionsCopy, ackName, requestId);
                        try {
                            ack = ackFutureAttempt.toCompletableFuture().get(optionsCopy.getAckTimeoutInMillis(), TimeUnit.MILLISECONDS);
                        } catch (ExecutionException | TimeoutException e) {
                            // skip
                        }
                        if (ack == null) {
                            throw new RemoteServiceAckTimeoutException("No ACK response after "
                                    + optionsCopy.getAckTimeoutInMillis() + "ms for request: " + request);
                        }
                    }
                    new RedissonBucket<>(commandExecutor, ackName).delete();
                }

                // poll for the response only if expected
                if (responseFuture != null) {
                    RemoteServiceResponse response = null;
                    try {
                        response = (RemoteServiceResponse) responseFuture.toCompletableFuture().join();
                    } catch (Exception e) {
                        // skip
                    }
                    if (response == null) {
                        throw new RemoteServiceTimeoutException("No response after "
                                + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + request);
                    }
                    if (response.getError() != null) {
                        throw response.getError();
                    }
                    if (method.getReturnType().equals(Optional.class)) {
                        if (response.getResult() == null) {
                            return Optional.empty();
                        }
                        return Optional.of(response.getResult());
                    }
                    return response.getResult();
                }

                return null;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

}
