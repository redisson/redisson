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

import org.redisson.RedissonBucket;
import org.redisson.RedissonList;
import org.redisson.RedissonMap;
import org.redisson.api.RFuture;
import org.redisson.api.RList;
import org.redisson.api.RMap;
import org.redisson.api.RemoteInvocationOptions;
import org.redisson.client.RedisException;
import org.redisson.client.codec.Codec;
import org.redisson.client.codec.LongCodec;
import org.redisson.client.codec.StringCodec;
import org.redisson.client.protocol.RedisCommands;
import org.redisson.codec.CompositeCodec;
import org.redisson.command.CommandAsyncExecutor;
import org.redisson.executor.RemotePromise;
import org.redisson.misc.CompletableFutureWrapper;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * 
 * @author Nikita Koksharov
 *
 */
public class AsyncRemoteProxy extends BaseRemoteProxy {

    protected final String cancelRequestMapName;
    
    public AsyncRemoteProxy(CommandAsyncExecutor commandExecutor, String name, String responseQueueName,
                            Codec codec, String executorId, String cancelRequestMapName, BaseRemoteService remoteService) {
        super(commandExecutor, name, responseQueueName, codec, executorId, remoteService);
        this.cancelRequestMapName = cancelRequestMapName;
    }
    
    protected List<Class<?>> permittedClasses() {
        return Arrays.asList(RFuture.class);
    }
    
