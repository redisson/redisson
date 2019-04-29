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

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.concurrent.ConcurrentMap;

import org.redisson.RedissonBucket;
import org.redisson.api.RFuture;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.RPromise;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class SyncRemoteProxy extends BaseRemoteProxy {

    public SyncRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
            ConcurrentMap<String, ResponseEntry> responses, Codec codec, String executorId, BaseRemoteService remoteService) {
        super(commandExecutor, name, responseQueueName, responses, codec, executorId, remoteService);
    }

    public <T> T create(Class<T> remoteInterface, RemoteInvocationOptions options) {
        // local copy of the options, to prevent mutation
        RemoteInvocationOptions optionsCopy = new RemoteInvocationOptions(options);
        String toString = getClass().getSimpleName() + "-" + remoteInterface.getSimpleName() + "-proxy-"
                + remoteService.generateRequestId();
        InvocationHandler handler = new InvocationHandler() {
            @Override
            public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
                if (method.getName().equals("toString")) {
                    return toString;
                } else if (method.getName().equals("equals")) {
                    return proxy == args[0];
                } else if (method.getName().equals("hashCode")) {
                    return toString.hashCode();
                }

                if (!optionsCopy.isResultExpected()
                        && !(method.getReturnType().equals(Void.class) || method.getReturnType().equals(Void.TYPE)))
                    throw new IllegalArgumentException("The noResult option only supports void return value");

                RequestId requestId = remoteService.generateRequestId();

                String requestQueueName = getRequestQueueName(remoteInterface);
                RemoteServiceRequest request = new RemoteServiceRequest(executorId, requestId.toString(), method.getName(), 
                                                        remoteService.getMethodSignature(method), args, optionsCopy, System.currentTimeMillis());
                
                final RFuture<RemoteServiceAck> ackFuture;
                if (optionsCopy.isAckExpected()) {
                    ackFuture = pollResponse(optionsCopy.getAckTimeoutInMillis(), requestId, false);
                } else {
                    ackFuture = null;
                }
                
                final RPromise<RRemoteServiceResponse> responseFuture;
                if (optionsCopy.isResultExpected()) {
                    long timeout = remoteService.getTimeout(optionsCopy.getExecutionTimeoutInMillis(), request);
                    responseFuture = pollResponse(timeout, requestId, false);
                } else {
                    responseFuture = null;
                }
                
                RemotePromise<Object> addPromise = new RemotePromise<Object>(requestId);
                RFuture<Boolean> futureAdd = remoteService.addAsync(requestQueueName, request, addPromise);
                futureAdd.await();
                if (!futureAdd.isSuccess()) {
                    if (responseFuture != null) {
                        responseFuture.cancel(false);
                    }
                    if (ackFuture != null) {
                        ackFuture.cancel(false);
                    }
                    throw futureAdd.cause();
                }
                
                if (!futureAdd.get()) {
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
                    ackFuture.await(optionsCopy.getAckTimeoutInMillis());
                    RemoteServiceAck ack = ackFuture.getNow();
                    if (ack == null) {
                        RFuture<RemoteServiceAck> ackFutureAttempt = 
                                tryPollAckAgainAsync(optionsCopy, ackName, requestId);
                        ackFutureAttempt.await(optionsCopy.getAckTimeoutInMillis());
                        ack = ackFutureAttempt.getNow();
                        if (ack == null) {
                            throw new RemoteServiceAckTimeoutException("No ACK response after "
                                    + optionsCopy.getAckTimeoutInMillis() + "ms for request: " + request);
                        }
                    }
                    new RedissonBucket<>(commandExecutor, ackName).delete();
                }

                // poll for the response only if expected
                if (responseFuture != null) {
                    responseFuture.awaitUninterruptibly();
                    RemoteServiceResponse response = (RemoteServiceResponse) responseFuture.getNow();
                    if (response == null) {
                        throw new RemoteServiceTimeoutException("No response after "
                                + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + request);
                    }
                    if (response.getError() != null) {
                        throw response.getError();
                    }
                    return response.getResult();
                }

                return null;
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

}