    public <T> T create(Class<T> remoteInterface, RemoteInvocationOptions options,
            Class<?> syncInterface) {
        for (Method m : remoteInterface.getMethods()) {
            try {
                syncInterface.getMethod(m.getName(), m.getParameterTypes());
            } catch (NoSuchMethodException e) {
                throw new IllegalArgumentException("Method '" + m.getName() + "' with params '"
                        + Arrays.toString(m.getParameterTypes()) + "' isn't defined in " + syncInterface);
            } catch (SecurityException e) {
                throw new IllegalArgumentException(e);
            }
            
            boolean permitted = false;
            for (Class<?> clazz : permittedClasses()) {
                if (clazz.isAssignableFrom(m.getReturnType())) {
                    permitted = true;
                    break;
                }
            }
            if (!permitted) {
                throw new IllegalArgumentException(
                        m.getReturnType().getClass() + " isn't allowed as return type");
            }
        }
        
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

                if (!optionsCopy.isResultExpected() && !(method.getReturnType().equals(Void.class)
                        || method.getReturnType().equals(Void.TYPE) || method.getReturnType().equals(RFuture.class))) {
                    throw new IllegalArgumentException("The noResult option only supports void return value");
                }

                String requestId = remoteService.generateRequestId(args);
                String requestQueueName = getRequestQueueName(syncInterface);
                Long ackTimeout = optionsCopy.getAckTimeoutInMillis();
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

                RemotePromise<Object> result = createResultPromise(optionsCopy, requestId, requestQueueName,
                        ackTimeout);
                CompletableFuture<Boolean> addFuture = remoteService.addAsync(requestQueueName, request, result);
                addFuture.whenComplete((res, e) -> {
                        if (e != null) {
                            if (responseFuture != null) {
                                responseFuture.cancel(false);
                            }
                            if (ackFuture != null) {
                                ackFuture.cancel(false);
                            }
                            result.completeExceptionally(e);
                            return;
                        }
                        
                        if (!res) {
                            result.completeExceptionally(new RedisException("Task hasn't been added"));
                            if (responseFuture != null) {
                                responseFuture.cancel(false);
                            }
                            if (ackFuture != null) {
                                ackFuture.cancel(false);
                            }
                            return;
                        }

                                if (optionsCopy.isAckExpected()) {
                                    ackFuture.whenComplete((ack, ex) -> {
                                        if (ex != null) {
                                            if (responseFuture != null) {
                                                responseFuture.cancel(false);
                                            }

                                            result.completeExceptionally(ex);
                                            return;
                                        }

                                        if (ack == null) {
                                            String ackName = remoteService.getAckName(requestId);
                                            CompletionStage<RemoteServiceAck> ackFutureAttempt =
                                                                        tryPollAckAgainAsync(optionsCopy, ackName, requestId);
                                            ackFutureAttempt.whenComplete((re, ex2) -> {
                                                if (ex2 != null) {
                                                    result.completeExceptionally(ex2);
                                                    return;
                                                }

                                                if (re == null) {
                                                    Exception exc = new RemoteServiceAckTimeoutException(
                                                            "No ACK response after "
                                                                    + optionsCopy.getAckTimeoutInMillis()
                                                                    + "ms for request: " + requestId);
                                                    result.completeExceptionally(exc);
                                                    return;
                                                }

                                                awaitResultAsync(optionsCopy, result, ackName, responseFuture);
                                            });
                                        } else {
                                            awaitResultAsync(optionsCopy, result, responseFuture);
                                        }
                                    });
                                } else {
                                    awaitResultAsync(optionsCopy, result, responseFuture);
                                }
                        });

                return convertResult(result, method.getReturnType());
            }

        };
        return (T) Proxy.newProxyInstance(remoteInterface.getClassLoader(), new Class[] { remoteInterface }, handler);
    }

    protected Object convertResult(RemotePromise<Object> result, Class<?> returnType) {
        return new CompletableFutureWrapper<>(result);
    }
    
    private void awaitResultAsync(RemoteInvocationOptions optionsCopy, RemotePromise<Object> result,
            String ackName, CompletableFuture<RRemoteServiceResponse> responseFuture) {
        RFuture<Boolean> deleteFuture = new RedissonBucket<>(commandExecutor, ackName).deleteAsync();
        deleteFuture.whenComplete((res, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            awaitResultAsync(optionsCopy, result, responseFuture);
        });
    }
    
    protected void awaitResultAsync(RemoteInvocationOptions optionsCopy, RemotePromise<Object> result,
            CompletionStage<RRemoteServiceResponse> responseFuture) {
        // poll for the response only if expected
        if (!optionsCopy.isResultExpected()) {
            return;
        }

        responseFuture.whenComplete((res, e) -> {
            if (e != null) {
                result.completeExceptionally(e);
                return;
            }

            if (res == null) {
                RemoteServiceTimeoutException ex = new RemoteServiceTimeoutException("No response after "
                        + optionsCopy.getExecutionTimeoutInMillis() + "ms for request: " + result.getRequestId());
                result.completeExceptionally(ex);
                return;
            }

            if (res instanceof RemoteServiceCancelResponse) {
                result.doCancel(true);
                return;
            }

            RemoteServiceResponse response = (RemoteServiceResponse) res;
            if (response.getError() != null) {
                result.completeExceptionally(response.getError());
                return;
            }

            result.complete(response.getResult());
        });
    }

    private RemotePromise<Object> createResultPromise(RemoteInvocationOptions optionsCopy,
                                                      String requestId, String requestQueueName, Long ackTimeout) {
        RemotePromise<Object> result = new RemotePromise<Object>(requestId) {

            @Override
            public boolean cancel(boolean mayInterruptIfRunning) {
                if (isCancelled()) {
                    return true;
                }
                
                if (isDone()) {
                    return false;
                }
                
                
                if (optionsCopy.isAckExpected()) {
                    String ackName = remoteService.getAckName(requestId);
                    RFuture<Boolean> future = commandExecutor.evalWriteAsync(responseQueueName, LongCodec.INSTANCE,
                            RedisCommands.EVAL_BOOLEAN,
                            "if redis.call('setnx', KEYS[1], 1) == 1 then "
                                + "redis.call('pexpire', KEYS[1], ARGV[1]);"
//                                        + "redis.call('lrem', KEYS[3], 1, ARGV[1]);"
//                                        + "redis.call('pexpire', KEYS[2], ARGV[2]);" 
                                + "return 1;" 
                            + "end;"
                            + "return 0;",
                            Arrays.<Object> asList(ackName),
//                                    Arrays.<Object> asList(ackName, responseQueueName, requestQueueName), 
                            ackTimeout);
                    
                    boolean ackNotSent = commandExecutor.get(future);
                    if (ackNotSent) {
                        RList<Object> list = new RedissonList<>(LongCodec.INSTANCE, commandExecutor, requestQueueName, null);
                        list.remove(requestId);
                        super.cancel(mayInterruptIfRunning);
                        return true;
                    }

                    return executeCancel(mayInterruptIfRunning);
                }

                Boolean removed = commandExecutor.get(remoteService.removeAsync(requestQueueName, requestId));
                if (removed == null || removed) {
                    super.cancel(mayInterruptIfRunning);
                    return true;
                }

                return executeCancel(mayInterruptIfRunning);
            }

            private boolean executeCancel(boolean mayInterruptIfRunning) {
                if (isCancelled()) {
                    return true;
                }
                
                if (isDone()) {
                    return false;
                }

                cancelExecution(optionsCopy, mayInterruptIfRunning, this, cancelRequestMapName);

                try {
                    toCompletableFuture().get(60, TimeUnit.SECONDS);
                } catch (Exception e) {
                    // skip
                }
                return isCancelled();
            }

            @Override
            public CompletableFuture<Boolean> cancelAsync(boolean mayInterruptIfRunning) {
                return AsyncRemoteProxy.this.cancelAsync(optionsCopy, this, requestId, requestQueueName, ackTimeout, mayInterruptIfRunning);
            }
        };
        return result;
    }

    private CompletableFuture<Boolean> cancelAsync(RemoteInvocationOptions optionsCopy, RemotePromise<Object> promise,
                                                   String requestId, String requestQueueName, Long ackTimeout, boolean mayInterruptIfRunning) {
        if (promise.isCancelled()) {
            return CompletableFuture.completedFuture(true);
        }

        if (promise.isDone()) {
            return CompletableFuture.completedFuture(false);
        }

        if (optionsCopy.isAckExpected()) {
            String ackName = remoteService.getAckName(requestId);
            RFuture<Boolean> f = commandExecutor.evalWriteNoRetryAsync(responseQueueName, LongCodec.INSTANCE,
                    RedisCommands.EVAL_BOOLEAN,
                    "if redis.call('setnx', KEYS[1], 1) == 1 then "
                        + "redis.call('pexpire', KEYS[1], ARGV[1]);"
//                                        + "redis.call('lrem', KEYS[3], 1, ARGV[1]);"
//                                        + "redis.call('pexpire', KEYS[2], ARGV[2]);"
                        + "return 1;"
                    + "end;"
                    + "return 0;",
                    Arrays.<Object> asList(ackName),
//                                    Arrays.<Object> asList(ackName, responseQueueName, requestQueueName),
                    ackTimeout);
            CompletableFuture<Boolean> future = f.toCompletableFuture();

            return future.thenCompose(ackNotSent -> {
                if (ackNotSent) {
                    RList<Object> list = new RedissonList<>(LongCodec.INSTANCE, commandExecutor, requestQueueName, null);
                    CompletableFuture<Boolean> removeFuture = list.removeAsync(requestId).toCompletableFuture();
                    return removeFuture.thenApply(res -> {
                        promise.doCancel(mayInterruptIfRunning);
                        return true;
                    });
                }

                return doCancelAsync(mayInterruptIfRunning, promise, optionsCopy);
            });
        }

        CompletableFuture<Boolean> removeFuture = remoteService.removeAsync(requestQueueName, requestId);
        return removeFuture.thenCompose(removed -> {
            if (removed == null || removed) {
                promise.doCancel(mayInterruptIfRunning);
            }

            return doCancelAsync(mayInterruptIfRunning, promise, optionsCopy);
        });
    }

    private CompletableFuture<Boolean> doCancelAsync(boolean mayInterruptIfRunning, RemotePromise<Object> promise, RemoteInvocationOptions optionsCopy) {
        if (promise.isCancelled()) {
            return CompletableFuture.completedFuture(true);
        }

        if (promise.isDone()) {
            return CompletableFuture.completedFuture(false);
        }

        cancelExecution(optionsCopy, mayInterruptIfRunning, promise, cancelRequestMapName);

        return promise.toCompletableFuture().thenApply(r -> promise.isCancelled());
    }

    private void cancelExecution(RemoteInvocationOptions optionsCopy,
            boolean mayInterruptIfRunning, RemotePromise<Object> remotePromise, String cancelRequestMapName) {
        RMap<String, RemoteServiceCancelRequest> canceledRequests = new RedissonMap<>(new CompositeCodec(StringCodec.INSTANCE, codec, codec), commandExecutor, cancelRequestMapName, null, null, null);
        canceledRequests.fastPutAsync(remotePromise.getRequestId().toString(), new RemoteServiceCancelRequest(mayInterruptIfRunning, false));
        canceledRequests.expireAsync(60, TimeUnit.SECONDS);
        
        // subscribe for async result if it's not expected before
        if (!optionsCopy.isResultExpected()) {
            RemoteInvocationOptions options = new RemoteInvocationOptions(optionsCopy);
            options.expectResultWithin(60, TimeUnit.SECONDS);
            CompletionStage<RRemoteServiceResponse> responseFuture = pollResponse(options.getExecutionTimeoutInMillis(), remotePromise.getRequestId(), false);
            awaitResultAsync(options, remotePromise, responseFuture);
        }
    }

}
